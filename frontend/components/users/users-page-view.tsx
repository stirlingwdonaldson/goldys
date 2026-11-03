"use client";

import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { KeyRound, Pencil, UserMinus, UserPlus } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import type { ApiError, ManagedUser } from "@/lib/api";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PageHeader } from "@/components/layout/page-header";
import { useToast } from "@/components/feedback/toast";

type Editor =
  | { kind: "create" }
  | { kind: "rename"; user: ManagedUser }
  | { kind: "reset"; user: ManagedUser }
  | { kind: "deactivate"; user: ManagedUser }
  | null;

interface UsersPageViewProps {
  users: ManagedUser[];
  loading: boolean;
  error: ApiError | null;
  reload: () => void;
}

/** The one owner role. Kept as copy because the backend stores the role as ALL × OWNER. */
function RoleBadge() {
  return <Badge variant="secondary">Owner</Badge>;
}

export function UsersPageView({ users, loading, error, reload }: UsersPageViewProps) {
  const api = useApi();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [editor, setEditor] = useState<Editor>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
      await queryClient.invalidateQueries({ queryKey: ["listUsers"] });
      toast({ title: success, tone: "success" });
      setEditor(null);
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  }

  function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    void run(
      () =>
        api.createUser({
          displayName: String(data.get("displayName") ?? ""),
          email: String(data.get("email") ?? ""),
          password: String(data.get("password") ?? ""),
        }),
      "Account created",
    );
  }

  function submitRename(event: FormEvent<HTMLFormElement>, user: ManagedUser) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    void run(
      () => api.updateUser(user.id, { displayName: String(data.get("displayName") ?? "") }),
      "Account renamed",
    );
  }

  function submitReset(event: FormEvent<HTMLFormElement>, user: ManagedUser) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    void run(
      () => api.resetUserPassword(user.id, String(data.get("password") ?? "")),
      "Password reset",
    );
  }

  const header = (
    <PageHeader
      title="Users"
      description="Owner accounts. New accounts change their password at first login."
    />
  );

  if (loading) return <LoadingState rows={3} />;
  if (error) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <ErrorState
          title="Couldn't load users"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={reload}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {header}

      <div className="flex justify-end">
        <Button onClick={() => setEditor({ kind: "create" })}>
          <UserPlus aria-hidden="true" />
          Add user
        </Button>
      </div>

      {actionError ? (
        <p role="alert" className="rounded-lg bg-destructive-soft p-3 text-sm text-destructive">
          {actionError}
        </p>
      ) : null}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead>Role</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {users.map(user => (
            <TableRow key={user.id}>
              <TableCell className="font-medium">
                {user.displayName}
                {user.self ? <span className="ml-2 text-xs text-muted-foreground">(you)</span> : null}
              </TableCell>
              <TableCell>{user.email}</TableCell>
              <TableCell>
                <RoleBadge />
              </TableCell>
              <TableCell>
                {user.active ? (
                  <Badge variant="outline">Active</Badge>
                ) : (
                  <Badge variant="secondary">Deactivated</Badge>
                )}
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Rename ${user.email}`}
                    onClick={() => setEditor({ kind: "rename", user })}
                  >
                    <Pencil aria-hidden="true" />
                    Rename
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Reset password for ${user.email}`}
                    onClick={() => setEditor({ kind: "reset", user })}
                  >
                    <KeyRound aria-hidden="true" />
                    Reset password
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={user.self && user.active}
                    aria-label={`${user.active ? "Deactivate" : "Reactivate"} ${user.email}`}
                    onClick={() =>
                      user.active
                        ? setEditor({ kind: "deactivate", user })
                        : void run(() => api.updateUser(user.id, { active: true }), "Account reactivated")
                    }
                  >
                    {user.active ? <UserMinus aria-hidden="true" /> : <UserPlus aria-hidden="true" />}
                    {user.active ? "Deactivate" : "Reactivate"}
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={editor !== null} onOpenChange={open => { if (!open) setEditor(null); }}>
        <DialogContent>
          {actionError ? (
            <p role="alert" className="rounded-lg bg-destructive-soft p-3 text-sm text-destructive">
              {actionError}
            </p>
          ) : null}
          {editor?.kind === "create" ? (
            <form onSubmit={submitCreate} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Add user</DialogTitle>
                <DialogDescription>
                  The account is an owner and must change this temporary password at first login.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-1.5">
                <Label htmlFor="user-name">Full name</Label>
                <Input id="user-name" name="displayName" required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-email">Email</Label>
                <Input id="user-email" name="email" type="email" required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-password">Temporary password</Label>
                <Input id="user-password" name="password" type="password" minLength={8} required />
              </div>
              <DialogFooter>
                <Button type="submit" disabled={busy}>
                  {busy ? "Creating…" : "Create user"}
                </Button>
              </DialogFooter>
            </form>
          ) : null}

          {editor?.kind === "rename" ? (
            <form onSubmit={event => submitRename(event, editor.user)} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Rename account</DialogTitle>
                <DialogDescription>{editor.user.email}</DialogDescription>
              </DialogHeader>
              <div className="space-y-1.5">
                <Label htmlFor="rename-name">Full name</Label>
                <Input
                  id="rename-name"
                  name="displayName"
                  defaultValue={editor.user.displayName}
                  required
                />
              </div>
              <DialogFooter>
                <Button type="submit" disabled={busy}>
                  {busy ? "Saving…" : "Save name"}
                </Button>
              </DialogFooter>
            </form>
          ) : null}

          {editor?.kind === "reset" ? (
            <form onSubmit={event => submitReset(event, editor.user)} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Reset password</DialogTitle>
                <DialogDescription>
                  {editor.user.email} must change this temporary password at next login.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-1.5">
                <Label htmlFor="reset-password">Temporary password</Label>
                <Input
                  id="reset-password"
                  name="password"
                  type="password"
                  minLength={8}
                  required
                />
              </div>
              <DialogFooter>
                <Button type="submit" disabled={busy}>
                  {busy ? "Resetting…" : "Reset password"}
                </Button>
              </DialogFooter>
            </form>
          ) : null}

          {editor?.kind === "deactivate" ? (
            <div className="space-y-4">
              <DialogHeader>
                <DialogTitle>Deactivate account</DialogTitle>
                <DialogDescription>
                  {editor.user.email} loses access immediately and any live session ends.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  disabled={busy}
                  onClick={() => setEditor(null)}
                >
                  Cancel
                </Button>
                <Button
                  variant="destructive"
                  disabled={busy}
                  onClick={() =>
                    void run(
                      () => api.updateUser(editor.user.id, { active: false }),
                      "Account deactivated",
                    )
                  }
                >
                  {busy ? "Deactivating…" : "Deactivate account"}
                </Button>
              </DialogFooter>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
