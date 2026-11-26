import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { bookingGstDocuments, GstDocumentView, openGstDocument } from '../../api/gstApi';
import Button from '../../components/common/Button';

/**
 * The booking's GST invoice, issued by the spa, as a printable page; once loaded, any credit notes
 * for refunds are listed beside it.
 */
export default function InvoiceLinks({ bookingId }: { bookingId: string }) {
  const [docs, setDocs] = useState<GstDocumentView[]>();
  const load = useMutation({
    mutationFn: async (open?: GstDocumentView) => {
      if (open) return openGstDocument(open);
      const found = await bookingGstDocuments(bookingId);
      setDocs(found);
      const invoice = found.find((d) => d.docType !== 'CREDIT_NOTE');
      if (!invoice) throw new Error('The invoice is issued once the booking is paid.');
      await openGstDocument(invoice);
    }
  });
  const notes = docs?.filter((d) => d.docType === 'CREDIT_NOTE') || [];
  return (
    <>
      <Button variant="secondary" disabled={load.isPending} onClick={() => load.mutate(undefined)}>
        View GST invoice
      </Button>
      {notes.map((n) => (
        <Button key={n.id} variant="secondary" disabled={load.isPending} onClick={() => load.mutate(n)}>
          Credit note {n.documentNumber}
        </Button>
      ))}
      {load.isError && (
        <span role="alert" className="text-sm text-rose-700">
          {errorMessage(load.error)}
        </span>
      )}
    </>
  );
}
