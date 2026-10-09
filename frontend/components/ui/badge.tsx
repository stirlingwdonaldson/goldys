import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors [&_svg]:size-3 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground hover:bg-primary/80",
        secondary:
          "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80",
        destructive:
          "border-transparent bg-destructive text-destructive-foreground hover:bg-destructive/80",
        outline: "text-foreground",
        // Soft status pills (docs/design/ui-direction.md): tinted background,
        // coloured text. State reads at a glance without a wall of solid colour.
        success: "border-transparent bg-status-success-soft text-status-success",
        conflict: "border-transparent bg-status-conflict-soft text-status-conflict",
        missing: "border-transparent bg-status-missing-soft text-status-missing",
        info: "border-transparent bg-status-info-soft text-status-info",
        failed: "border-transparent bg-destructive-soft text-destructive",
        neutral: "border-transparent bg-muted text-muted-foreground",
        brand: "border-transparent bg-brand/25 text-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
