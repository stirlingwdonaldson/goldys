export const weeklyTargetIds: string[] = ["food_target", "beverage_target", "sales_target", "beverage_variance_target_min", "beverage_variance_target_max", "food_variance_target_min", "food_variance_target_max", "beverage_wastage_target_percent", "food_wastage_target_percent", "total_wastage_target_percent", "food_cogs_target_percent", "beverage_cogs_target_percent"];

export function reportPercent(value: number | null): string {
  return value === null ? "—" : `${(value * 100).toFixed(2)}%`;
}
