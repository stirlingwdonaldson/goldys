import type { ZodType } from "zod";
import { ApiError, isAbortError, type ApiDiagnostic } from "./errors";

export interface JsonResponseOptions<T> { responseType?: "json"; schema?: ZodType<T>; }
export interface OptionalJsonResponseOptions<T> extends JsonResponseOptions<T> { allowNoContent: true; }
export interface VoidResponseOptions { responseType: "void"; }
export type ResponseOptions<T> = JsonResponseOptions<T> | OptionalJsonResponseOptions<T> | VoidResponseOptions;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

const ACTION_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Invalid email or password.",
  NOT_PERMITTED: "You do not have permission to perform this action.",
  VALIDATION_FAILED: "Some values were not accepted. Check the form and try again.",
  OVERRIDE_CONFLICT: "This record changed. Refresh it before saving your decision.",
  RECOMPUTATION_PENDING: "The data is still being updated. Try again once that finishes.",
};

function httpFailure(body: unknown, status: number | undefined, headerId?: string): ApiError {
  if (!isRecord(body) || typeof body.code !== "string" || typeof body.message !== "string") {
    return new ApiError("UNPARSEABLE_RESPONSE", "The server returned an error that could not be read.", headerId, undefined,
      { status, kind: "protocol", diagnostics: [{ path: [], code: "invalid_error_envelope" }] });
  }
  const diagnostics: ApiDiagnostic[] = [];
  let correlationId = headerId;
  if (body.correlationId != null) {
    if (typeof body.correlationId === "string" && body.correlationId.length > 0) correlationId = body.correlationId;
    else diagnostics.push({ path: ["correlationId"], code: "invalid_type" });
  }
  let fields: Record<string, string> = {};
  if (body.fields != null) {
    if (isRecord(body.fields) && Object.values(body.fields).every(value => typeof value === "string")) {
      fields = body.fields as Record<string, string>;
    } else diagnostics.push({ path: ["fields"], code: "invalid_type" });
  }
  const message = status !== undefined && status < 500
    ? (Object.hasOwn(ACTION_MESSAGES, body.code) ? ACTION_MESSAGES[body.code] : "The request could not be completed. Try again later.")
    : "The server could not complete the request. Try again later.";
  return new ApiError(body.code, message, correlationId, fields, { status, kind: "http", diagnostics });
}

function isAuthRedirect(path: string, response: Response): boolean {
  try {
    const base = typeof window === "undefined" ? path : window.location.href;
    const original = new URL(path, base);
    const target = new URL(response.url);
    return original.pathname.startsWith("/api/") && original.origin === target.origin &&
      (target.pathname === "/login" || target.pathname.startsWith("/oauth2/") || target.pathname.startsWith("/login/oauth2/"));
  } catch {
    return false;
  }
}

/** A single body-read path; schema validation is enabled only by the endpoint owner. */
export async function readApiResponse<T>(path: string, response: Response, options: ResponseOptions<T>): Promise<unknown> {
  const status = response.status || undefined;
  const correlationId = response.headers.get("X-Correlation-ID") || undefined;
  const protocolError = (code: string, message: string, cause?: unknown) =>
    new ApiError(code, message, correlationId, undefined, { status, kind: "protocol", cause });

  if (response.type === "opaqueredirect" || response.redirected) {
    const auth = response.redirected && isAuthRedirect(path, response);
    throw protocolError(auth ? "AUTH_REQUIRED" : "UNEXPECTED_REDIRECT",
      auth ? "Your session could not be verified. Sign in to continue." : "The server unexpectedly redirected the request.");
  }
  if (response.status === 204) {
    if (options.responseType === "void" || ("allowNoContent" in options && options.allowNoContent)) return undefined;
    throw protocolError("UNPARSEABLE_RESPONSE", "The server returned no data where a response was required.");
  }

  let text: string;
  try {
    text = await response.text();
  } catch (cause) {
    if (isAbortError(cause)) throw cause;
    throw protocolError("UNPARSEABLE_RESPONSE", "The server response could not be read.", cause);
  }
  if (response.ok && text.length === 0 && options.responseType === "void") return undefined;
  const mediaType = response.headers.get("Content-Type")?.split(";")[0].trim().toLowerCase();
  if (response.ok && text.length > 0 && mediaType && !/^application\/(json|[^;]+\+json)$/.test(mediaType)) {
    throw protocolError("UNEXPECTED_CONTENT_TYPE", "The server returned an unexpected response format.");
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch (cause) {
    throw protocolError("UNPARSEABLE_RESPONSE", "The server returned a response that could not be read.", cause);
  }
  if (!response.ok) throw httpFailure(parsed, status, correlationId);
  if (options.responseType === "void") return undefined;
  if (!options.schema) return parsed;

  let result;
  try {
    result = options.schema.safeParse(parsed);
  } catch (cause) {
    if (isAbortError(cause)) throw cause;
    throw new ApiError("UNEXPECTED_STATUS", "The server response could not be validated.", correlationId, undefined,
      { status, kind: "unexpected", cause });
  }
  if (!result.success) {
    throw new ApiError("INVALID_RESPONSE", "The server returned invalid data.", correlationId, undefined, {
      status, kind: "integrity", cause: result.error,
      diagnostics: result.error.issues.map(issue => ({
        path: issue.path.map(part => typeof part === "number" ? part : String(part)), code: issue.code,
      })),
    });
  }
  return result.data;
}
