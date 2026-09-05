import { readFileSync } from 'node:fs'

import { describe, expect, it } from 'vitest'

const frontendRoot = process.cwd()
const readFrontendFile = (path) => readFileSync(`${frontendRoot}/${path}`, 'utf8')

describe('Frontend production container contract', () => {
  it('proxies only the Core Public API surface', () => {
    const nginx = readFrontendFile('nginx/default.conf.template')

    expect(nginx).toContain('location ^~ /core-api/api/v1/')
    expect(nginx).toContain('rewrite ^/core-api(/api/v1/.*)$ $1 break;')
    expect(nginx).toContain('location ^~ /core-api/')
    expect(nginx).not.toContain('rewrite ^/core-api/(.*)$')
  })

  it('refuses paths that dodged normalization instead of forwarding them to Core', () => {
    const nginx = readFrontendFile('nginx/default.conf.template')

    // `..;` 는 Nginx 에게 상위 디렉터리가 아니라 이름이 `..;` 인 세그먼트라 정규화되지 않고
    // Prefix 검사를 통과한다. 정규화가 끝난 $uri 에 `..` 가 남아 있으면 그런 변형이다.
    expect(nginx).toContain('if ($uri ~ "\\.\\.")')
    // Query String 을 포함하는 $request_uri 로 검사하면 값에 `..` 가 든 정상 요청까지 막는다.
    expect(nginx).not.toContain('if ($request_uri ~ "\\.\\.")')
  })

  it('does not require IPv6 support to start Nginx', () => {
    const nginx = readFrontendFile('nginx/default.conf.template')

    expect(nginx).toContain('listen 8080;')
    expect(nginx).not.toContain('listen [::]:8080;')
  })

  it('passes only public Vite settings into the static build', () => {
    const dockerfile = readFrontendFile('Dockerfile')
    const apiAdapter = readFrontendFile('src/services/finboundApi.js')

    expect(dockerfile).toContain('ARG VITE_FINBOUND_API_MODE=real')
    expect(dockerfile).toContain('ARG VITE_FINBOUND_API_BASE_URL=/core-api')
    expect(apiAdapter).toContain('import.meta.env.VITE_FINBOUND_API_MODE')
    expect(apiAdapter).toContain('import.meta.env.VITE_FINBOUND_API_BASE_URL')
    expect(dockerfile).not.toMatch(/ARG .*CREDENTIAL/)
    expect(dockerfile).not.toMatch(/ENV .*CREDENTIAL/)
  })
})
