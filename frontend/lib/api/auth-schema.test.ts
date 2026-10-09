import { expect, it } from "vitest";
import { currentUserSchema } from "./auth-schema";

const profile = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER" };
it("accepts Spring StaffProfileSummary without inventing id", () => {
  expect(currentUserSchema.parse(profile)).toEqual(profile);
});
it.each([null, [], {}, { ...profile, displayName: 42 }, { displayName: "Fixture", department: "MANAGEMENT" }])("rejects malformed profile %#", value => {
  expect(currentUserSchema.safeParse(value).success).toBe(false);
});
it("accepts future role strings without coercion and strips extra response fields", () => {
  const future = { displayName: "  Fixture  ", department: "NEW_DEPARTMENT", seniority: "NEW_ROLE" };
  expect(currentUserSchema.parse({ ...future, internalId: "not-public" })).toEqual(future);
});
