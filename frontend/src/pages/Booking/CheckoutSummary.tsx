import { CheckoutPreview, checkoutLines, money } from '../../api/rewardsApi';

/** The price, line by line, in the order the server applies discounts, points and gift cards. */
export default function CheckoutSummary({ preview, payAtSpa }: { preview: CheckoutPreview; payAtSpa?: boolean }) {
  const lines = checkoutLines(preview);
  const last = lines[lines.length - 1];
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
        preview.giftCardCents > 0 && (
          <p className="mt-2 text-sm text-slate-600">
            {money(last.cents, preview.currency)} {payAtSpa ? 'to pay at the spa' : 'to pay now'}.
          </p>
        )
      )}
      {preview.giftCardCents > 0 && preview.giftCardBalanceCents > preview.giftCardCents && (
        <p className="mt-1 text-xs text-slate-500">
          {money(preview.giftCardBalanceCents - preview.giftCardCents, preview.currency)} stays on your gift card.
        </p>
      )}
      {preview.couponError && (
        <p role="alert" className="mt-2 text-sm text-rose-700">
          Coupon not applied: {preview.couponError}
        </p>
      )}
      {preview.giftCardError && (
        <p role="alert" className="mt-2 text-sm text-rose-700">
          Gift card not applied: {preview.giftCardError}
        </p>
      )}
    </section>
  );
}
