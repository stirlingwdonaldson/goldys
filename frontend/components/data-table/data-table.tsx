"use client";

import { useState, type ReactNode } from "react";
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
  type PaginationState,
  type Row,
  type SortingState,
  type VisibilityState,
} from "@tanstack/react-table";
import { Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { DEFAULT_PAGE_SIZE, TablePagination } from "./table-pagination";

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  /** Column id the toolbar's text filter applies to. Omit to hide the filter input. */
  filterColumn?: string;
  filterPlaceholder?: string;
  /**
   * Initial client-side page size (people can change it from the rows-per-page menu). `false`
   * renders every row, for callers that page on the server with their own `TablePagination`.
   */
  pageSize?: number | false;
  initialSorting?: SortingState;
  /** Shown in the body when there are no rows (or the filter matches none). */
  emptyMessage?: string;
  /** Extra toolbar content, rendered left of the column-visibility menu. */
  toolbar?: ReactNode;
  /** Stable row id; defaults to TanStack's index-based id. */
  getRowId?: (row: TData, index: number) => string;
  /** When set, body rows become clickable and call this with the row's data. */
  onRowClick?: (row: TData) => void;
  /** Highlight (aria-selected) rows for which this returns true. */
  isRowSelected?: (row: TData) => boolean;
}

/**
 * The shadcn "data table" pattern: a TanStack Table instance rendered with the shadcn
 * `Table` primitives, plus a toolbar (text filter + column visibility) and pagination.
 * Columns opt into sorting by using {@link DataTableColumnHeader} as their header.
 */
export function DataTable<TData, TValue>({
  columns,
  data,
  filterColumn,
  filterPlaceholder = "Filter…",
  pageSize = DEFAULT_PAGE_SIZE,
  initialSorting = [],
  emptyMessage = "No results.",
  toolbar,
  getRowId,
  onRowClick,
  isRowSelected,
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = useState<SortingState>(initialSorting);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const paginated = pageSize !== false;
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: pageSize === false ? DEFAULT_PAGE_SIZE : pageSize,
  });

  const table = useReactTable({
    data,
    columns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    ...(paginated ? { getPaginationRowModel: getPaginationRowModel() } : {}),
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    onPaginationChange: setPagination,
    state: { sorting, columnFilters, columnVisibility, ...(paginated ? { pagination } : {}) },
  });

  const hideableColumns = table.getAllColumns().filter((c) => c.getCanHide());
  const filter = filterColumn ? table.getColumn(filterColumn) : undefined;
  const rows = table.getRowModel().rows;
  const filteredCount = table.getFilteredRowModel().rows.length;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        {filter ? (
          <Input
            aria-label={filterPlaceholder}
            placeholder={filterPlaceholder}
            value={(filter.getFilterValue() as string) ?? ""}
            onChange={(e) => filter.setFilterValue(e.target.value)}
            className="h-8 w-56"
          />
        ) : null}
        <div className="ml-auto flex items-center gap-2">
          {toolbar}
          {hideableColumns.length > 1 ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" className="h-8">
                  <Settings2 className="mr-1.5 h-4 w-4" aria-hidden="true" />
                  Columns
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48">
                <DropdownMenuLabel>Show columns</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {hideableColumns.map((column) => (
                  <DropdownMenuCheckboxItem
                    key={column.id}
                    checked={column.getIsVisible()}
                    onCheckedChange={(value) => column.toggleVisibility(!!value)}
                  >
                    {columnTitle(column.columnDef.meta, column.id)}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      </div>

      <div className="overflow-hidden rounded-xl border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((group) => (
              <TableRow key={group.id} className="hover:bg-transparent">
                {group.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    className={cn("h-10", alignClass(header.column.columnDef.meta))}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {rows.length ? (
              rows.map((row) => (
                <DataTableRow
                  key={row.id}
                  row={row}
                  onRowClick={onRowClick}
                  isRowSelected={isRowSelected}
                />
              ))
            ) : (
              <TableRow className="hover:bg-transparent">
                <TableCell
                  colSpan={table.getVisibleLeafColumns().length}
                  className="h-20 text-center text-muted-foreground"
                >
                  {emptyMessage}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {paginated ? (
        <TablePagination
          page={pagination.pageIndex}
          pageSize={pagination.pageSize}
          total={filteredCount}
          onPageChange={(pageIndex) => table.setPageIndex(pageIndex)}
          onPageSizeChange={(size) => setPagination({ pageIndex: 0, pageSize: size })}
        />
      ) : null}
    </div>
  );
}

function DataTableRow<TData>({
  row,
  onRowClick,
  isRowSelected,
}: {
  row: Row<TData>;
  onRowClick?: (row: TData) => void;
  isRowSelected?: (row: TData) => boolean;
}) {
  const selected = isRowSelected?.(row.original) ?? false;
  return (
    <TableRow
      data-state={selected ? "selected" : undefined}
      className={onRowClick ? "cursor-pointer" : undefined}
      onClick={onRowClick ? () => onRowClick(row.original) : undefined}
    >
      {row.getVisibleCells().map((cell) => (
        <TableCell
          key={cell.id}
          className={cn("px-4 py-2", alignClass(cell.column.columnDef.meta))}
        >
          {flexRender(cell.column.columnDef.cell, cell.getContext())}
        </TableCell>
      ))}
    </TableRow>
  );
}

/**
 * Per-column display hints carried on `columnDef.meta`. `title` names the column in the
 * visibility menu; `align: "right"` right-aligns numeric columns in header and body.
 */
export interface DataTableColumnMeta {
  title?: string;
  align?: "left" | "right";
}

function asMeta(meta: unknown): DataTableColumnMeta {
  return (meta ?? {}) as DataTableColumnMeta;
}

function columnTitle(meta: unknown, fallback: string): string {
  return asMeta(meta).title ?? fallback;
}

function alignClass(meta: unknown): string {
  return asMeta(meta).align === "right" ? "text-right" : "";
}
