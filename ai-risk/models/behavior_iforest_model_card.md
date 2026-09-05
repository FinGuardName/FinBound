# Behavior Isolation Forest Model Card

## 개요

- 모델: `iforest-1`
- Feature Schema: `behavior-features-1`
- 합성 데이터: `synthetic-agent-log-4`
- 목적: 허용된 범위 안에서 발생하는 Agent 행동의 상대적 이상 위험을 `LOW`, `ALERT`,
  `CRITICAL` 신호로 제공합니다.

이 모델은 보조 Risk Signal을 생성할 뿐 `ALLOW` 또는 `BLOCK`을 직접 결정하지 않습니다. 최종 결정은
Scope, Prompt Risk, Hard Limit과 함께 OPA 정책이 수행합니다. `behaviorRisk`는 공격 확률이 아니라
검증 정상 분포에 대해 보정한 상대 점수입니다.

## 학습 및 등급 보정

Isolation Forest는 합성 정상 Session의 최근 5분 행동 Feature만으로 학습합니다. 학습 데이터와
Runtime은 동일한 Feature Builder를 사용하며, 현재 요청의 미래 Outcome은 Feature에 포함하지
않습니다. Session 단위로 Train, Validation, Held-out을 분리합니다.

Alert Threshold는 정상 Validation 점수의 90번째 분위수입니다. Critical Threshold는 야간 누적
호출을 Critical 양성으로, 업무시간 빠른 반복 호출을 Alert-only 음성으로 두고 정상 전체 및 정상
Scenario별 오탐률 제약 안에서 선택합니다. 현재 Artifact의 Threshold는 각각 `0.9042`, `1.0000`이며
Runtime은 Artifact에 저장된 값을 단일 기준으로 사용합니다.

## 평가 결과

고정 Held-out 평가에서 Alert 재현율은 `1.0000`, 정상 Alert 오탐률은 `0.1250`, 정상 Critical
오탐률은 `0.0000`입니다. 업무시간 빠른 반복 호출의 Critical 승격률은 `0.0125`, 야간 누적 호출의
Critical 재현율은 `0.9625`, ROC-AUC는 `0.9940`입니다.

별도 5개 Seed의 분포 이동 평가에서 전체 Alert 재현율 최솟값은 `0.9792`, 정상 Critical 오탐률
최댓값은 `0.0225`, 업무시간 빠른 반복 호출의 Alert 재현율 최솟값은 `0.9583`, Critical 승격률
최댓값은 `0.2250`, 야간 누적 호출의 Critical 재현율 최솟값은 `0.8417`, ROC-AUC 최솟값은
`0.9713`입니다.

## 제한 및 안전한 사용

- 평가는 합성 데이터에서의 P0 feasibility만 보여주며 실제 금융사 로그에 대한 일반화 성능을
  보장하지 않습니다.
- 정상 Alert 오탐은 의도적으로 허용됩니다. Alert는 차단이 아니라 추가 표시와 감사 신호입니다.
- Critical도 단독 권한 판정이 아니며, 정책 계층에서 다른 신뢰 가능한 Context와 함께 소비해야 합니다.
- 최근 5분 유효 완료 이력이 5건 미만이면 모델을 적용하지 않고 중립 `COLD_START` 신호를 반환합니다.
- 운영 전 실제 또는 시연 Replay 데이터로 Scenario별 Threshold와 분포 이동 성능을 다시 검증해야
  합니다.

## 재현성

고정 Seed 재학습은 Scaler, Calibration 점수, Threshold, 평가 JSON과 고정 Feature 전체의 추론 결과가
동일한지 검증합니다. joblib 파일의 SHA-256은 특정 배포 파일의 무결성 확인에는 사용할 수 있지만,
직렬화 환경이 달라질 수 있는 재학습 결과의 의미적 동일성 기준으로 사용하지 않습니다.
배포 Runtime은 `behavior_iforest.json`에 고정된 SHA-256과 일치하는 Artifact만 허용하고, 검증에
사용한 동일 바이트에서 joblib Bundle을 로드합니다.
