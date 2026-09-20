import { ApiError } from "./errors";
import type { ApiErrorResponse } from "./types";

/** The CSRF token Spring sets in a cookie, read back into the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Fetch a backend endpoint and parse either its JSON body (2xx) or the stable
 * error envelope (any non-2xx). Every failure is a typed {@link ApiError};
 * this function never throws a bare `SyntaxError` or `TypeError`.
 */
export async function fetchApi<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...((init?.headers as Record<string, string> | undefined) ?? {}),
    };
    const csrf = csrfToken();
    const method = (init?.method ?? "GET").toUpperCase();
    if (csrf && method !== "GET" && method !== "HEAD") {
      headers["X-XSRF-TOKEN"] = csrf;
    }
    response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  } catch {
    throw new ApiError(
      "NETWORK_ERROR",
      "Could not reach the server. Check your connection and try again.",
    );
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T;
    try {
      return (await response.json()) as T;
    } catch {
      // A 2xx with a non-JSON body (proxy shell HTML, gateway interstitial) must
      // not leak a raw SyntaxError through the abstraction that promises typed errors.
      throw new ApiError(
        "UNPARSEABLE_RESPONSE",
        "The server returned a response that could not be read.",
      );
    }
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
