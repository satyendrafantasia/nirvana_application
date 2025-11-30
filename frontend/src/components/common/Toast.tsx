import { useEffect, useState } from 'react';

type ToastType = 'success' | 'error' | 'info';

interface ToastState {
  message: string;
  type: ToastType;
}

const eventName = 'nirvana:toast';

export const showToast = (message: string, type: ToastType = 'info') => {
  const event = new CustomEvent(eventName, { detail: { message, type } });
  window.dispatchEvent(event);
};

const Toast = () => {
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    const handler = (event: Event) => {
      const custom = event as CustomEvent<ToastState>;
      setToast(custom.detail);
      setTimeout(() => setToast(null), 3000);
    };

    window.addEventListener(eventName, handler);
    return () => window.removeEventListener(eventName, handler);
  }, []);

  if (!toast) return null;

  const colors: Record<ToastType, string> = {
    success: 'bg-emerald-500',
    error: 'bg-rose-500',
    info: 'bg-primary-600'
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      <div className={`${colors[toast.type]} text-white rounded-xl px-4 py-3 shadow-lg`}>{toast.message}</div>
    </div>
  );
};

export default Toast;
