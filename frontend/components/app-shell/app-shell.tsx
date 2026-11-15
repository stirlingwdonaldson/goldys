"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { ScreenErrorBoundary } from "@/components/states/error-boundary";
import { DemoBanner } from "@/components/demo-banner";
import { AppHeader } from "./app-header";
import { AppSidebar } from "./app-sidebar";
import { CommandPaletteProvider } from "./command-palette";
import { ShellStatusProvider } from "./shell-status";

interface AppShellProps {
  children: ReactNode;
  /** Sidebar expanded/collapsed state from the `sidebar_state` cookie, read on the server. */
  defaultSidebarOpen?: boolean;
}

export function AppShell({ children, defaultSidebarOpen = true }: AppShellProps) {
  const pathname = usePathname();

  return (
    <ShellStatusProvider>
      <CommandPaletteProvider>
        <SidebarProvider defaultOpen={defaultSidebarOpen}>
          <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-md focus:bg-background focus:p-3" onClick={(event) => { event.preventDefault(); document.getElementById("main-content")?.focus(); }}>Skip to main content</a>
          <AppSidebar />
          {/* Inset variant: the page sits in a raised panel on the sidebar's canvas. */}
          <SidebarInset className="min-w-0 overflow-hidden">
            <DemoBanner />
            <AppHeader />
            <div id="main-content" tabIndex={-1} className="flex flex-1 flex-col gap-6 p-4 md:px-8 md:py-7">
              {/* Keying the boundary on the path remounts it per route, so a render
                  error on one screen clears when the user navigates to another. */}
              <ScreenErrorBoundary key={pathname}>{children}</ScreenErrorBoundary>
            </div>
          </SidebarInset>
        </SidebarProvider>
      </CommandPaletteProvider>
    </ShellStatusProvider>
  );
}
