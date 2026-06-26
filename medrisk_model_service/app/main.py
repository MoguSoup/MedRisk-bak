from __future__ import annotations

from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .model_registry import ModelRegistry
from .schemas import (
    ActivateModelRequest,
    EvaluateRequest,
    EvaluationResponse,
    ModelInfo,
    PredictionResponse,
    TrainingHistoryResponse,
    TrainingStartRequest,
    TrainingStatusResponse,
)
from .training import TrainingManager


app = FastAPI(title="MedRisk AI Model Service", version="1.0.0")
registry = ModelRegistry()
training_manager = TrainingManager()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "medrisk-model-service"}


@app.get("/models/active", response_model=list[ModelInfo])
def active_models() -> list[ModelInfo]:
    return registry.info()


@app.get("/models/metrics", response_model=list[ModelInfo])
def model_metrics() -> list[ModelInfo]:
    return registry.info()


@app.post("/predict/{disease_type}", response_model=PredictionResponse)
def predict(disease_type: str, payload: dict[str, Any]) -> PredictionResponse:
    try:
        return registry.predict(disease_type, payload)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail=f"Unsupported disease type: {disease_type}") from exc


@app.post("/training/start", response_model=TrainingStatusResponse)
def start_training(request: TrainingStartRequest) -> TrainingStatusResponse:
    task = training_manager.start(request)
    return task_response(task)


@app.post("/training/{task_id}/stop", response_model=TrainingStatusResponse)
def stop_training(task_id: str) -> TrainingStatusResponse:
    return task_response(training_manager.stop(task_id))


@app.get("/training/{task_id}/status", response_model=TrainingStatusResponse)
def training_status(task_id: str) -> TrainingStatusResponse:
    return task_response(training_manager.get(task_id))


@app.get("/training/{task_id}/history", response_model=TrainingHistoryResponse)
def training_history(task_id: str) -> TrainingHistoryResponse:
    return TrainingHistoryResponse(taskId=task_id, history=training_manager.history(task_id))


@app.post("/evaluate/{model_version}", response_model=EvaluationResponse)
def evaluate_model(model_version: str, request: EvaluateRequest) -> EvaluationResponse:
    metrics, predictions = training_manager.evaluate(model_version, request.datasetPath, request.modelPath)
    return EvaluationResponse(modelVersion=model_version, metrics=metrics, predictions=predictions)


@app.post("/models/activate")
def activate_model(request: ActivateModelRequest) -> dict[str, str]:
    try:
        registry.activate_trained_model(request.diseaseType, request.version, request.modelPath)
        return {"status": "activated", "version": request.version}
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def task_response(task: Any) -> TrainingStatusResponse:
    return TrainingStatusResponse(
        taskId=task.task_id,
        status=task.status,
        progress=task.progress,
        currentLoss=task.current_loss,
        message=task.message,
        modelVersion=task.model_version,
        modelPath=task.model_path,
        historyPath=task.history_path,
        metadataPath=task.metadata_path,
        metrics=task.metrics,
    )
