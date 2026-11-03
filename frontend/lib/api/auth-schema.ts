import { z } from "zod";

/** Matches Spring StaffProfileSummary; future role strings are not client permissions. */
export const currentUserSchema = z.object({
  displayName: z.string(),
  department: z.string(),
  seniority: z.string(),
  // Older fixtures and payloads predate the forced-change flow.
  mustChangePassword: z.boolean().default(false),
});

export type CurrentUser = z.infer<typeof currentUserSchema>;
