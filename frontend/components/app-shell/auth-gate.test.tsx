import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { AuthGate } from "./auth-gate";
import { CurrentUserProvider } from "./current-user-provider";
import { fetchApi } from "@/lib/api/client";
import { login } from "@/lib/api";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
let pathname = "/sales-detail";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => pathname,
}));
setupApiServer();
beforeEach(() => { replace.mockReset(); pathname = "/sales-detail"; });
afterEach(() => vi.unstubAllEnvs());

const me = "http://localhost:3000/api/me";
const owner = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" };

function renderGate() {
  render(
    <CurrentUserProvider>
      <AuthGate>
        <p>The app</p>
      </AuthGate>
    </CurrentUserProvider>,
  );
}

it("sends a user who must change their password to the change screen", async () => {
  server.use(http.get(me, () => HttpResponse.json({ ...owner, mustChangePassword: true })));
  renderGate();
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/change-password"));
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it("stays on the change screen without redirecting again", async () => {
  pathname = "/change-password";
  server.use(http.get(me, () => HttpResponse.json({ ...owner, mustChangePassword: true })));
  renderGate();
  expect(await screen.findByText("The app")).toBeInTheDocument();
  expect(replace).not.toHaveBeenCalled();
});

it("renders the app for a signed-in user", async () => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  renderGate();
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
  expect(await screen.findByText("The app")).toBeInTheDocument();
  expect(replace).not.toHaveBeenCalled();
});

it("sends a signed-out visitor to login and never renders the app", async () => {
  vi.stubEnv("NEXT_PUBLIC_DEMO_MODE", "true"); // even a demo build gates on a real 401
  server.use(http.get(me, () => HttpResponse.json({ code: "AUTH_REQUIRED", message: "fixture" }, { status: 401 })));
  renderGate();
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fsales-detail"));
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it("shows an error instead of the app when the backend is unreachable", async () => {
  vi.stubEnv("NEXT_PUBLIC_DEMO_MODE", "false");
  server.use(http.get(me, () => HttpResponse.error()));
  renderGate();
  expect(await screen.findByText("Couldn't reach Goldy's")).toBeInTheDocument();
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it("lets a demo build run on fixtures when there is no backend", async () => {
  vi.stubEnv("NEXT_PUBLIC_DEMO_MODE", "true");
  server.use(http.get(me, () => HttpResponse.error()));
  renderGate();
  expect(await screen.findByText("The app")).toBeInTheDocument();
});

it.each(["json", "html", "redirect"])("redirects an already signed-in user after a %s authentication failure", async failure => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  renderGate();
  await screen.findByText("The app");
  server.use(http.get("http://localhost:3000/api/fixture", () =>
    failure === "redirect" ? HttpResponse.redirect("http://localhost:3000/login") :
      failure === "html" ? new HttpResponse("Sign in", { status: 401 }) :
        HttpResponse.json({ code: "AUTH_REQUIRED", message: "expired" }, { status: 401 })),
  http.get("http://localhost:3000/login", () => new HttpResponse("Sign in")));
  await act(async () => { await expect(fetchApi("/api/fixture")).rejects.toBeDefined(); });
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fsales-detail"));
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it.each([403, 500])("keeps the session after an API HTTP %i", async status => {
  server.use(http.get(me, () => HttpResponse.json(owner)),
    http.get("http://localhost:3000/api/fixture", () => HttpResponse.json({ code: "NOT_PERMITTED", message: "fixture" }, { status })));
  renderGate();
  await screen.findByText("The app");
  await act(async () => { await expect(fetchApi("/api/fixture")).rejects.toBeDefined(); });
  expect(screen.getByText("The app")).toBeInTheDocument();
  expect(replace).not.toHaveBeenCalled();
});

it("keeps invalid login credentials local to the login form", async () => {
  server.use(http.get(me, () => HttpResponse.json(owner)),
    http.post("http://localhost:3000/api/auth/login", () => HttpResponse.json({ code: "INVALID_CREDENTIALS", message: "fixture" }, { status: 401 })));
  renderGate();
  await screen.findByText("The app");
  await act(async () => { await expect(login({ email: "fixture@example.com", password: "wrong" })).rejects.toMatchObject({ code: "INVALID_CREDENTIALS" }); });
  expect(screen.getByText("The app")).toBeInTheDocument();
  expect(replace).not.toHaveBeenCalled();
});

it("verifies a CSRF denial and redirects if the session has expired", async () => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  renderGate();
  await screen.findByText("The app");
  server.use(http.get(me, () => HttpResponse.json({ code: "AUTH_REQUIRED", message: "expired" }, { status: 401 })),
    http.post("http://localhost:3000/api/fixture", () => HttpResponse.json({ code: "NOT_PERMITTED", message: "csrf" }, { status: 403 })));
  await act(async () => { await expect(fetchApi("/api/fixture", { method: "POST" })).rejects.toBeDefined(); });
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fsales-detail"));
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it.each(["focus", "visibilitychange"])("rechecks the session on %s after a server restart", async event => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  renderGate();
  await screen.findByText("The app");
  server.use(http.get(me, () => HttpResponse.json({ code: "AUTH_REQUIRED", message: "expired" }, { status: 401 })));
  act(() => { (event === "focus" ? window : document).dispatchEvent(new Event(event)); });
  await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fsales-detail"));
  expect(screen.queryByText("The app")).not.toBeInTheDocument();
});

it("keeps the app available when a returning tab cannot reach the server", async () => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  renderGate();
  await screen.findByText("The app");
  let checked = false;
  server.use(http.get(me, () => { checked = true; return HttpResponse.error(); }));
  await act(async () => { window.dispatchEvent(new Event("focus")); });
  await waitFor(() => expect(checked).toBe(true));
  expect(screen.getByText("The app")).toBeInTheDocument();
  expect(replace).not.toHaveBeenCalled();
});
