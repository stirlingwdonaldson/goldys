"use client";

import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { CircleAlert, CircleCheck, Info, X } from "lucide-react";
import { cn } from "@/lib/utils";

type ToastTone = "info" | "error" | "success";

interface ToastInput {
  title: string;
  description?: string;
  tone?: ToastTone;
}

interface Toast extends ToastInput {
  id: number;
}

interface ToastContextValue {
  toast: (input: ToastInput) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within a ToastProvider");
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback<ToastContextValue["toast"]>(
    ({ title, description, tone = "info" }) => {
      const id = nextId.current++;
      setToasts((prev) => [...prev, { id, title, description, tone }]);
      setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div
        className="fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2"
        aria-live="polite"
      >
        {toasts.map((t) => (
          <ToastCard key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: () => void }) {
  const Icon = toast.tone === "error" ? CircleAlert : toast.tone === "success" ? CircleCheck : Info;
  return (
    <div role="status" className="flex items-start gap-3 rounded-lg border bg-background p-4 shadow-sm">
      <Icon
        className={cn("mt-0.5 h-4 w-4 shrink-0", {
          "text-destructive": toast.tone === "error",
          "text-status-success": toast.tone === "success",
          "text-muted-foreground": toast.tone === "info",
        })}
        aria-hidden="true"
      />
      <div className="flex-1">
        <p className="text-sm font-medium">{toast.title}</p>
        {toast.description ? (
          <p className="mt-0.5 text-sm text-muted-foreground">{toast.description}</p>
        ) : null}
      </div>
      <button
        type="button"
        onClick={onDismiss}
        className="rounded p-1 text-muted-foreground hover:text-foreground"
        aria-label="Dismiss"
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
