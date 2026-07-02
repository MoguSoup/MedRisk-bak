from __future__ import annotations

import os
from dataclasses import dataclass
from threading import Lock
from typing import Any

from .schemas import QaGenerateRequest, QaGenerateResponse


@dataclass
class QaCapability:
    available: bool
    provider: str
    model: str | None
    reason: str | None = None


class HuggingFaceQaGenerator:
    def __init__(self) -> None:
        self.model_name = os.getenv("MEDRISK_HF_QA_MODEL", "").strip()
        self.device = os.getenv("MEDRISK_HF_QA_DEVICE", "auto").strip() or "auto"
        self.max_new_tokens = int(os.getenv("MEDRISK_HF_QA_MAX_NEW_TOKENS", "512"))
        self.trust_remote_code = os.getenv("MEDRISK_HF_QA_TRUST_REMOTE_CODE", "false").lower() == "true"
        self._lock = Lock()
        self._pipeline: Any | None = None
        self._load_error: str | None = None

    def capability(self) -> QaCapability:
        if not self.model_name:
            return QaCapability(False, "huggingface-local", None, "MEDRISK_HF_QA_MODEL is not configured")
        if self._load_error:
            return QaCapability(False, "huggingface-local", self.model_name, self._load_error)
        try:
            import transformers  # noqa: F401
        except Exception as exc:
            return QaCapability(False, "huggingface-local", self.model_name, f"transformers is not installed: {exc}")
        return QaCapability(True, "huggingface-local", self.model_name)

    def generate(self, request: QaGenerateRequest) -> QaGenerateResponse:
        capability = self.capability()
        if not capability.available:
            raise RuntimeError(capability.reason or "Hugging Face QA model is unavailable")
        pipe = self._load_pipeline()
        prompt = self._prompt(request)
        output = pipe(
            prompt,
            max_new_tokens=request.maxNewTokens or self.max_new_tokens,
            do_sample=False,
            return_full_text=False,
        )
        answer = self._extract_answer(output)
        if not answer:
            raise RuntimeError("Hugging Face QA model returned an empty answer")
        return QaGenerateResponse(answer=answer, usedModel=self.model_name, provider="huggingface-local", fallbackUsed=False)

    def _load_pipeline(self) -> Any:
        if self._pipeline is not None:
            return self._pipeline
        with self._lock:
            if self._pipeline is not None:
                return self._pipeline
            try:
                from transformers import pipeline

                kwargs: dict[str, Any] = {"model": self.model_name, "trust_remote_code": self.trust_remote_code}
                if self.device == "auto":
                    kwargs["device_map"] = "auto"
                elif self.device not in {"cpu", ""}:
                    kwargs["device"] = int(self.device) if self.device.lstrip("-").isdigit() else self.device
                self._pipeline = pipeline("text-generation", **kwargs)
                return self._pipeline
            except Exception as exc:
                self._load_error = str(exc)
                raise

    def _prompt(self, request: QaGenerateRequest) -> str:
        context = request.context.strip() or "暂无可用知识库上下文。"
        return f"""你是医疗健康知识问答助手。请严格基于给定知识库上下文回答问题；如果上下文不足，请明确说明证据不足。
回答必须使用中文、结构清晰、谨慎表达，并优先按“结论、依据、建议、注意事项”组织。
不要编造未给出的检查结果，结尾必须保留免责声明。

问题：{request.question.strip()}

知识库上下文：
{context[:9000]}

免责声明：{request.disclaimer}
"""

    def _extract_answer(self, output: Any) -> str:
        if isinstance(output, list) and output:
            first = output[0]
            if isinstance(first, dict):
                return str(first.get("generated_text") or first.get("text") or "").strip()
            return str(first).strip()
        return str(output or "").strip()
