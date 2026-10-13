import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { AuthGate } from "./auth-gate";
import { CurrentUserProvider } from "./current-user-provider";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/sales-detail",
}));
setupApiServer();
beforeEach(() => replace.mockReset());
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
