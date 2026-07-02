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
    model_type: str = "xgboost"
    version: str
    accuracy: float
    precision: float
    recall: float
    f1: float
    auc: float
    active: bool = True
    deployed: bool = True
    evaluation_dataset: str | None = None
    dataset_source: str | None = None
    dataset_url: str | None = None
    dataset_license: str | None = None
    sample_count: int | None = None
    validation_type: str | None = None


class TrainingStartRequest(BaseModel):
    taskId: str
    datasetPath: str
    evaluationDatasetPath: str | None = None
    evaluationDatasetName: str | None = None
    evaluationDatasetSource: str | None = None
    evaluationDatasetUrl: str | None = None
    evaluationDatasetLicense: str | None = None
    evaluationSampleCount: int | None = None
    diseaseType: str
    modelName: str
    modelType: str = "xgboost"
    epochs: int = Field(default=30, ge=1, le=500)
    learningRate: float = Field(default=0.05, ge=0, le=1)
    testSize: float = Field(default=0.2, gt=0.05, lt=0.5)
    hyperparameters: dict[str, Any] = Field(default_factory=dict)
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
    modelType: str | None = None
    hyperparameters: dict[str, Any] | None = None
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


class QaGenerateRequest(BaseModel):
    question: str
    context: str = ""
    disclaimer: str = "本回答仅用于教学演示和健康知识参考，不能替代医生诊断。"
    maxNewTokens: int | None = Field(default=None, ge=16, le=2048)


class QaGenerateResponse(BaseModel):
    answer: str
    usedModel: str
    provider: str = "huggingface-local"
    fallbackUsed: bool = False
