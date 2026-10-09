import { expect, it } from "vitest";
import * as Sentry from "@sentry/nextjs";
import { ApiError } from "./api/errors";
import { beforeSend } from "./sentry-options";

it("scrubs actual SDK-linked cause messages without losing local causes or safe correlation", async () => {
  const cause = new SyntaxError("fixture-private-response");
  const error = new ApiError("UNPARSEABLE_RESPONSE", "Safe public message", "req-safe", undefined,
    { status: 200, kind: "protocol", cause });
  const event: Sentry.ErrorEvent = { type: undefined, exception: { values: [{ type: "ApiError", value: error.message }] } };
  const integration = Sentry.linkedErrorsIntegration();
  type IntegrationClient = Parameters<NonNullable<typeof integration.preprocessEvent>>[2];
  integration.preprocessEvent?.(event, { originalException: error }, {
    getOptions: () => ({ stackParser: Sentry.defaultStackParser }),
  } as IntegrationClient);
  expect(JSON.stringify(event)).toContain("fixture-private-response");
  const sanitized = await beforeSend(event, { originalException: error });
  expect(JSON.stringify(sanitized)).not.toContain("fixture-private-response");
  expect(sanitized?.exception?.values?.at(-1)?.value).toBe("Safe public message");
  expect(sanitized?.tags).toMatchObject({ api_code: "UNPARSEABLE_RESPONSE", http_status: "200", correlation_id: "req-safe" });
  expect(error.cause).toBe(cause);
  expect(JSON.stringify(event)).toContain("fixture-private-response");
});

it("does not publish invalid correlation/code values from untrusted envelopes", async () => {
  const error = new ApiError("fixture@example.invalid", "Safe", "fixture@example.invalid");
  const event: Sentry.ErrorEvent = { type: undefined, exception: { values: [{ type: "ApiError", value: "Safe" }] } };
  const sanitized = await beforeSend(event, { originalException: error });
  expect(JSON.stringify(sanitized)).not.toContain("fixture@example.invalid");
  expect(sanitized?.tags?.api_code).toBe("UNRECOGNIZED");
  expect(sanitized?.tags?.correlation_id).toBeUndefined();
});

it("preserves unrelated exception reporting", async () => {
  const error = new Error("Programmer failure");
  const event: Sentry.ErrorEvent = { type: undefined, exception: { values: [{ type: "Error", value: error.message }] } };
  expect(await beforeSend(event, { originalException: error })).toBe(event);
});
