package com.goldys.platform.conversational;

import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.conversational.ConversationService.ThreadSummary;
import com.goldys.platform.conversational.ConversationService.ThreadView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thread CRUD for the persisted Ask Goldy's conversations, scoped to the authenticated user. */
@RestController
@RequestMapping("/api/conversational/threads")
public class ThreadController {
  private static final ResourceKey RESOURCE = new ResourceKey("conversational.threads");

  private final ConversationService conversations;
  private final CurrentUserService currentUser;
  private final PermissionService permissions;

  public ThreadController(
      ConversationService conversations,
      CurrentUserService currentUser,
      PermissionService permissions) {
    this.conversations = conversations;
    this.currentUser = currentUser;
    this.permissions = permissions;
  }

  @GetMapping
  List<ThreadSummary> list(@AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return conversations.list(user.id());
  }

  @GetMapping("/{id}")
  ThreadView get(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.READ);
    return conversations.get(user.id(), id);
  }

  @PatchMapping("/{id}")
  ThreadView rename(
      @PathVariable UUID id,
      @RequestBody RenameRequest body,
      @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.WRITE);
    conversations.rename(user.id(), id, body.title());
    return conversations.get(user.id(), id);
  }

  @DeleteMapping("/{id}")
  void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    permissions.require(currentUser.roleOf(user), RESOURCE, PermissionAction.WRITE);
    conversations.delete(user.id(), id);
  }

  record RenameRequest(String title) {}
}
