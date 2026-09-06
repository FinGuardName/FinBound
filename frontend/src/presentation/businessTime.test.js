import { describe, expect, it } from 'vitest'

import { formatBusinessTime } from './businessTime'

describe('formatBusinessTime', () => {
  it('shows a UTC timestamp from Core in Korean time', () => {
    // 이 한 줄이 이 파일의 존재 이유다. 예전 화면은 문자열을 잘라 09:43:00 을 보여줬다.
    expect(formatBusinessTime('2026-09-06T09:43:00.116656Z')).toBe('18:43:00')
  })

  it('leaves an already-Korean timestamp where it is', () => {
    expect(formatBusinessTime('2026-08-25T18:15:42+09:00')).toBe('18:15:42')
  })

  it('converts a timestamp from any other offset', () => {
    // 뉴욕 자정 = 한국 오후 1시. 보는 이의 시간대와 무관하게 같은 값이어야 한다.
    expect(formatBusinessTime('2026-09-06T00:00:00-04:00')).toBe('13:00:00')
  })

  it('crosses the date boundary rather than clamping', () => {
    // UTC 로 늦은 밤은 한국에서 다음 날 아침이다.
    expect(formatBusinessTime('2026-09-06T22:30:00Z')).toBe('07:30:00')
  })

  it('shows Korean midnight as 00, not 24', () => {
    expect(formatBusinessTime('2026-09-06T15:00:00Z')).toBe('00:00:00')
  })

  it('does not crash on a missing or unreadable value', () => {
    expect(formatBusinessTime(null)).toBe('--:--:--')
    expect(formatBusinessTime('')).toBe('--:--:--')
    expect(formatBusinessTime('not a timestamp')).toBe('--:--:--')
  })
})
