"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { ScreenErrorBoundary } from "@/components/states/error-boundary";
import { DemoBanner } from "@/components/demo-banner";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <DemoBanner />
        <AppHeader />
        <main className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          {/* Keying the boundary on the path remounts it per route, so a render
              error on one screen clears when the user navigates to another. */}
          <ScreenErrorBoundary key={pathname}>{children}</ScreenErrorBoundary>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
