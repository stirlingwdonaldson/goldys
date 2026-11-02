import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LightspeedReceiptImport } from "./lightspeed-receipt-import";
const upload = vi.fn().mockResolvedValue({ rawId: "test", distinctObservations: 2, qualification: "SOURCE_COVERAGE_UNVERIFIED" });
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({ uploadLightspeedReceiptCsv: upload }) }));

describe("LightspeedReceiptImport", () => {
  it("sends the operator's declared export scope and reports qualified success", async () => {
    render(<QueryClientProvider client={new QueryClient()}><LightspeedReceiptImport /></QueryClientProvider>);
    fireEvent.change(screen.getByLabelText("Selected site ID"), { target: { value: "96181" } });
    fireEvent.change(screen.getByLabelText("Exact site name from export"), { target: { value: "Goldy’s Tavern" } });
    fireEvent.change(screen.getByLabelText("Export start date"), { target: { value: "2026-10-28" } });
    fireEvent.change(screen.getByLabelText("Export end date"), { target: { value: "2026-10-29" } });
    const file = new File(["csv"], "sales.csv", { type: "text/csv" });
    fireEvent.change(screen.getByLabelText("CSV file"), { target: { files: [file] } });
    fireEvent.submit(screen.getByRole("button", { name: "Import CSV" }).closest("form")!);
    await waitFor(() => expect(upload).toHaveBeenCalledWith("transactions", expect.any(File), { siteNumber: 96181, siteName: "Goldy’s Tavern", from: "2026-10-28", to: "2026-10-29" }));
    expect(await screen.findByText(/Imported 2 distinct observations/)).toBeInTheDocument();
  });
});
