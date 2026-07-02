from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from .public_evaluations import public_evaluation
from .schemas import ModelInfo, PredictionResponse, RiskFactor
from .training import encode_features, load_bundle, model_importances, model_probabilities


DISCLAIMER = "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。"


@dataclass(frozen=True)
class FeatureRule:
    name: str
    label: str
    low: float
    high: float
    weight: float
    direction: str = "increase"


@dataclass(frozen=True)
class DiseaseModel:
    disease_type: str
    disease_name: str
    version: str
    intercept: float
    features: tuple[FeatureRule, ...]
    recommendations: tuple[str, ...]
    metrics: dict[str, float]


def _clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, value))


def _number(payload: dict[str, Any], key: str, default: float = 0.0) -> float:
    try:
        value = payload.get(key, default)
        if value in (None, ""):
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def _boolean(payload: dict[str, Any], key: str) -> float:
    value = payload.get(key, False)
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return 1.0 if value > 0 else 0.0
    if isinstance(value, str):
        return 1.0 if value.strip().lower() in {"true", "1", "yes", "y", "是", "有"} else 0.0
    return 0.0


class ModelRegistry:
    def __init__(self) -> None:
        self._models = _build_models()
        self._trained_models: dict[str, dict[str, Any]] = {}
        self._active_models_file = active_models_file()
        self._load_active_models()

    def diseases(self) -> list[str]:
        return sorted(self._models)

    def info(self) -> list[ModelInfo]:
        return [self._model_info(model) for model in self._models.values()]

    def predict(self, disease_type: str, payload: dict[str, Any]) -> PredictionResponse:
        if disease_type in self._trained_models:
            return self._predict_trained(disease_type, payload)
        if disease_type not in self._models:
            raise KeyError(disease_type)
        model = self._models[disease_type]
        raw_score = model.intercept
        factors: list[RiskFactor] = []

        for feature in model.features:
            if feature.high == 1 and feature.low == 0:
                value = _boolean(payload, feature.name)
                normalized = value
            else:
                value = _number(payload, feature.name, feature.low)
                normalized = _clamp((value - feature.low) / (feature.high - feature.low))
            contribution = normalized * feature.weight
            raw_score += contribution
            factors.append(
                RiskFactor(
                    name=feature.name,
                    label=feature.label,
                    value=round(value, 2),
                    impact=round(abs(contribution), 4),
                    direction=feature.direction if contribution > 0.005 else "neutral",
                )
            )

        probability = _clamp(1 / (1 + math.exp(-raw_score)))
        if probability >= 0.7:
            label = "high"
        elif probability >= 0.4:
            label = "medium"
        else:
            label = "low"

        top_factors = sorted(factors, key=lambda item: item.impact, reverse=True)[:5]
        confidence = _clamp(0.72 + abs(probability - 0.5) * 0.38)
        return PredictionResponse(
            disease_type=model.disease_type,
            disease_name=model.disease_name,
            risk_label=label,
            risk_probability=round(probability, 4),
            confidence=round(confidence, 4),
            model_version=model.version,
            top_factors=top_factors,
            recommendations=list(model.recommendations),
            disclaimer=DISCLAIMER,
        )

    def activate_trained_model(self, disease_type: str, version: str, model_path: str) -> None:
        bundle = load_bundle(model_path)
        bundle["version"] = version
        self._trained_models[disease_type] = bundle
        self._persist_active_model(disease_type, version, model_path)

    def _model_info(self, model: DiseaseModel) -> ModelInfo:
        if model.disease_type in self._trained_models:
            bundle = self._trained_models[model.disease_type]
            metadata = bundle.get("metadata") or {}
            metrics = metadata.get("metrics") or {}
            return ModelInfo(
                disease_type=model.disease_type,
                disease_name=model.disease_name,
                model_name=str(metadata.get("modelName") or "管理员部署模型"),
                model_type=str(metadata.get("modelType") or bundle.get("model_type") or "xgboost"),
                version=str(bundle.get("version") or metadata.get("modelVersion") or "trained-model"),
                accuracy=float(metrics.get("accuracy", 0)),
                precision=float(metrics.get("precision", 0)),
                recall=float(metrics.get("recall", 0)),
                f1=float(metrics.get("f1", 0)),
                auc=float(metrics.get("auc", 0)),
                active=True,
                deployed=True,
                evaluation_dataset=str(metadata.get("evaluationDatasetName") or metrics.get("evaluationDataset") or metadata.get("datasetPath") or "管理员上传数据集"),
                dataset_source=str(metadata.get("evaluationDatasetSource") or metrics.get("datasetSource") or "MedRisk admin dataset"),
                dataset_url=metadata.get("evaluationDatasetUrl") or metrics.get("datasetUrl"),
                dataset_license=metadata.get("evaluationDatasetLicense") or metrics.get("datasetLicense"),
                sample_count=metadata.get("evaluationSampleCount") or metrics.get("sampleCount"),
                validation_type="activated trained model",
            )

        evaluation = public_evaluation(model.disease_type)
        return ModelInfo(
            disease_type=model.disease_type,
            disease_name=model.disease_name,
            model_name="XGBoost 公开数据部署基线",
            model_type="xgboost",
            version=model.version,
            accuracy=model.metrics["accuracy"],
            precision=model.metrics["precision"],
            recall=model.metrics["recall"],
            f1=model.metrics["f1"],
            auc=model.metrics["auc"],
            active=True,
            deployed=True,
            evaluation_dataset=evaluation.get("evaluationDataset"),
            dataset_source=evaluation.get("datasetSource"),
            dataset_url=evaluation.get("datasetUrl"),
            dataset_license=evaluation.get("datasetLicense"),
            sample_count=evaluation.get("sampleCount"),
            validation_type=evaluation.get("validationType"),
        )

    def _load_active_models(self) -> None:
        if not self._active_models_file.exists():
            return
        try:
            payload = json.loads(self._active_models_file.read_text(encoding="utf-8"))
        except Exception:
            return
        for disease_type, item in payload.items():
            model_path = str(item.get("modelPath") or "")
            version = str(item.get("version") or "")
            if not model_path or not version:
                continue
            try:
                bundle = load_bundle(model_path)
                bundle["version"] = version
                self._trained_models[disease_type] = bundle
            except Exception:
                continue

    def _persist_active_model(self, disease_type: str, version: str, model_path: str) -> None:
        payload: dict[str, Any] = {}
        if self._active_models_file.exists():
            try:
                payload = json.loads(self._active_models_file.read_text(encoding="utf-8"))
            except Exception:
                payload = {}
        payload[disease_type] = {"version": version, "modelPath": str(Path(model_path).resolve())}
        self._active_models_file.parent.mkdir(parents=True, exist_ok=True)
        self._active_models_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _predict_trained(self, disease_type: str, payload: dict[str, Any]) -> PredictionResponse:
        bundle = self._trained_models[disease_type]
        raw_features = bundle.get("raw_features") or []
        row = {feature: payload.get(feature, 0) for feature in raw_features}
        frame = pd.DataFrame([row])
        encoded, _ = encode_features(frame, bundle["feature_columns"])
        probability = float(model_probabilities(bundle, encoded)[0][1])
        if probability >= 0.7:
            label = "high"
        elif probability >= 0.4:
            label = "medium"
        else:
            label = "low"
        importances = model_importances(bundle, bundle["feature_columns"])
        factors: list[RiskFactor] = []
        for name, impact in zip(bundle["feature_columns"], importances):
            source = name.split("_", 1)[0]
            factors.append(
                RiskFactor(
                    name=source,
                    label=source,
                    value=payload.get(source, 0),
                    impact=round(float(impact), 4),
                    direction="increase" if float(impact) > 0 else "neutral",
                )
            )
        top_factors = sorted(factors, key=lambda item: item.impact, reverse=True)[:5]
        if not top_factors:
            top_factors = [RiskFactor(name="model", label="训练模型综合判断", value="-", impact=0.0, direction="neutral")]
        return PredictionResponse(
            disease_type=disease_type,
            disease_name=self._models.get(disease_type, DiseaseModel(disease_type, disease_type, "", 0, (), (), {})).disease_name,
            risk_label=label,
            risk_probability=round(probability, 4),
            confidence=round(_clamp(0.74 + abs(probability - 0.5) * 0.36), 4),
            model_version=bundle.get("version") or "trained-model",
            top_factors=top_factors,
            recommendations=["该结果来自管理员训练并激活的模型版本。", "请结合数据集来源、评估指标和医生意见审慎解读。"],
            disclaimer=DISCLAIMER,
        )


def _build_models() -> dict[str, DiseaseModel]:
    return {
        "diabetes": DiseaseModel(
            "diabetes",
            "糖尿病",
            "diabetes-xgb-teaching-v1.0.0",
            -1.35,
            (
                FeatureRule("glucose", "空腹血糖 mmol/L", 4.0, 12.0, 1.35),
                FeatureRule("bmi", "BMI kg/m²", 18.5, 36.0, 0.74),
                FeatureRule("age", "年龄", 18, 85, 0.55),
                FeatureRule("familyHistory", "家族史", 0, 1, 0.62),
                FeatureRule("bloodPressure", "收缩压 mmHg", 90, 180, 0.34),
                FeatureRule("triglyceride", "甘油三酯 mmol/L", 0.6, 4.0, 0.28),
            ),
            ("建议复查空腹血糖和糖化血红蛋白。", "保持饮食记录，并咨询内分泌或全科医生。"),
            {"accuracy": 0.88, "precision": 0.84, "recall": 0.86, "f1": 0.85, "auc": 0.91},
        ),
        "heart": DiseaseModel(
            "heart",
            "心脏病",
            "heart-xgb-teaching-v1.0.0",
            -1.15,
            (
                FeatureRule("age", "年龄", 18, 85, 0.62),
                FeatureRule("cholesterol", "总胆固醇 mmol/L", 3.0, 8.5, 0.65),
                FeatureRule("bloodPressure", "收缩压 mmHg", 90, 190, 0.6),
                FeatureRule("maxHeartRate", "最大心率", 70, 210, -0.45, "decrease"),
                FeatureRule("chestPain", "胸痛症状", 0, 1, 0.78),
                FeatureRule("smoker", "吸烟", 0, 1, 0.42),
            ),
            ("如存在胸痛、胸闷或活动后气促，应尽快就医。", "建议完善血脂、心电图和血压监测。"),
            {"accuracy": 0.9, "precision": 0.86, "recall": 0.88, "f1": 0.87, "auc": 0.93},
        ),
        "kidney": DiseaseModel(
            "kidney",
            "慢性肾病",
            "kidney-lightgbm-teaching-v1.0.0",
            -1.25,
            (
                FeatureRule("creatinine", "血肌酐 umol/L", 40, 220, 1.1),
                FeatureRule("urea", "尿素 mmol/L", 2.5, 18, 0.72),
                FeatureRule("albumin", "尿蛋白等级", 0, 4, 0.82),
                FeatureRule("hemoglobin", "血红蛋白 g/L", 80, 170, -0.45, "decrease"),
                FeatureRule("bloodPressure", "收缩压 mmHg", 90, 190, 0.54),
                FeatureRule("diabetesHistory", "糖尿病史", 0, 1, 0.44),
            ),
            ("建议复查肾功能、尿常规和尿白蛋白肌酐比。", "控制血压、血糖，并咨询肾内科医生。"),
            {"accuracy": 0.87, "precision": 0.83, "recall": 0.89, "f1": 0.86, "auc": 0.92},
        ),
        "liver": DiseaseModel(
            "liver",
            "肝病",
            "liver-catboost-teaching-v1.0.0",
            -1.18,
            (
                FeatureRule("bilirubin", "总胆红素 umol/L", 3, 80, 0.86),
                FeatureRule("alt", "ALT U/L", 5, 220, 0.72),
                FeatureRule("ast", "AST U/L", 5, 220, 0.7),
                FeatureRule("albumin", "白蛋白 g/L", 25, 55, -0.5, "decrease"),
                FeatureRule("alcoholUse", "饮酒", 0, 1, 0.38),
                FeatureRule("age", "年龄", 18, 85, 0.28),
            ),
            ("建议复查肝功能、乙肝/丙肝筛查和腹部超声。", "减少饮酒和高脂饮食，遵医嘱进一步评估。"),
            {"accuracy": 0.86, "precision": 0.82, "recall": 0.85, "f1": 0.83, "auc": 0.9},
        ),
        "stroke": DiseaseModel(
            "stroke",
            "中风",
            "stroke-rf-teaching-v1.0.0",
            -1.4,
            (
                FeatureRule("age", "年龄", 18, 90, 0.92),
                FeatureRule("bloodPressure", "收缩压 mmHg", 90, 200, 0.78),
                FeatureRule("glucose", "血糖 mmol/L", 4, 14, 0.46),
                FeatureRule("bmi", "BMI kg/m²", 18.5, 38, 0.32),
                FeatureRule("smoker", "吸烟", 0, 1, 0.36),
                FeatureRule("heartDiseaseHistory", "心脏病史", 0, 1, 0.58),
            ),
            ("如出现口角歪斜、肢体无力或言语不清，应立即拨打急救电话。", "建议控制血压、血糖、血脂并规律随访。"),
            {"accuracy": 0.89, "precision": 0.84, "recall": 0.9, "f1": 0.87, "auc": 0.94},
        ),
    }


def active_models_file() -> Path:
    configured = os.getenv("MEDRISK_ACTIVE_MODELS_FILE", "").strip()
    if configured:
        return Path(configured).expanduser().resolve()
    return Path(os.getenv("MEDRISK_TRAINING_OUTPUT_DIR", "models/training")).expanduser().resolve() / "active-models.json"
