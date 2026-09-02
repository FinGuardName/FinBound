import pytest

from app.prompt.model import HybridPromptClassifier, PromptDetectorConfig, PromptModelError


class FakeComponent:
    def __init__(self, score: float, failure: bool = False) -> None:
        self.score = score
        self.failure = failure

    def check_ready(self) -> None:
        if self.failure:
            raise PromptModelError("unavailable")

    def predict_attack_score(self, text: str) -> float:
        if self.failure:
            raise PromptModelError("unavailable")
        return self.score


def test_hybrid_uses_independently_calibrated_max_evidence() -> None:
    config = PromptDetectorConfig.load()
    classifier = HybridPromptClassifier(
        config,
        pretrained=FakeComponent(config.pretrained_score_threshold * 0.2),
        domain_adapter=FakeComponent(config.domain_adapter_score_threshold),
    )

    assert classifier.predict_attack_score("input is not retained") == 1.0


def test_hybrid_readiness_fails_when_any_required_component_fails() -> None:
    config = PromptDetectorConfig.load()
    classifier = HybridPromptClassifier(
        config,
        pretrained=FakeComponent(0.0),
        domain_adapter=FakeComponent(0.0, failure=True),
    )

    with pytest.raises(PromptModelError):
        classifier.check_ready()
