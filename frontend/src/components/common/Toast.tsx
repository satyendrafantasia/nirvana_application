import { useEffect, useState } from 'react';
type ToastType = 'success' | 'error' | 'info';
interface Notice {
  message: string;
  type: ToastType;
}
const eventName = 'nirvana:toast';
export const showToast = (message: string, type: ToastType = 'info') => {
  window.dispatchEvent(new CustomEvent<Notice>(eventName, { detail: { message, type } }));
};
export default function Toast() {
  const [toast, setToast] = useState<Notice | null>(null);
  useEffect(() => {
    const receive = (event: Event) => setToast((event as CustomEvent<Notice>).detail);
    window.addEventListener(eventName, receive);
    return () => window.removeEventListener(eventName, receive);
  }, []);
  useEffect(() => {
    if (!toast || toast.type === 'error') return;
    const timer = window.setTimeout(() => setToast(null), 6500);
    return () => window.clearTimeout(timer);
  }, [toast]);
  return (
    <aside aria-label="Notifications" className="notice-stack">
      <div role="status" aria-atomic="true">
        {toast && toast.type !== 'error' && toast.message}
      </div>
      <div role="alert" aria-atomic="true">
        {toast?.type === 'error' && toast.message}
      </div>
      {toast && (
        <button type="button" aria-label="Dismiss notification" onClick={() => setToast(null)}>
          ×
        </button>
      )}
    </aside>
  );
}
