const supportEmail = import.meta.env.VITE_SUPPORT_EMAIL?.trim() || 'contact@nirvana-spa.in';

export function ProductAttribution() {
  return (
    <p>
      <span className="font-semibold text-primary-700">Nirvana Wellness</span> - a product of Bluemantrix Technologies Pvt Ltd
    </p>
  );
}

export function SupportEmailLink() {
  return <a className="underline" href={`mailto:${supportEmail}`}>{supportEmail}</a>;
}
