# Prompt Risk Evaluation Data

이 폴더는 F12 Prompt Injection Detector의 데이터 출처와 FinBound 자체 평가 Seed를 관리합니다.
실제 개인정보·금융 데이터·Credential은 사용하지 않습니다.

## 데이터 계층

1. `sources.json`: 외부 후보의 License, Revision, Split, 채택 판단
2. `native_ko_seed.jsonl`: FinBound 업무에 맞게 직접 작성한 한국어·혼합어 금융 평가 Seed
3. `fetch_public.py`: 승인된 외부 소스의 고정 Parquet Artifact를 로컬 Cache로 수집
4. `prepare.py`: Schema, 중복·유사도, 민감정보, Group Leakage 검증 및 평가 Report 생성

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

`reviewStatus=DRAFT`인 자체 작성 문장은 팀원이 Label과 자연스러움을 검토한 뒤 `APPROVED`로
바꿉니다. 현재 Seed는 모두 `DRAFT`이므로 최종 성능 수치에 포함할 수 없습니다. 판정 기준과 2인 검토 절차는
[`LABELING_GUIDE.md`](LABELING_GUIDE.md)를 따릅니다.

현재 Seed는 각 Split의 정상·Hard Negative·6개 공격 유형을 균형 있게 유지하고, 문서 기반
Injection, 띄어쓰기 우회, 한국어·영어 혼합 입력을 포함합니다. 이 구성은 Precision, Recall,
F1, False Positive Rate, 공격 유형별 Recall, 언어별 성능을 측정하기 위한 기반이며 특정 모델의
성능을 보장하거나 실제 금융 환경 일반화 성능을 주장하지 않습니다.

## 실행

```bash
cd ai-risk
pip install -e ".[data]"
python -m datasets.prompt.prepare
python -m datasets.prompt.fetch_public hf-deepset-prompt-injections train
```

고정 Revision이나 Artifact가 사라지거나 Checksum이 달라지면 수집은 실패합니다. Dataset Card,
License, Schema와 Sample을 다시 검토한 뒤에만 `sources.json`을 갱신합니다.
