import type { CancellationQuote } from '../../api/bookingApi';

export default function CancellationBreakdown({ quote }: { quote: CancellationQuote }) {
  const fmt = new Intl.NumberFormat('en-IN', { style: 'currency', currency: quote.currency });
  const money = (cents: number) => fmt.format(cents / 100);
  const rows = [
    {
      name:
        quote.route === 'WALLET_CREDIT'
          ? 'Payment refunded to your Nirvana wallet'
          : 'Original payment method',
      fee: quote.feeCents,
      refund: quote.refundCents
    },
    {
      name: 'Nirvana wallet credit returned',
      fee: quote.walletFeeCents ?? 0,
      refund: quote.walletRefundCents ?? 0
    },
    {
      name: 'Gift card credit returned',
      fee: quote.giftCardFeeCents ?? 0,
      refund: quote.giftCardRefundCents ?? 0
    }
  ].filter((row) => row.refund > 0 || row.fee > 0);
  return (
    <div className="space-y-2">
      <p>Cancellation fee: {money(rows.reduce((sum, row) => sum + row.fee, 0))}</p>
      <p className="font-semibold">
        Expected refund: {money(rows.reduce((sum, row) => sum + row.refund, 0))}
      </p>
      {rows.length > 0 && (
        <ul className="space-y-1 text-sm">
          {rows.map((row) => (
            <li key={row.name}>
              {row.name}: {money(row.refund)}
              {row.fee > 0 && ` · ${money(row.fee)} fee`}
            </li>
          ))}
        </ul>
      )}
      {quote.packageSession && <p>Your package session will be returned.</p>}
    </div>
  );
}
