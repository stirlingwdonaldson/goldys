"use client";

import { memo } from "react";
import type { NodeProps } from "@xyflow/react";
import { NODE_WIDTH } from "./layout";
import type { ColumnHeadingNode } from "./types";

/**
 * A quiet, centred label drawn above a column of nodes. Non-interactive: the canvas marks
 * these nodes undraggable/unselectable, so this is purely presentational.
 */
function ColumnHeadingComponent({ data }: NodeProps<ColumnHeadingNode>) {
  return (
    <div
      className="text-center text-xs font-medium uppercase tracking-wide text-muted-foreground"
      style={{ width: NODE_WIDTH }}
    >
      {data.title}
    </div>
  );
}

export const ColumnHeading = memo(ColumnHeadingComponent);
