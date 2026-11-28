import { useMutation } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { restartCheckoutReminders, stopCheckoutReminders } from '../../api/checkoutRecoveryApi';
import Button from '../../components/common/Button';

/** The unsubscribe page a checkout reminder links to. Works without signing in. */
export default function StopReminders() {
  const [params] = useSearchParams();
  const token = params.get('t') || '';
  const change = useMutation({
    mutationFn: (stop: boolean) => (stop ? stopCheckoutReminders(token) : restartCheckoutReminders(token))
  });
  const enabled = change.data?.remindersEnabled;
  return (
    <section className="mx-auto max-w-xl space-y-4 px-4 py-12">
      <h1 className="text-2xl font-semibold">Checkout reminders</h1>
      {enabled === false ? (
        <>
          <p role="status">Done. We won't remind you about bookings you didn't finish.</p>
          <Button variant="secondary" disabled={change.isPending} onClick={() => change.mutate(false)}>
            Turn them back on
          </Button>
        </>
      ) : enabled === true ? (
        <p role="status">Reminders are back on. We send at most one for a checkout, and never more than one a day.</p>
      ) : (
        <>
          <p>When you choose a time but don't finish paying, we send one short reminder. You can turn these off.</p>
          <Button disabled={!token || change.isPending} onClick={() => change.mutate(true)}>
            Stop checkout reminders
          </Button>
        </>
      )}
      {change.isError && <p role="alert" className="text-rose-700">{errorMessage(change.error)}</p>}
      <p>
        <Link className="underline" to="/spas">Browse spas</Link>
      </p>
    </section>
  );
}
