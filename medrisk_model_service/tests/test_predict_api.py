import time

import numpy as np
import pandas as pd
from fastapi.testclient import TestClient

import app.model_adapters as model_adapters
from app.main import app, registry
from app.model_registry import ModelRegistry


client = TestClient(app)


class FakeOptionalClassifier:
    def fit(self, X, y):
        self.bias = float(np.mean(y))
        return self

    def predict_proba(self, X):
        values = np.clip(np.linspace(0.2, 0.8, len(X)) + self.bias * 0.05, 0.05, 0.95)
        return np.column_stack([1 - values, values])


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_all_diseases_return_required_prediction_fields() -> None:
    sample = {
        "age": 62,
        "glucose": 8.7,
        "bmi": 29.8,
        "bloodPressure": 152,
        "cholesterol": 6.2,
        "familyHistory": True,
        "smoker": True,
        "chestPain": True,
        "maxHeartRate": 118,
        "creatinine": 128,
        "urea": 9.2,
        "albumin": 3,
        "hemoglobin": 112,
        "diabetesHistory": True,
        "bilirubin": 34,
        "alt": 92,
        "ast": 88,
        "alcoholUse": True,
        "heartDiseaseHistory": True,
    }
    for disease in ["diabetes", "heart", "kidney", "liver", "stroke"]:
        response = client.post(f"/predict/{disease}", json=sample)
        assert response.status_code == 200
        body = response.json()
        assert body["disease_type"] == disease
        assert body["risk_label"] in {"low", "medium", "high"}
        assert 0 <= body["risk_probability"] <= 1
        assert len(body["top_factors"]) <= 5
        assert "不能替代医生诊断" in body["disclaimer"]


def test_unsupported_disease_returns_404() -> None:
    response = client.post("/predict/unknown", json={})
    assert response.status_code == 404


def test_model_capabilities_report_optional_models() -> None:
    response = client.get("/models/capabilities")
    assert response.status_code == 200
    body = response.json()
    by_type = {item["modelType"]: item for item in body}
    assert by_type["xgboost"]["available"] is True
    assert {"xgboost", "tabpfn", "tabicl"}.issubset(by_type)


def test_public_evaluations_expose_recognized_sources() -> None:
    response = client.get("/models/public-evaluations")
    assert response.status_code == 200
    body = response.json()
    assert body["diabetes"]["datasetSource"] == "CDC BRFSS 2024"
    assert body["liver"]["datasetSource"] == "CDC NHANES 2017-March 2020"
    assert body["heart_uci"]["datasetSource"] == "UCI Machine Learning Repository"
    assert body["kidney_uci"]["datasetLicense"] == "CC BY 4.0"
    assert body["heart"]["sampleCount"] > 100000


def test_qa_capability_reports_missing_huggingface_model() -> None:
    response = client.get("/qa/capabilities")
    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "huggingface-local"
    assert body["available"] is False
    assert "MEDRISK_HF_QA_MODEL" in body["reason"]


def test_training_and_evaluation_round_trip(tmp_path, monkeypatch) -> None:
    rows = []
    for index in range(40):
        age = 30 + index
        glucose = 4.5 + index * 0.15
        bmi = 21 + index * 0.18
        label = 1 if glucose > 7.0 or age > 55 else 0
        rows.append({"age": age, "glucose": glucose, "bmi": bmi, "label": label})
    dataset_path = tmp_path / "diabetes.csv"
    pd.DataFrame(rows).to_csv(dataset_path, index=False)
    eval_path = tmp_path / "diabetes_eval.csv"
    pd.DataFrame(rows[-20:]).to_csv(eval_path, index=False)
    output_dir = tmp_path / "model"
    active_file = tmp_path / "active-models.json"
    registry._active_models_file = active_file

    response = client.post(
        "/training/start",
        json={
            "taskId": "pytest-task",
            "datasetPath": str(dataset_path),
            "diseaseType": "diabetes",
            "modelName": "pytest-xgb",
            "epochs": 4,
            "learningRate": 0.1,
            "testSize": 0.25,
            "hyperparameters": {"nEstimators": 6, "maxDepth": 2, "learningRate": 0.12, "subsample": 0.8, "colsampleBytree": 0.85, "regLambda": 1.2, "minChildWeight": 1, "testSize": 0.25},
            "evaluationDatasetPath": str(eval_path),
            "evaluationDatasetName": "pytest public eval",
            "evaluationDatasetSource": "pytest source",
            "evaluationDatasetUrl": "https://example.test/eval",
            "outputDir": str(output_dir),
        },
    )
    assert response.status_code == 200

    status = response.json()
    for _ in range(60):
        status = client.get("/training/pytest-task/status").json()
        if status["status"] in {"训练成功", "训练失败", "训练终止"}:
            break
        time.sleep(0.2)
    assert status["status"] == "训练成功", status
    assert status["metrics"]["accuracy"] >= 0
    assert status["metrics"]["evaluationDataset"] == "pytest public eval"
    assert status["metrics"]["datasetSource"] == "pytest source"
    assert status["hyperparameters"]["nEstimators"] == 6
    assert status["hyperparameters"]["maxDepth"] == 2
    assert (output_dir / "model.joblib").exists()
    assert (output_dir / "history.json").exists()

    history = client.get("/training/pytest-task/history").json()
    assert len(history["history"]["trainLogloss"]) >= 1

    evaluation = client.post(
        f"/evaluate/{status['modelVersion']}",
        json={"datasetPath": str(dataset_path), "modelPath": status["modelPath"]},
    )
    assert evaluation.status_code == 200
    assert "confusionMatrix" in evaluation.json()["metrics"]

    activation = client.post(
        "/models/activate",
        json={"diseaseType": "diabetes", "version": status["modelVersion"], "modelPath": status["modelPath"]},
    )
    assert activation.status_code == 200
    assert active_file.exists()
    trained_prediction = client.post("/predict/diabetes", json={"age": 64, "glucose": 8.4, "bmi": 28.0})
    assert trained_prediction.status_code == 200
    assert trained_prediction.json()["model_version"] == status["modelVersion"]

    monkeypatch.setenv("MEDRISK_ACTIVE_MODELS_FILE", str(active_file))
    reloaded = ModelRegistry()
    assert any(item.version == status["modelVersion"] for item in reloaded.info() if item.disease_type == "diabetes")


def test_tabpfn_adapter_round_trip_with_mocked_classifier(tmp_path, monkeypatch) -> None:
    monkeypatch.setattr(model_adapters, "optional_classifier", lambda module_name, class_name: FakeOptionalClassifier)

    rows = []
    for index in range(24):
        rows.append({"age": 35 + index, "glucose": 5 + index * 0.2, "label": 1 if index >= 12 else 0})
    dataset_path = tmp_path / "tabpfn.csv"
    pd.DataFrame(rows).to_csv(dataset_path, index=False)
    output_dir = tmp_path / "tabpfn-model"

    response = client.post(
        "/training/start",
        json={
            "taskId": "pytest-tabpfn",
            "datasetPath": str(dataset_path),
            "diseaseType": "diabetes",
            "modelName": "pytest-tabpfn",
            "modelType": "tabpfn",
            "epochs": 2,
            "learningRate": 0.1,
            "testSize": 0.25,
            "hyperparameters": {"maxTrainSamples": 16, "device": "cpu", "ensembleSize": 3, "testSize": 0.25},
            "outputDir": str(output_dir),
        },
    )
    assert response.status_code == 200

    status = response.json()
    for _ in range(60):
        status = client.get("/training/pytest-tabpfn/status").json()
        if status["status"] in {"训练成功", "训练失败", "训练终止"}:
            break
        time.sleep(0.2)
    assert status["status"] == "训练成功", status
    assert status["modelType"] == "tabpfn"
    assert status["hyperparameters"]["maxTrainSamples"] == 16
    assert "tabpfn" in status["modelVersion"]
