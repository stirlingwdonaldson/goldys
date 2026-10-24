import { expect, it } from "vitest";
import { demoApi } from "./demo";

it("demo product exceptions honour bounded server pages", async () => {
  const all = await demoApi.listProductExceptions(0, 200);
  const first = await demoApi.listProductExceptions(0, 1);
  const exhausted = await demoApi.listProductExceptions(all.total, 1);
  expect(first.items).toEqual(all.items.slice(0, 1));
  expect(first.total).toBe(all.total);
  expect(exhausted.items).toEqual([]);
});
