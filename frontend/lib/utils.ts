import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * tailwind-merge only knows Tailwind's built-in font sizes, so it read our custom `text-ui`
 * (and friends, tailwind.config.ts `fontSize`) as a text *colour* and dropped the real colour
 * next to it: a small filled Button lost `text-primary-foreground` and rendered dark-on-dark.
 */
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [{ text: ["2xs", "ui", "nav", "kpi"] }],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
