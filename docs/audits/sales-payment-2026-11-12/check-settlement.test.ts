/** Dated audit reproduction; asserts observed behavior, not desired acceptance. */
import { expect, mock, test } from "bun:test";
import fixture from "./balanced-day.json";
mock.module("lucide-react", () => ({
  Banknote: () => null, CircleDollarSign: () => null, ReceiptText: () => null,
  ShoppingBasket: () => null, Trash2: () => null,
}));
const { buildSettlementInput, buildSettlementGraph } = await import(
  "../../../frontend/components/sales-detail/settlement-graph"
);

test("11 November balances in cents but the existing graph warns", () => {
  const cents = (rows: { amount: string }[]) =>
    rows.reduce((sum, row) => sum + Math.round(Number(row.amount) * 100), 0);
  expect(cents(fixture.saleItemMix)).toBe(1307507);
  expect(cents(fixture.paymentMix)).toBe(1307507);
  const input = buildSettlementInput(fixture as Parameters<typeof buildSettlementInput>[0]);
  const graph = buildSettlementGraph(input);
  expect(graph.columns[3][0].data.tone).toBe("warn");
  expect(graph.columns[3][0].data.detail).toBe("Difference vs sales: −$0");
});
