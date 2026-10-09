"use client";

import { Fragment } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { AskGoldysDrawer } from "@/components/ask-goldys/ask-goldys-drawer";
import { findNav } from "@/lib/nav";
import { cn } from "@/lib/utils";
import { useCurrentUser } from "./current-user-provider";
import { FreshnessChip } from "./freshness-chip";

export function AppHeader() {
  const { user } = useCurrentUser();
  const pathname = usePathname();
  const here = findNav(pathname);
  // Where am I: section › page, using the same names as the sidebar (heuristics 1 and 6).
  const crumbs = [here?.group, here?.item.title].filter((c): c is string => Boolean(c));

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4 md:px-6">
      <SidebarTrigger className="-ml-1.5" />
      <Separator orientation="vertical" className="mr-1 h-4" />
      <Breadcrumb className="min-w-0">
        <BreadcrumbList className="flex-nowrap whitespace-nowrap">
          {crumbs.length === 0 ? (
            <BreadcrumbItem>
              <BreadcrumbPage>
                <Link href="/dashboard">Goldy&apos;s</Link>
              </BreadcrumbPage>
            </BreadcrumbItem>
          ) : (
            crumbs.map((c, i) => (
              <Fragment key={c}>
                {/* The section crumb drops out on narrow headers; the page name never wraps. */}
                {i > 0 ? <BreadcrumbSeparator className="hidden md:block" /> : null}
                <BreadcrumbItem className={cn("min-w-0", i < crumbs.length - 1 && "hidden md:inline-flex")}>
                  {i === crumbs.length - 1 ? (
                    <BreadcrumbPage className="truncate">{c}</BreadcrumbPage>
                  ) : (
                    <span>{c}</span>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))
          )}
        </BreadcrumbList>
      </Breadcrumb>
      <div className="ml-auto flex shrink-0 items-center gap-2">
        <FreshnessChip />
        <AskGoldysDrawer seniority={user?.seniority} />
      </div>
    </header>
  );
}
