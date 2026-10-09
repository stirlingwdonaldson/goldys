"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { LogIn, LogOut } from "lucide-react";
import { logout, type CurrentUser } from "@/lib/api";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarMenuButton } from "@/components/ui/sidebar";
import { useCurrentUser } from "./current-user-provider";
import { useToast } from "@/components/feedback/toast";
import { InlineError } from "@/components/states/inline-error";

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

function SignedInUser({ user, signingOut, onSignOut }: { user: CurrentUser; signingOut: boolean; onSignOut: () => void }) {
  return (
    <div className="space-y-1 px-2 py-1.5">
      <div className="flex items-center gap-2">
        <Avatar className="h-8 w-8 rounded-lg">
          <AvatarFallback className="rounded-lg">{initials(user.displayName)}</AvatarFallback>
        </Avatar>
        <div className="grid flex-1 text-left text-sm leading-tight">
          <span className="truncate font-semibold">{user.displayName}</span>
          <span className="truncate text-xs text-muted-foreground">{user.department} &middot; {user.seniority}</span>
        </div>
      </div>
      <Button variant="ghost" size="sm" className="w-full justify-start" onClick={onSignOut} disabled={signingOut}>
        <LogOut className="mr-2 h-4 w-4" />
        {signingOut ? "Signing out…" : "Sign out"}
      </Button>
    </div>
  );
}

export function UserMenu() {
  const { user, status, error, refresh, clear } = useCurrentUser();
  const { toast } = useToast();
  const busy = useRef(false);
  const [signingOut, setSigningOut] = useState(false);

  async function handleSignOut() {
    if (busy.current) return;
    busy.current = true;
    setSigningOut(true);
    try {
      await logout();
      clear();
      toast({ title: "Signed out", tone: "success" });
    } catch {
      toast({ title: "Couldn't confirm sign out", description: "Your session will be checked again. Try signing out once that finishes.", tone: "error" });
      refresh();
    } finally {
      busy.current = false;
      setSigningOut(false);
    }
  }

  if (status === "error") {
    return (
      <div className="space-y-2 px-2 py-1.5">
        <InlineError className="p-2 text-xs">{error?.message ?? "Couldn't verify your profile."}</InlineError>
        {error?.correlationId ? <p className="break-words text-xs text-muted-foreground">Reference: {error.correlationId}</p> : null}
        <Button variant="ghost" size="sm" onClick={refresh}>Retry profile</Button>
      </div>
    );
  }

  if (status === "loading") {
    return (
      <div className="flex items-center gap-2 px-2 py-1.5">
        <Skeleton className="h-8 w-8 rounded-lg" />
        <div className="space-y-1">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-3 w-16" />
        </div>
      </div>
    );
  }

  if (status === "authenticated" && user) {
    return <SignedInUser user={user} signingOut={signingOut} onSignOut={handleSignOut} />;
  }

  return (
    <SidebarMenuButton size="lg" asChild>
      <Link href="/login">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted">
          <LogIn className="h-4 w-4" aria-hidden="true" />
        </div>
        <div className="grid flex-1 text-left text-sm leading-tight">
          <span className="truncate font-semibold">Sign in</span>
          <span className="truncate text-xs text-muted-foreground">Access your data</span>
        </div>
      </Link>
    </SidebarMenuButton>
  );
}
