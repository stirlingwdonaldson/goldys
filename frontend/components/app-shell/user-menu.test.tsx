import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { SidebarProvider } from "@/components/ui/sidebar";
import { server, useApiServer } from "@/tests/api-server";
import { CurrentUserProvider } from "./current-user-provider";
import { UserMenu } from "./user-menu";

const { toast } = vi.hoisted(() => ({ toast: vi.fn() }));
vi.mock("@/components/feedback/toast", () => ({ useToast: () => ({ toast }) }));
useApiServer();
beforeEach(() => {
  toast.mockReset();
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
});
const me = "http://localhost:3000/api/me";
const profile = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" };
function show() {
  render(<SidebarProvider><CurrentUserProvider><UserMenu /></CurrentUserProvider></SidebarProvider>);
}

it("shows a persistent profile failure with correlation and retry", async () => {
  server.use(http.get(me, () => new HttpResponse(null, { status: 500, headers: { "X-Correlation-ID": "req-profile" } })));
  show();
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.getByText(/req-profile/)).toBeInTheDocument();
  server.use(http.get(me, () => HttpResponse.json(profile)));
  fireEvent.click(screen.getByRole("button", { name: "Retry profile" }));
  expect(await screen.findByText("Fixture owner")).toBeInTheDocument();
});

it("locks pending sign-out, clears identity and reports success", async () => {
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let calls = 0;
  server.use(http.get(me, () => HttpResponse.json(profile)), http.post("http://localhost:3000/api/auth/logout", async () => {
    calls++;
    await pending;
    return new HttpResponse(null, { status: 204 });
  }));
  show();
  const button = await screen.findByRole("button", { name: "Sign out" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(screen.getByRole("button", { name: "Signing out…" })).toBeDisabled();
  await waitFor(() => expect(calls).toBe(1));
  await act(async () => release());
  expect(await screen.findByRole("link", { name: /Sign in/ })).toBeInTheDocument();
  expect(screen.queryByText("Fixture owner")).not.toBeInTheDocument();
  expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "Signed out", tone: "success" }));
});

it("consumes sign-out rejection, reports uncertainty and rechecks the session", async () => {
  let profileCalls = 0;
  server.use(http.get(me, () => {
    profileCalls++;
    return profileCalls === 1 ? HttpResponse.json(profile) : new HttpResponse(null, { status: 401 });
  }), http.post("http://localhost:3000/api/auth/logout", () => HttpResponse.error()));
  show();
  fireEvent.click(await screen.findByRole("button", { name: "Sign out" }));
  await waitFor(() => expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "Couldn't confirm sign out", tone: "error" })));
  expect(await screen.findByRole("link", { name: /Sign in/ })).toBeInTheDocument();
  expect(profileCalls).toBe(2);
  expect(screen.queryByText("Fixture owner")).not.toBeInTheDocument();
});
