import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { demoWeeklyReport } from "@/lib/api/weekly-report-demo";
import type { WeeklyReportWeek } from "@/lib/api/types";
import { DailyDetail, TargetComparison, WeekSummary } from "./report-sections";

const fixture = () => demoWeeklyReport("2026-10-19", 2).weeks;
const row = (week: WeeklyReportWeek, id: string) => week.rows.find(r => r.id === id)!;
const salesAssessment = () => within(screen.getByRole("rowheader", { name: "Total sales ex GST (AUD)" }).closest("tr")!);

describe("weekly report presentation", () => {
  it("renders snapshot assessments while retaining partial differences and known zero", () => {
    const [partial, full] = fixture();
    const view = render(<TargetComparison week={partial} />);
    expect(salesAssessment().getByText("Incomplete comparison evidence")).toBeVisible();
    expect(salesAssessment().getByText("-$30,000.00")).toBeVisible();
    view.rerender(<TargetComparison week={full} />);
    expect(salesAssessment().getByText("Below target")).toBeVisible();
    expect(salesAssessment().getByText("-$23,000.00")).toBeVisible();
    // A known zero difference is supported, and meets the inclusive boundary.
    row(full, "net_sales").total = row(full, "sales_target").total;
    row(full, "sales_difference").total = 0;
    full.assessments.net_sales = { outcome: "MEETS_TARGET", reason: null };
    view.rerender(<TargetComparison week={full} />);
    expect(salesAssessment().getByText("Meets target")).toBeVisible();
    expect(salesAssessment().getByText("$0.00")).toBeVisible();
  });

  it.each([
    ["NO_EFFECTIVE_TARGET", "No effective target"],
    ["BASIS_MISMATCH", "Basis differs"],
    ["COMPARISON_UNAVAILABLE", "Comparison unavailable"],
    ["INCOMPLETE_EVIDENCE", "Incomplete comparison evidence"],
  ] as const)("shows the server's withholding reason: %s", (reason, label) => {
    const full = fixture()[1];
    full.assessments.net_sales = { outcome: "WITHHELD", reason };
    render(<TargetComparison week={full} />);
    expect(salesAssessment().getByText(label)).toBeVisible();
    expect(salesAssessment().queryByText("Below target")).not.toBeInTheDocument();
    expect(salesAssessment().queryByText("Meets target")).not.toBeInTheDocument();
  });

  it("accepts an assessed weekly rate with an undefined daily ratio and keeps zero visible", () => {
    const full = fixture()[1];
    const view = render(<TargetComparison week={full} />);
    const assessment = () => within(screen.getByRole("rowheader", { name: "Total wastage %" }).closest("tr")!);
    expect(assessment().getByText("Incomplete comparison evidence")).toBeVisible();
    row(full, "total_wastage_percent").days = [null, 0, 0, 0, 0, 0, 0];
    row(full, "total_wastage_percent").total = 0;
    full.assessments.total_wastage_percent = { outcome: "MEETS_TARGET", reason: null };
    view.rerender(<TargetComparison week={full} />);
    expect(assessment().getByText("Meets target")).toBeVisible();
    row(full, "total_wastage_percent").total = 0.01;
    full.assessments.total_wastage_percent = { outcome: "ABOVE_LIMIT", reason: null };
    view.rerender(<TargetComparison week={full} />);
    expect(assessment().getByText("Above limit")).toBeVisible();
  });

  it("does not invent a verdict when an older snapshot has no assessment", () => {
    const full = fixture()[1];
    delete full.assessments.net_sales;
    render(<TargetComparison week={full} />);
    expect(salesAssessment().getByText("Assessment unavailable")).toBeVisible();
    expect(salesAssessment().queryByText("Below target")).not.toBeInTheDocument();
  });

  it("keeps missing daily values distinct from recorded zero, without daily targets or ratings", () => {
    const partial = fixture()[0];
    row(partial, "total_wastage").days[0] = 0;
    render(<DailyDetail week={partial} />);
    const table = screen.getByRole("table", { name: "Daily detail" });
    expect(within(table).getByText("$0.00")).toBeVisible();
    expect(within(table).getAllByText("—").length).toBeGreaterThan(0);
    expect(within(table).queryByText(/target|rating/i)).not.toBeInTheDocument();
    expect(within(table).getByRole("cell", { name: "Total sales ex GST: unavailable" })).toHaveTextContent("—");
  });

  it("summarises unavailable values as missing, and known zero as zero", () => {
    const full = fixture()[1];
    row(full, "net_sales").total = 0;
    row(full, "review_average").total = null;
    row(full, "review_average").status = "UNAVAILABLE";
    render(<WeekSummary week={full} />);
    expect(screen.getByText("$0.00")).toBeVisible();
    expect(screen.getByText("—")).toBeVisible();
    expect(screen.getByRole("button", { name: "Source notes for Average review rating, week of 2026-10-26" }).parentElement).toHaveTextContent("Unavailable");
  });
});
