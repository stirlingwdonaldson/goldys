import { expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { getCurrentUser, login, logout, signup } from "./index";

setupApiServer();
const origin = "http://localhost:3000";
const profile = { displayName: "Fixture staff", department: "GENERAL", seniority: "JUNIOR" };
const input = { email: "fixture+staff@example.invalid", password: "fixture-&-日本語" };

it("forwards the profile request signal", async () => {
  const controller = new AbortController();
  server.use(http.get(`${origin}/api/me`, () => HttpResponse.json(profile)));
  const spy = vi.spyOn(globalThis, "fetch");
  await expect(getCurrentUser({ signal: controller.signal })).resolves.toEqual(profile);
  expect(spy.mock.calls[0][1]?.signal).toBe(controller.signal);
});

it.each(["profile", "login", "signup"])("validates malformed %s success", async endpoint => {
  const handler = () => HttpResponse.json({ displayName: "Fixture", department: "GENERAL" }, { headers: { "X-Correlation-ID": "req-profile" } });
  server.use(http.get(`${origin}/api/me`, handler), http.post(`${origin}/api/auth/login`, handler), http.post(`${origin}/api/auth/signup`, handler));
  const result = endpoint === "profile" ? getCurrentUser() : endpoint === "login" ? login(input) : signup({ ...input, displayName: "Fixture" });
  await expect(result).rejects.toMatchObject({ code: "INVALID_RESPONSE", status: 200, correlationId: "req-profile", diagnostics: [{ path: ["seniority"], code: "invalid_type" }] });
});

it("preserves form-encoded email/password characters for Spring login", async () => {
  let observed: Record<string, string> = {};
  server.use(http.post(`${origin}/api/auth/login`, async ({ request }) => {
    expect(request.headers.get("content-type")).toBe("application/x-www-form-urlencoded");
    observed = Object.fromEntries(new URLSearchParams(await request.text()));
    return HttpResponse.json(profile);
  }));
  await expect(login(input)).resolves.toEqual(profile);
  expect(observed).toEqual(input);
});

it("sends JSON signup and accepts Spring's 201", async () => {
  let observed: unknown;
  server.use(http.post(`${origin}/api/auth/signup`, async ({ request }) => {
    expect(request.headers.get("content-type")).toBe("application/json");
    observed = await request.json();
    return HttpResponse.json(profile, { status: 201 });
  }));
  const payload = { ...input, displayName: "Fixture staff" };
  await expect(signup(payload)).resolves.toEqual(profile);
  expect(observed).toEqual(payload);
});

it("preserves INVALID_CREDENTIALS and 401", async () => {
  server.use(http.post(`${origin}/api/auth/login`, () => HttpResponse.json({ code: "INVALID_CREDENTIALS", message: "fixture" }, { status: 401 })));
  await expect(login(input)).rejects.toMatchObject({ code: "INVALID_CREDENTIALS", status: 401 });
});

it("logs out with Spring CSRF and accepts 204", async () => {
  document.cookie = "XSRF-TOKEN=fixture%2Bcsrf; path=/";
  let csrf: string | null = null;
  server.use(http.post(`${origin}/api/auth/logout`, ({ request }) => {
    csrf = request.headers.get("x-xsrf-token");
    return new HttpResponse(null, { status: 204 });
  }));
  await expect(logout()).resolves.toBeUndefined();
  expect(csrf).toBe("fixture+csrf");
});
