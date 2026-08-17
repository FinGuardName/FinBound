from fastapi import FastAPI

app = FastAPI(
    title="FinGuard AI Risk Engine",
    version="0.1.0",
    description="Returns risk signals only; authorization decisions belong to OPA.",
)


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {"status": "UP"}
