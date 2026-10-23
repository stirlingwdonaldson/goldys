"use client";

import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { StaffPageView } from "@/components/staff/staff-page-view";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";

/** The 30 days ending today, as local calendar dates. */
function last30Days(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getFullYear(), to.getMonth(), to.getDate() - 29);
  return { from: from.toLocaleDateString("en-CA"), to: to.toLocaleDateString("en-CA") };
}

export default function StaffPage() {
  const queryApi = useApi();
  const { user } = useCurrentUser();
  const { from, to } = last30Days();
  const labour = useQuery({ queryKey: ["getLabourSummary", from, to], queryFn: ({ signal }) => queryApi.getLabourSummary(from, to, { signal }) });
  return <StaffPageView seniority={user?.seniority} labour={{ data: labour.data ?? null, loading: labour.isPending, error: labour.error, reload: () => { void labour.refetch(); } }} />;
}
