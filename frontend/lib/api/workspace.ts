import { z } from "zod";
import { widgetSchema } from "@/components/widgets/parse";
import { draftSchema, provenanceSchema } from "./dashboard-schema";

export const workspaceJobSchema = z.object({
  id: z.string().uuid(), threadId: z.string().uuid(),
  state: z.enum(["QUEUED", "RUNNING", "COMPLETED", "CANCELLED", "INTERRUPTED", "FAILED", "BUDGET_EXHAUSTED"]),
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

export const workspaceFileSchema = z.object({ id: z.string().uuid(), name: z.string(), kind: z.enum(["UPLOAD", "ARTIFACT"]), byteLength: z.number().int().positive(), kept: z.boolean(), expiresAt: z.string(), available: z.boolean() });
export type WorkspaceFile = z.infer<typeof workspaceFileSchema>;
export interface WorkspaceApi {
  deleteWorkspaceThread(id: string): Promise<void>;
  listWorkspaceFiles(id: string, options?: WorkspaceReadOptions): Promise<WorkspaceFile[]>;
  uploadWorkspaceFile(id: string, file: File): Promise<WorkspaceFile>;
  keepWorkspaceFile(id: string, file: string, keep: boolean): Promise<void>;
  deleteWorkspaceFile(id: string, file: string): Promise<void>;
  workspaceFileDownloadUrl(id: string, file: string): Promise<string>;
  listWorkspaceThreads(before?: string, limit?: number, options?: WorkspaceReadOptions): Promise<WorkspacePage>;
  submitWorkspaceJob(input: WorkspaceSubmission): Promise<WorkspaceJob>;
  getWorkspaceJob(id: string, options?: WorkspaceReadOptions): Promise<WorkspaceJob>;
  stopWorkspaceJob(id: string): Promise<WorkspaceJob>;
  listWorkspaceFollowUps(id: string, options?: WorkspaceReadOptions): Promise<WorkspaceFollowUp[]>;
  enqueueWorkspaceFollowUp(id: string, requestId: string, prompt: string): Promise<WorkspaceFollowUp>;
  editWorkspaceFollowUp(id: string, requestId: string, prompt: string): Promise<void>;
  removeWorkspaceFollowUp(id: string, requestId: string): Promise<void>;
}
