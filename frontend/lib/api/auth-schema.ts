import { z } from "zod";

/** Matches Spring StaffProfileSummary; future role strings are not client permissions. */
export const currentUserSchema = z.object({
  displayName: z.string(),
  department: z.string(),
  seniority: z.string(),
});

export type CurrentUser = z.infer<typeof currentUserSchema>;
