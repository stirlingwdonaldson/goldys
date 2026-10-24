"use client";

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { useApi } from "@/lib/demo-mode";
import type { ApiError, ReconciliationException } from "@/lib/api";
import type { DataPage } from "@/lib/api/types";
import { listPage } from "@/lib/list-url";
import { Badge } from "@/components/ui/badge";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { ErrorState } from "@/components/states/error-state";
import { ExceptionsList, type ExceptionsListProps } from "./exceptions-list";

type ExceptionResult = UseQueryResult<DataPage<ReconciliationException>, ApiError>;

export function useExceptionPages(params: URLSearchParams) {
  const api = useApi();
  const { page, pageSize } = listPage(params);
  const tab: "daily" | "product" = params.get("tab") === "product" ? "product" : "daily";
  const dailyPage = tab === "daily" ? page : 0;
  const productPage = tab === "product" ? page : 0;
  const daily = useQuery({ queryKey: ["listReconciliationExceptions", dailyPage, pageSize], queryFn: ({ signal }) => api.listReconciliationExceptions(dailyPage, pageSize, { signal }) });
  const product = useQuery({ queryKey: ["listProductExceptions", productPage, pageSize], queryFn: ({ signal }) => api.listProductExceptions(productPage, pageSize, { signal }) });
  return { page, pageSize, tab, daily, product, current: tab === "daily" ? daily : product };
}

export function ExceptionStatus({ daily, product }: { daily: ExceptionResult; product: ExceptionResult }) {
  if (daily.isError || product.isError) return <Badge variant="missing">Checks unavailable</Badge>;
  if (daily.isPending || product.isPending) return <Badge variant="neutral">Checking…</Badge>;
  const total = daily.data.total + product.data.total;
  return <Badge variant={total > 0 ? "conflict" : "neutral"}>{total} open exceptions</Badge>;
}

export function ExceptionResults({ result, ...props }: Omit<ExceptionsListProps, "exceptions"> & { result: ExceptionResult }) {
  if (result.isPending) return <LoadingState rows={4} />;
  if (result.isError) {
    if (result.error.code === "NOT_PERMITTED") return <PermissionDenied subject={`${props.kind} reconciliation data`} />;
    return <ErrorState title="Couldn't load exceptions" message={result.error.message} onRetry={result.refetch} />;
  }
  return <ExceptionsList {...props} exceptions={result.data.items} />;
}
