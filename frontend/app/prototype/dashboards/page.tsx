import { Suspense } from "react";
import { notFound } from "next/navigation";
import { DashboardEditorPrototype } from "@/components/dashboards/prototype/dashboard-editor-prototype";

export default function PrototypePage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <Suspense fallback={<p className="p-8">Opening the prototype…</p>}><DashboardEditorPrototype /></Suspense>;
}
