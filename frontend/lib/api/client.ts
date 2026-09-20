import { ApiError } from "./errors";
import type { ApiErrorResponse } from "./types";

/**
 * Fetch a backend endpoint and parse either its JSON body (2xx) or the stable
 * error envelope (any non-2xx). Every failure is a typed {@link ApiError};
 * this function never throws a bare `SyntaxError` or `TypeError`.
 */
export async function fetchApi<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: { Accept: "application/json", ...(init?.headers ?? {}) },
      credentials: "same-origin",
    });
  } catch {
    throw new ApiError(
      "NETWORK_ERROR",
      "Could not reach the server. Check your connection and try again.",
    );
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  let body: ApiErrorResponse | null = null;
  try {
    body = (await response.json()) as ApiErrorResponse;
  } catch {
    body = null;
  }

  if (body && typeof body.code === "string" && typeof body.message === "string") {
    throw new ApiError(body.code, body.message, body.correlationId, body.fields);
  }

  throw new ApiError(
    "UNPARSEABLE_RESPONSE",
    `The server returned an error (HTTP ${response.status}) that could not be read.`,
  );
}
