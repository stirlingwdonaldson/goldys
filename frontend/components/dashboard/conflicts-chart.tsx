"use client"

import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import {
  ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
} from "@/components/ui/chart"
import { conflictsBySource } from "@/lib/mock-dashboard-data"

const chartConfig = {
  resolved: {
    label: "Resolved",
    color: "hsl(var(--status-success))",
  },
  open: {
    label: "Open",
    color: "hsl(var(--status-warning))",
  },
} satisfies ChartConfig

// Horizontal layout — "Cooking the Books" is long enough that a vertical
// category axis crowds/misaligns ticks at this card's width; putting
// source names on the y-axis gives them room without rotating labels.
export function ConflictsChart() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Exceptions by source</CardTitle>
        <CardDescription>Resolved vs. still-open, this week</CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer config={chartConfig} className="aspect-auto h-[280px] w-full">
          <BarChart
            data={conflictsBySource}
            layout="vertical"
            margin={{ left: 12, right: 12 }}
          >
            <CartesianGrid horizontal={false} />
            <XAxis type="number" tickLine={false} axisLine={false} />
            <YAxis
              type="category"
              dataKey="source"
              tickLine={false}
              axisLine={false}
              width={110}
            />
            <ChartTooltip content={<ChartTooltipContent />} />
            <Bar dataKey="resolved" fill="var(--color-resolved)" radius={4} />
            <Bar dataKey="open" fill="var(--color-open)" radius={4} />
            <ChartLegend content={<ChartLegendContent />} />
          </BarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
