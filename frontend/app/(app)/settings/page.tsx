import type { Metadata } from "next";
import Link from "next/link";
import { IdentityCard } from "@/components/app-shell/identity-card";
import { IncompleteNoticesToggle } from "@/components/settings/incomplete-notices-toggle";
import { DemoModeToggle } from "@/components/settings/demo-mode-toggle";
import { PageHeader } from "@/components/layout/page-header";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Settings" description="Account and access." />
      <IdentityCard />
      <DemoModeToggle />
      <IncompleteNoticesToggle />
      <Link
        href="/settings/users"
        className="rounded-lg border p-4 text-sm font-medium underline-offset-4 hover:underline"
      >
        Users and access
        <span className="mt-1 block font-normal text-muted-foreground">
          Create, rename, deactivate and reset owner accounts.
        </span>
      </Link>
    </div>
  );
}
