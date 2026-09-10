"use client"

import * as React from "react"

import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"

// sidebar-07's original TeamSwitcher assumes multi-workspace/multi-tenant
// use (a dropdown to switch teams, an "Add team" action) — Goldy's is a
// single pub, single workspace, so this is a static header instead of a
// functional switcher. Kept the same export name/shape (a `teams` array,
// using its first entry) so app-sidebar.tsx didn't need to change its data
// shape; revisit properly if multi-venue ever becomes a real requirement.
export function TeamSwitcher({
  teams,
}: {
  teams: {
    name: string
    logo: React.ElementType
    plan: string
  }[]
}) {
  const workspace = teams[0]

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <SidebarMenuButton size="lg" className="cursor-default hover:bg-transparent">
          <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
            <workspace.logo className="size-4" />
          </div>
          <div className="grid flex-1 text-left text-sm leading-tight">
            <span className="truncate font-semibold">{workspace.name}</span>
            <span className="truncate text-xs">{workspace.plan}</span>
          </div>
        </SidebarMenuButton>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
