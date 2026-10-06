"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { useToast } from "@/components/feedback/toast";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { ReconciliationDrillIn } from "@/components/reconciliation/drill-in";
import { ExceptionsTable } from "@/components/reconciliation/exceptions-table";

export default function ReconciliationPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <ReconciliationContent />
    </Suspense>
  );
}

function ReconciliationContent() {
  const api = useApi();
  const searchParams = useSearchParams();
  const { data: exceptions, loading, error, reload } = useApiData((a) =>
    a.listReconciliationExceptions(),
  );
  const { data: productExceptions, reload: reloadProducts } = useApiData((a) =>
    a.listProductExceptions(),
  );
  const { toast } = useToast();
  const recordParam = searchParams.get("record");
  const dateParam = searchParams.get("date");
  const productParam = searchParams.get("product");
  const [selectedId, setSelectedId] = useState<string | null>(recordParam);
  const [selectedProduct, setSelectedProduct] = useState<{
    date: string;
    product: string;
  } | null>(dateParam && productParam ? { date: dateParam, product: productParam } : null);
  const [saving, setSaving] = useState(false);
  const [tab, setTab] = useState<"daily" | "product">("daily");

  async function handleSave(field: string, source: string, reason?: string) {
    if (!selectedId) return;
    setSaving(true);
    try {
      await api.saveOverride({ recordId: selectedId, field, source, reason });
      toast({
        title: "Override saved",
        description: `${field} is now resolved from ${source}.`,
        tone: "success",
      });
      await reload();
      setSelectedId(null);
    } catch (e) {
      toast({
        title: "Couldn't save override",
        description: e instanceof Error ? e.message : undefined,
        tone: "error",
      });
    } finally {
      setSaving(false);
    }
  }

  async function handleProductSave(_field: string, source: string, reason?: string) {
    if (!selectedProduct) return;
    setSaving(true);
    try {
      await api.saveProductOverride({
        date: selectedProduct.date,
        product: selectedProduct.product,
        source,
        reason,
      });
      toast({
        title: "Override saved",
        description: `${selectedProduct.product} is now resolved from ${source}.`,
        tone: "success",
      });
      await reloadProducts();
      setSelectedProduct(null);
    } catch (e) {
      toast({
        title: "Couldn't save override",
        description: e instanceof Error ? e.message : undefined,
        tone: "error",
      });
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <LoadingState rows={4} />;
  if (error) {
    if (error.code === "NOT_PERMITTED") return <PermissionDenied subject="reconciliation data" />;
    return (
      <ErrorState
        title="Couldn't load exceptions"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={reload}
      />
    );
  }

  if (selectedId) {
    return (
      <ReconciliationDrillIn
        fetchRecord={(api) => api.getReconciliationRecord(selectedId)}
        deps={[selectedId]}
        onBack={() => setSelectedId(null)}
        onSave={handleSave}
        saving={saving}
      />
    );
  }

  if (selectedProduct) {
    return (
      <ReconciliationDrillIn
        fetchRecord={(api) =>
          api.getProductRecord(selectedProduct.date, selectedProduct.product)
        }
        deps={[selectedProduct.date, selectedProduct.product]}
        onBack={() => setSelectedProduct(null)}
        onSave={handleProductSave}
        saving={saving}
      />
    );
  }

  const daily = exceptions ?? [];
  const products = productExceptions ?? [];
  const total = daily.length + products.length;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reconciliation</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {total === 0
            ? "Field-level discrepancies between your sources."
            : `${total} ${total === 1 ? "item needs" : "items need"} a decision — where sources disagree or one is missing.`}
        </p>
      </div>

      <div role="tablist" aria-label="Reconciliation categories" className="flex gap-4 border-b">
        <TabButton active={tab === "daily"} onClick={() => setTab("daily")}>
          Daily sales ({daily.length})
        </TabButton>
        <TabButton active={tab === "product"} onClick={() => setTab("product")}>
          Product sales ({products.length})
        </TabButton>
      </div>

      {tab === "daily" ? (
        <ExceptionsTable
          kind="daily"
          exceptions={daily}
          onReview={(ex) => setSelectedId(ex.recordId)}
        />
      ) : (
        <ExceptionsTable
          kind="product"
          exceptions={products}
          onReview={(ex) =>
            setSelectedProduct({ date: ex.id.split(":")[0], product: ex.recordId })
          }
        />
      )}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`border-b-2 px-2 pb-2 text-sm font-medium transition-colors ${
        active
          ? "border-primary text-foreground"
          : "border-transparent text-muted-foreground hover:text-foreground"
      }`}
    >
      {children}
    </button>
  );
}
