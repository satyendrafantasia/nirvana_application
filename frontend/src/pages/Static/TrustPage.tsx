import { Link, useLocation } from 'react-router-dom';
import { deletionDate } from '../../api/accountApi';
import { TrackingChoice } from '../../telemetry';
export default function TrustPage({ kind }: { kind: 'privacy' | 'terms' }) {
  // Set by account settings right after a deletion request.
  const requested = (useLocation().state as { deletionDate?: string } | null)?.deletionDate;
  return (
    <article className="mx-auto max-w-2xl px-5 py-10 space-y-5">
      {kind === 'privacy' && requested && (
        <p role="status" className="card p-4">
          Your account will be deleted on {deletionDate(requested)}. You have been signed out on
          every device. To keep your account, sign in again before then.
        </p>
      )}
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
          <h2 id="your-rights">Your rights over your data</h2>
          <p>
            Under India&apos;s Digital Personal Data Protection Act, 2023 you can get a copy of
            your data and ask for it to be erased. Both are in account settings once you sign in.
          </p>
          <h3>Get a copy of your data</h3>
          <p>
            Choose Download my data to get a file with your profile, bookings, payment and refund
            summaries, invoices, packages, vouchers, reviews, consents and notification choices.
            The file is JSON, which you can open in a text editor or bring to another service.
          </p>
          <h3>Delete your account</h3>
          <p>
            Confirm with your password or a code sent to your verified mobile or email. You are
            then signed out on every device, and the account is deleted 7 days later. To change
            your mind, sign in again before then: that cancels the deletion.
          </p>
          <p>
            Deletion erases your name, email, phone number, photo, sign-in methods (such as Google),
            sessions, saved spas, notification preferences and messages you wrote. Your reviews
            stay without your name. Unused loyalty points, vouchers and package sessions are lost.
            Your email and phone number can then be used for a new account.
          </p>
          <p>
            Records of bookings, payments, invoices and refunds are kept, because tax and
            accounting law requires them, but they are no longer linked to your name or contact
            details. Invoices already issued are kept as issued for the period the law sets. We
            record that a deletion was asked for, cancelled or completed, without any of your
            details.
          </p>
          <p>
            You cannot delete your account while you have an upcoming booking or a refund in
            progress, or while you manage a spa that is still open. Wait until the booking is over
            or the refund is complete; spa owners first transfer each spa to another owner or close
            it.
          </p>
          <p>
            The operator must confirm other retention periods and a public privacy contact before
            launch.
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
