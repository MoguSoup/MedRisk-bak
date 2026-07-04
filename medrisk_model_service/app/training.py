from __future__ import annotations

import json
import os
import threading
import time
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.calibration import calibration_curve
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    log_loss,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
)
from sklearn.model_selection import train_test_split
from xgboost.callback import TrainingCallback

from .model_adapters import create_adapter, normalize_model_type


STATUS_PREPARE = "准备训练"
STATUS_RUNNING = "训练中"
STATUS_SUCCESS = "训练成功"
STATUS_FAILED = "训练失败"
STATUS_STOP = "训练终止"
MAX_METRIC_CURVE_POINTS = 400
TASK_RETENTION_SECONDS = int(os.getenv("MEDRISK_TASK_RETENTION_SECONDS", "7200"))
MAX_TASK_CACHE = int(os.getenv("MEDRISK_TASK_CACHE_LIMIT", "200"))


@dataclass
class TrainingTask:
    task_id: str
    status: str = STATUS_PREPARE
    progress: int = 0
    current_loss: float | None = None
    message: str | None = None
    model_version: str | None = None
    model_path: str | None = None
    history_path: str | None = None
    metadata_path: str | None = None
    model_type: str | None = None
    hyperparameters: dict[str, Any] | None = None
    metrics: dict[str, Any] | None = None
    history: dict[str, list[float]] = field(default_factory=dict)
    stop_requested: bool = False
    completed_at: float | None = None


class StopTraining(Exception):
    pass


class StopCallback(TrainingCallback):
    def __init__(self, task: TrainingTask):
        self.task = task

    def after_iteration(self, model: Any, epoch: int, evals_log: dict[str, Any]) -> bool:
        if self.task.stop_requested:
            raise StopTraining()
        total = max(1, int(self.task.message or "1"))
        self.task.progress = min(95, int((epoch + 1) / total * 95))
        valid = evals_log.get("validation_1") or evals_log.get("validation_0") or {}
        loss_values = valid.get("logloss") or []
        if loss_values:
            self.task.current_loss = float(loss_values[-1])
        return False


class TrainingManager:
    def __init__(self) -> None:
        self._tasks: dict[str, TrainingTask] = {}
        self._lock = threading.Lock()

    def start(self, request: Any) -> TrainingTask:
        with self._lock:
            self._purge_finished_locked()
            model_type = normalize_model_type(getattr(request, "modelType", None))
            hyperparameters = normalize_hyperparameters(model_type, getattr(request, "hyperparameters", {}) or {}, request)
            task = TrainingTask(
                task_id=str(request.taskId),
                message=str(hyperparameters.get("nEstimators", request.epochs)),
                model_type=model_type,
                hyperparameters=hyperparameters,
            )
            self._tasks[task.task_id] = task
        thread = threading.Thread(target=self._run_training, args=(task, request), daemon=True)
        thread.start()
        return task

    def stop(self, task_id: str) -> TrainingTask:
        task = self.get(task_id)
        task.stop_requested = True
        if task.status in {STATUS_PREPARE, STATUS_RUNNING}:
            task.status = STATUS_STOP
            task.progress = min(task.progress, 99)
            task.completed_at = time.time()
        return task

    def get(self, task_id: str) -> TrainingTask:
        with self._lock:
            if task_id not in self._tasks:
                self._tasks[task_id] = self._load_task_from_disk(task_id)
            return self._tasks[task_id]

    def history(self, task_id: str) -> dict[str, list[float]]:
        return self.get(task_id).history

    def evaluate(self, model_version: str, dataset_path: str, model_path: str | None = None) -> tuple[dict[str, Any], list[dict[str, Any]]]:
        path = Path(model_path) if model_path else default_output_root() / model_version / "model.joblib"
        bundle = joblib.load(path)
        df = read_dataset(Path(dataset_path))
        X, y = split_features_label(df)
        metrics, predictions = evaluate_bundle(bundle, X, y)
        return metrics, predictions

    def _run_training(self, task: TrainingTask, request: Any) -> None:
        try:
            task.status = STATUS_RUNNING
            task.progress = 1
            dataset_path = Path(request.datasetPath)
            df = read_dataset(dataset_path)
            X, y = split_features_label(df)
            if y.nunique() != 2:
                raise ValueError("label 列必须是二分类标签")
            stratify = y if y.value_counts().min() >= 2 else None
            model_type = normalize_model_type(getattr(request, "modelType", None))
            hyperparameters = normalize_hyperparameters(model_type, getattr(request, "hyperparameters", {}) or {}, request)
            request.hyperparameters = hyperparameters
            task.model_type = model_type
            task.hyperparameters = hyperparameters
            test_size = float(hyperparameters.get("testSize", request.testSize))
            X_train, X_test, y_train, y_test = train_test_split(
                X,
                y,
                test_size=test_size,
                random_state=42,
                stratify=stratify,
            )
            train_matrix, feature_columns = encode_features(X_train)
            test_matrix, _ = encode_features(X_test, feature_columns)
            label_map = {label: index for index, label in enumerate(sorted(y.unique(), key=str))}
            y_train_encoded = y_train.map(label_map).astype(int)
            y_test_encoded = y_test.map(label_map).astype(int)
            adapter = create_adapter(model_type)
            history = adapter.fit(train_matrix, y_train_encoded, test_matrix, y_test_encoded, request, task, StopCallback)
            bundle = {
                "adapter": adapter,
                "model_type": model_type,
                "feature_columns": feature_columns,
                "raw_features": list(X.columns),
                "label_map": label_map,
                "disease_type": request.diseaseType,
                "hyperparameters": hyperparameters,
            }
            bundle["decision_threshold"] = select_decision_threshold(bundle, X_test, y_test)
            if getattr(request, "evaluationDatasetPath", None):
                eval_df = read_dataset(Path(request.evaluationDatasetPath))
                X_eval, y_eval = split_features_label(eval_df)
                metrics, predictions = evaluate_bundle(bundle, X_eval, y_eval)
                metrics.update(evaluation_metadata(request, len(eval_df)))
            else:
                metrics, predictions = evaluate_bundle(bundle, X_test, y_test)
            training_note = getattr(adapter, "training_note", None)
            if training_note:
                metrics["trainingNote"] = training_note
            version = f"{request.diseaseType}-{model_type}-{slugify(request.modelName)}-{int(time.time())}"
            output_dir = Path(request.outputDir) if request.outputDir else default_output_root() / version
            output_dir.mkdir(parents=True, exist_ok=True)
            model_path = output_dir / "model.joblib"
            history_path = output_dir / "history.json"
            metadata_path = output_dir / "metadata.json"
            joblib.dump(bundle, model_path)
            write_json(history_path, history)
            write_json(metadata_path, {
                "version": version,
                "diseaseType": request.diseaseType,
                "modelName": request.modelName,
                "modelType": model_type,
                "hyperparameters": hyperparameters,
                "datasetPath": str(dataset_path),
                "evaluationDatasetPath": getattr(request, "evaluationDatasetPath", None),
                "evaluationDatasetName": getattr(request, "evaluationDatasetName", None),
                "evaluationDatasetSource": getattr(request, "evaluationDatasetSource", None),
                "evaluationDatasetUrl": getattr(request, "evaluationDatasetUrl", None),
                "evaluationDatasetLicense": getattr(request, "evaluationDatasetLicense", None),
                "evaluationSampleCount": getattr(request, "evaluationSampleCount", None),
                "trainingNote": training_note,
                "metrics": metrics,
                "features": list(X.columns),
                "labelMap": {str(key): value for key, value in label_map.items()},
            })
            task.status = STATUS_SUCCESS
            task.progress = 100
            task.current_loss = last_loss(history)
            task.model_version = version
            task.model_type = model_type
            task.hyperparameters = hyperparameters
            task.model_path = str(model_path)
            task.history_path = str(history_path)
            task.metadata_path = str(metadata_path)
            task.metrics = metrics
            task.history = history
            task.message = training_note or "训练完成"
            self._mark_finished(task)
        except StopTraining:
            task.status = STATUS_STOP
            task.message = "训练已终止"
            self._mark_finished(task)
        except Exception as exc:
            task.status = STATUS_FAILED
            task.message = str(exc)
            task.progress = max(task.progress, 1)
            self._mark_finished(task)

    def _load_task_from_disk(self, task_id: str) -> TrainingTask:
        output_dir = default_output_root() / f"job-{task_id}"
        model_path = output_dir / "model.joblib"
        history_path = output_dir / "history.json"
        metadata_path = output_dir / "metadata.json"
        if not model_path.exists() or not metadata_path.exists():
            return TrainingTask(task_id=task_id, status=STATUS_FAILED, progress=0, message="任务不存在或服务已重启")
        try:
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        except Exception:
            metadata = {}
        try:
            history = json.loads(history_path.read_text(encoding="utf-8")) if history_path.exists() else {}
        except Exception:
            history = {}
        return TrainingTask(
            task_id=task_id,
            status=STATUS_SUCCESS,
            progress=100,
            current_loss=last_loss(history),
            message=str(metadata.get("trainingNote") or "训练完成"),
            model_version=str(metadata.get("version") or output_dir.name),
            model_path=str(model_path),
            history_path=str(history_path) if history_path.exists() else None,
            metadata_path=str(metadata_path),
            model_type=str(metadata.get("modelType") or ""),
            hyperparameters=metadata.get("hyperparameters") or {},
            metrics=metadata.get("metrics") or {},
            history=history,
            completed_at=time.time(),
        )

    def _mark_finished(self, task: TrainingTask) -> None:
        task.completed_at = time.time()
        with self._lock:
            self._purge_finished_locked(exclude=task.task_id)

    def _purge_finished_locked(self, exclude: str | None = None) -> None:
        now = time.time()
        terminal = [
            (task_id, task)
            for task_id, task in self._tasks.items()
            if task_id != exclude and task.status in {STATUS_SUCCESS, STATUS_FAILED, STATUS_STOP}
        ]
        for task_id, task in terminal:
            if task.completed_at is not None and now - task.completed_at > TASK_RETENTION_SECONDS:
                self._tasks.pop(task_id, None)
        if len(self._tasks) <= MAX_TASK_CACHE:
            return
        terminal = [
            (task_id, task)
            for task_id, task in self._tasks.items()
            if task_id != exclude and task.status in {STATUS_SUCCESS, STATUS_FAILED, STATUS_STOP}
        ]
        terminal.sort(key=lambda item: item[1].completed_at or 0)
        overflow = len(self._tasks) - MAX_TASK_CACHE
        for task_id, _ in terminal[:overflow]:
            self._tasks.pop(task_id, None)


def default_output_root() -> Path:
    return Path(os.getenv("MEDRISK_TRAINING_OUTPUT_DIR", "models/training")).resolve()


def normalize_hyperparameters(model_type: str, payload: dict[str, Any], request: Any) -> dict[str, Any]:
    if model_type == "logistic_regression":
        return {
            "cValue": float_value(payload.get("cValue"), 1.0),
            "maxIterations": int_value(payload.get("maxIterations"), 300),
            "classWeight": str(payload.get("classWeight") or "balanced"),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type in {"random_forest", "extra_trees"}:
        return {
            "nEstimators": int_value(payload.get("nEstimators"), getattr(request, "epochs", 160)),
            "maxDepth": int_value(payload.get("maxDepth"), 6),
            "classWeight": str(payload.get("classWeight") or "balanced"),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type == "hist_gradient_boosting":
        return {
            "maxIterations": int_value(payload.get("maxIterations", payload.get("nEstimators")), getattr(request, "epochs", 120)),
            "maxDepth": int_value(payload.get("maxDepth"), 4),
            "learningRate": float_value(payload.get("learningRate"), getattr(request, "learningRate", 0.05)),
            "regLambda": float_value(payload.get("regLambda"), 0.0),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type in {"lightgbm", "catboost"}:
        return {
            "nEstimators": int_value(payload.get("nEstimators"), getattr(request, "epochs", 160)),
            "maxDepth": int_value(payload.get("maxDepth"), 6),
            "learningRate": float_value(payload.get("learningRate"), getattr(request, "learningRate", 0.05)),
            "subsample": float_value(payload.get("subsample"), 0.9),
            "colsampleBytree": float_value(payload.get("colsampleBytree"), 0.9),
            "regLambda": float_value(payload.get("regLambda"), 1.0),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type == "tabpfn":
        return {
            "maxTrainSamples": int_value(payload.get("maxTrainSamples"), 2048),
            "device": str(payload.get("device") or "auto"),
            "ensembleSize": int_value(payload.get("ensembleSize"), 8),
            "version": str(payload.get("version") or "v2"),
            "useOnlineWeights": bool_value(payload.get("useOnlineWeights", payload.get("onlineWeights")), False),
            "fallbackMaxIterations": int_value(payload.get("fallbackMaxIterations"), 120),
            "fallbackMaxDepth": int_value(payload.get("fallbackMaxDepth"), 4),
            "fallbackLearningRate": float_value(payload.get("fallbackLearningRate"), 0.05),
            "fallbackRegLambda": float_value(payload.get("fallbackRegLambda"), 0.0),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type == "tabicl":
        return {
            "contextSize": int_value(payload.get("contextSize"), 2048),
            "maxTrainSamples": int_value(payload.get("maxTrainSamples"), 10000),
            "device": str(payload.get("device") or "auto"),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    if model_type == "ft_transformer":
        return {
            "maxTrainSamples": int_value(payload.get("maxTrainSamples"), 10000),
            "contextSize": int_value(payload.get("contextSize"), 2048),
            "learningRate": float_value(payload.get("learningRate"), getattr(request, "learningRate", 0.001)),
            "device": str(payload.get("device") or "auto"),
            "seed": int_value(payload.get("seed"), 42),
            "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
        }
    return {
        "nEstimators": int_value(payload.get("nEstimators"), getattr(request, "epochs", 80)),
        "maxDepth": int_value(payload.get("maxDepth"), 3),
        "learningRate": float_value(payload.get("learningRate"), getattr(request, "learningRate", 0.05)),
        "subsample": float_value(payload.get("subsample"), 0.9),
        "colsampleBytree": float_value(payload.get("colsampleBytree"), 0.9),
        "regLambda": float_value(payload.get("regLambda"), 1.0),
        "minChildWeight": float_value(payload.get("minChildWeight"), 1.0),
        "testSize": float_value(payload.get("testSize"), getattr(request, "testSize", 0.2)),
    }


def int_value(value: Any, fallback: int) -> int:
    try:
        return int(value)
    except Exception:
        return int(fallback)


def float_value(value: Any, fallback: float) -> float:
    try:
        return float(value)
    except Exception:
        return float(fallback)


def bool_value(value: Any, fallback: bool) -> bool:
    if value is None:
        return fallback
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def evaluation_metadata(request: Any, sample_count: int) -> dict[str, Any]:
    return {
        "evaluationDataset": getattr(request, "evaluationDatasetName", None) or Path(getattr(request, "evaluationDatasetPath", "")).name,
        "datasetSource": getattr(request, "evaluationDatasetSource", None) or "MedRisk evaluation dataset",
        "datasetUrl": getattr(request, "evaluationDatasetUrl", None),
        "datasetLicense": getattr(request, "evaluationDatasetLicense", None),
        "sampleCount": getattr(request, "evaluationSampleCount", None) or sample_count,
        "validationType": "external evaluation dataset",
    }


def read_dataset(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"数据集不存在: {path}")
    if path.is_dir():
        candidates = sorted(path.glob("*.csv"))
        if not candidates:
            raise ValueError("数据集目录中没有 CSV 文件")
        return pd.read_csv(candidates[0])
    if path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as archive:
            csv_names = [name for name in archive.namelist() if name.lower().endswith(".csv") and not name.endswith("/")]
            preferred = [name for name in csv_names if Path(name).name in {"train.csv", "dataset.csv", "data.csv"}]
            selected = (preferred or csv_names)[0] if csv_names else None
            if not selected:
                raise ValueError("zip 数据集中没有 CSV 文件")
            with archive.open(selected) as handle:
                return pd.read_csv(handle)
    if path.suffix.lower() == ".csv":
        return pd.read_csv(path)
    raise ValueError("仅支持 csv 或 zip 数据集")


def split_features_label(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    if "label" not in df.columns:
        raise ValueError("数据集必须包含 label 列")
    cleaned = df.dropna(subset=["label"]).copy()
    if len(cleaned) < 8:
        raise ValueError("训练数据至少需要 8 条有效样本")
    X = cleaned.drop(columns=["label"])
    y = cleaned["label"]
    if X.empty:
        raise ValueError("数据集至少需要 1 个特征列")
    return X, y


def encode_features(df: pd.DataFrame, columns: list[str] | None = None) -> tuple[pd.DataFrame, list[str]]:
    encoded = pd.get_dummies(df, dummy_na=True)
    encoded = encoded.apply(pd.to_numeric, errors="coerce").fillna(0)
    if columns is None:
        return encoded, list(encoded.columns)
    aligned = encoded.reindex(columns=columns, fill_value=0)
    return aligned, columns


def normalize_history(raw: dict[str, dict[str, list[float]]]) -> dict[str, list[float]]:
    train = raw.get("validation_0", {})
    valid = raw.get("validation_1", {})
    return {
        "trainLogloss": [float(item) for item in train.get("logloss", [])],
        "validLogloss": [float(item) for item in valid.get("logloss", [])],
        "trainError": [float(item) for item in train.get("error", [])],
        "validError": [float(item) for item in valid.get("error", [])],
    }


def evaluate_bundle(bundle: dict[str, Any], X: pd.DataFrame, y: pd.Series) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    encoded, _ = encode_features(X, bundle["feature_columns"])
    label_map = bundle["label_map"]
    y_encoded = y.map(label_map)
    if y_encoded.isna().any():
        raise ValueError("评估数据集包含训练集中未出现的 label 值")
    y_encoded = y_encoded.astype(int)
    adapter = bundle.get("adapter")
    if adapter is not None:
        probabilities = adapter.predict_proba(encoded)[:, 1]
    else:
        model = bundle["model"]
        probabilities = model.predict_proba(encoded)[:, 1]
    probabilities = np.asarray(probabilities, dtype=float)
    threshold = float(bundle.get("decision_threshold", 0.5) or 0.5)
    predicted = (probabilities >= threshold).astype(int)
    matrix = confusion_matrix(y_encoded, predicted, labels=[0, 1])
    tn, fp, fn, tp = matrix.ravel()
    specificity = tn / (tn + fp) if (tn + fp) else 0.0
    has_both_classes = len(set(y_encoded)) > 1
    if has_both_classes:
        fpr, tpr, roc_thresholds = roc_curve(y_encoded, probabilities)
        precision_curve, recall_curve, pr_thresholds = precision_recall_curve(y_encoded, probabilities)
        calibration_prob_true, calibration_prob_pred = calibration_curve(y_encoded, probabilities, n_bins=min(10, max(2, len(y_encoded) // 5)), strategy="uniform")
    else:
        fpr, tpr, roc_thresholds = np.array([0.0, 1.0]), np.array([0.0, 1.0]), np.array([1.0, 0.0])
        precision_curve, recall_curve, pr_thresholds = np.array([0.0, 1.0]), np.array([1.0, 0.0]), np.array([0.5])
        calibration_prob_true, calibration_prob_pred = np.array([]), np.array([])
    metrics = {
        "accuracy": round(float(accuracy_score(y_encoded, predicted)), 4),
        "balancedAccuracy": round(float(balanced_accuracy_score(y_encoded, predicted)), 4),
        "precision": round(float(precision_score(y_encoded, predicted, zero_division=0)), 4),
        "recall": round(float(recall_score(y_encoded, predicted, zero_division=0)), 4),
        "sensitivity": round(float(recall_score(y_encoded, predicted, zero_division=0)), 4),
        "specificity": round(float(specificity), 4),
        "f1": round(float(f1_score(y_encoded, predicted, zero_division=0)), 4),
        "auc": round(float(roc_auc_score(y_encoded, probabilities)) if has_both_classes else 0.5, 4),
        "prAuc": round(float(average_precision_score(y_encoded, probabilities)) if has_both_classes else 0.0, 4),
        "logLoss": round(float(log_loss(y_encoded, probabilities, labels=[0, 1])), 4),
        "brierScore": round(float(brier_score_loss(y_encoded, probabilities)), 4),
        "confusionMatrix": matrix.tolist(),
        "decisionThreshold": round(float(threshold), 4),
        "sampleCount": int(len(y_encoded)),
        "positiveCount": int(sum(y_encoded)),
        "negativeCount": int(len(y_encoded) - sum(y_encoded)),
        "predictedPositiveCount": int(sum(predicted)),
        "predictedNegativeCount": int(len(predicted) - sum(predicted)),
        "positivePredictionRate": round(float(sum(predicted) / len(predicted)) if len(predicted) else 0.0, 4),
        "prevalence": round(float(sum(y_encoded) / len(y_encoded)) if len(y_encoded) else 0.0, 4),
        "rocCurve": curve_rows(fpr, tpr, roc_thresholds, "fpr", "tpr"),
        "prCurve": curve_rows(recall_curve, precision_curve, pr_thresholds, "recall", "precision"),
        "calibrationCurve": curve_rows(calibration_prob_pred, calibration_prob_true, None, "predicted", "observed"),
    }
    predictions = [
        {
            "index": int(index),
            "actual": int(actual),
            "predicted": int(pred),
            "probability": round(float(prob), 4),
        }
        for index, actual, pred, prob in zip(range(min(20, len(y_encoded))), y_encoded.iloc[:20], predicted[:20], probabilities[:20])
    ]
    return metrics, predictions


def select_decision_threshold(bundle: dict[str, Any], X: pd.DataFrame, y: pd.Series) -> float:
    try:
        encoded, _ = encode_features(X, bundle["feature_columns"])
        label_map = bundle["label_map"]
        y_encoded = y.map(label_map)
        if y_encoded.isna().any() or len(set(y_encoded.dropna().astype(int))) < 2:
            return 0.5
        y_encoded = y_encoded.astype(int)
        probabilities = np.asarray(model_probabilities(bundle, encoded)[:, 1], dtype=float)
        candidates = np.unique(np.clip(probabilities, 0.0, 1.0))
        if len(candidates) > 250:
            candidates = np.quantile(candidates, np.linspace(0.02, 0.98, 250))
        candidates = np.unique(np.concatenate([candidates, np.array([0.5])]))
        best_threshold = 0.5
        best_score = (-1.0, -1.0, -1.0, 0.0)
        for threshold in candidates:
            predicted = (probabilities >= threshold).astype(int)
            if predicted.sum() == 0:
                continue
            f1 = float(f1_score(y_encoded, predicted, zero_division=0))
            recall = float(recall_score(y_encoded, predicted, zero_division=0))
            precision = float(precision_score(y_encoded, predicted, zero_division=0))
            balanced = float(balanced_accuracy_score(y_encoded, predicted))
            score = (f1, recall, balanced, precision)
            if score > best_score:
                best_score = score
                best_threshold = float(threshold)
        return round(best_threshold, 4)
    except Exception:
        return 0.5


def curve_rows(x_values: Any, y_values: Any, thresholds: Any | None, x_key: str, y_key: str) -> list[dict[str, float]]:
    rows: list[dict[str, float]] = []
    x_list = list(x_values)
    y_list = list(y_values)
    count = min(len(x_list), len(y_list))
    if count == 0:
        return rows
    if count <= MAX_METRIC_CURVE_POINTS:
        indices = range(count)
    else:
        indices = sorted(
            set(np.linspace(0, count - 1, MAX_METRIC_CURVE_POINTS, dtype=int).tolist())
        )
    threshold_values = list(thresholds) if thresholds is not None else []
    for index in indices:
        x_value = x_list[index]
        y_value = y_list[index]
        row = {
            x_key: round(float(x_value), 4),
            y_key: round(float(y_value), 4),
        }
        if index < len(threshold_values) and np.isfinite(float(threshold_values[index])):
            row["threshold"] = round(float(threshold_values[index]), 4)
        rows.append(row)
    return rows


def load_bundle(path: str) -> dict[str, Any]:
    return joblib.load(path)


def model_probabilities(bundle: dict[str, Any], encoded: pd.DataFrame) -> Any:
    adapter = bundle.get("adapter")
    if adapter is not None:
        return adapter.predict_proba(encoded)
    return bundle["model"].predict_proba(encoded)


def model_importances(bundle: dict[str, Any], feature_columns: list[str]) -> list[float]:
    adapter = bundle.get("adapter")
    if adapter is not None:
        return adapter.explain(feature_columns)
    values = getattr(bundle.get("model"), "feature_importances_", [])
    return [float(value) for value in values]


def slugify(value: str) -> str:
    text = "".join(ch.lower() if ch.isalnum() else "-" for ch in value).strip("-")
    return text or "model"


def last_loss(history: dict[str, list[float]]) -> float | None:
    values = history.get("validLogloss") or history.get("trainLogloss") or []
    return float(values[-1]) if values else None


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
