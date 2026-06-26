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
import pandas as pd
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier
from xgboost.callback import TrainingCallback


STATUS_PREPARE = "准备训练"
STATUS_RUNNING = "训练中"
STATUS_SUCCESS = "训练成功"
STATUS_FAILED = "训练失败"
STATUS_STOP = "训练终止"


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
    metrics: dict[str, Any] | None = None
    history: dict[str, list[float]] = field(default_factory=dict)
    stop_requested: bool = False


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
            task = TrainingTask(task_id=str(request.taskId), message=str(request.epochs))
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
            X_train, X_test, y_train, y_test = train_test_split(
                X,
                y,
                test_size=request.testSize,
                random_state=42,
                stratify=stratify,
            )
            train_matrix, feature_columns = encode_features(X_train)
            test_matrix, _ = encode_features(X_test, feature_columns)
            label_map = {label: index for index, label in enumerate(sorted(y.unique(), key=str))}
            y_train_encoded = y_train.map(label_map).astype(int)
            y_test_encoded = y_test.map(label_map).astype(int)
            model = XGBClassifier(
                n_estimators=request.epochs,
                learning_rate=request.learningRate,
                max_depth=3,
                subsample=0.9,
                colsample_bytree=0.9,
                objective="binary:logistic",
                eval_metric=["logloss", "error"],
                random_state=42,
                n_jobs=1,
                callbacks=[StopCallback(task)],
            )
            model.fit(
                train_matrix,
                y_train_encoded,
                eval_set=[(train_matrix, y_train_encoded), (test_matrix, y_test_encoded)],
                verbose=False,
            )
            history = normalize_history(model.evals_result())
            bundle = {
                "model": model,
                "feature_columns": feature_columns,
                "raw_features": list(X.columns),
                "label_map": label_map,
                "disease_type": request.diseaseType,
            }
            metrics, predictions = evaluate_bundle(bundle, X_test, y_test)
            version = f"{request.diseaseType}-{slugify(request.modelName)}-{int(time.time())}"
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
                "datasetPath": str(dataset_path),
                "metrics": metrics,
                "features": list(X.columns),
                "labelMap": {str(key): value for key, value in label_map.items()},
            })
            task.status = STATUS_SUCCESS
            task.progress = 100
            task.current_loss = last_loss(history)
            task.model_version = version
            task.model_path = str(model_path)
            task.history_path = str(history_path)
            task.metadata_path = str(metadata_path)
            task.metrics = metrics
            task.history = history
            task.message = "训练完成"
        except StopTraining:
            task.status = STATUS_STOP
            task.message = "训练已终止"
        except Exception as exc:
            task.status = STATUS_FAILED
            task.message = str(exc)
            task.progress = max(task.progress, 1)

    def _load_task_from_disk(self, task_id: str) -> TrainingTask:
        return TrainingTask(task_id=task_id, status=STATUS_FAILED, progress=0, message="任务不存在或服务已重启")


def default_output_root() -> Path:
    return Path(os.getenv("MEDRISK_TRAINING_OUTPUT_DIR", "models/training")).resolve()


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
    y_encoded = y.map(label_map).astype(int)
    model = bundle["model"]
    probabilities = model.predict_proba(encoded)[:, 1]
    predicted = (probabilities >= 0.5).astype(int)
    metrics = {
        "accuracy": round(float(accuracy_score(y_encoded, predicted)), 4),
        "precision": round(float(precision_score(y_encoded, predicted, zero_division=0)), 4),
        "recall": round(float(recall_score(y_encoded, predicted, zero_division=0)), 4),
        "f1": round(float(f1_score(y_encoded, predicted, zero_division=0)), 4),
        "auc": round(float(roc_auc_score(y_encoded, probabilities)) if len(set(y_encoded)) > 1 else 0.5, 4),
        "confusionMatrix": confusion_matrix(y_encoded, predicted, labels=[0, 1]).tolist(),
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


def load_bundle(path: str) -> dict[str, Any]:
    return joblib.load(path)


def slugify(value: str) -> str:
    text = "".join(ch.lower() if ch.isalnum() else "-" for ch in value).strip("-")
    return text or "model"


def last_loss(history: dict[str, list[float]]) -> float | None:
    values = history.get("validLogloss") or history.get("trainLogloss") or []
    return float(values[-1]) if values else None


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
