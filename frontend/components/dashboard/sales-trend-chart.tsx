"use client"

import { Area, AreaChart, CartesianGrid, XAxis } from "recharts"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import {
  ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
} from "@/components/ui/chart"
import { salesTrend } from "@/lib/mock-dashboard-data"

const chartConfig = {
  pos: {
    label: "POS (Lightspeed)",
    color: "hsl(var(--chart-1))",
  },
  bank: {
    label: "Bank deposits",
    color: "hsl(var(--chart-2))",
  },
} satisfies ChartConfig

export function SalesTrendChart() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Net sales — last 7 days</CardTitle>
        <CardDescription>
          POS-reported sales vs. bank deposits, by day
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer config={chartConfig} className="aspect-auto h-[280px] w-full">
          <AreaChart data={salesTrend} margin={{ left: 12, right: 12 }}>
            <CartesianGrid vertical={false} />
            <XAxis
              dataKey="day"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              padding={{ left: 16, right: 16 }}
            />
            <ChartTooltip content={<ChartTooltipContent indicator="dot" />} />
            <defs>
              <linearGradient id="fillPos" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-pos)" stopOpacity={0.35} />
                <stop offset="95%" stopColor="var(--color-pos)" stopOpacity={0.02} />
              </linearGradient>
              <linearGradient id="fillBank" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-bank)" stopOpacity={0.3} />
                <stop offset="95%" stopColor="var(--color-bank)" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <Area
              dataKey="bank"
              type="monotone"
              fill="url(#fillBank)"
              stroke="var(--color-bank)"
              strokeWidth={2}
            />
            <Area
              dataKey="pos"
              type="monotone"
              fill="url(#fillPos)"
              stroke="var(--color-pos)"
              strokeWidth={2}
            />
            <ChartLegend content={<ChartLegendContent />} />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
