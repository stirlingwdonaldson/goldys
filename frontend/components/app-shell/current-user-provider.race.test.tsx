import { act, renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError, getCurrentUser, type CurrentUser } from "@/lib/api";
import { CurrentUserProvider, useCurrentUser } from "./current-user-provider";

vi.mock("@/lib/api", async () => ({ ...await vi.importActual<typeof import("@/lib/api")>("@/lib/api"), getCurrentUser: vi.fn() }));
beforeEach(() => { vi.mocked(getCurrentUser).mockReset(); });

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
const owner = { displayName: "Old owner", department: "MANAGEMENT", seniority: "OWNER", mustChangePassword: false };
const junior = { displayName: "Current staff", department: "GENERAL", seniority: "JUNIOR", mustChangePassword: false };

it.each(["success", "failure"])("ignores obsolete %s even when promises ignore abort", async oldOutcome => {
  const old = deferred<CurrentUser>();
  const current = deferred<CurrentUser>();
  vi.mocked(getCurrentUser).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  act(() => result.current.refresh());
  expect(vi.mocked(getCurrentUser).mock.calls[0][0]?.signal?.aborted).toBe(true);
  await act(async () => current.resolve(junior));
  await act(async () => oldOutcome === "success" ? old.resolve(owner) : old.reject(new ApiError("NETWORK_ERROR", "offline")));
  expect(result.current).toMatchObject({ user: junior, status: "authenticated", error: null });
});

it("clear prevents pending identity from republishing", async () => {
  const pending = deferred<CurrentUser>();
  vi.mocked(getCurrentUser).mockReturnValue(pending.promise);
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  act(() => result.current.clear());
  await act(async () => pending.resolve(owner));
  expect(result.current).toMatchObject({ user: null, status: "unauthenticated", error: null });
});

it("aborts on unmount and consumes late rejection", async () => {
  const pending = deferred<CurrentUser>();
  vi.mocked(getCurrentUser).mockReturnValue(pending.promise);
  const { unmount } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  const signal = vi.mocked(getCurrentUser).mock.calls[0][0]?.signal;
  unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => pending.reject(new Error("late failure")));
});

it("handles StrictMode effect replacement without publishing the old owner", async () => {
  const old = deferred<CurrentUser>();
  const current = deferred<CurrentUser>();
  vi.mocked(getCurrentUser).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider, reactStrictMode: true });
  expect(getCurrentUser).toHaveBeenCalledTimes(2);
  await act(async () => current.resolve(junior));
  await act(async () => old.resolve(owner));
  expect(result.current.user).toEqual(junior);
});

it.each([
  ["AUTH_REQUIRED", "unauthenticated"], ["UNEXPECTED_REDIRECT", "error"], ["NETWORK_ERROR", "error"],
])("classifies %s separately from permission", async (code, status) => {
  vi.mocked(getCurrentUser).mockRejectedValue(new ApiError(code, "fixture"));
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await act(async () => {});
  expect(result.current).toMatchObject({ status, user: null });
});


it("checks once when a visible tab returns, coalesces events, and expires on 401", async () => {
  const check = deferred<CurrentUser>();
  const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
  vi.mocked(getCurrentUser).mockResolvedValueOnce(owner).mockReturnValueOnce(check.promise);
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await act(async () => {});
  act(() => window.dispatchEvent(new Event("focus")));
  expect(getCurrentUser).toHaveBeenCalledTimes(1);
  visibility.mockReturnValue("visible");
  act(() => {
    document.dispatchEvent(new Event("visibilitychange"));
    window.dispatchEvent(new Event("focus"));
  });
  expect(getCurrentUser).toHaveBeenCalledTimes(2);
  expect(result.current.status).toBe("authenticated");
  await act(async () => check.reject(new ApiError("AUTH_REQUIRED", "expired", undefined, undefined, { status: 401 })));
  expect(result.current).toMatchObject({ user: null, status: "unauthenticated" });
  visibility.mockRestore();
});

it("ignores an obsolete focus rejection after a new identity is verified", async () => {
  const check = deferred<CurrentUser>();
  const visibility = vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
  vi.mocked(getCurrentUser).mockResolvedValueOnce(owner).mockReturnValueOnce(check.promise).mockResolvedValueOnce(junior);
  const { result } = renderHook(() => useCurrentUser(), { wrapper: CurrentUserProvider });
  await act(async () => {});
  act(() => window.dispatchEvent(new Event("focus")));
  await act(async () => result.current.refresh());
  await act(async () => check.reject(new ApiError("AUTH_REQUIRED", "obsolete")));
  expect(result.current).toMatchObject({ user: junior, status: "authenticated" });
  visibility.mockRestore();
});
