import { describe, it, expect } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { ColumnDef } from "@tanstack/react-table";
import { DataTable } from "./data-table";
import { DataTableColumnHeader } from "./data-table-column-header";

interface Row {
  name: string;
  amount: number;
}

const columns: ColumnDef<Row>[] = [
  { accessorKey: "name", header: "Name", meta: { title: "Name" } },
  {
    accessorKey: "amount",
    header: ({ column }) => <DataTableColumnHeader column={column} title="Amount" />,
    meta: { title: "Amount", align: "right" },
  },
];

const data: Row[] = [
  { name: "Acme", amount: 20 },
  { name: "Bidfood", amount: 300 },
  { name: "Costa", amount: 5 },
];

function bodyNames(): string[] {
  const rows = screen.getAllByRole("row").slice(1);
  return rows.map((r) => within(r).getAllByRole("cell")[0].textContent ?? "");
}

describe("DataTable", () => {
  it("sorts by a column when its header is clicked", () => {
    render(<DataTable columns={columns} data={data} />);
    fireEvent.click(screen.getByRole("button", { name: "Sort by Amount" }));
    expect(bodyNames()).toEqual(["Costa", "Acme", "Bidfood"]);
    fireEvent.click(screen.getByRole("button", { name: "Sort by Amount" }));
    expect(bodyNames()).toEqual(["Bidfood", "Acme", "Costa"]);
  });

  it("filters rows by the configured column", () => {
    render(<DataTable columns={columns} data={data} filterColumn="name" filterPlaceholder="Filter names" />);
    fireEvent.change(screen.getByLabelText("Filter names"), { target: { value: "bid" } });
    expect(bodyNames()).toEqual(["Bidfood"]);
  });

  it("paginates client-side and shows an empty message when nothing matches", () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        pageSize={2}
        filterColumn="name"
        emptyMessage="No suppliers match."
      />,
    );
    expect(bodyNames()).toEqual(["Acme", "Bidfood"]);
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(bodyNames()).toEqual(["Costa"]);

    fireEvent.change(screen.getByLabelText("Filter…"), { target: { value: "zzz" } });
    expect(screen.getByText("No suppliers match.")).toBeInTheDocument();
  });
});
