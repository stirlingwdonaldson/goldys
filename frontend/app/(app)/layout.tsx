import type { ReactNode } from "react";
import { AppShell } from "@/components/app-shell/app-shell";
import { CurrentUserProvider } from "@/components/app-shell/current-user-provider";
import { ToastProvider } from "@/components/feedback/toast";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <CurrentUserProvider>
      <ToastProvider>
        <AppShell>{children}</AppShell>
      </ToastProvider>
    </CurrentUserProvider>
  );
}
