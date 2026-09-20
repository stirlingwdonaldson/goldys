import { fetchApi } from "./client";
import type { CurrentUser, HealthResponse } from "./types";

/** Fetch the signed-in staff profile. Throws `ApiError(NOT_PERMITTED)` when unauthenticated. */
export async function getCurrentUser(): Promise<CurrentUser> {
  return fetchApi<CurrentUser>("/api/me");
}

/** Fetch the backend health status. */
export async function getHealth(): Promise<HealthResponse> {
  return fetchApi<HealthResponse>("/api/health");
}

export { ApiError, isApiError } from "./errors";
export type { ApiErrorCode } from "./errors";
export type { CurrentUser, HealthResponse, ApiErrorResponse } from "./types";
