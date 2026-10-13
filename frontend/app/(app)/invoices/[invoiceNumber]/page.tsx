"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Link2 } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import {
  InvoiceDetailBody,
  InvoicePdfActions,
  useCopyInvoiceLink,
  useInvoiceData,
} from "@/components/invoices/invoice-detail";

/**
 * One invoice on its own page: summary, flags and lines on the left, the PDF alongside on wide
 * screens so lines can be checked against the supplier's document. Reached from the list's
 * slide-over ("Expand") or a copied link.
 */
export default function InvoicePage() {
  const params = useParams<{ invoiceNumber: string }>();
  const invoiceNumber = decodeURIComponent(params.invoiceNumber);
  const copyLink = useCopyInvoiceLink(invoiceNumber);
  const data = useInvoiceData(invoiceNumber);
  const invoice = data.detail.data;


  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 self-start text-muted-foreground">
        <Link href="/invoices">
          <ArrowLeft className="mr-1.5 h-4 w-4" aria-hidden="true" />
          Invoices
        </Link>
      </Button>
      <PageHeader
        title={`Invoice ${invoiceNumber}`}
        description={invoice?.supplierName}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={copyLink}>
              <Link2 className="mr-1.5 h-4 w-4" aria-hidden="true" />
              Copy link
            </Button>
            {invoice ? <InvoicePdfActions invoice={invoice} /> : null}
          </>
        }
      />
      <InvoiceDetailBody data={data} layout="page" />
    </div>
  );
}
