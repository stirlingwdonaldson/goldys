"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import { AlertTriangle, Check, CircleDashed, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { NODE_HEIGHT, NODE_WIDTH } from "./layout";
import type { FlowTone, StepNode } from "./types";

/** Icon-tile tint per tone. The tile carries the colour; the card body stays neutral. */
const TILE_CLASS: Record<FlowTone, string> = {
  ok: "bg-status-success-soft text-status-success",
  warn: "bg-status-conflict-soft text-status-conflict",
  fail: "bg-destructive-soft text-destructive",
  missing: "bg-muted text-status-missing",
  info: "bg-status-info-soft text-status-info",
  neutral: "bg-muted text-foreground",
};

const BORDER_CLASS: Record<FlowTone, string> = {
  ok: "border-border",
  warn: "border-status-conflict",
  fail: "border-destructive",
  missing: "border-dashed border-status-missing",
  info: "border-border",
  neutral: "border-border",
};

/** The small corner badge n8n uses to show a node's last outcome. Only problems + success get one. */
function CornerBadge({ tone }: { tone: FlowTone }) {
  const config = {
    ok: { Icon: Check, cls: "bg-status-success text-white", label: "OK" },
    warn: { Icon: AlertTriangle, cls: "bg-status-conflict text-white", label: "Needs attention" },
    fail: { Icon: X, cls: "bg-destructive text-destructive-foreground", label: "Failed" },
    missing: { Icon: CircleDashed, cls: "bg-status-missing text-white", label: "No data" },
  } as const;
  if (!(tone in config)) return null;
  const { Icon, cls, label } = config[tone as keyof typeof config];
  return (
    <span
      className={cn(
        "absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full ring-2 ring-background",
        cls,
      )}
      title={label}
    >
      <Icon className="h-3 w-3" strokeWidth={3} aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  );
}

const handleClass =
  "!h-2.5 !w-2.5 !border-2 !border-muted-foreground/50 !bg-background";

function StepNodeComponent({ data, selected }: NodeProps<StepNode>) {
  const Icon = data.icon;
  return (
    <div
      className={cn(
        "relative flex items-center gap-3 rounded-lg border bg-card px-3 text-card-foreground shadow-sm transition-shadow",
        BORDER_CLASS[data.tone],
        (data.href || data.drill) && "cursor-pointer hover:shadow-md",
        data.emphasis && "border-2 border-foreground",
        selected && "ring-2 ring-ring ring-offset-2 ring-offset-background",
      )}
      style={{ width: NODE_WIDTH, height: NODE_HEIGHT }}
    >
      {data.hasInput ? (
        <Handle type="target" position={data.vertical ? Position.Top : Position.Left} isConnectable={false} className={handleClass} />
      ) : null}
      <div
        className={cn(
          "flex h-10 w-10 shrink-0 items-center justify-center rounded-md",
          TILE_CLASS[data.tone],
        )}
      >
        <Icon className="h-5 w-5" aria-hidden="true" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium leading-tight" title={data.title}>
          {data.title}
        </p>
        {data.subtitle ? (
          <p className="truncate text-xs tabular-nums text-muted-foreground" title={data.subtitle}>
            {data.subtitle}
          </p>
        ) : null}
        {data.detail ? (
          <p
            className={cn(
              "truncate text-[11px] leading-tight",
              data.tone === "fail" ? "text-destructive" : "text-muted-foreground/80",
            )}
            title={data.detail}
          >
            {data.detail}
          </p>
        ) : null}
      </div>
      <CornerBadge tone={data.tone} />
      {data.hasOutput ? (
        <Handle type="source" position={data.vertical ? Position.Bottom : Position.Right} isConnectable={false} className={handleClass} />
      ) : null}
    </div>
  );
}

export const StepNodeView = memo(StepNodeComponent);
