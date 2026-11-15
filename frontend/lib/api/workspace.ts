import { z } from "zod";
import { widgetSchema } from "@/components/widgets/parse";
import { draftSchema, provenanceSchema } from "./dashboard-schema";

export const workspaceJobSchema = z.object({
  id: z.string().uuid(), threadId: z.string().uuid(),
  state: z.enum(["QUEUED", "RUNNING", "COMPLETED", "CANCELLED", "INTERRUPTED"]),
  prompt: z.string(), revision: z.number().int().nonnegative(),
  messages: z.array(z.object({ id: z.string(), role: z.enum(["user", "assistant", "tool"]), text: z.string(),
    draft: draftSchema.nullable().optional(),
    reporting: z.object({ widget: widgetSchema, notices: z.array(z.string()),
      provenance: z.array(provenanceSchema), relatedMetrics: z.array(z.string()) }).nullable(),
  })),
});
export const workspaceFollowUpSchema = z.object({ id: z.string().uuid(), prompt: z.string(), state: z.enum(["QUEUED", "TAKEN", "CANCELLED"]) });
export const workspacePageSchema = z.object({
  threads: z.array(z.object({ id: z.string().uuid(), createdAt: z.string(),
    access: z.enum(["AVAILABLE", "NOT_PERMITTED"]), latestJobId: z.string().uuid().nullable() })),
  nextCursor: z.string().uuid().nullable(),
});
export type WorkspaceJob = z.infer<typeof workspaceJobSchema>;
export type WorkspaceFollowUp = z.infer<typeof workspaceFollowUpSchema>;
export type WorkspacePage = z.infer<typeof workspacePageSchema>;
export type WorkspaceSubmission = { threadId: string; requestId: string; prompt: string };
export type WorkspaceReadOptions = { signal?: AbortSignal };

export interface WorkspaceApi {
  listWorkspaceThreads(before?: string, limit?: number, options?: WorkspaceReadOptions): Promise<WorkspacePage>;
  submitWorkspaceJob(input: WorkspaceSubmission): Promise<WorkspaceJob>;
  getWorkspaceJob(id: string, options?: WorkspaceReadOptions): Promise<WorkspaceJob>;
  stopWorkspaceJob(id: string): Promise<WorkspaceJob>;
  listWorkspaceFollowUps(id: string, options?: WorkspaceReadOptions): Promise<WorkspaceFollowUp[]>;
  enqueueWorkspaceFollowUp(id: string, requestId: string, prompt: string): Promise<WorkspaceFollowUp>;
  editWorkspaceFollowUp(id: string, requestId: string, prompt: string): Promise<void>;
  removeWorkspaceFollowUp(id: string, requestId: string): Promise<void>;
}
