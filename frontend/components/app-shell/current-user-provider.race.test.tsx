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
const owner = { displayName: "Old owner", department: "MANAGEMENT", seniority: "OWNER" };
const junior = { displayName: "Current staff", department: "GENERAL", seniority: "JUNIOR" };

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
