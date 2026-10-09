import * as React from "react"

import { cn } from "@/lib/utils"

/** A keyboard key hint, e.g. <Kbd>⌘K</Kbd>. */
const Kbd = React.forwardRef<HTMLElement, React.HTMLAttributes<HTMLElement>>(
  ({ className, ...props }, ref) => (
    <kbd
      ref={ref}
      className={cn(
        "inline-flex h-5 items-center rounded border bg-muted px-1.5 font-mono text-2xs text-muted-foreground",
        className
      )}
      {...props}
    />
  )
)
Kbd.displayName = "Kbd"

export { Kbd }
