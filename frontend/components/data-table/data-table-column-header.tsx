"use client";

import type { Column } from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface DataTableColumnHeaderProps<TData, TValue> {
  column: Column<TData, TValue>;
  title: string;
  className?: string;
}

/** A sortable column header: the first click sorts ascending, later clicks flip the direction. */
export function DataTableColumnHeader<TData, TValue>({
  column,
  title,
  className,
}: DataTableColumnHeaderProps<TData, TValue>) {
  if (!column.getCanSort()) {
    return <span className={className}>{title}</span>;
  }
  const sorted = column.getIsSorted();
  const Icon = sorted === "asc" ? ArrowUp : sorted === "desc" ? ArrowDown : ChevronsUpDown;
  return (
    <Button
      variant="ghost"
      size="sm"
      className={cn("-mx-3 h-8 data-[sorted=true]:text-foreground", className)}
      data-sorted={sorted ? "true" : "false"}
      aria-label={`Sort by ${title}`}
      onClick={() => column.toggleSorting(sorted === "asc")}
    >
      {title}
      <Icon className="ml-1.5 h-3.5 w-3.5" aria-hidden="true" />
    </Button>
  );
}
