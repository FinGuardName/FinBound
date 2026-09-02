# syntax=docker/dockerfile:1

FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN addgroup --system finguard && adduser --system --ingroup finguard finguard

COPY requirements.lock pyproject.toml ./
RUN pip install --upgrade pip \
    && pip install -r requirements.lock

COPY app app
COPY models models
RUN pip install --no-deps -e .

USER finguard

EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --retries=12 --start-period=20s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/ready', timeout=2).read()"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
