import { fetchApi } from "./client";
import type { CurrentUser, HealthResponse } from "./types";
import { currentUserSchema } from "./auth-schema";

/** Fetch the session profile; authentication, permission and integrity failures stay distinct. */
export async function getCurrentUser(options?: { signal?: AbortSignal }): Promise<CurrentUser> {
  return fetchApi<CurrentUser>("/api/me", { signal: options?.signal }, { schema: currentUserSchema });
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
  }, { schema: currentUserSchema });
}

/** Log in with email + password. The backend uses form login, so POST form-encoded. */
export async function login(input: LoginInput): Promise<CurrentUser> {
  const body = new URLSearchParams({ email: input.email, password: input.password });
  return fetchApi<CurrentUser>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  }, { schema: currentUserSchema });
}

export async function logout(): Promise<void> {
  return fetchApi<void>("/api/auth/logout", { method: "POST" }, { responseType: "void" });
}

export { ApiError, isApiError, isAbortError } from "./errors";
export type { ApiErrorCode } from "./errors";
export type {
  ActivityPoint,
  Api,
  ApiErrorResponse,
  ConnectorRunStatus,
  ConnectorStatus,
  ConversationMessage,
  ConversationThreadSummary,
  ConversationThreadView,
  CurrentUser,
  CustomLogic,
  DailyCovers,
  DailySales,
  DashboardSummary,
  ExceptionStatus,
  HealthResponse,
  InventoryLineBreakdown,
  InventorySummary,
  InvoiceGraphNode,
  InvoiceIngestFlag,
  LabourSummary,
  LineGraphNode,
  LatestSales,
  OverrideResult,
  Provenance,
  RecomputeState,
  RecomputeStatus,
  ReconciliationAuditChange,
  ReconciliationAuditEntry,
  ReconciliationException,
  ReconciliationField,
  ReconciliationRecord,
  ReservationSummary,
  ResolutionRule,
  RuleAuditEntry,
  RuleStrategy,
  SalesTrendPoint,
  SaveOverrideInput,
  SaveResolutionRuleInput,
  SourceValue,
  SupplierCogs,
  SupplierGraphNode,
  TopSeller,
  UomUnitCost,
} from "./types";
