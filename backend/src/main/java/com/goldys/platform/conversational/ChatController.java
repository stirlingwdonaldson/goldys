package com.goldys.platform.conversational;

import com.goldys.platform.api.NotConfiguredException;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.PermissionAction;
import com.goldys.platform.auth.PermissionService;
import com.goldys.platform.auth.ResourceKey;
import com.goldys.platform.auth.UserRole;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams "Ask Goldy's" answers as SSE, Owner-only. */
@RestController
@RequestMapping("/api/conversational")
public class ChatController {
  private static final ResourceKey RESOURCE = new ResourceKey("conversational.chat");

  private final CurrentUserService currentUser;
  private final PermissionService permissions;
  private final ObjectProvider<AssistantService> assistant;

  public ChatController(
      CurrentUserService currentUser,
      PermissionService permissions,
      ObjectProvider<AssistantService> assistant) {
    this.currentUser = currentUser;
    this.permissions = permissions;
    this.assistant = assistant;
  }

  @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter chat(
      @RequestBody ChatRequest request, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    permissions.require(role, RESOURCE, PermissionAction.READ);

    AssistantService service = assistant.getIfAvailable();
    if (service == null) {
      throw new NotConfiguredException(
          "Ask Goldy's is not configured (set OPENAI_API_KEY and SPRING_AI_MODEL_CHAT=openai).");
    }

    SseEmitter emitter = new SseEmitter(0L); // no idle timeout
    service.stream(request, role)
        .subscribe(
            event -> send(emitter, event),
            err -> send(emitter, new ConversationEvent.Error("Something went wrong.")),
            emitter::complete);
    return emitter;
  }

  private static void send(SseEmitter emitter, ConversationEvent event) {
    try {
      switch (event) {
        case ConversationEvent.TextDelta(var delta) ->
            emitter.send(SseEmitter.event().name("text").data(Map.of("delta", delta)));
        case ConversationEvent.Answer(var payload) ->
            emitter.send(SseEmitter.event().name("answer").data(payload));
        case ConversationEvent.Error(var message) ->
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
      }
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
  }
}
