/** Which existing screen each resolved domain jumps to, with the screen's display name. */
export const RESOLVED_SCREENS: Record<string, { href: string; label: string }> = {
  resolved_daily_sales: { href: "/sales", label: "Sales" },
  resolved_product_sales: { href: "/sales", label: "Sales" },
  resolved_payment_day: { href: "/sales-detail", label: "Sales detail" },
  resolved_deleted_sale_day: { href: "/sales-detail", label: "Sales detail" },
  resolved_sale_item_day: { href: "/sales-detail", label: "Sales detail" },
  resolved_reservation_day: { href: "/reservations", label: "Reservations" },
  resolved_labour_day: { href: "/staff", label: "Staff & Labor" },
  resolved_inventory_day: { href: "/kitchen", label: "Kitchen" },
};
