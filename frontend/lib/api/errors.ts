/**
 * Known backend error codes plus client-side synthesized ones. The backend may
 * return additional codes in future slices, so `ApiError.code` stays a plain
 * `string`; this union is documentation, not a closed set.
 */
export type ApiErrorCode =
  | "NOT_PERMITTED"
  | "VALIDATION_FAILED"
  | "INVALID_CREDENTIALS"
  | "CONNECTOR_AUTH_FAILED"
  | "CONNECTOR_FETCH_FAILED"
  | "CONNECTOR_SCHEMA_MISMATCH"
  | "MATCH_AMBIGUOUS"
  | "OVERRIDE_CONFLICT"
  | "RECOMPUTATION_PENDING"
  | "NETWORK_ERROR"
  | "UNPARSEABLE_RESPONSE"
  | "UNEXPECTED_STATUS"
  | "REQUEST_CONFIGURATION_ERROR"
  | "AUTH_REQUIRED"
  | "UNEXPECTED_REDIRECT"
  | "UNEXPECTED_CONTENT_TYPE"
  | "INVALID_RESPONSE";

export type ApiErrorKind = "network" | "http" | "protocol" | "integrity" | "unexpected";

/** Diagnostics deliberately contain no response values or body snippets. */
export interface ApiDiagnostic {
  path: readonly (string | number)[];
  code: string;
}

export interface ApiErrorDetails {
  status?: number;
  kind?: ApiErrorKind;
  cause?: unknown;
  diagnostics?: readonly ApiDiagnostic[];
}

/** Code-first failure with available HTTP metadata; network failures have no status. */
export class ApiError extends Error {
  readonly code: string;
  readonly correlationId?: string;
  readonly fields: Record<string, string>;
  readonly status?: number;
  readonly kind: ApiErrorKind;
  readonly diagnostics: readonly ApiDiagnostic[];

  constructor(code: string, message: string, correlationId?: string, fields?: Record<string, string>, details: ApiErrorDetails = {}) {
    super(message, { cause: details.cause });
    this.name = "ApiError";
    this.code = code;
    this.correlationId = correlationId;
    this.fields = fields ?? {};
    this.status = details.status;
    this.kind = details.kind ?? "unexpected";
    this.diagnostics = details.diagnostics ?? [];
  }
}

/** True when the error is an {@link ApiError} with the given code. */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Name-based recognition also works for cancellation from another browser realm. */
export function isAbortError(error: unknown): boolean {
  return typeof error === "object" && error !== null && "name" in error && error.name === "AbortError";
}
