"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { useToast } from "@/components/feedback/toast";
import { Badge } from "@/components/ui/badge";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { ReconciliationDrillIn } from "@/components/reconciliation/drill-in";

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

  if (!exceptions || exceptions.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-xl font-semibold">Reconciliation</h1>
          <p className="text-sm text-muted-foreground">
            Field-level conflicts between sources, shown exception-first.
          </p>
        </div>
        {renderProductSection()}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reconciliation</h1>
        <p className="text-sm text-muted-foreground">
          Field-level conflicts between sources, shown exception-first.
        </p>
      </div>
      <div className="flex flex-col gap-3">
        {exceptions.map((ex) => (
          <button
            key={ex.id}
            type="button"
            onClick={() => setSelectedId(ex.recordId)}
            className="flex items-center justify-between rounded-lg border bg-card p-4 text-left hover:bg-accent"
          >
            <div>
              <p className="text-sm font-medium">{ex.entity}</p>
              <p className="text-sm text-muted-foreground">{ex.field}</p>
            </div>
            <Badge variant={ex.status === "conflict" ? "secondary" : "outline"}>
              {ex.status === "conflict" ? "Conflict" : "Missing data"}
            </Badge>
          </button>
        ))}
      </div>
      {renderProductSection()}
    </div>
  );

  function renderProductSection() {
    if (!productExceptions || productExceptions.length === 0) return null;
    return (
      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-muted-foreground">Product-level</h2>
        {productExceptions.map((ex) => (
          <button
            key={ex.id}
            type="button"
            onClick={() =>
              setSelectedProduct({ date: ex.id.split(":")[0], product: ex.recordId })
            }
            className="flex items-center justify-between rounded-lg border bg-card p-4 text-left hover:bg-accent"
          >
            <div>
              <p className="text-sm font-medium">{ex.entity}</p>
              <p className="text-sm text-muted-foreground">
                {ex.sources.map((s) => `${s.source}: ${s.value ?? "no data"}`).join(" · ")}
              </p>
            </div>
            <Badge variant={ex.status === "conflict" ? "secondary" : "outline"}>
              {ex.status === "conflict" ? "Conflict" : "Missing data"}
            </Badge>
          </button>
        ))}
      </div>
    );
  }
}
