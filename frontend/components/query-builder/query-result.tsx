"use client";

import { useMemo, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { QueryResult } from "@/lib/api/types";
import {
  EMPTY,
  formatCurrency,
  formatDateTime,
  formatNumber,
  humanizeKey,
} from "@/lib/format";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { DataTable } from "@/components/data-table/data-table";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  AXIS_TICK,
  CHART_TOOLTIP_PROPS,
} from "@/components/widgets/chart-style";

export function QueryResultView({
  result,
  refreshedAt,
}: {
  result: QueryResult;
  refreshedAt: number;
}) {
  const [view, setView] = useState("table");
  const canChart =
    result.columns.length === 2 &&
    ["DECIMAL", "INTEGER"].includes(result.columns[1].type);
  const columns = useMemo<ColumnDef<QueryResult["rows"][number]>[]>(
    () =>
      result.columns.map((column, index) => ({
        id: column.name,
        header: `${humanizeKey(column.name)}${column.unit ? ` (${column.unit})` : ""}`,
        accessorFn: (row) => row[index],
        cell: ({ row }) => {
          const cell = row.original[index];
          if (cell == null) return EMPTY;
          if (column.type === "DECIMAL" || column.type === "INTEGER") {
            const numeric = Number(cell);
            if (!Number.isFinite(numeric)) return String(cell);
            return column.unit === "AUD"
              ? formatCurrency(numeric)
              : formatNumber(numeric, column.type === "INTEGER" ? 0 : 2);
          }
          return typeof cell === "boolean"
            ? cell
              ? "True"
              : "False"
            : String(cell);
        },
        meta: {
          align:
            column.type === "DECIMAL" || column.type === "INTEGER"
              ? "right"
              : "left",
          title: humanizeKey(column.name),
        },
      })),
    [result.columns],
  );
  const chartData = result.rows.map((row) => ({
    label: row[0] == null ? EMPTY : String(row[0]),
    value: row[1] == null ? null : Number(row[1]),
  }));

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Result</CardTitle>
            <CardDescription>
              {result.rows.length} rows returned · Refreshed{" "}
              {formatDateTime(new Date(refreshedAt).toISOString())}
            </CardDescription>
          </div>
          {canChart ? (
            <Tabs value={view} onValueChange={setView}>
              <TabsList aria-label="Result view">
                <TabsTrigger value="table">Table</TabsTrigger>
                <TabsTrigger value="chart">Chart</TabsTrigger>
              </TabsList>
            </Tabs>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="outline">
            {result.semantics.readMode === "RESOLVED"
              ? "Resolved data"
              : "Source observations"}
          </Badge>
          <span>Result grain: {result.semantics.grain}</span>
          {result.pagination.truncated ? (
            <Badge variant="missing">Row limit reached</Badge>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <DataQualifications notices={result.notices} />
        {result.pagination.truncated ? (
          <p className="text-sm text-muted-foreground">
            The result may be incomplete. Narrow your filters or increase the
            row limit.
          </p>
        ) : null}
        {view === "table" || !canChart ? (
          <DataTable
            columns={columns}
            data={result.rows}
            emptyMessage="No records match this query. Try broadening the filters."
          />
        ) : (
          <div
            className="h-64"
            role="img"
            aria-label={`${humanizeKey(result.columns[1].name)} by ${humanizeKey(result.columns[0].name)}. Use the table for exact values.`}
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" tick={AXIS_TICK} />
                <YAxis tick={AXIS_TICK} />
                <Tooltip {...CHART_TOOLTIP_PROPS} />
                <Bar
                  dataKey="value"
                  name={humanizeKey(result.columns[1].name)}
                  fill="hsl(var(--chart-1))"
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
