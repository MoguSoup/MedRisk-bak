from __future__ import annotations

import importlib.util
from dataclasses import dataclass
from typing import Any

import pandas as pd
from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
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
        task.progress = max(task.progress, 90)
        return {"trainLogloss": [], "validLogloss": [], "trainError": [], "validError": []}

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
        return classifier(
            n_estimators=int(hp.get("nEstimators", 160)),
            max_depth=int(hp.get("maxDepth", -1)),
            learning_rate=float(hp.get("learningRate", 0.05)),
            subsample=float(hp.get("subsample", 0.9)),
            colsample_bytree=float(hp.get("colsampleBytree", 0.9)),
            reg_lambda=float(hp.get("regLambda", 1.0)),
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
            verbose=False,
            allow_writing_files=False,
        )


class TabPFNAdapter(BaseModelAdapter):
    model_type = "tabpfn"
    label = MODEL_LABELS[model_type]

    def __init__(self) -> None:
        self.model: Any = None

    def fit(self, train_matrix: pd.DataFrame, y_train: pd.Series, valid_matrix: pd.DataFrame, y_valid: pd.Series, request: Any, task: Any, stop_callback_cls: type | None = None) -> dict[str, list[float]]:
        classifier = optional_classifier("tabpfn", "TabPFNClassifier")
        hp = getattr(request, "hyperparameters", {}) or {}
        max_rows = int(hp.get("maxTrainSamples", len(train_matrix)))
        train_matrix = train_matrix.head(max_rows)
        y_train = y_train.head(max_rows)
        task.progress = max(task.progress, 12)
        self.model = classifier()
        self.model.fit(train_matrix, y_train)
        task.progress = max(task.progress, 85)
        return {"trainLogloss": [], "validLogloss": [], "trainError": [], "validError": []}

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
        task.progress = max(task.progress, 85)
        return {"trainLogloss": [], "validLogloss": [], "trainError": [], "validError": []}

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
        optional_capability("tabpfn", "tabpfn"),
        optional_capability("tabicl", "tabicl"),
        ModelCapability("ft_transformer", MODEL_LABELS["ft_transformer"], False, "需要安装并配置 FT-Transformer 深度表格训练运行时；默认部署不强制安装。"),
    ]


def optional_capability(model_type: str, module_name: str) -> ModelCapability:
    if importlib.util.find_spec(module_name) is not None:
        return ModelCapability(model_type, MODEL_LABELS[model_type], True)
    return ModelCapability(model_type, MODEL_LABELS[model_type], False, f"未安装可选依赖 {module_name}，请按 requirements-advanced.txt 安装。")


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


def sigmoid_proba(scores: Any) -> Any:
    import numpy as np

    values = np.asarray(scores, dtype=float)
    probabilities = 1.0 / (1.0 + np.exp(-values))
    return np.column_stack([1.0 - probabilities, probabilities])


def normalize_xgb_history(raw: dict[str, dict[str, list[float]]]) -> dict[str, list[float]]:
    train = raw.get("validation_0", {})
    valid = raw.get("validation_1", {})
    return {
        "trainLogloss": [float(item) for item in train.get("logloss", [])],
        "validLogloss": [float(item) for item in valid.get("logloss", [])],
        "trainError": [float(item) for item in train.get("error", [])],
        "validError": [float(item) for item in valid.get("error", [])],
    }
