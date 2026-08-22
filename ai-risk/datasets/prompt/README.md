# Prompt Risk Evaluation Data

이 폴더는 F12 Prompt Injection Detector의 데이터 출처와 FinGuard 자체 평가 Seed를 관리합니다.
실제 개인정보·금융 데이터·Credential은 사용하지 않습니다.

## 데이터 계층

1. `sources.json`: 외부 후보의 License, Revision, Split, 채택 판단
2. `native_ko_seed.jsonl`: FinGuard 업무에 맞게 직접 작성한 한국어 금융 평가 Seed
3. `fetch_public.py`: 승인된 외부 소스를 Revision 확인 후 로컬 Cache로 수집
4. `prepare.py`: Schema, 중복, 민감정보, Group Leakage 검증 및 평가 Report 생성

외부 원문은 Git에 Commit하지 않습니다. 승인된 공개 데이터도 `datasets/cache/prompt`에만 내려받고
Source Revision과 License를 통해 재현합니다.

## Split 정책

- `development`: Rule과 후보 모델 구현에 사용
- `validation`: 후보 모델 선택, Rule 결합식과 Threshold 선정에 사용
- `held_out_test`: 모델과 Threshold를 고정한 후 한 번만 최종 평가
- 동일 `groupId`의 변형 문장은 반드시 같은 Split에 둠
- 공개 영어 데이터의 번역본을 Native Korean이라고 부르지 않음

`reviewStatus=DRAFT`인 자체 작성 문장은 팀원이 Label과 자연스러움을 검토한 뒤 `APPROVED`로
바꿉니다. 승인 전에는 최종 성능 수치에 포함하지 않습니다.

## 실행

```bash
cd ai-risk
python -m datasets.prompt.prepare
python -m datasets.prompt.fetch_public hf-deepset-prompt-injections train
```

외부 Revision이 변경되면 수집은 실패합니다. 변경된 Dataset Card, License, Schema와 Sample을
다시 검토한 후 `sources.json`을 갱신해야 합니다.
