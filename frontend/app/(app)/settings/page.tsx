import type { Metadata } from "next";
import { IdentityCard } from "@/components/app-shell/identity-card";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Account and access.</p>
      </div>
      <IdentityCard />
      <p className="text-sm text-muted-foreground">
        Role and permission administration is not available yet.
      </p>
    </div>
  );
}
