"use client"

import * as React from "react"
import {
  LayoutDashboard,
  Plug,
  Scale,
  Settings2,
  Store,
} from "lucide-react"

import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
import { TeamSwitcher } from "@/components/team-switcher"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from "@/components/ui/sidebar"

// Sample data for the scaffold shell only — wire up the real signed-in
// user and role once auth/session (backend PermissionService) lands.
// TeamSwitcher takes an array for parity with the sidebar-07 primitive,
// but Goldy's is a single workspace, so it's seeded with one entry.
const data = {
  user: {
    name: "Stirling Donaldson",
    email: "stirling@donaldsonblack.com.au",
    avatar: "",
  },
  teams: [
    {
      name: "Goldy's Platform",
      logo: Store,
      plan: "Unified Data",
    },
  ],
  navMain: [
    {
      title: "Dashboard",
      url: "/",
      icon: LayoutDashboard,
      isActive: true,
    },
    {
      title: "Reconciliation",
      url: "#",
      icon: Scale,
    },
    {
      title: "Connectors",
      url: "#",
      icon: Plug,
    },
    {
      title: "Settings",
      url: "#",
      icon: Settings2,
    },
  ],
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <TeamSwitcher teams={data.teams} />
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={data.navMain} />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={data.user} />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
