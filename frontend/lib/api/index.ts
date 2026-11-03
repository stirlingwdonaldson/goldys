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

export interface LoginInput {
  email: string;
  password: string;
}

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

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

/** Sets a new password for the signed-in account and clears the forced-change flag. */
export async function changePassword(input: ChangePasswordInput): Promise<void> {
  return fetchApi<void>(
    "/api/auth/change-password",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    { responseType: "void" },
  );
}

export { ApiError, isApiError, isAbortError } from "./errors";
export type { ApiErrorCode } from "./errors";
export type {
  ActivityPoint,
  Api,
  ApiErrorResponse,
  AssignmentRow,
  AspectSentimentSummary,
  AspectSentimentView,
  BacktestRow,
  CategoryNode,
  CategorySuggestion,
  ClassificationRun,
  ClassificationRunKind,
  ClassificationRunStatus,
  ConnectorRunStatus,
  ConnectorStatus,
  ConversationMessage,
  ConversationThreadSummary,
  ConversationThreadView,
  CoversToday,
  CreateCategoryInput,
  CreateUserInput,
  CurrentUser,
  CustomLogic,
  DailyCovers,
  DailySales,
  DashboardSummary,
  DeletedSaleDay,
  DeletedSaleFilter,
  DeletedSaleRow,
  DendrogramNode,
  ExceptionStatus,
  FoodCost,
  ForecastAccuracyMetric,
  ForecastBacktest,
  ForecastDayRow,
  ForecastDiagnostics,
  ForecastImportanceRow,
  HealthResponse,
  InventoryLineBreakdown,
  InventorySummary,
  InventoryObservation,
  InventoryObservationPage,
  InvoiceGraphNode,
  InvoiceIngestFlag,
  LabourSummary,
  LineGraphNode,
  ManagedUser,
  NetworkEdgeDto,
  NetworkGraphDto,
  NetworkNodeDto,
  LatestSales,
  OpenTableFrame,
  OpenTableInput,
  OpenTableSessionHandlers,
  OpenTableSessionState,
  OpenTableSessionStateName,
  OpenTableSyncMode,
  OverrideResult,
  PaymentFilter,
  PaymentMix,
  PaymentRow,
  ProductPoint,
  Provenance,
  RatingBucket,
  RatingTrendBucket,
  RecipeCosting,
  RecipeIngredient,
  RecomputeState,
  RecomputeStatus,
  ReconciliationAuditChange,
  ReconciliationAuditEntry,
  ReconciliationException,
  ReconciliationField,
  ReconciliationRecord,
  ReservationSummary,
  ResolutionRule,
  ReviewFilter,
  ReviewOverview,
  ReviewView,
  RuleAuditEntry,
  RuleStrategy,
  SaleItemFilter,
  SaleItemMix,
  SaleItemRow,
  SalesTrendPoint,
  SalesTrendResult,
  TopSellersResult,
  MetricCoverage,
  MetricResultProvenance,
  SaveOverrideInput,
  SaveResolutionRuleInput,
  SentimentBucket,
  SourceSummary,
  SourceValue,
  SupplierCogs,
  SupplierGraphNode,
  TopSeller,
  UpdateCategoryInput,
  UpdateUserInput,
  UomUnitCost,
  WeekdayErrorRow,
} from "./types";
