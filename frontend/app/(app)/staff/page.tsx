"use client";

import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { StaffPageView } from "@/components/staff/staff-page-view";
import { useApiData } from "@/lib/use-api-data";

/** The 30 days ending today, as local calendar dates. */
function last30Days(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getFullYear(), to.getMonth(), to.getDate() - 29);
  return { from: from.toLocaleDateString("en-CA"), to: to.toLocaleDateString("en-CA") };
}

export default function StaffPage() {
  const { user } = useCurrentUser();
  const { from, to } = last30Days();
  const labour = useApiData((api) => api.getLabourSummary(from, to), [from, to]);
  return <StaffPageView seniority={user?.seniority} labour={labour} />;
}
