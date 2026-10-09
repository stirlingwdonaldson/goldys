import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/**
 * A tinted square behind an icon. Carries a little colour into otherwise neutral
 * cards (docs/design/ui-direction.md). Tones reuse the status tokens.
 */
const iconTileVariants = cva(
  "inline-flex shrink-0 items-center justify-center [&_svg]:shrink-0",
  {
    variants: {
      tone: {
        neutral: "bg-muted text-muted-foreground",
        surface: "bg-background text-foreground",
        success: "bg-status-success-soft text-status-success",
        conflict: "bg-status-conflict-soft text-status-conflict",
        missing: "bg-status-missing-soft text-status-missing",
        info: "bg-status-info-soft text-status-info",
        failed: "bg-destructive-soft text-destructive",
        brand: "bg-brand text-brand-foreground",
      },
      size: {
        sm: "size-8 rounded-lg [&_svg]:size-4",
        md: "size-9 rounded-md [&_svg]:size-[18px]",
        lg: "size-11 rounded-xl [&_svg]:size-5",
      },
    },
    defaultVariants: { tone: "neutral", size: "sm" },
  }
)

export interface IconTileProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof iconTileVariants> {}

function IconTile({ className, tone, size, ...props }: IconTileProps) {
  return <span aria-hidden="true" className={cn(iconTileVariants({ tone, size }), className)} {...props} />
}

export { IconTile, iconTileVariants }
