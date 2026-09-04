# syntax=docker/dockerfile:1@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32

FROM python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184ea AS dependencies

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    VIRTUAL_ENV=/opt/venv

RUN python -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /build
COPY requirements.runtime.lock ./requirements.runtime.lock
RUN --mount=type=cache,id=finbound-ai-risk-pip,target=/root/.cache/pip \
    python -m pip install --requirement requirements.runtime.lock


# Network access and Hugging Face cache exist only in this disposable stage.
# The pinned revision and every runtime model/tokenizer digest are checked before copy.
FROM dependencies AS prompt-model

WORKDIR /build
COPY app ./app
COPY models/download_prompt_model.py models/prompt_detector.json ./models/
RUN --mount=type=cache,id=finbound-ai-risk-huggingface,target=/root/.cache/huggingface \
    python -m models.download_prompt_model --target /opt/prompt-model


FROM python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184ea AS runtime

ENV FINGUARD_BEHAVIOR_MODEL_PATH=/app/models/behavior_iforest.joblib \
    HOME=/tmp \
    PATH=/opt/venv/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN groupadd --gid 10001 --system finbound \
    && useradd --uid 10001 --gid finbound --system --no-create-home finbound

WORKDIR /app
# Keep executable code and model artifacts root-owned and read-only to UID 10001.
COPY --from=dependencies /opt/venv /opt/venv
COPY app ./app
COPY models/behavior_iforest.joblib \
    models/behavior_iforest.json \
    models/prompt_detector.json \
    models/prompt_domain_adapter.joblib \
    ./models/
COPY --from=prompt-model /opt/prompt-model/onnx/int8 ./models/prompt_guard_onnx/onnx/int8

USER 10001:10001
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/ready', timeout=2).read()"]

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
