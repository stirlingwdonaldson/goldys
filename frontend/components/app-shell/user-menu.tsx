"use client";

import Link from "next/link";
import { LogIn } from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
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
  const { user, status } = useCurrentUser();

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
      <SidebarMenuButton size="lg" className="pointer-events-none">
        <Avatar className="h-8 w-8 rounded-lg">
          <AvatarFallback className="rounded-lg">{initials(user.displayName)}</AvatarFallback>
        </Avatar>
        <div className="grid flex-1 text-left text-sm leading-tight">
          <span className="truncate font-semibold">{user.displayName}</span>
          <span className="truncate text-xs text-muted-foreground">
            {user.department} &middot; {user.seniority}
          </span>
        </div>
      </SidebarMenuButton>
    );
  }

  return (
    <SidebarMenuButton size="lg" asChild>
      <Link href="/oauth2/authorization/goldys">
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
