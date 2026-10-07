package com.goldys.platform.conversational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.auth.DepartmentCode;
import com.goldys.platform.auth.SeniorityCode;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.dashboard.DashboardFilters;
import com.goldys.platform.dashboard.DashboardWidgetValidator;
import com.goldys.platform.dashboard.SavedWidget;
import com.goldys.platform.dashboard.WidgetLayout;
import com.goldys.platform.reporting.ToolDispatcher;
import com.goldys.platform.reporting.ToolId;
import com.goldys.platform.reporting.ToolResult;
import com.goldys.platform.semantic.catalog.Calendar;
import com.goldys.platform.semantic.catalog.Dimension;
import com.goldys.platform.semantic.catalog.MetricCatalog;
import com.goldys.platform.semantic.catalog.MetricId;
import com.goldys.platform.semantic.catalog.MetricQuery;
import com.goldys.platform.semantic.catalog.TimeGrain;
import com.goldys.platform.semantic.catalog.TimeRange;
import com.goldys.platform.widget.StatWidgetSpec;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class DashboardDraftToolTest {

  private static final UserRole OWNER =
      new UserRole(new DepartmentCode("ALL"), new SeniorityCode("OWNER"));
  private static final TimeRange RANGE =
      new TimeRange(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), Calendar.CALENDAR);

  private final CreateDashboardDraftTool tool =
      new CreateDashboardDraftTool(new DashboardWidgetValidator(new MetricCatalog()));

  @Test
  void draftRejectsRankedListRenderTypeForTimeSeriesMetric() {
    // MetricId is an enum, so an unknown metric cannot be constructed; instead assert the
    // equivalent invalid case: a render type that the metric does not support.
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "ranked-list",
                                List.of(
                                    new MetricQuery(
                                        MetricId.SALES_GROSS,
                                        RANGE,
                                        TimeGrain.DAY,
                                        Set.of(),
                                        null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftRejectsUnknownRenderType() {
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "pie-chart",
                                List.of(
                                    new MetricQuery(
                                        MetricId.SALES_GROSS,
                                        RANGE,
                                        TimeGrain.DAY,
                                        Set.of(),
                                        null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftRejectsDimensionNotValidForMetric() {
    // SALES_GROSS declares no valid dimensions, so any grouping axis is rejected.
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "time-series",
                                List.of(
                                    new MetricQuery(
                                        MetricId.SALES_GROSS,
                                        RANGE,
                                        TimeGrain.DAY,
                                        Set.of(Dimension.DEPARTMENT),
                                        null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftAcceptsDimensionValidForMetric() {
    // RESERVATIONS_BOOKINGS declares SERVICE_PERIOD as a valid dimension.
    CreateDashboardDraftInput input =
        new CreateDashboardDraftInput(
            "Bookings by service",
            null,
            DashboardFilters.empty(),
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(
                        new MetricQuery(
                            MetricId.RESERVATIONS_BOOKINGS,
                            RANGE,
                            TimeGrain.DAY,
                            Set.of(Dimension.SERVICE_PERIOD),
                            null)),
                    new WidgetLayout(6, 2))));

    tool.validate(input);

    assertThat(tool.toDraft(input).title()).isEqualTo("Bookings by service");
  }

  @Test
  void toDraftCarriesTitleDescriptionFiltersAndWidgets() {
    CreateDashboardDraftInput input = validInput();

    DashboardDraft draft = tool.toDraft(input);

    assertThat(draft.title()).isEqualTo("My dashboard");
    assertThat(draft.description()).isEqualTo("Daily sales");
    assertThat(draft.filters()).isEqualTo(DashboardFilters.empty());
    assertThat(draft.widgets()).hasSize(1);
    assertThat(draft.widgets().get(0).renderType()).isEqualTo("time-series");
  }

  @Test
  void updateDraftCarriesDashboardId() {
    DashboardDraft draft =
        tool.toDraft(
            new CreateDashboardDraftInput(
                "Weekend", null, DashboardFilters.empty(), List.of(), UUID.randomUUID()));

    assertThat(draft.dashboardId()).isNotNull();
  }

  @Test
  void executeReturnsAStatConfirmationWidget() {
    ToolResult result = tool.execute(validInput(), OWNER);

    assertThat(result.widget()).isInstanceOf(StatWidgetSpec.class);
    assertThat(result.widget().title()).isEqualTo("Dashboard draft ready");
    assertThat(result.notices()).isEmpty();
  }

  @Test
  void callbackRecordsDraftIntoContextInsteadOfAWidget() throws Exception {
    ToolDispatcher dispatcher = mock(ToolDispatcher.class);
    when(dispatcher.dispatch(eq(ToolId.CREATE_DASHBOARD_DRAFT), any(), eq(OWNER)))
        .thenAnswer(inv -> tool.execute(inv.getArgument(1), OWNER));

    ConversationContext context = new ConversationContext();
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ToolCallback callback =
        new ReportingToolCallbacks()
            .forTools(List.of(tool), OWNER, context, dispatcher, mapper)
            .get(0);

    String raw = callback.call(mapper.writeValueAsString(validInput()));

    assertThat(raw).contains("\"ok\":true");
    assertThat(context.toAnswerPayload().widgets()).isEmpty();
    assertThat(context.toAnswerPayload().draft()).isNotNull();
    assertThat(context.toAnswerPayload().draft().title()).isEqualTo("My dashboard");
  }

  @Test
  void draftDefaultsNullFiltersToEmpty() {
    CreateDashboardDraftInput input =
        new CreateDashboardDraftInput(
            "My dashboard",
            "Daily sales",
            null,
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(
                        new MetricQuery(
                            MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null)),
                    new WidgetLayout(6, 2))));

    DashboardDraft draft = tool.toDraft(input);

    assertThat(draft.filters()).isEqualTo(DashboardFilters.empty());
  }

  @Test
  void draftAcceptsRankedListForTopSellers() {
    CreateDashboardDraftInput input =
        new CreateDashboardDraftInput(
            "Top sellers",
            null,
            DashboardFilters.empty(),
            List.of(
                new SavedWidget(
                    "w1",
                    "ranked-list",
                    List.of(
                        new MetricQuery(
                            MetricId.PRODUCT_TOP_SELLERS, RANGE, TimeGrain.DAY, Set.of(), null)),
                    new WidgetLayout(6, 2))));

    tool.validate(input);

    assertThat(tool.toDraft(input).widgets().get(0).renderType()).isEqualTo("ranked-list");
  }

  @Test
  void draftRejectsStatForMultiQueryWidget() {
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "stat",
                                List.of(
                                    new MetricQuery(
                                        MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null),
                                    new MetricQuery(
                                        MetricId.SALES_NET, RANGE, TimeGrain.DAY, Set.of(), null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftRejectsRankedListForMultiQueryWidget() {
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "ranked-list",
                                List.of(
                                    new MetricQuery(
                                        MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null),
                                    new MetricQuery(
                                        MetricId.SALES_NET, RANGE, TimeGrain.DAY, Set.of(), null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftRejectsRankedMetricInCompositeWidget() {
    assertThatThrownBy(
            () ->
                tool.validate(
                    new CreateDashboardDraftInput(
                        "X",
                        null,
                        DashboardFilters.empty(),
                        List.of(
                            new SavedWidget(
                                "w1",
                                "time-series",
                                List.of(
                                    new MetricQuery(
                                        MetricId.PRODUCT_TOP_SELLERS,
                                        RANGE,
                                        TimeGrain.DAY,
                                        Set.of(),
                                        null),
                                    new MetricQuery(
                                        MetricId.SALES_GROSS,
                                        RANGE,
                                        TimeGrain.DAY,
                                        Set.of(),
                                        null)),
                                new WidgetLayout(6, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void draftAcceptsCompositeTimeSeriesWidget() {
    CreateDashboardDraftInput input =
        new CreateDashboardDraftInput(
            "Sales and net",
            null,
            DashboardFilters.empty(),
            List.of(
                new SavedWidget(
                    "w1",
                    "time-series",
                    List.of(
                        new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null),
                        new MetricQuery(MetricId.SALES_NET, RANGE, TimeGrain.DAY, Set.of(), null)),
                    new WidgetLayout(12, 2))));

    tool.validate(input);

    assertThat(tool.toDraft(input).widgets().get(0).queries()).hasSize(2);
  }

  private static CreateDashboardDraftInput validInput() {
    return new CreateDashboardDraftInput(
        "My dashboard",
        "Daily sales",
        DashboardFilters.empty(),
        List.of(
            new SavedWidget(
                "w1",
                "time-series",
                List.of(
                    new MetricQuery(MetricId.SALES_GROSS, RANGE, TimeGrain.DAY, Set.of(), null)),
                new WidgetLayout(6, 2))));
  }
}
