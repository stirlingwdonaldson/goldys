import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

// shadcn/ui's standard cn() helper - every shadcn component you add via
// `bunx shadcn@latest add <component>` will import this from @/lib/utils.
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
