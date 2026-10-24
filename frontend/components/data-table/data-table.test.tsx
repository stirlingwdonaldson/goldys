import { describe, it, expect, vi } from "vitest";
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
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Amount" />
    ),
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
    render(
      <DataTable
        columns={columns}
        data={data}
        filterColumn="name"
        filterPlaceholder="Filter names"
      />,
    );
    fireEvent.change(screen.getByLabelText("Filter names"), {
      target: { value: "bid" },
    });
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
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(bodyNames()).toEqual(["Costa"]);

    fireEvent.change(screen.getByLabelText("Filter…"), {
      target: { value: "zzz" },
    });
    expect(screen.getByText("No suppliers match.")).toBeInTheDocument();
  });

  it("restores an opted-in list's sort and page after navigating away", async () => {
    const first = render(
      <DataTable
        columns={columns}
        data={data}
        pageSize={2}
        stateKey="navigation-test"
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Sort by Amount" }));
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(bodyNames()).toEqual(["Bidfood"]);
    first.unmount();
    render(
      <DataTable
        columns={columns}
        data={data}
        pageSize={2}
        stateKey="navigation-test"
      />,
    );
    expect(bodyNames()).toEqual(["Bidfood"]);
  });

  it("clamps a retained page when returning to a smaller result set", () => {
    const first=render(<DataTable columns={columns} data={data} pageSize={2} stateKey="smaller-list-test"/>);
    fireEvent.click(screen.getByRole("button",{name:"Next page"}));
    first.unmount();
    render(<DataTable columns={columns} data={data.slice(0,1)} pageSize={2} stateKey="smaller-list-test"/>);
    expect(bodyNames()).toEqual(["Acme"]);
  });

  it("renders a checkbox column and reports the selection", () => {
    const onSelected = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={data}
        selectable
        getRowId={(r) => r.name}
        onSelectedRowsChange={onSelected}
      />,
    );

    expect(
      screen.getByRole("checkbox", { name: "Select all" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("checkbox", { name: "Select row" })[0]);
    expect(onSelected).toHaveBeenCalledWith([{ name: "Acme", amount: 20 }]);
  });

  it("renders a row-actions button per row", () => {
    render(
      <DataTable
        columns={columns}
        data={data}
        rowActions={() => <div>act</div>}
      />,
    );
    expect(screen.getAllByRole("button", { name: "Row actions" })).toHaveLength(
      3,
    );
  });
});
