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
  | "UNEXPECTED_STATUS";

/** A typed, code-first API error. Callers branch on `code`, never raw HTTP status. */
export class ApiError extends Error {
  readonly code: string;
  readonly correlationId?: string;
  readonly fields: Record<string, string>;

  constructor(code: string, message: string, correlationId?: string, fields?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.correlationId = correlationId;
    this.fields = fields ?? {};
  }
}

/** True when the error is an {@link ApiError} with the given code. */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
