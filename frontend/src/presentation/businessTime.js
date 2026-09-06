/**
 * 업무 시각 표기. **항상 한국 시간으로 보여준다.**
 *
 * <p>Core 는 감사 기록의 `requestedAt` 을 UTC(`...Z`)로 준다. 예전에는 화면이 ISO 문자열의
 * 11~19번째 글자를 잘라 썼는데(`requestedAt.slice(11, 19)`), 그러면 UTC 가 한국 시간인 척
 * 표시돼 아홉 시간이 어긋났다. Mock Fixture 는 `+09:00` 으로 적혀 있어 잘라도 맞았기 때문에
 * 실제 Core 에 붙이기 전까지 드러나지 않았다.
 *
 * <p>브라우저 로컬 시간대가 아니라 Asia/Seoul 로 못 박는다. 행동 이상 모델이 업무 시간 밖을
 * Asia/Seoul 기준 9시 전·18시 이후로 판단하기 때문이다
 * (`ai-risk/app/feature_builder/behavior.py`). 화면이 보는 이의 시간대를 따라가면 해외에서
 * 여는 순간 "업무 시간 밖이라 이상" 이라는 근거와 화면의 시각이 서로 어긋난다.
 */
const BUSINESS_TIME_ZONE = 'Asia/Seoul'

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: BUSINESS_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

/** `2026-09-06T09:43:00Z` → `18:43:00`. 값이 없거나 해석되지 않으면 `--:--:--`. */
export function formatBusinessTime(isoText) {
  if (!isoText) return '--:--:--'
  const moment = new Date(isoText)
  if (Number.isNaN(moment.getTime())) return '--:--:--'
  // ko-KR 은 24시 자정을 "24:00:00" 으로 낸다. ISO 와 맞춰 00 으로 되돌린다.
  return timeFormatter.format(moment).replace(/^24:/, '00:')
}
