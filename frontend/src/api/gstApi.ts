import client from './client';

/** A GST document: the spa's invoice or credit note to a guest, or Nirvana's commission invoice to the spa. */
export interface GstDocumentView {
  id: number;
  docType: 'TAX_INVOICE' | 'BILL_OF_SUPPLY' | 'CREDIT_NOTE' | 'COMMISSION_INVOICE';
  title: string;
  documentNumber: string;
  issueDate: string;
  bookingId: number | null;
  subscriptionId: number | null;
  originalDocumentId: number | null;
  supplierName: string;
  supplierGstin: string | null;
  recipientName: string | null;
  description: string;
  currency: string;
  taxableCents: number;
  cgstCents: number;
  sgstCents: number;
  igstCents: number;
  totalCents: number;
  periodStart: string | null;
  periodEnd: string | null;
  htmlUrl: string;
}

/** The booking's invoice and any credit notes; asking issues whichever are due. */
export const bookingGstDocuments = async (bookingId: string) =>
  (await client.get<GstDocumentView[]>(`/api/bookings/${bookingId}/gst-documents`)).data;

export const spaGstDocuments = async (spa: string, from: string, to: string) =>
  (await client.get<GstDocumentView[]>(`/api/owner/spas/${spa}/gst-documents`, { params: { from, to } })).data;

/** Nirvana's commission invoice for a month that has ended; null when there was no commission. */
export const commissionInvoice = async (spa: string, month: string) => {
  const response = await client.post<GstDocumentView>(`/api/owner/spas/${spa}/commission-invoices/${month}`);
  return response.status === 204 ? null : response.data;
};

/**
 * Opens the document as a printable page in a new tab (the reader prints or saves it as a PDF). The
 * tab is opened before the download so pop-up blockers allow it; without one the page downloads.
 */
export const openGstDocument = async (doc: Pick<GstDocumentView, 'htmlUrl' | 'documentNumber'>) => {
  const tab = window.open('', '_blank');
  try {
    const response = await client.get<string>(doc.htmlUrl, { responseType: 'text' });
    const url = URL.createObjectURL(new Blob([response.data], { type: 'text/html' }));
    if (tab) {
      tab.location.href = url;
    } else {
      const link = document.createElement('a');
      link.href = url;
      link.download = `${doc.documentNumber.replaceAll('/', '-')}.html`;
      link.click();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error) {
    tab?.close();
    throw error;
  }
};

export const rupees = (cents: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(cents / 100);
