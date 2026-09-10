import { AppSidebar } from "@/components/app-sidebar"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
} from "@/components/ui/breadcrumb"
import { Separator } from "@/components/ui/separator"
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar"
import { StatCard } from "@/components/dashboard/stat-card"
import { SalesTrendChart } from "@/components/dashboard/sales-trend-chart"
import { ConflictsChart } from "@/components/dashboard/conflicts-chart"
import { ConnectorStatus } from "@/components/dashboard/connector-status"
import { ExceptionsTable } from "@/components/dashboard/exceptions-table"
import { kpis } from "@/lib/mock-dashboard-data"

// Mock landing page — every number here comes from lib/mock-dashboard-data.ts,
// not the backend. Real KPI/reconciliation data is blocked on the PRD's open
// questions (entity matching, field-to-role permissions); see CLAUDE.md.
export default function Home() {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbPage>Dashboard</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        </header>
        <main className="flex min-w-0 flex-1 flex-col gap-6 p-6">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">
              Good afternoon, Stirling
            </h1>
            <p className="text-sm text-muted-foreground">
              Mock data — layout preview only, not live figures.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {kpis.map((kpi) => (
              <StatCard key={kpi.label} {...kpi} />
            ))}
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <SalesTrendChart />
            <ConflictsChart />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_1.4fr]">
            <ConnectorStatus />
            <ExceptionsTable />
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}
