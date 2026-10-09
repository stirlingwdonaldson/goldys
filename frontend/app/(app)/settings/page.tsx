import type { Metadata } from "next";
import { IdentityCard } from "@/components/app-shell/identity-card";
import { DemoModeToggle } from "@/components/settings/demo-mode-toggle";
import { PageHeader } from "@/components/layout/page-header";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Settings" description="Account and access." />
      <IdentityCard />
      <DemoModeToggle />
      <p className="text-sm text-muted-foreground">
        Role and permission administration is not available yet.
      </p>
    </div>
  );
}
