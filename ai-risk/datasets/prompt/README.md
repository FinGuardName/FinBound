# Prompt Risk Evaluation Data

이 폴더는 F12 Prompt Injection Detector의 데이터 출처와 FinBound 자체 평가 Seed를 관리합니다.
실제 개인정보·금융 데이터·Credential은 사용하지 않습니다.

## 데이터 계층

1. `sources.json`: 외부 후보의 License, Revision, Split, 채택 판단
2. `finbound_eval_seed.jsonl`: FinBound 업무에 맞게 직접 작성한 한국어·영어·혼합어 평가 Seed
3. `fetch_public.py`: 승인된 외부 소스의 고정 Parquet Artifact를 로컬 Cache로 수집
4. `prepare.py`: Schema, 중복·유사도, 민감정보, Group Leakage 검증 및 평가 Report 생성
5. `review.py`: 정답을 숨긴 2인 독립 검토 Packet 생성 및 최종 승인 Set 생성
6. `evaluate/prompt_metrics.py`: 승인 Set과 Detector 예측으로 필수 성능지표 산출

외부 원문은 Git에 Commit하지 않습니다. 승인된 공개 데이터도 `datasets/cache/prompt`에만 내려받고
Source Revision, Parquet Revision, SHA-256, License를 통해 재현합니다. Hub의 최신 Revision이
변경되어도 Manifest에 고정된 Artifact를 사용하며, Checksum이 다르면 수집을 중단합니다.

## Split 정책

- `development`: Rule과 후보 모델 구현에 사용
- `validation`: 후보 모델 선택, Rule 결합식과 Threshold 선정에 사용
- `held_out_test`: 모델과 Threshold를 고정한 후 한 번만 최종 평가
- 동일 `groupId`의 변형 문장은 반드시 같은 Split에 둠
- 서로 다른 Split의 문자 3-gram 유사도가 `0.82` 이상이면 단순 변형 누수로 보고 실패
- 공개 영어 데이터의 번역본을 Native Korean이라고 부르지 않음

`reviewStatus=DRAFT`인 자체 작성 문장은 팀원 2명이 서로의 판단을 보지 않고 Label, 자연스러움,
Group을 검토해야 합니다. 두 Packet이 모두 작성되고 원본 Dataset SHA-256과 일치할 때만
`review.py finalize`가 `APPROVED` 최종 Set을 만듭니다. 현재 Source Seed는 의도적으로 모두
`DRAFT`이며, 승인 Set 생성 전에는 최종 성능 수치에 포함할 수 없습니다. 자세한 절차는
[`LABELING_GUIDE.md`](LABELING_GUIDE.md)를 따릅니다.

현재 Seed는 144건이며 각 Split에 48건을 둡니다. Split별 정상 12, Hard Negative 12, 공격 24,
Label 0/1 각 24건이고, 한국어 32, 혼합어 4, 영어 12건입니다. 6개 공격 유형은 Split마다 각
4건입니다. 문서 기반 Injection과 띄어쓰기·표기 우회도 포함합니다.

공개 영어 데이터는 외부 Benchmark/공격 Supplement로만 사용하고 FinBound Held-out Test와
합치지 않습니다. FinBound 최종 지표는 2인 승인된 자체 평가 Set에서 산출하며, 공개 Benchmark
결과는 출처별로 별도 보고합니다.

## 실행

```bash
cd ai-risk
pip install -e ".[data]"
python -m datasets.prompt.prepare
python -m datasets.prompt.fetch_public hf-deepset-prompt-injections train
```

고정 Revision이나 Artifact가 사라지거나 Checksum이 달라지면 수집은 실패합니다. Dataset Card,
License, Schema와 Sample을 다시 검토한 뒤에만 `sources.json`을 갱신합니다.

## 2인 독립 검토

두 Reviewer는 같은 Dataset SHA-256에서 각자 Packet을 만들고, 상대방의 작성 결과를 보지 않은
상태로 모든 항목을 채웁니다. Packet에는 기존 Label이 노출되지 않습니다.

```bash
python -m datasets.prompt.review create --reviewer reviewer-a --output datasets/cache/prompt/reviews/a.json
python -m datasets.prompt.review create --reviewer reviewer-b --output datasets/cache/prompt/reviews/b.json
python -m datasets.prompt.review finalize \
  --review-a datasets/cache/prompt/reviews/a.json \
  --review-b datasets/cache/prompt/reviews/b.json \
  --output datasets/prompt/finbound_eval_approved.jsonl \
  --manifest datasets/prompt/approval_manifest.json
```

Reviewer 이름은 달라야 하며 `reviewedAt`, Label, Sample Type, Attack Type, 자연스러움, Group 검토,
최종 결정을 모두 기록합니다. 불일치나 미검토 항목이 하나라도 있으면 승인 Set을 만들지 않습니다.
승인 후 생성되는 최종 Set과 원문 없는 Approval Manifest를 PR에 Commit하고 다음 명령으로
`finalEvaluationReady=true` Report를 다시 생성합니다.

```bash
python -m datasets.prompt.prepare \
  --source datasets/prompt/finbound_eval_approved.jsonl \
  --report evaluate/prompt_dataset_report.json
```

## 최종 성능 평가

최종 평가에서는 Prompt Detector가 승인 Set 중 `held_out_test`의 모든 `sampleId`에 대해
`detected` Boolean을 JSONL로 출력합니다. 기본 실행은 Held-out만 평가하며, Development나
Validation 분석은 `--split`을 명시합니다. Report에는 원문이 아니라 오탐·미탐 `sampleId`만
기록됩니다.

```bash
python -m evaluate.prompt_metrics \
  --gold datasets/prompt/finbound_eval_approved.jsonl \
  --predictions datasets/cache/prompt/final/predictions.jsonl \
  --output evaluate/prompt_metrics.json
```

Report는 Precision, Recall, F1, False Positive Rate, 언어별 지표, 공격 유형별 Recall,
False Positive/Negative Sample ID를 포함합니다. 특정 모델의 성능이나 실제 금융 환경 일반화
성능은 측정 결과 없이 주장하지 않습니다.
