/** The current staff identity the backend resolves from the OIDC session. */
export interface CurrentUser {
  displayName: string;
  department: string;
  seniority: string;
}

/** `GET /api/health` response body. */
export interface HealthResponse {
  status: string;
}

/** The one envelope every API error uses (mirrors the backend's `ApiErrorResponse`). */
export interface ApiErrorResponse {
  code: string;
  message: string;
  correlationId?: string;
  fields?: Record<string, string>;
}
