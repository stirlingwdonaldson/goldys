/**
 * True when the given seniority is the Owner level. The canonical role code is the
 * backend's uppercase `OWNER`; the comparison normalizes to uppercase so the same
 * value means the same thing on both sides. Fails closed when the value is absent.
 */
export function isOwner(seniority: string | null | undefined): boolean {
  return seniority?.trim().toUpperCase() === "OWNER";
}
