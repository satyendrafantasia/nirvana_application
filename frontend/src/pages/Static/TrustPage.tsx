import { Link } from 'react-router-dom';
import { TrackingChoice } from '../../telemetry';
export default function TrustPage({ kind }: { kind: 'privacy' | 'terms' }) {
  return (
    <article className="mx-auto max-w-2xl px-5 py-10 space-y-5">
      <h1 className="text-3xl">{kind === 'privacy' ? 'Privacy policy' : 'Booking terms'}</h1>
      <p>Draft for business and legal review.</p>
      {kind === 'privacy' ? (
        <>
          <h2>Accounts and appointments</h2>
          <p>
            Nirvana uses contact information to sign you in, verify requests and manage
            appointments. A booking can include the spa, service, time, therapist preference,
            payment status and notes you provide.
          </p>
          <h2>Your browser and service providers</h2>
          <p>
            Sign-in uses tokens in browser storage. Online checkout uses Razorpay. Google Places
            supplies discovery results and images. Choosing nearby search shares your selected
            coordinates with Nirvana and Google. Fonts and image providers receive browser requests
            when their resources load.
          </p>
          <p>
            Google Maps features are subject to the{' '}
            <a href="https://www.google.com/help/terms_maps/" className="underline">
              Google Maps terms
            </a>{' '}
            and{' '}
            <a href="https://policies.google.com/privacy" className="underline">
              Google Privacy Policy
            </a>
            .
          </p>
          <h2>Usage measurement</h2>
          <p>
            Optional measurement records booking steps when enabled and accepted. You can change
            your choice here.
          </p>
          <TrackingChoice />
          <h2>Account requests and retention</h2>
          <p>
            Use account settings to review your details or contact support about correction or
            deletion. The operator must confirm retention periods, applicable rights and a public
            privacy contact before launch.
          </p>
          <Link to="/me/settings" className="underline">
            Account settings
          </Link>
        </>
      ) : (
        <>
          <h2>Reservations</h2>
          <p>
            Select a service, date and available time. The server checks availability, eligibility
            and prices when you book. A temporary slot hold expires. Check your booking reference
            and status before travelling.
          </p>
          <h2>Payment and changes</h2>
          <p>
            A pending payment requires completion before the booking is confirmed. Use My bookings
            to resume payment, request an eligible cancellation or choose a new available time.
            Refund eligibility and any fees depend on the booking policy and payment route.
          </p>
          <h2>Your account</h2>
          <p>
            Provide accurate contact details, protect your sign-in information and check the service
            and total before submitting an appointment.
          </p>
          <p>
            The operator must approve the final cancellation, refund and dispute terms before
            launch.
          </p>
        </>
      )}
      <p>
        <Link to="/features/messages" className="underline">
          Contact booking support
        </Link>
      </p>
    </article>
  );
}
