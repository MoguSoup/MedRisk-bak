from __future__ import annotations

import importlib.util
import os
from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd
from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import log_loss, zero_one_loss
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier


MODEL_LABELS = {
    "xgboost": "XGBoost 稳定基线",
    "logistic_regression": "Logistic Regression 可解释基线",
    "random_forest": "Random Forest 随机森林",
    "extra_trees": "ExtraTrees 极端随机树",
    "hist_gradient_boosting": "HistGradientBoosting 直方图提升树",
    "lightgbm": "LightGBM 梯度提升树",
    "catboost": "CatBoost 类别特征提升树",
    "tabpfn": "TabPFN 表格基础模型",
    "tabicl": "TabICL 表格上下文学习模型",
    "ft_transformer": "FT-Transformer 论文模型",
}


@dataclass
class ModelCapability:
    modelType: str
    label: str
    available: bool
    reason: str | None = None


class BaseModelAdapter:
    model_type = "base"
    label = "Base model"

    def fit(
        self,
        train_matrix: pd.DataFrame,
        y_train: pd.Series,
        valid_matrix: pd.DataFrame,
        y_valid: pd.Series,
        request: Any,
        task: Any,
        stop_callback_cls: type | None = None,
    ) -> dict[str, list[float]]:
        raise NotImplementedError

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        raise NotImplementedError

    def explain(self, feature_columns: list[str]) -> list[float]:
        return [0.0 for _ in feature_columns]


class XGBoostAdapter(BaseModelAdapter):
    model_type = "xgboost"
    label = MODEL_LABELS[model_type]

    def __init__(self) -> None:
        self.model: XGBClassifier | None = None
        self.history: dict[str, list[float]] = {}

    def fit(
        self,
        train_matrix: pd.DataFrame,
        y_train: pd.Series,
        valid_matrix: pd.DataFrame,
        y_valid: pd.Series,
        request: Any,
        task: Any,
        stop_callback_cls: type | None = None,
    ) -> dict[str, list[float]]:
        callbacks = [stop_callback_cls(task)] if stop_callback_cls is not None else []
        hp = getattr(request, "hyperparameters", {}) or {}
        self.model = XGBClassifier(
            n_estimators=int(hp.get("nEstimators", request.epochs)),
            learning_rate=float(hp.get("learningRate", request.learningRate)),
            max_depth=int(hp.get("maxDepth", 3)),
            subsample=float(hp.get("subsample", 0.9)),
            colsample_bytree=float(hp.get("colsampleBytree", 0.9)),
            reg_lambda=float(hp.get("regLambda", 1.0)),
            min_child_weight=float(hp.get("minChildWeight", 1.0)),
            scale_pos_weight=float(hp.get("scalePosWeight") or class_balance_weight(y_train)),
            objective="binary:logistic",
            eval_metric=["logloss", "error"],
            random_state=42,
            n_jobs=1,
            callbacks=callbacks,
        )
        self.model.fit(
            train_matrix,
            y_train,
            eval_set=[(train_matrix, y_train), (valid_matrix, y_valid)],
            verbose=False,
        )
        self.history = normalize_xgb_history(self.model.evals_result())
        return self.history

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        if self.model is None:
            raise ValueError("XGBoost model has not been fitted")
        return self.model.predict_proba(matrix)

    def explain(self, feature_columns: list[str]) -> list[float]:
        if self.model is None:
            return super().explain(feature_columns)
        values = getattr(self.model, "feature_importances_", [])
        return [float(value) for value in values]


class SklearnClassifierAdapter(BaseModelAdapter):
    model_type = "sklearn"
    label = "Scikit-learn classifier"

    def __init__(self) -> None:
        self.model: Any = None

    def fit(self, train_matrix: pd.DataFrame, y_train: pd.Series, valid_matrix: pd.DataFrame, y_valid: pd.Series, request: Any, task: Any, stop_callback_cls: type | None = None) -> dict[str, list[float]]:
        self.model = self.build_model(getattr(request, "hyperparameters", {}) or {})
        task.progress = max(task.progress, 20)
        self.model.fit(train_matrix, y_train)
        history = single_step_history(self, train_matrix, y_train, valid_matrix, y_valid)
        task.current_loss = last_history_loss(history)
        task.progress = max(task.progress, 90)
        return history

    def build_model(self, hp: dict[str, Any]) -> Any:
        raise NotImplementedError

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        if self.model is None:
            raise ValueError(f"{self.label} model has not been fitted")
        if hasattr(self.model, "predict_proba"):
            return self.model.predict_proba(matrix)
        scores = self.model.decision_function(matrix)
        return sigmoid_proba(scores)

    def explain(self, feature_columns: list[str]) -> list[float]:
        if self.model is None:
            return super().explain(feature_columns)
        model = self.model
        if isinstance(model, Pipeline):
            model = model.steps[-1][1]
        if hasattr(model, "feature_importances_"):
            return [float(value) for value in getattr(model, "feature_importances_")]
        if hasattr(model, "coef_"):
            coef = getattr(model, "coef_")
            values = coef[0] if getattr(coef, "ndim", 1) > 1 else coef
            return [float(abs(value)) for value in values]
        return super().explain(feature_columns)


class LogisticRegressionAdapter(SklearnClassifierAdapter):
    model_type = "logistic_regression"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        class_weight = hp.get("classWeight", "balanced")
        return Pipeline([
            ("scaler", StandardScaler()),
            ("classifier", LogisticRegression(
                C=float(hp.get("cValue", 1.0)),
                max_iter=int(hp.get("maxIterations", 300)),
                class_weight=None if class_weight == "none" else "balanced",
                solver="lbfgs",
            )),
        ])


class RandomForestAdapter(SklearnClassifierAdapter):
    model_type = "random_forest"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        class_weight = hp.get("classWeight", "balanced")
        return RandomForestClassifier(
            n_estimators=int(hp.get("nEstimators", 160)),
            max_depth=none_if_zero(hp.get("maxDepth", 6)),
            class_weight=None if class_weight == "none" else "balanced",
            random_state=int(hp.get("seed", 42)),
            n_jobs=1,
        )


class ExtraTreesAdapter(SklearnClassifierAdapter):
    model_type = "extra_trees"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        class_weight = hp.get("classWeight", "balanced")
        return ExtraTreesClassifier(
            n_estimators=int(hp.get("nEstimators", 180)),
            max_depth=none_if_zero(hp.get("maxDepth", 6)),
            class_weight=None if class_weight == "none" else "balanced",
            random_state=int(hp.get("seed", 42)),
            n_jobs=1,
        )


class HistGradientBoostingAdapter(SklearnClassifierAdapter):
    model_type = "hist_gradient_boosting"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        return HistGradientBoostingClassifier(
            max_iter=int(hp.get("maxIterations", hp.get("nEstimators", 120))),
            max_depth=none_if_zero(hp.get("maxDepth", 4)),
            learning_rate=float(hp.get("learningRate", 0.05)),
            l2_regularization=float(hp.get("regLambda", 0.0)),
            random_state=int(hp.get("seed", 42)),
        )


class LightGBMAdapter(SklearnClassifierAdapter):
    model_type = "lightgbm"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        classifier = optional_classifier("lightgbm", "LGBMClassifier")
        class_weight = hp.get("classWeight", "balanced")
        return classifier(
            n_estimators=int(hp.get("nEstimators", 160)),
            max_depth=int(hp.get("maxDepth", -1)),
            learning_rate=float(hp.get("learningRate", 0.05)),
            subsample=float(hp.get("subsample", 0.9)),
            colsample_bytree=float(hp.get("colsampleBytree", 0.9)),
            reg_lambda=float(hp.get("regLambda", 1.0)),
            class_weight=None if class_weight == "none" else "balanced",
            random_state=int(hp.get("seed", 42)),
            n_jobs=1,
            verbose=-1,
        )


class CatBoostAdapter(SklearnClassifierAdapter):
    model_type = "catboost"
    label = MODEL_LABELS[model_type]

    def build_model(self, hp: dict[str, Any]) -> Any:
        classifier = optional_classifier("catboost", "CatBoostClassifier")
        return classifier(
            iterations=int(hp.get("nEstimators", 160)),
            depth=int(hp.get("maxDepth", 6)),
            learning_rate=float(hp.get("learningRate", 0.05)),
            l2_leaf_reg=float(hp.get("regLambda", 3.0)),
            random_seed=int(hp.get("seed", 42)),
            auto_class_weights=None if hp.get("classWeight") == "none" else "Balanced",
            verbose=False,
            allow_writing_files=False,
        )


class LocalTabPFNFallbackClassifier:
    def __init__(self, reason: str, hp: dict[str, Any]) -> None:
        self.reason = reason
        self.model = HistGradientBoostingClassifier(
            max_iter=int(hp.get("fallbackMaxIterations", 120)),
            max_depth=none_if_zero(hp.get("fallbackMaxDepth", 4)),
            learning_rate=float(hp.get("fallbackLearningRate", 0.05)),
            l2_regularization=float(hp.get("fallbackRegLambda", 0.0)),
            random_state=int(hp.get("seed", 42)),
        )

    def fit(self, train_matrix: pd.DataFrame, y_train: pd.Series) -> "LocalTabPFNFallbackClassifier":
        self.model.fit(train_matrix, y_train)
        return self

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        return self.model.predict_proba(matrix)


class TabPFNAdapter(BaseModelAdapter):
    model_type = "tabpfn"
    label = MODEL_LABELS[model_type]

    def __init__(self) -> None:
        self.model: Any = None
        self.training_note: str | None = None

    def fit(self, train_matrix: pd.DataFrame, y_train: pd.Series, valid_matrix: pd.DataFrame, y_valid: pd.Series, request: Any, task: Any, stop_callback_cls: type | None = None) -> dict[str, list[float]]:
        hp = getattr(request, "hyperparameters", {}) or {}
        requested_rows = int(hp.get("maxTrainSamples", tabpfn_max_train_rows()))
        max_rows = min(requested_rows, tabpfn_max_train_rows())
        if requested_rows > max_rows:
            self.training_note = f"TabPFN 在当前部署中最多使用 {max_rows} 条训练样本，已从 {requested_rows} 条自动下采样。"
        train_matrix, y_train = limit_training_rows(train_matrix, y_train, max_rows)
        task.progress = max(task.progress, 12)
        if not tabpfn_online_enabled(hp):
            fallback_note = "当前部署默认不在线下载 TabPFN 权重，已自动使用本地表格备用模型完成训练。"
            self.training_note = join_notes(self.training_note, fallback_note)
            task.message = self.training_note
            task.progress = max(task.progress, 35)
            self.model = LocalTabPFNFallbackClassifier("online TabPFN disabled", hp)
            self.model.fit(train_matrix, y_train)
            history = single_step_history(self, train_matrix, y_train, valid_matrix, y_valid)
            task.current_loss = last_history_loss(history)
            task.progress = max(task.progress, 85)
            return history
        try:
            self.model = build_tabpfn_classifier(hp)
            self.model.fit(train_matrix, y_train)
        except Exception as exc:
            reason = compact_error(exc)
            fallback_note = f"TabPFN 权重或运行环境不可用，已自动使用本地表格备用模型完成训练：{reason}"
            self.training_note = join_notes(self.training_note, fallback_note)
            task.message = self.training_note
            task.progress = max(task.progress, 35)
            self.model = LocalTabPFNFallbackClassifier(reason, hp)
            self.model.fit(train_matrix, y_train)
        history = single_step_history(self, train_matrix, y_train, valid_matrix, y_valid)
        task.current_loss = last_history_loss(history)
        task.progress = max(task.progress, 85)
        return history

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        return self.model.predict_proba(matrix)


class TabICLAdapter(BaseModelAdapter):
    model_type = "tabicl"
    label = MODEL_LABELS[model_type]

    def __init__(self) -> None:
        self.model: Any = None

    def fit(self, train_matrix: pd.DataFrame, y_train: pd.Series, valid_matrix: pd.DataFrame, y_valid: pd.Series, request: Any, task: Any, stop_callback_cls: type | None = None) -> dict[str, list[float]]:
        classifier = optional_classifier("tabicl", "TabICLClassifier")
        hp = getattr(request, "hyperparameters", {}) or {}
        max_rows = int(hp.get("maxTrainSamples", len(train_matrix)))
        train_matrix = train_matrix.head(max_rows)
        y_train = y_train.head(max_rows)
        task.progress = max(task.progress, 12)
        self.model = classifier()
        self.model.fit(train_matrix, y_train)
        history = single_step_history(self, train_matrix, y_train, valid_matrix, y_valid)
        task.current_loss = last_history_loss(history)
        task.progress = max(task.progress, 85)
        return history

    def predict_proba(self, matrix: pd.DataFrame) -> Any:
        return self.model.predict_proba(matrix)


def normalize_model_type(value: str | None) -> str:
    normalized = (value or "xgboost").strip().lower()
    return normalized if normalized in MODEL_LABELS else "xgboost"


def create_adapter(model_type: str | None) -> BaseModelAdapter:
    normalized = normalize_model_type(model_type)
    if normalized == "logistic_regression":
        return LogisticRegressionAdapter()
    if normalized == "random_forest":
        return RandomForestAdapter()
    if normalized == "extra_trees":
        return ExtraTreesAdapter()
    if normalized == "hist_gradient_boosting":
        return HistGradientBoostingAdapter()
    if normalized == "lightgbm":
        return LightGBMAdapter()
    if normalized == "catboost":
        return CatBoostAdapter()
    if normalized == "tabpfn":
        return TabPFNAdapter()
    if normalized == "tabicl":
        return TabICLAdapter()
    if normalized == "ft_transformer":
        raise RuntimeError("FT-Transformer 需要额外的深度表格训练运行时，本版本仅保留能力入口。")
    return XGBoostAdapter()


def capabilities() -> list[ModelCapability]:
    return [
        ModelCapability("xgboost", MODEL_LABELS["xgboost"], True),
        ModelCapability("logistic_regression", MODEL_LABELS["logistic_regression"], True),
        ModelCapability("random_forest", MODEL_LABELS["random_forest"], True),
        ModelCapability("extra_trees", MODEL_LABELS["extra_trees"], True),
        ModelCapability("hist_gradient_boosting", MODEL_LABELS["hist_gradient_boosting"], True),
        optional_capability("lightgbm", "lightgbm"),
        optional_capability("catboost", "catboost"),
        tabpfn_capability(),
        optional_capability("tabicl", "tabicl"),
        ModelCapability("ft_transformer", MODEL_LABELS["ft_transformer"], False, "需要安装并配置 FT-Transformer 深度表格训练运行时；默认部署不强制安装。"),
    ]


def optional_capability(model_type: str, module_name: str) -> ModelCapability:
    if importlib.util.find_spec(module_name) is not None:
        return ModelCapability(model_type, MODEL_LABELS[model_type], True)
    return ModelCapability(model_type, MODEL_LABELS[model_type], False, f"未安装可选依赖 {module_name}，请按 requirements-advanced.txt 安装。")


def tabpfn_capability() -> ModelCapability:
    if importlib.util.find_spec("tabpfn") is None:
        return ModelCapability("tabpfn", MODEL_LABELS["tabpfn"], True, "未安装 tabpfn，将使用本地表格备用模型完成训练。")
    if not tabpfn_online_enabled({}):
        return ModelCapability("tabpfn", MODEL_LABELS["tabpfn"], True, "默认使用本地表格备用模型；设置 MEDRISK_TABPFN_ONLINE=true 后才会在线加载 TabPFN 权重。")
    version = tabpfn_version({})
    if version == "v3" and not tabpfn_token_available():
        return ModelCapability("tabpfn", MODEL_LABELS["tabpfn"], True, "TabPFN v3 权重未配置授权，将使用本地表格备用模型完成训练。")
    return ModelCapability("tabpfn", MODEL_LABELS["tabpfn"], True)


def optional_classifier(module_name: str, class_name: str) -> Any:
    try:
        module = __import__(module_name, fromlist=[class_name])
        return getattr(module, class_name)
    except Exception as exc:
        raise RuntimeError(f"高级模型依赖 {module_name}.{class_name} 不可用，请先安装 requirements-advanced.txt。") from exc


def none_if_zero(value: Any) -> int | None:
    try:
        parsed = int(value)
    except Exception:
        return None
    return parsed if parsed > 0 else None


def build_tabpfn_classifier(hp: dict[str, Any]) -> Any:
    module = __import__("tabpfn", fromlist=["TabPFNClassifier"])
    classifier = getattr(module, "TabPFNClassifier")
    kwargs = {
        "n_estimators": max(1, int(hp.get("ensembleSize", 8))),
        "device": str(hp.get("device") or "auto"),
        "show_progress_bar": False,
    }
    version = tabpfn_version(hp)
    if version == "v3" and not tabpfn_token_available():
        raise RuntimeError("TabPFN v3 权重需要先在 Hugging Face/Prior Labs 接受条款，并在模型服务环境中配置 HF_TOKEN 或 TABPFN_TOKEN。可将 MEDRISK_TABPFN_VERSION 设为 v2 使用默认公开权重。")
    if hasattr(classifier, "create_default_for_version"):
        try:
            constants = __import__("tabpfn.constants", fromlist=["ModelVersion"])
            model_version = getattr(constants, "ModelVersion")
            normalized = getattr(model_version, "V2")
            if version in {"v2.5", "v2_5"} and hasattr(model_version, "V2_5"):
                normalized = getattr(model_version, "V2_5")
            elif version in {"v2.6", "v2_6"} and hasattr(model_version, "V2_6"):
                normalized = getattr(model_version, "V2_6")
            elif version == "v3" and hasattr(model_version, "V3"):
                normalized = getattr(model_version, "V3")
            return classifier.create_default_for_version(normalized, **kwargs)
        except RuntimeError:
            raise
        except Exception:
            pass
    return classifier(**kwargs)


def limit_training_rows(train_matrix: pd.DataFrame, y_train: pd.Series, max_rows: int) -> tuple[pd.DataFrame, pd.Series]:
    if max_rows <= 0 or len(train_matrix) <= max_rows:
        return train_matrix, y_train
    classes = list(y_train.dropna().unique())
    if len(classes) < 2:
        limited_index = train_matrix.head(max_rows).index
        return train_matrix.loc[limited_index], y_train.loc[limited_index]
    per_class = max(1, max_rows // len(classes))
    selected: list[Any] = []
    for label in classes:
        class_index = list(y_train[y_train == label].sample(
            n=min(per_class, int((y_train == label).sum())),
            random_state=42,
        ).index)
        selected.extend(class_index)
    if len(selected) < max_rows:
        remaining = y_train.drop(index=selected, errors="ignore")
        selected.extend(list(remaining.sample(n=min(max_rows - len(selected), len(remaining)), random_state=42).index))
    selected = selected[:max_rows]
    return train_matrix.loc[selected], y_train.loc[selected]


def tabpfn_max_train_rows() -> int:
    try:
        return max(64, int(os.getenv("MEDRISK_TABPFN_MAX_TRAIN_ROWS", "2048")))
    except Exception:
        return 2048


def join_notes(*items: str | None) -> str | None:
    notes = [item for item in items if item]
    return "；".join(notes) if notes else None


def compact_error(exc: Exception) -> str:
    message = str(exc).strip() or exc.__class__.__name__
    message = " ".join(message.split())
    return message[:240]


def tabpfn_version(hp: dict[str, Any]) -> str:
    return str(hp.get("version") or os.getenv("MEDRISK_TABPFN_VERSION") or "v2").strip().lower()


def tabpfn_online_enabled(hp: dict[str, Any]) -> bool:
    value = hp.get("useOnlineWeights", hp.get("onlineWeights", os.getenv("MEDRISK_TABPFN_ONLINE", "false")))
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def tabpfn_token_available() -> bool:
    return bool(os.getenv("HF_TOKEN") or os.getenv("HUGGINGFACE_HUB_TOKEN") or os.getenv("TABPFN_TOKEN"))


def class_balance_weight(y_train: pd.Series) -> float:
    positives = int((y_train == 1).sum())
    negatives = int((y_train == 0).sum())
    if positives <= 0 or negatives <= 0:
        return 1.0
    return max(1.0, negatives / positives)


def sigmoid_proba(scores: Any) -> Any:
    values = np.asarray(scores, dtype=float)
    probabilities = 1.0 / (1.0 + np.exp(-values))
    return np.column_stack([1.0 - probabilities, probabilities])


def single_step_history(adapter: BaseModelAdapter, train_matrix: pd.DataFrame, y_train: pd.Series, valid_matrix: pd.DataFrame, y_valid: pd.Series) -> dict[str, list[float]]:
    train_logloss, train_error = classification_loss(adapter, train_matrix, y_train)
    valid_logloss, valid_error = classification_loss(adapter, valid_matrix, y_valid)
    return {
        "trainLogloss": [] if train_logloss is None else [train_logloss],
        "validLogloss": [] if valid_logloss is None else [valid_logloss],
        "trainError": [] if train_error is None else [train_error],
        "validError": [] if valid_error is None else [valid_error],
    }


def classification_loss(adapter: BaseModelAdapter, matrix: pd.DataFrame, labels: pd.Series) -> tuple[float | None, float | None]:
    try:
        probabilities = normalize_binary_probabilities(adapter.predict_proba(matrix))
        predicted = (probabilities[:, 1] >= 0.5).astype(int)
        return (
            round(float(log_loss(labels, probabilities, labels=[0, 1])), 6),
            round(float(zero_one_loss(labels, predicted)), 6),
        )
    except Exception:
        return None, None


def normalize_binary_probabilities(values: Any) -> np.ndarray:
    probabilities = np.asarray(values, dtype=float)
    if probabilities.ndim == 1:
        positives = np.clip(probabilities, 1e-6, 1 - 1e-6)
        return np.column_stack([1.0 - positives, positives])
    if probabilities.shape[1] == 1:
        positives = np.clip(probabilities[:, 0], 1e-6, 1 - 1e-6)
        return np.column_stack([1.0 - positives, positives])
    probabilities = probabilities[:, :2]
    probabilities = np.clip(probabilities, 1e-6, 1 - 1e-6)
    row_sums = probabilities.sum(axis=1, keepdims=True)
    row_sums[row_sums == 0] = 1.0
    return probabilities / row_sums


def last_history_loss(history: dict[str, list[float]]) -> float | None:
    values = history.get("validLogloss") or history.get("trainLogloss") or []
    return float(values[-1]) if values else None


def normalize_xgb_history(raw: dict[str, dict[str, list[float]]]) -> dict[str, list[float]]:
    train = raw.get("validation_0", {})
    valid = raw.get("validation_1", {})
    return {
        "trainLogloss": [float(item) for item in train.get("logloss", [])],
        "validLogloss": [float(item) for item in valid.get("logloss", [])],
        "trainError": [float(item) for item in train.get("error", [])],
        "validError": [float(item) for item in valid.get("error", [])],
    }
