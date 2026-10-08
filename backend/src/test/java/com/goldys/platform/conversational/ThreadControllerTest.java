package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.auth.AccessDeniedException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.conversational.ConversationService.ThreadSummary;
import com.goldys.platform.conversational.ConversationService.ThreadView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThreadControllerTest {

  private static final ResourceKey RESOURCE = new ResourceKey("conversational.threads");
  private static final UUID USER_ID = UUID.randomUUID();
  private static final AccountUserDetails USER =
      new AccountUserDetails(USER_ID, "owner@example.com", "hash", "Owner", "ALL", "OWNER", true);
  private static final UserRole ROLE =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));

  private ConversationService conversations;
  private CurrentUserService currentUser;
  private PermissionService permissions;
  private ThreadController controller;

  @BeforeEach
  void setUp() {
    conversations = mock(ConversationService.class);
    currentUser = mock(CurrentUserService.class);
    permissions = mock(PermissionService.class);
    when(currentUser.roleOf(USER)).thenReturn(ROLE);
    controller = new ThreadController(conversations, currentUser, permissions);
  }

  @Test
  void listRequiresReadAndReturnsThreadsScopedToUser() {
    ThreadSummary summary =
        new ThreadSummary(UUID.randomUUID(), "Sales review", Instant.now(), "preview");
    when(conversations.list(USER_ID)).thenReturn(List.of(summary));

    List<ThreadSummary> result = controller.list(USER);

    verify(permissions).require(ROLE, RESOURCE, PermissionAction.READ);
    verify(conversations).list(USER_ID);
    assertThat(result).containsExactly(summary);
  }

  @Test
  void getRequiresReadAndReturnsViewScopedToUser() {
    UUID threadId = UUID.randomUUID();
    ThreadView view = new ThreadView(threadId, "Sales review", List.of());
    when(conversations.get(USER_ID, threadId)).thenReturn(view);

    ThreadView result = controller.get(threadId, USER);

    verify(permissions).require(ROLE, RESOURCE, PermissionAction.READ);
    verify(conversations).get(USER_ID, threadId);
    assertThat(result).isSameAs(view);
  }

  @Test
  void cannotReadAnotherUsersThread() {
    UUID threadId = UUID.randomUUID();
    when(conversations.get(USER_ID, threadId))
        .thenThrow(AccessDeniedException.forResource("conversation.thread"));

    assertThatThrownBy(() -> controller.get(threadId, USER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void renameRequiresWriteAndReturnsUpdatedView() {
    UUID threadId = UUID.randomUUID();
    ThreadView view = new ThreadView(threadId, "New title", List.of());
    when(conversations.get(USER_ID, threadId)).thenReturn(view);

    ThreadView result =
        controller.rename(threadId, new ThreadController.RenameRequest("New title"), USER);

    verify(permissions).require(ROLE, RESOURCE, PermissionAction.WRITE);
    verify(conversations).rename(USER_ID, threadId, "New title");
    assertThat(result).isSameAs(view);
  }

  @Test
  void deleteRequiresWrite() {
    UUID threadId = UUID.randomUUID();

    controller.delete(threadId, USER);

    verify(permissions).require(ROLE, RESOURCE, PermissionAction.WRITE);
    verify(conversations).delete(USER_ID, threadId);
  }

  @Test
  void deniesListWhenNotPermitted() {
    doThrow(AccessDeniedException.forResource("conversational.threads"))
        .when(permissions)
        .require(any(), any(), any());

    assertThatThrownBy(() -> controller.list(USER)).isInstanceOf(AccessDeniedException.class);
  }
}
