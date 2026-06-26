import time

import pandas as pd
from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


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


def test_training_and_evaluation_round_trip(tmp_path) -> None:
    rows = []
    for index in range(40):
        age = 30 + index
        glucose = 4.5 + index * 0.15
        bmi = 21 + index * 0.18
        label = 1 if glucose > 7.0 or age > 55 else 0
        rows.append({"age": age, "glucose": glucose, "bmi": bmi, "label": label})
    dataset_path = tmp_path / "diabetes.csv"
    pd.DataFrame(rows).to_csv(dataset_path, index=False)
    output_dir = tmp_path / "model"

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
    trained_prediction = client.post("/predict/diabetes", json={"age": 64, "glucose": 8.4, "bmi": 28.0})
    assert trained_prediction.status_code == 200
    assert trained_prediction.json()["model_version"] == status["modelVersion"]
