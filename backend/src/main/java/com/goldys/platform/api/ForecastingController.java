package com.goldys.platform.api;

import com.goldys.platform.application.CreateForecastRequest;
import com.goldys.platform.application.ForecastComparisonDto;
import com.goldys.platform.application.ForecastMetricDto;
import com.goldys.platform.application.ForecastRunDto;
import com.goldys.platform.application.ForecastingApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TimesFM forecasting: list forecastable metrics, submit runs, and compare to actuals.
 * Delivery-only.
 */
@RestController
@RequestMapping("/api/forecasting")
public class ForecastingController {

  private final ForecastingApplicationService forecasting;
  private final CurrentUserService currentUser;

  public ForecastingController(
      ForecastingApplicationService forecasting, CurrentUserService currentUser) {
    this.forecasting = forecasting;
    this.currentUser = currentUser;
  }

  @GetMapping("/metrics")
  List<ForecastMetricDto> metrics(@AuthenticationPrincipal AccountUserDetails user) {
    return forecasting.metrics(currentUser.roleOf(user));
  }

  @PostMapping("/runs")
  ForecastRunDto create(
      @RequestBody CreateForecastRequest request,
      @AuthenticationPrincipal AccountUserDetails user) {
    return forecasting.create(currentUser.roleOf(user), user.email(), request);
  }

  @GetMapping("/runs")
  List<ForecastRunDto> list(@AuthenticationPrincipal AccountUserDetails user) {
    return forecasting.list(currentUser.roleOf(user));
  }

  @GetMapping("/runs/{id}")
  ForecastRunDto get(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return forecasting.get(currentUser.roleOf(user), id);
  }

  @GetMapping("/runs/{id}/comparison")
  ForecastComparisonDto comparison(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return forecasting.comparison(currentUser.roleOf(user), id);
  }
}
