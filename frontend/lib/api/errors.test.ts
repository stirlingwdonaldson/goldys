import { expect, it } from "vitest";
import { ApiError, isAbortError, isApiError } from "./errors";

it("retains legacy construction", () => {
  const error = new ApiError("NOT_PERMITTED", "Denied", "req-1", { title: "Required" });
  expect(isApiError(error)).toBe(true);
  expect(error).toMatchObject({ code: "NOT_PERMITTED", message: "Denied", correlationId: "req-1", fields: { title: "Required" } });
});

it("preserves status, cause and value-free diagnostics", () => {
  const cause = new SyntaxError("Invalid response JSON");
  const error = new ApiError("UNPARSEABLE_RESPONSE", "Unreadable", "req-2", undefined,
    { status: 502, kind: "protocol", cause, diagnostics: [{ path: ["fields"], code: "invalid_type" }] });
  expect(error).toMatchObject({ status: 502, kind: "protocol", diagnostics: [{ path: ["fields"], code: "invalid_type" }] });
  expect(error.cause).toBe(cause);
});

it("recognizes cross-realm cancellation without classifying network errors as aborts", () => {
  expect(isAbortError(new DOMException("Cancelled", "AbortError"))).toBe(true);
  expect(isAbortError({ name: "AbortError" })).toBe(true);
  expect(isAbortError(new TypeError("offline"))).toBe(false);
  expect(isAbortError(null)).toBe(false);
});
