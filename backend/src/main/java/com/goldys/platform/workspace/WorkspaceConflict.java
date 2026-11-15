package com.goldys.platform.workspace;

/** Expected admission/lease/queue conflict, safe to present without exposing internal failures. */
public final class WorkspaceConflict extends IllegalStateException {
  public WorkspaceConflict(String message) {
    super(message);
  }
}
