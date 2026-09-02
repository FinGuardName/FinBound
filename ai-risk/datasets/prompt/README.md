# Prompt Risk Evaluation Data

이 폴더는 F12 Prompt Injection Detector의 데이터 출처와 FinBound 자체 평가 Seed를 관리합니다.
실제 개인정보·금융 데이터·Credential은 사용하지 않습니다.

## 데이터 계층

1. `sources.json`: 외부 후보의 License, Revision, Split, 채택 판단
2. `finbound_eval_seed.jsonl`: FinBound 업무에 맞게 직접 작성한 한국어 중심·영어·혼합어 평가 Seed
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
- 같은 기본 의미·요청 의도의 번역·패러프레이즈는 언어와 외부 문맥이 달라도 같은 Group으로 취급
- 서로 다른 Split의 문자 3-gram 유사도가 `0.82` 이상이면 단순 변형 누수로 보고 실패
- 공개 영어 데이터의 번역본을 Native Korean이라고 부르지 않음

`reviewStatus=DRAFT`인 자체 작성 문장은 Label, 자연스러움, Group을 항목별로 검토해야 합니다.
기본 절차는 독립 Reviewer 2명의 Blind Review입니다. P0 일정상 팀이 명시적으로 합의한 경우에는
AI 항목별 검수와 Dataset Owner의 전체 승인으로 대체할 수 있으며, 이때 Manifest에
`reviewMethod=AI_ASSISTED_OWNER_APPROVAL`, `independentHumanReview=false`와 한계를 기록합니다.
어느 방식이든 원본 Dataset SHA-256과 Packet이 일치할 때만 `APPROVED` Set을 만들며, 승인 전에는
최종 성능 수치에 포함할 수 없습니다. 자세한 절차는 [`LABELING_GUIDE.md`](LABELING_GUIDE.md)를
따릅니다.

현재 Seed는 216건입니다. Development와 Validation은 각각 48건이며 정상 12, Hard Negative 12,
공격 24, Label 0/1 각 24건, 한국어 32, 혼합어 4, 영어 12건입니다. Held-out Test는 최종 지표의
변동폭을 낮추기 위해 120건으로 분리하며 정상 30, Hard Negative 30, 공격 60, 한국어 80,
혼합어 20, 영어 20건입니다. Held-out의 6개 공격 유형은 각 10건입니다. 한국어를 주 평가로,
혼합어를 필수 방어 평가로, 순수 영어를 보조 평가로 해석합니다. 문서 기반 Injection과
띄어쓰기·표기 우회도 포함합니다.

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
상태로 모든 항목을 채웁니다. Packet에는 기존 Label이 노출되지 않습니다. 원본 `groupId` 대신
Dataset SHA에 결박된 불투명 `reviewGroupId`를 표시하고 같은 Group의 항목을 인접 배치하므로,
Reviewer는 정답이나 Split을 보지 않고 Grouping의 타당성을 검토할 수 있습니다.

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

## AI 검수 + Dataset Owner 승인

P0 일정상 팀 합의로 독립 2인 검수를 대체할 때만 사용합니다. AI Reviewer가 모든 항목의 Label,
Sample Type, Attack Type, 자연스러움과 Grouping을 기록한 Packet을 만든 뒤 Dataset Owner가 전체
범위를 승인합니다. 사람 2인의 독립 검수로 표시하지 않으며 Manifest에 검수 방식과 한계를 남깁니다.

```bash
python -m datasets.prompt.review finalize-ai-assisted \
  --review datasets/cache/prompt/reviews/codex-ai-review.json \
  --approver YEOUL0520 \
  --approved-at 2026-09-01T12:00:00+09:00 \
  --output datasets/prompt/finbound_eval_approved.jsonl \
  --manifest datasets/prompt/approval_manifest.json
```

현재 승인 Snapshot은 Dataset Owner의 명시적 팀 합의에 따라 `codex-ai-semantic-review-v7` 전수 검수와
`YEOUL0520` 전체 승인으로 생성했습니다. `approval_manifest.json`은 독립 사람 검수가 없었다는
한계를 명시하며, 승인 Set 216건의 SHA-256과 검수 Packet SHA-256을 고정합니다. Source Seed는
재검수 출발점으로 계속 `DRAFT`를 유지하고 최종 평가는 `finbound_eval_approved.jsonl`만 사용합니다.

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

Report는 Precision, Recall, F1, False Positive Rate, Wilson 95% 신뢰구간, 언어별 지표,
공격 유형별 Recall, 한국어 금융 정상/Hard Negative 전용 FPR, False Positive/Negative Sample ID를
포함합니다. 균형 평가 Set의 Precision은 운영 환경의 실제 공격 비율을 반영한 값으로 해석하지
않습니다. 특정 모델의 성능이나 실제 금융 환경 일반화 성능은 측정 결과 없이 주장하지 않습니다.
