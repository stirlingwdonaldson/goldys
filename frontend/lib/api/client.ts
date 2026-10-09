import { ApiError, isAbortError } from "./errors";
import { readApiResponse, type JsonResponseOptions, type OptionalJsonResponseOptions, type ResponseOptions, type VoidResponseOptions } from "./response";

export type { JsonResponseOptions, OptionalJsonResponseOptions, VoidResponseOptions } from "./response";

/** The CSRF token Spring sets in a cookie, read back into the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Fetch a backend endpoint and parse either its JSON body (2xx) or the stable
 * error envelope (any non-2xx). Endpoint owners declare optional/void bodies and schemas.
 * Failures are ApiError; native cancellations remain AbortError.
 */
export function fetchApi<T>(path: string, init: RequestInit | undefined, options: OptionalJsonResponseOptions<T>): Promise<T | undefined>;
export function fetchApi<T>(path: string, init?: RequestInit, options?: JsonResponseOptions<T>): Promise<T>;
export function fetchApi<T extends void = void>(path: string, init: RequestInit | undefined, options: VoidResponseOptions): Promise<T>;
export async function fetchApi(path: string, init?: RequestInit, options: ResponseOptions<unknown> = { responseType: "json" }): Promise<unknown> {
  let headers: Headers;
  try {
    headers = new Headers(init?.headers);
    if (!headers.has("Accept")) headers.set("Accept", "application/json");
    const method = (init?.method ?? "GET").toUpperCase();
    if (method !== "GET" && method !== "HEAD") {
      const csrf = csrfToken();
      if (csrf !== null) headers.set("X-XSRF-TOKEN", csrf);
    }
  } catch (cause) {
    throw new ApiError("REQUEST_CONFIGURATION_ERROR", "The request could not be prepared.", undefined, undefined,
      { kind: "unexpected", cause });
  }

  let response: Response;
  try {
    response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  } catch (cause) {
    if (isAbortError(cause)) throw cause;
    throw new ApiError(
      "NETWORK_ERROR",
      "Could not reach the server. Check your connection and try again.",
      undefined, undefined, { kind: "network", cause },
    );
  }

  return readApiResponse(path, response, options);
}
