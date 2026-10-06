import type { ReactNode } from "react";
import { BarChartWidgetView } from "./bar-chart-widget";
import { RankedListWidgetView } from "./ranked-list-widget";
import { StatWidgetView } from "./stat-widget";
import { TableWidgetView } from "./table-widget";
import { TimeSeriesWidgetView } from "./time-series-widget";
import type {
  BarChartWidget,
  RankedListWidget,
  StatWidget,
  TableWidget,
  TimeSeriesWidget,
  WidgetSpec,
} from "./types";

/**
 * The trusted mapping from widget type to a pre-built component. Both built-in dashboards and AI
 * answers render through this registry; the AI never names a component, class, or handler.
 */
export const widgetRegistry: Record<string, (widget: WidgetSpec) => ReactNode> = {
  stat: (w) => <StatWidgetView widget={w as StatWidget} />,
  "time-series": (w) => <TimeSeriesWidgetView widget={w as TimeSeriesWidget} />,
  "bar-chart": (w) => <BarChartWidgetView widget={w as BarChartWidget} />,
  table: (w) => <TableWidgetView widget={w as TableWidget} />,
  "ranked-list": (w) => <RankedListWidgetView widget={w as RankedListWidget} />,
};
