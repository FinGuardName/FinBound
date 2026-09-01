import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from app.prompt.rules import normalize_prompt_text
from datasets.prompt.prepare import dataset_digest, validate_records
from evaluate.prompt_runtime import select_model_score_threshold

DEFAULT_DATASET = (
    Path(__file__).resolve().parents[1] / "datasets" / "prompt" / "finbound_eval_approved.jsonl"
)
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "models" / "prompt_domain_adapter.joblib"
RANDOM_SEED = 42
REGULARIZATION_C = 1.0


def read_records(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def build_classifier() -> Pipeline:
    return Pipeline(
        [
            (
                "features",
                TfidfVectorizer(
                    analyzer="char_wb",
                    ngram_range=(2, 5),
                    min_df=1,
                    sublinear_tf=True,
                    max_features=20_000,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    C=REGULARIZATION_C,
                    class_weight="balanced",
                    random_state=RANDOM_SEED,
                    max_iter=2_000,
                ),
            ),
        ]
    )


def train(dataset: Path = DEFAULT_DATASET, output: Path = DEFAULT_OUTPUT) -> dict[str, Any]:
    records = read_records(dataset)
    validate_records(records)
    development = [record for record in records if record["split"] == "development"]
    validation = [record for record in records if record["split"] == "validation"]
    classifier = build_classifier()
    classifier.fit(
        [normalize_prompt_text(record["text"]) for record in development],
        [record["label"] for record in development],
    )
    scores = {
        record["sampleId"]: float(
            classifier.predict_proba([normalize_prompt_text(record["text"])])[0][1]
        )
        for record in validation
    }
    threshold, report = select_model_score_threshold(records, scores)
    bundle = {
        "artifactVersion": "prompt-domain-adapter-2",
        "datasetSha256": dataset_digest(records),
        "trainingSplit": "development",
        "thresholdSelectionSplit": "validation",
        "randomSeed": RANDOM_SEED,
        "classifier": classifier,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, output, compress=3)
    return {
        "artifact": str(output),
        "artifactSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "selectedThreshold": threshold,
        "validation": report,
        "heldOutRead": False,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    arguments = parser.parse_args()
    print(json.dumps(train(arguments.dataset, arguments.output), ensure_ascii=False, indent=2))
