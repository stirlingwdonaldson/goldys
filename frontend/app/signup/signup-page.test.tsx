import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server, useApiServer } from "@/tests/api-server";
import SignupPage from "./page";

const { push, refresh } = vi.hoisted(() => ({ push: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));
useApiServer();
beforeEach(() => { push.mockReset(); refresh.mockReset(); });
const signupUrl = "http://localhost:3000/api/auth/signup";
const loginUrl = "http://localhost:3000/api/auth/login";
const profile = { displayName: "Fixture", department: "GENERAL", seniority: "JUNIOR" };
function fill() {
  render(<SignupPage />);
  fireEvent.change(screen.getByPlaceholderText("Full name"), { target: { value: "Fixture" } });
  fireEvent.change(screen.getByPlaceholderText("Work email"), { target: { value: "fixture@example.invalid" } });
  fireEvent.change(screen.getByPlaceholderText("Password (at least 8 characters)"), { target: { value: "fixture-password" } });
  return screen.getByRole("button", { name: "Sign up" }).closest("form")!;
}

it("does not offer account creation again after signup succeeds and auto-login fails", async () => {
  let created = 0;
  server.use(http.post(signupUrl, () => { created++; return HttpResponse.json(profile, { status: 201 }); }),
    http.post(loginUrl, () => HttpResponse.json({ code: "INVALID_CREDENTIALS", message: "fixture" }, { status: 401, headers: { "X-Correlation-ID": "req-login" } })));
  fireEvent.submit(fill());
  expect(await screen.findByText("Account created. Sign in to continue.")).toBeInTheDocument();
  expect(screen.getByRole("alert")).toBeInTheDocument();
  expect(screen.getByText(/req-login/)).toBeInTheDocument();
  expect(created).toBe(1);
  expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
  expect(screen.queryByRole("button", { name: "Sign up" })).not.toBeInTheDocument();
  expect(push).not.toHaveBeenCalled();
});

it("locks duplicate signup while pending and navigates once after validated auto-login", async () => {
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let created = 0;
  let logins = 0;
  server.use(http.post(signupUrl, async () => { created++; await pending; return HttpResponse.json(profile, { status: 201 }); }),
    http.post(loginUrl, () => { logins++; return HttpResponse.json(profile); }));
  const form = fill();
  act(() => { fireEvent.submit(form); fireEvent.submit(form); });
  expect(screen.getByRole("button", { name: "Creating account…" })).toBeDisabled();
  try {
    await waitFor(() => expect(created).toBeGreaterThan(0));
    expect(created).toBe(1);
  } finally { await act(async () => release()); }
  await waitFor(() => expect(push).toHaveBeenCalledWith("/dashboard"));
  expect(logins).toBe(1);
  expect(push).toHaveBeenCalledTimes(1);
  expect(refresh).toHaveBeenCalledTimes(1);
});

it("does not log in or navigate after rejected signup", async () => {
  let logins = 0;
  server.use(http.post(signupUrl, () => HttpResponse.json({ code: "VALIDATION_FAILED", message: "fixture-secret" }, { status: 400 })),
    http.post(loginUrl, () => { logins++; return HttpResponse.json(profile); }));
  fireEvent.submit(fill());
  expect(await screen.findByRole("alert")).not.toHaveTextContent("fixture-secret");
  expect(logins).toBe(0);
  expect(push).not.toHaveBeenCalled();
});

it("explains uncertain signup without automatic retry", async () => {
  let calls = 0;
  server.use(http.post(signupUrl, () => { calls++; return HttpResponse.error(); }));
  fireEvent.submit(fill());
  expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't confirm account creation. If you already created an account, sign in.");
  expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
  expect(calls).toBe(1);
  expect(push).not.toHaveBeenCalled();
});
