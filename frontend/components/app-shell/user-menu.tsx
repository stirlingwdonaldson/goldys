"use client";

import Link from "next/link";
import { LogIn, LogOut } from "lucide-react";
import { logout } from "@/lib/api";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SidebarMenuButton } from "@/components/ui/sidebar";
import { useCurrentUser } from "./current-user-provider";

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function UserMenu() {
  const { user, status, refresh } = useCurrentUser();

  async function handleSignOut() {
    try {
      await logout();
    } finally {
      refresh();
    }
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
    return (
      <div className="space-y-1 px-2 py-1.5">
        <div className="flex items-center gap-2">
          <Avatar className="h-8 w-8 rounded-lg">
            <AvatarFallback className="rounded-lg">{initials(user.displayName)}</AvatarFallback>
          </Avatar>
          <div className="grid flex-1 text-left text-sm leading-tight">
            <span className="truncate font-semibold">{user.displayName}</span>
            <span className="truncate text-xs text-muted-foreground">
              {user.department} &middot; {user.seniority}
            </span>
          </div>
        </div>
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start"
          onClick={handleSignOut}
        >
          <LogOut className="mr-2 h-4 w-4" />
          Sign out
        </Button>
      </div>
    );
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
