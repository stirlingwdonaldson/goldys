import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  use: {
    baseURL: "http://127.0.0.1:3117",
    headless: true,
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE },
    trace: "retain-on-failure",
  },
  webServer: {
    command: "bun run start --hostname 127.0.0.1 --port 3117",
    url: "http://127.0.0.1:3117/login",
    reuseExistingServer: false,
    env: { NEXT_PUBLIC_DEMO_MODE: "false", NEXT_TELEMETRY_DISABLED: "1" },
    timeout: 120_000,
  },
});
