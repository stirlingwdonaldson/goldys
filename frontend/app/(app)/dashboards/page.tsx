"use client";

import { useApi } from "@/lib/demo-mode";
import { DashboardsPageView } from "@/components/dashboards/dashboards-page-view";

export default function DashboardsPage() {
  const api = useApi();
  return <DashboardsPageView api={api} />;
}
