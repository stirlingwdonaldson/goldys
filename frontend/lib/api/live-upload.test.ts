// @vitest-environment node
import { expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { liveApi } from "./live";

setupApiServer();
it.each([200, 204])("uploads native multipart bytes and accepts empty %i", async status => {
  let uploaded = "";
  server.use(http.post("http://localhost:3000/api/connectors/opentable/upload", async ({ request }) => {
    expect(request.headers.get("content-type")).toMatch(/^multipart\/form-data; boundary=/);
    const form = await request.formData();
    const file = form.get("file");
    expect(file).toBeInstanceOf(File);
    if (!(file instanceof File)) throw new Error("Missing fixture upload");
    expect(file.name).toBe("fixture.csv");
    uploaded = await file.text();
    return new HttpResponse(null, { status });
  }));
  await expect(liveApi.uploadOpenTableCsv(new File(["fixture bytes"], "fixture.csv"))).resolves.toBeUndefined();
  expect(uploaded).toBe("fixture bytes");
});
