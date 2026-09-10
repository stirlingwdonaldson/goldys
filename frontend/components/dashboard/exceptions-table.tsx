import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { recentExceptions } from "@/lib/mock-dashboard-data"

const severityClass: Record<"warning" | "missing", string> = {
  warning: "bg-status-warning text-status-warning-foreground border-transparent",
  missing: "bg-status-missing text-status-missing-foreground border-transparent",
}

const severityLabel: Record<"warning" | "missing", string> = {
  warning: "Conflict",
  missing: "No data",
}

export function ExceptionsTable() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Open exceptions</CardTitle>
        <CardDescription>
          Records where sources disagree or one is missing — a list to
          triage, not a summary
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Entity</TableHead>
              <TableHead>Field</TableHead>
              <TableHead>Sources</TableHead>
              <TableHead className="text-right">Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {recentExceptions.map((exception) => (
              <TableRow key={exception.id}>
                <TableCell className="font-medium">
                  {exception.entity}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {exception.field}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {exception.sources}
                </TableCell>
                <TableCell className="text-right">
                  <Badge className={severityClass[exception.severity]}>
                    {severityLabel[exception.severity]}
                  </Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}
