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

export interface SignupInput {
  email: string;
  displayName: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

/** Create an account (at the lowest role) and return it. The caller then logs in. */
export async function signup(input: SignupInput): Promise<CurrentUser> {
  return fetchApi<CurrentUser>("/api/auth/signup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

/** Log in with email + password. The backend uses form login, so POST form-encoded. */
export async function login(input: LoginInput): Promise<CurrentUser> {
  const body = new URLSearchParams({ email: input.email, password: input.password });
  return fetchApi<CurrentUser>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
}

export async function logout(): Promise<void> {
  return fetchApi<void>("/api/auth/logout", { method: "POST" });
}

export { ApiError, isApiError } from "./errors";
export type { ApiErrorCode } from "./errors";
export type {
  ActivityPoint,
  Api,
  ApiErrorResponse,
  ConnectorRunStatus,
  ConnectorStatus,
  CurrentUser,
  DashboardSummary,
  ExceptionStatus,
  HealthResponse,
  OverrideResult,
  ReconciliationException,
  ReconciliationField,
  ReconciliationRecord,
  SaveOverrideInput,
  SourceValue,
} from "./types";
