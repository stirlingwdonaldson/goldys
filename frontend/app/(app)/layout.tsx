import type { ReactNode } from "react";
import { cookies } from "next/headers";
import { AppShell } from "@/components/app-shell/app-shell";
import { CurrentUserProvider } from "@/components/app-shell/current-user-provider";
import { ToastProvider } from "@/components/feedback/toast";
import { DemoModeProvider } from "@/lib/demo-mode";

export default async function AppLayout({ children }: { children: ReactNode }) {
  // shadcn's sidebar persists its collapsed state in this cookie; reading it here
  // renders the right width on first paint instead of flashing open.
  const sidebarOpen = (await cookies()).get("sidebar_state")?.value !== "false";
  return (
    <CurrentUserProvider>
      <DemoModeProvider>
        <ToastProvider>
          <AppShell defaultSidebarOpen={sidebarOpen}>{children}</AppShell>
        </ToastProvider>
      </DemoModeProvider>
    </CurrentUserProvider>
  );
}
