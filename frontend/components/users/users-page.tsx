"use client";

import { useQuery } from "@tanstack/react-query";
import { useApi } from "@/lib/demo-mode";
import type { ApiError } from "@/lib/api";
import { UsersPageView } from "./users-page-view";

export function UsersPage() {
  const api = useApi();
  const users = useQuery({
    queryKey: ["listUsers"],
    queryFn: ({ signal }) => api.listUsers({ signal }),
  });

  return (
    <UsersPageView
      users={users.data ?? []}
      loading={users.isPending}
      error={(users.error as ApiError | null) ?? null}
      reload={() => void users.refetch()}
    />
  );
}
