import joblib

from train.train_prompt_adapter import REGULARIZATION_C, train


def test_prompt_adapter_keeps_validation_selected_regularization(tmp_path) -> None:
    artifact = tmp_path / "prompt-domain-adapter.joblib"

    result = train(output=artifact)
    bundle = joblib.load(artifact)

    assert bundle["artifactVersion"] == "prompt-domain-adapter-2"
    assert bundle["classifier"].named_steps["classifier"].C == REGULARIZATION_C == 1.0
    assert result["validation"]["overall"]["f1"] >= 0.93
    assert result["validation"]["overall"]["falsePositiveRate"] <= 0.05
    assert result["heldOutRead"] is False
