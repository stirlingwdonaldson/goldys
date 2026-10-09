"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight, Search } from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from "@/components/ui/sidebar";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { NAV_GROUPS, SETTINGS_ITEM, isActive, type NavItem } from "@/lib/nav";
import { isFailing, useShellStatus } from "./shell-status";
import { useCommandPalette } from "./command-palette";
import { UserMenu } from "./user-menu";

function NavLink({ item, pathname, badge }: { item: NavItem; pathname: string; badge?: React.ReactNode }) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton asChild isActive={isActive(pathname, item.href)} tooltip={item.title}>
        <Link href={item.href}>
          <item.icon />
          <span>{item.title}</span>
        </Link>
      </SidebarMenuButton>
      {badge}
    </SidebarMenuItem>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const { openConflicts, connectors } = useShellStatus();
  const { setOpen: openPalette } = useCommandPalette();
  const failing = connectors?.filter(isFailing).length ?? 0;
  const { open, setOpen } = useSidebar();
  // setOpen changes identity whenever `open` changes; read it through a ref so the
  // listener below only fires on real breakpoint crossings, not on manual toggles.
  const setOpenRef = useRef(setOpen);
  setOpenRef.current = setOpen;

  // Between tablet and laptop widths the full sidebar squeezes the page, so it
  // collapses to icons when the window narrows past 1024px, and re-expands on
  // widening only if it was this code (not the person) that collapsed it. It can
  // always be toggled by hand (header button, rail, or ⌘B).
  const openRef = useRef(open);
  openRef.current = open;
  const autoCollapsed = useRef(false);
  useEffect(() => {
    const narrow = window.matchMedia("(max-width: 1023px)");
    const apply = () => {
      if (narrow.matches && openRef.current) {
        autoCollapsed.current = true;
        setOpenRef.current(false);
        // setOpen persists to the sidebar_state cookie; keep the person's saved
        // preference ("expanded") so a later wide-window load isn't collapsed.
        document.cookie = "sidebar_state=true; path=/; max-age=604800";
      } else if (!narrow.matches && autoCollapsed.current) {
        autoCollapsed.current = false;
        setOpenRef.current(true);
      }
    };
    apply();
    narrow.addEventListener("change", apply);
    return () => narrow.removeEventListener("change", apply);
  }, []);

  // Live status next to the nav items it concerns, visible from any page (heuristic 1).
  function badgeFor(href: string) {
    if (href === "/reconciliation" && openConflicts) {
      return (
        <SidebarMenuBadge
          aria-label={`${openConflicts} open`}
          className="rounded-full bg-status-conflict-soft px-1.5 text-status-conflict peer-data-[active=true]/menu-button:bg-white/15 peer-data-[active=true]/menu-button:text-white"
        >
          {openConflicts}
        </SidebarMenuBadge>
      );
    }
    if (href === "/data-health" && failing > 0) {
      return (
        <SidebarMenuBadge aria-label={`${failing} source${failing === 1 ? "" : "s"} failing`}>
          <span className="size-2 rounded-full bg-destructive" />
        </SidebarMenuBadge>
      );
    }
    return null;
  }

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader className="gap-3">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild className="hover:bg-transparent">
              <Link href="/dashboard" aria-label="Goldy's Data Platform, Overview">
                <div className="flex aspect-square size-8 items-center justify-center rounded-[10px] bg-brand text-brand-foreground">
                  <span className="text-[15px] font-bold">G</span>
                </div>
                <div className="grid flex-1 text-left leading-tight">
                  <span className="truncate text-sm font-semibold text-foreground">Goldy&apos;s</span>
                  <span className="truncate text-xs text-muted-foreground">Data platform</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {/* Collapsed (icon) mode keeps search reachable as an icon button. */}
        <SidebarMenu className="hidden group-data-[collapsible=icon]:flex">
          <SidebarMenuItem>
            <SidebarMenuButton tooltip="Search or jump to… (⌘K)" onClick={() => openPalette(true)}>
              <Search />
              <span>Search</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <button
          type="button"
          onClick={() => openPalette(true)}
          className="flex h-9 items-center gap-2 whitespace-nowrap rounded-lg border bg-background px-2.5 text-sm text-muted-foreground transition-colors hover:text-foreground group-data-[collapsible=icon]:hidden"
        >
          <Search className="size-4" aria-hidden="true" />
          <span className="truncate">Search…</span>
          <kbd className="ml-auto rounded border bg-sidebar px-1.5 font-mono text-[10.5px]">⌘K</kbd>
        </button>
      </SidebarHeader>

      <SidebarContent>
        {NAV_GROUPS.map((group) =>
          group.comingSoon ? (
            <Collapsible
              key={group.label}
              className="group/collapsible"
              defaultOpen={group.items.some((i) => isActive(pathname, i.href))}
            >
              <SidebarGroup>
                <SidebarGroupLabel asChild>
                  <CollapsibleTrigger className="w-full">
                    {group.label}
                    <ChevronRight className="ml-auto transition-transform group-data-[state=open]/collapsible:rotate-90" />
                  </CollapsibleTrigger>
                </SidebarGroupLabel>
                <CollapsibleContent>
                  <SidebarGroupContent>
                    <SidebarMenu>
                      {group.items.map((item) => (
                        <NavLink key={item.href} item={item} pathname={pathname} />
                      ))}
                    </SidebarMenu>
                  </SidebarGroupContent>
                </CollapsibleContent>
              </SidebarGroup>
            </Collapsible>
          ) : (
            <SidebarGroup key={group.label}>
              <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => (
                    <NavLink key={item.href} item={item} pathname={pathname} badge={badgeFor(item.href)} />
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ),
        )}
      </SidebarContent>

      <SidebarFooter className="border-t border-sidebar-border">
        <SidebarMenu>
          <NavLink item={SETTINGS_ITEM} pathname={pathname} />
          <SidebarMenuItem>
            <UserMenu />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      {/* Click or drag the edge to collapse to icons; ⌘B / Ctrl+B also toggles. */}
      <SidebarRail />
    </Sidebar>
  );
}
