"use client";

import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { StaffPageView } from "@/components/staff/staff-page-view";

export default function StaffPage() {
  const { user } = useCurrentUser();
  return <StaffPageView seniority={user?.seniority} />;
}
