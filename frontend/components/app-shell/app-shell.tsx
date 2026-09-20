"use client";

import type { ReactNode } from "react";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { ScreenErrorBoundary } from "@/components/states/error-boundary";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <AppHeader />
        <main className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <ScreenErrorBoundary>{children}</ScreenErrorBoundary>
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
