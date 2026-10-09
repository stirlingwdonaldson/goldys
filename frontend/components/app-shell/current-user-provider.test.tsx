import { act, renderHook, waitFor } from "@testing-library/react";
import { expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { CurrentUserProvider, useCurrentUser } from "./current-user-provider";

setupApiServer();
const me = "http://localhost:3000/api/me";
const owner = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" };

it.each([401, 403, 500])("clears identity immediately and classifies HTTP %i", async status => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await waitFor(() => expect(result.current.status).toBe("authenticated"));
  server.use(http.get(me, () => HttpResponse.json({ code: status === 403 ? "NOT_PERMITTED" : "UNEXPECTED_STATUS", message: "fixture" }, { status })));
  act(() => result.current.refresh());
  expect(result.current.user).toBeNull();
  await waitFor(() => expect(result.current.status).toBe(status === 401 ? "unauthenticated" : "error"));
  expect(result.current.user).toBeNull();
  expect(result.current.error?.status).toBe(status);
});

it.each(["malformed", "incomplete", "network"])("treats %s profile as error, not sign-out", async failure => {
  server.use(http.get(me, () => failure === "network" ? HttpResponse.error()
    : failure === "malformed" ? new HttpResponse("{", { headers: { "Content-Type": "application/json" } })
      : HttpResponse.json({ seniority: "OWNER" })));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await waitFor(() => expect(result.current.status).toBe("error"));
  expect(result.current.user).toBeNull();
});

it("explicitly clears a verified identity", async () => {
  server.use(http.get(me, () => HttpResponse.json(owner)));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await waitFor(() => expect(result.current.user?.seniority).toBe("OWNER"));
  act(() => result.current.clear());
  expect(result.current).toMatchObject({ user: null, status: "unauthenticated", error: null });
});
