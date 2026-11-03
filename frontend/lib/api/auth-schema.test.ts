import { expect, it } from "vitest";
import { currentUserSchema } from "./auth-schema";

const profile = { displayName: "Fixture owner", department: "MANAGEMENT", seniority: "OWNER", mustChangePassword: false };
it("accepts Spring StaffProfileSummary without inventing id", () => {
  expect(currentUserSchema.parse(profile)).toEqual(profile);
});
it("defaults the forced-change flag when an older payload omits it", () => {
  const legacy = { displayName: profile.displayName, department: profile.department, seniority: profile.seniority };
  expect(currentUserSchema.parse(legacy).mustChangePassword).toBe(false);
  expect(currentUserSchema.parse({ ...legacy, mustChangePassword: true }).mustChangePassword).toBe(true);
});
it.each([null, [], {}, { ...profile, displayName: 42 }, { displayName: "Fixture", department: "MANAGEMENT" }])("rejects malformed profile %#", value => {
  expect(currentUserSchema.safeParse(value).success).toBe(false);
});
it("accepts future role strings without coercion and strips extra response fields", () => {
  const future = { displayName: "  Fixture  ", department: "NEW_DEPARTMENT", seniority: "NEW_ROLE", mustChangePassword: false };
  expect(currentUserSchema.parse({ ...future, internalId: "not-public" })).toEqual(future);
});
