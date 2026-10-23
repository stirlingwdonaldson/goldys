// @vitest-environment node
import { expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { demoRecipeComposition } from "./demo-recipe-composition";
import { liveApi } from "./live";

setupApiServer();

it("treats not-yet-received recipe details and folders as missing data", async () => {
  server.use(
    http.get("http://localhost:3000/api/recipes/463/composition", () => new HttpResponse(null, { status: 204 })),
    http.get("http://localhost:3000/api/recipes/folders", () => new HttpResponse(null, { status: 204 })),
  );
  expect(await liveApi.getRecipeComposition(463)).toBeNull();
  expect(await liveApi.getRecipeCatalog()).toBeNull();
});

it("preserves complete observations and their qualifications", async () => {
  const value = demoRecipeComposition(3);
  server.use(http.get("http://localhost:3000/api/recipes/3/composition", () => HttpResponse.json(value)));
  expect(await liveApi.getRecipeComposition(3)).toEqual(value);
});
