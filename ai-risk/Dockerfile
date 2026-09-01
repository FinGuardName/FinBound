# syntax=docker/dockerfile:1.7

FROM python:3.12-slim AS dependencies

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    VIRTUAL_ENV=/opt/venv

RUN python -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /build
COPY requirements.lock ./requirements.lock
RUN --mount=type=cache,target=/root/.cache/pip \
    python -m pip install --requirement requirements.lock


# Fetch the pinned Prompt Detector in a disposable stage. The downloader verifies
# the ONNX digest before the artifact can enter the runtime image.
FROM dependencies AS prompt-model

WORKDIR /build
COPY app ./app
COPY models ./models
RUN --mount=type=cache,target=/root/.cache/huggingface \
    python -m models.download_prompt_model --target /opt/prompt-model


FROM python:3.12-slim AS runtime

ENV FINGUARD_BEHAVIOR_MODEL_PATH=/app/models/behavior_iforest.joblib \
    HOME=/tmp \
    PATH=/opt/venv/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN groupadd --gid 10001 --system finguard \
    && useradd --uid 10001 --gid finguard --system --no-create-home finguard

WORKDIR /app
COPY --from=dependencies /opt/venv /opt/venv
COPY --chown=10001:10001 app ./app
COPY --chown=10001:10001 models ./models
COPY --from=prompt-model --chown=10001:10001 /opt/prompt-model ./models/prompt_guard_onnx

USER 10001:10001
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/ready', timeout=2).read()"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
