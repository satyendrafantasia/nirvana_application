import { useEffect, useId, useRef } from 'react';
import Button from './Button';
interface ModalProps {
  title: string;
  description?: string;
  isOpen: boolean;
  onClose: () => void;
  children?: React.ReactNode;
}
export default function Modal({ title, description, isOpen, onClose, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const id = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || !isOpen) return;
    dialog.showModal();
    return () => dialog.close();
  }, [isOpen]);
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      aria-describedby={description ? id + '-desc' : undefined}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="max-h-[85dvh] w-[min(32rem,calc(100%_-_2rem))] max-w-full overflow-auto rounded-2xl bg-white p-6 shadow-card backdrop:bg-slate-900/50"
    >
      {isOpen && (
        <>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 id={id} className="text-lg font-semibold">
                {title}
              </h2>
              {description && <p id={id + '-desc'}>{description}</p>}
            </div>
            <Button variant="ghost" onClick={onClose}>
              Close
            </Button>
          </div>
          <div className="mt-4">{children}</div>
        </>
      )}
    </dialog>
  );
}
