import { CheckoutPreview, checkoutLines, money } from '../../api/rewardsApi';

/** The price, line by line, in the order the server applies discounts, points and gift cards. */
export default function CheckoutSummary({ preview, payAtSpa, wallet = false }: { preview: CheckoutPreview; payAtSpa?: boolean; wallet?: boolean }) {
  const lines = checkoutLines(preview);
  const last = lines[lines.length - 1];
  const used = wallet ? preview.walletCents ?? 0 : preview.giftCardCents;
  const balance = wallet ? preview.walletBalanceCents ?? 0 : preview.giftCardBalanceCents;
  return (
    <section aria-label="Price summary" className="rounded-xl border border-slate-200 bg-white p-4">
      <dl className="space-y-1 text-sm">
        {lines.map((line, i) => (
          <div
            key={line.label + i}
            className={`flex justify-between ${line.kind === 'total' ? 'border-t border-slate-200 pt-1 font-semibold' : ''} ${
              line.kind === 'discount' || line.kind === 'payment' ? 'text-emerald-700' : ''
            }`}
          >
            <dt>{line.label}</dt>
            <dd>{line.cents < 0 ? `−${money(-line.cents, preview.currency)}` : money(line.cents, preview.currency)}</dd>
          </div>
        ))}
      </dl>
      {last.cents === 0 ? (
        <p className="mt-2 text-sm font-medium" role="status">
          Nothing left to pay. Your appointment is confirmed when you book.
        </p>
      ) : (
        used > 0 && (
          <p className="mt-2 text-sm text-slate-600">
            {money(last.cents, preview.currency)} {payAtSpa ? 'to pay at the spa' : 'to pay now'}.
          </p>
        )
      )}
      {used > 0 && balance > used && (
        <p className="mt-1 text-xs text-slate-500">
          {money(balance - used, preview.currency)} {wallet ? 'stays in your wallet.' : 'stays on your gift card.'}
        </p>
      )}
      {preview.couponError && (
        <p role="alert" className="mt-2 text-sm text-rose-700">
          Coupon not applied: {preview.couponError}
        </p>
      )}
      {(preview.giftCardError || preview.walletError) && (
        <p role="alert" className="mt-2 text-sm text-rose-700">
          {wallet ? 'Wallet credit' : 'Gift card'} not applied: {preview.walletError || preview.giftCardError}
        </p>
      )}
    </section>
  );
}
