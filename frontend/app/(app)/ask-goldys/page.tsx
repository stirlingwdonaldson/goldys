"use client";

import dynamic from "next/dynamic";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { PermissionDenied } from "@/components/states/permission-denied";
import { isOwner } from "@/lib/roles";
import { AskGoldysChat } from "@/components/ask-goldys/ask-goldys-chat";

const WorkspacePage = dynamic(() => import("@/components/workspace/workspace-page").then(module => module.WorkspacePage));

export default function AskGoldysPage() {
  const { user } = useCurrentUser();
  if (!isOwner(user?.seniority)) return <div className="p-6"><PermissionDenied subject="Ask Goldy's" /></div>;
  return process.env.NEXT_PUBLIC_WORKSPACE_ENABLED === "true" || process.env.NEXT_PUBLIC_WORKSPACE_PREVIEW === "true"
    ? <WorkspacePage /> : <AskGoldysChat />;
}
