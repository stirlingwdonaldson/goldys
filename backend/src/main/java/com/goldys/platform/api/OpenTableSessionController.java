package com.goldys.platform.api;

import com.goldys.platform.application.OpenTableSessionApplicationService;
import com.goldys.platform.application.OpenTableSessionApplicationService.Frame;
import com.goldys.platform.application.OpenTableSessionApplicationService.Input;
import com.goldys.platform.application.OpenTableSessionApplicationService.SessionState;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.UserRole;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The OpenTable assisted sign-in: start a sync, watch and drive the sidecar browser while signing
 * in, end it. Delivery-only; authorization is in {@link OpenTableSessionApplicationService}.
 *
 * <p>Request bodies on {@code /input} carry what the owner types (including their OpenTable
 * password); nothing here logs them.
 */
@RestController
@RequestMapping("/api/connectors/opentable/session")
public class OpenTableSessionController {
  /** Keeps idle SSE connections open through proxies while the owner reads the login page. */
  private static final long HEARTBEAT_SECONDS = 15;

  private final OpenTableSessionApplicationService sessions;
  private final CurrentUserService currentUser;
  private final ScheduledExecutorService heartbeats =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "opentable-sse-heartbeat");
            t.setDaemon(true);
            return t;
          });

  public OpenTableSessionController(
      OpenTableSessionApplicationService sessions, CurrentUserService currentUser) {
    this.sessions = sessions;
    this.currentUser = currentUser;
  }

  public record StartRequest(String mode) {}

  @jakarta.annotation.PreDestroy
  void shutdown() {
    heartbeats.shutdownNow();
  }

  @GetMapping
  SessionState state(@AuthenticationPrincipal AccountUserDetails user) {
    return sessions.state(role(user));
  }

  @PostMapping
  SessionState start(
      @RequestBody(required = false) StartRequest request,
      @AuthenticationPrincipal AccountUserDetails user) {
    return sessions.start(role(user), request == null ? null : request.mode());
  }

  @PostMapping("/input")
  ResponseEntity<Void> input(
      @RequestBody Input input, @AuthenticationPrincipal AccountUserDetails user) {
    boolean relayed = sessions.input(role(user), input);
    return relayed
        ? ResponseEntity.noContent().build()
        : ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  @DeleteMapping
  ResponseEntity<Void> end(
      @RequestParam(name = "logout", defaultValue = "false") boolean logout,
      @AuthenticationPrincipal AccountUserDetails user) {
    sessions.end(role(user), logout);
    return ResponseEntity.noContent().build();
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream(@AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = role(user);
    SseEmitter emitter = new SseEmitter(0L); // No idle timeout; the session ends it.
    AtomicReference<Runnable> detach = new AtomicReference<>(() -> {});
    AtomicReference<ScheduledFuture<?>> heartbeat = new AtomicReference<>();
    Runnable cleanup =
        () -> {
          detach.getAndSet(() -> {}).run();
          ScheduledFuture<?> beat = heartbeat.getAndSet(null);
          if (beat != null) {
            beat.cancel(false);
          }
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    detach.set(
        sessions.watch(
            role,
            new OpenTableSessionApplicationService.Listener() {
              @Override
              public void onState(SessionState state) {
                send(emitter, "state", state);
              }

              @Override
              public void onFrame(Frame frame) {
                send(emitter, "frame", frame);
              }

              @Override
              public void onClosed() {
                emitter.complete();
              }
            }));
    heartbeat.set(
        heartbeats.scheduleAtFixedRate(
            () -> send(emitter, "ping", java.util.Map.of()),
            HEARTBEAT_SECONDS,
            HEARTBEAT_SECONDS,
            TimeUnit.SECONDS));
    return emitter;
  }

  private UserRole role(AccountUserDetails user) {
    if (!sessions.isAvailable()) {
      throw new NotConfiguredException(
          "OpenTable assisted sync is not enabled (set OPENTABLE_API_ENABLED=true and run the"
              + " opentable-browser sidecar).");
    }
    return currentUser.roleOf(user);
  }

  /** A failed send means the viewer went away; completing triggers cleanup (detach). */
  private static void send(SseEmitter emitter, String name, Object data) {
    try {
      // Frames are sent concurrently from the CDP thread and state from the session scheduler.
      synchronized (emitter) {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
      }
    } catch (IOException | IllegalStateException e) {
      emitter.completeWithError(e);
    }
  }
}
