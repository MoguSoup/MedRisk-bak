from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


RiskLabel = Literal["low", "medium", "high"]


class RiskFactor(BaseModel):
    name: str
    label: str
    value: Any
    impact: float = Field(ge=0, le=1)
    direction: Literal["increase", "decrease", "neutral"]


class PredictionResponse(BaseModel):
    disease_type: str
    disease_name: str
    risk_label: RiskLabel
    risk_probability: float = Field(ge=0, le=1)
    confidence: float = Field(ge=0, le=1)
    model_version: str
    top_factors: list[RiskFactor]
    recommendations: list[str]
    disclaimer: str


class ModelInfo(BaseModel):
    disease_type: str
    disease_name: str
    model_name: str
    version: str
    accuracy: float
    precision: float
    recall: float
    f1: float
    auc: float
    active: bool = True


class TrainingStartRequest(BaseModel):
    taskId: str
    datasetPath: str
    diseaseType: str
    modelName: str
    epochs: int = Field(default=30, ge=1, le=500)
    learningRate: float = Field(default=0.05, gt=0, le=1)
    testSize: float = Field(default=0.2, gt=0.05, lt=0.5)
    outputDir: str | None = None


class TrainingStatusResponse(BaseModel):
    taskId: str
    status: str
    progress: int = Field(ge=0, le=100)
    currentLoss: float | None = None
    message: str | None = None
    modelVersion: str | None = None
    modelPath: str | None = None
    historyPath: str | None = None
    metadataPath: str | None = None
    metrics: dict[str, Any] | None = None


class TrainingHistoryResponse(BaseModel):
    taskId: str
    history: dict[str, list[float]]


class EvaluateRequest(BaseModel):
    datasetPath: str
    modelPath: str | None = None


class EvaluationResponse(BaseModel):
    modelVersion: str
    metrics: dict[str, Any]
    predictions: list[dict[str, Any]]


class ActivateModelRequest(BaseModel):
    diseaseType: str
    version: str
    modelPath: str
