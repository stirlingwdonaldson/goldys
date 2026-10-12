"use client";

import { useEffect, useState } from "react";

/**
 * Resolves design-token CSS variables (HSL triplets like `240 6% 10%`) to colours a canvas can
 * paint with, since `fillStyle` doesn't understand `var(...)`. Empty until mounted.
 */
export function useCssColours<K extends string>(variables: Record<K, string>): Partial<Record<K, string>> {
  const [colours, setColours] = useState<Partial<Record<K, string>>>({});
  const key = JSON.stringify(variables);
  useEffect(() => {
    const style = getComputedStyle(document.documentElement);
    const parsed = JSON.parse(key) as Record<K, string>;
    const resolved: Partial<Record<K, string>> = {};
    for (const [name, variable] of Object.entries(parsed) as [K, string][]) {
      const value = style.getPropertyValue(variable).trim();
      resolved[name] = value ? `hsl(${value})` : "#8a8a8a";
    }
    setColours(resolved);
  }, [key]);
  return colours;
}
