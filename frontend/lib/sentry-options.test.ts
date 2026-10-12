import { expect, it } from "vitest";
import * as Sentry from "@sentry/nextjs";
import { ApiError } from "./api/errors";
import { beforeSend, beforeBreadcrumb, scrubTokenQuery } from "./sentry-options";

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

it("strips only the token query parameter's value from a URL", () => {
  expect(scrubTokenQuery("https://host/api/ingest/lightspeed?token=SECRET&a=1&b=2"))
    .toBe("https://host/api/ingest/lightspeed?a=1&b=2");
});

it("leaves other query parameters untouched when no token is present", () => {
  expect(scrubTokenQuery("https://host/dashboard?from=2026-09-01&to=2026-09-30&domain=sales"))
    .toBe("https://host/dashboard?from=2026-09-01&to=2026-09-30&domain=sales");
});

it("drops the query entirely when token is the only parameter", () => {
  expect(scrubTokenQuery("https://host/api/ingest/lightspeed?token=SECRET"))
    .toBe("https://host/api/ingest/lightspeed");
});

it("does not strip a bare value with no query string", () => {
  expect(scrubTokenQuery("https://host/api/ingest/lightspeed"))
    .toBe("https://host/api/ingest/lightspeed");
});

it("does not touch a parameter that merely contains the word token", () => {
  expect(scrubTokenQuery("https://host/api?tokenizer=x&token=SECRET")).toBe("https://host/api?tokenizer=x");
});

it("scrubs the url in an HTTP breadcrumb's data", () => {
  const breadcrumb: Sentry.Breadcrumb = {
    type: "http",
    category: "http",
    data: { url: "https://host/api/ingest/lightspeed?token=SECRET&a=1", method: "POST" },
  };
  const scrubbed = beforeBreadcrumb(breadcrumb);
  expect(scrubbed?.data?.url).toBe("https://host/api/ingest/lightspeed?a=1");
  expect(scrubbed?.data?.method).toBe("POST");
});
