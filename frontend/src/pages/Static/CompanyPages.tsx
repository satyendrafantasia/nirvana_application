import { Link } from 'react-router-dom';
import { ProductAttribution, SupportEmailLink } from '../../components/common/CompanyInfo';

export function AboutPage() {
  return (
    <article className="mx-auto max-w-2xl space-y-5 px-5 py-10">
      <h1 className="text-3xl">About Nirvana Wellness</h1>
      <ProductAttribution />
      <p>
        Find spas by city, name or treatment. At spas that take bookings on Nirvana, you can
        check services and prices, choose an available appointment and manage it in My Bookings.
      </p>
      <Link to="/spas" className="underline">Browse spas</Link>
    </article>
  );
}

export function ContactPage() {
  return (
    <article className="mx-auto max-w-2xl space-y-5 px-5 py-10">
      <h1 className="text-3xl">Contact us</h1>
      <ProductAttribution />
      <section className="space-y-2">
        <h2 className="text-xl font-semibold">General and privacy enquiries</h2>
        <p>Email us at <SupportEmailLink />.</p>
      </section>
      <section className="space-y-2">
        <h2 className="text-xl font-semibold">Help with a booking</h2>
        <p>For help with an appointment, payment or refund, open a support case. Include your booking reference if you have one.</p>
        <Link to="/support" className="inline-block underline">Contact booking support</Link>
      </section>
    </article>
  );
}
