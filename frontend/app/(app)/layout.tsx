import type { ReactNode } from "react";
import { AppShell } from "@/components/app-shell/app-shell";
import { CurrentUserProvider } from "@/components/app-shell/current-user-provider";
import { ToastProvider } from "@/components/feedback/toast";
import { DemoModeProvider } from "@/lib/demo-mode";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <CurrentUserProvider>
      <DemoModeProvider>
        <ToastProvider>
          <AppShell>{children}</AppShell>
        </ToastProvider>
      </DemoModeProvider>
    </CurrentUserProvider>
  );
}
