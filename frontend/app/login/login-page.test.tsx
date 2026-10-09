import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import LoginPage from "./page";

const { push, refresh } = vi.hoisted(() => ({ push: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));
setupApiServer();
beforeEach(() => { push.mockReset(); refresh.mockReset(); });
const url = "http://localhost:3000/api/auth/login";
const profile = { displayName: "Fixture", department: "GENERAL", seniority: "JUNIOR" };
function fill() {
  render(<LoginPage />);
  fireEvent.change(screen.getByPlaceholderText("Email"), { target: { value: "fixture@example.invalid" } });
  fireEvent.change(screen.getByPlaceholderText("Password"), { target: { value: "fixture-password" } });
  return screen.getByRole("button", { name: "Sign in" }).closest("form")!;
}

it("locks same-tick submits, exposes pending state and navigates once", async () => {
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let calls = 0;
  server.use(http.post(url, async () => { calls++; await pending; return HttpResponse.json(profile); }));
  const form = fill();
  act(() => { fireEvent.submit(form); fireEvent.submit(form); });
  expect(screen.getByRole("button", { name: "Signing in…" })).toBeDisabled();
  try {
    await waitFor(() => expect(calls).toBeGreaterThan(0));
    expect(calls).toBe(1);
  } finally { await act(async () => release()); }
  await waitFor(() => expect(push).toHaveBeenCalledWith("/dashboard"));
  expect(push).toHaveBeenCalledTimes(1);
  expect(refresh).toHaveBeenCalledTimes(1);
});

it.each([401, 500, 200])("shows safe accessible failure at HTTP %i without navigation", async status => {
  server.use(http.post(url, () => HttpResponse.json(status === 200 ? { seniority: "OWNER" } : { code: status === 401 ? "INVALID_CREDENTIALS" : "FIXTURE", message: "fixture-secret-do-not-display" }, { status, headers: { "X-Correlation-ID": "req-login" } })));
  fireEvent.submit(fill());
  const alert = await screen.findByRole("alert");
  expect(alert).not.toHaveTextContent("fixture-secret-do-not-display");
  expect(screen.getByText(/req-login/)).toBeInTheDocument();
  expect(push).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "Sign in" })).toBeEnabled();
});

it("names the existing form inputs for assistive technology", () => {
  fill();
  expect(screen.getByRole("textbox", { name: "Email" })).toBeInTheDocument();
  expect(screen.getByLabelText("Password")).toBeInTheDocument();
});
