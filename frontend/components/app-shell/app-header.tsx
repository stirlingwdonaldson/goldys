"use client";

import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { AskGoldysDrawer } from "@/components/ask-goldys/ask-goldys-drawer";
import { useCurrentUser } from "./current-user-provider";

export function AppHeader() {
  const { user } = useCurrentUser();
  return (
    <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <span className="text-sm font-medium">Goldy&apos;s Data Platform</span>
      <div className="ml-auto">
        <AskGoldysDrawer seniority={user?.seniority} />
      </div>
    </header>
  );
}
