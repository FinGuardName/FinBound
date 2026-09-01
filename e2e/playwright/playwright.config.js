import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  workers: 1,
  reporter: [['line']],
  use: {
    baseURL: process.env.FRONTEND_BASE_URL ?? 'http://127.0.0.1:18088',
    trace: 'retain-on-failure',
  },
})
