import { cn } from "@/lib/utils"

const TONE = {
  success: "bg-status-success ring-status-success-soft",
  conflict: "bg-status-conflict ring-status-conflict-soft",
  missing: "bg-status-missing ring-status-missing-soft",
  failed: "bg-destructive ring-destructive-soft",
} as const

/** A small status dot with a soft halo, for live state in dense chrome. */
export function StatusDot({
  tone,
  halo = true,
  className,
}: {
  tone: keyof typeof TONE
  halo?: boolean
  className?: string
}) {
  return (
    <span
      aria-hidden="true"
      className={cn("inline-block size-2 shrink-0 rounded-full", TONE[tone], halo && "ring-[3px]", className)}
    />
  )
}
