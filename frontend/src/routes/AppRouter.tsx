import { lazy, Suspense, useLayoutEffect, useRef } from 'react';
import { RememberSearchLink } from '../pages/Discovery/PlaceDetailsLink';
const Support = lazy(() => import('../pages/Support/Support'));
import TrustPage from '../pages/Static/TrustPage';
import { AboutPage, ContactPage } from '../pages/Static/CompanyPages';
const Discovery = lazy(() => import('../pages/Discovery/Discovery'));
import { matchPath, Route, Routes, useLocation, type Location } from 'react-router-dom';
const PlacePage = lazy(() => import('../pages/Discovery/PlacePage'));
const Packages = lazy(() => import('../pages/Packages/Packages'));
const MyPackages = lazy(() => import('../pages/Packages/MyPackages'));
const FeaturePage = lazy(() => import('../features/FeaturePage'));
const PasswordReset = lazy(() => import('../pages/Auth/PasswordReset'));
const Media = lazy(() => import('../pages/Staff/Media'));
const CityDiscovery = lazy(() => import('../pages/Staff/CityDiscovery'));
const ListingClaims = lazy(() => import('../pages/Staff/ListingClaims'));
const SpaLeads = lazy(() => import('../pages/Staff/SpaLeads'));
const AdminPayouts = lazy(() => import('../pages/Staff/AdminPayouts'));
const Home = lazy(() => import('../pages/Home/Home'));
const SpaSearch = lazy(() => import('../pages/SpaSearch/SpaSearch'));
const SpaDetails = lazy(() => import('../pages/SpaDetails/SpaDetails'));
const ClaimedStorefront = lazy(() => import('../pages/SpaDetails/SpaDetails').then(m => ({default: m.ClaimedStorefront})));
const Booking = lazy(() => import('../pages/Booking/Booking'));
const Login = lazy(() => import('../pages/Auth/Login'));
const Register = lazy(() => import('../pages/Auth/Register'));
const EmailLinkCallback = lazy(() => import('../pages/Auth/EmailLinkCallback'));
const OtpVerification = lazy(() => import('../pages/Auth/OtpVerification'));
const SocialLoginCallback = lazy(() => import('../pages/Auth/SocialLoginCallback'));
const MyBookings = lazy(() => import('../pages/Profile/MyBookings'));
const AccountSettings = lazy(() => import('../pages/Profile/AccountSettings'));
const OwnerPortal = lazy(() => import('../pages/Owner/OwnerPortal'));
const NotFound = lazy(() => import('../pages/Static/NotFound'));
const SpaBookingReply = lazy(() => import('../pages/SpaBookingReply/SpaBookingReply'));
const GiftCards = lazy(() => import('../pages/GiftCards/GiftCards'));
const GiftCardsAdmin = lazy(() => import('../pages/Staff/GiftCardsAdmin'));
const ResumeCheckout = lazy(() => import('../pages/Booking/ResumeCheckout'));
const StopReminders = lazy(() => import('../pages/Booking/StopReminders'));
const CheckoutRecoveryStats = lazy(() => import('../pages/Owner/CheckoutRecoveryStats'));
import { isAdmin, isStaff } from '../auth/roles';
import { RequireAuth } from '../components/layout/RequireAuth';

const AppRouter = () => {
  const location = useLocation();
  const search = useRef<{
    location: Location; scrollY: number; link: HTMLAnchorElement; restoration: ScrollRestoration;
  }>();
  const origin = search.current;
  const showingDetails = !!origin && location.state?.discoveryFrom === origin.location.key &&
    !!matchPath('/discover/places/:placeId', location.pathname);
  const wasShowingDetails = useRef(false);
  useLayoutEffect(() => {
    let frame: number | undefined;
    if (!showingDetails && origin) {
      if (location.key !== origin.location.key) {
        window.history.scrollRestoration = origin.restoration;
        search.current = undefined;
      } else if (wasShowingDetails.current) {
        origin.link.focus({ preventScroll: true });
        const restorePosition = () => window.scrollTo({ top: origin.scrollY, behavior: 'instant' });
        restorePosition();
        frame = window.requestAnimationFrame(restorePosition);
      }
    }
    wasShowingDetails.current = showingDetails;
    return () => { if (frame !== undefined) window.cancelAnimationFrame(frame); };
  }, [location.key, origin, showingDetails]);

  // Keep the search mounted for this detail visit. Google results stay in component memory.
  return (
    <RememberSearchLink.Provider value={link => {
      const restoration = search.current?.restoration ?? window.history.scrollRestoration;
      // The browser cannot restore scroll against the hidden search DOM during a history traversal.
      window.history.scrollRestoration = 'manual';
      search.current = { location, scrollY: window.scrollY, link, restoration };
    }}>
      <div hidden={showingDetails}>
        <PageRoutes location={showingDetails ? origin.location : location} />
      </div>
      {showingDetails && (
        <Suspense fallback={<p role="status">Loading spa details…</p>}>
          <Routes>
            <Route path="/discover/places/:placeId" element={<RequireAuth><PlacePage fromSearch /></RequireAuth>} />
          </Routes>
        </Suspense>
      )}
    </RememberSearchLink.Provider>
  );
};

function PageRoutes({ location }: { location: Location }) {
  return (
    <Routes location={location}>
      <Route path="/discover/places/:placeId" element={<RequireAuth><PlacePage /></RequireAuth>} />
      <Route
        path="/discover"
        element={
          <RequireAuth>
            <Discovery />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/city-discovery"
        element={
          <RequireAuth allowed={isAdmin}>
            <CityDiscovery />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/listing-claims"
        element={
          <RequireAuth allowed={isAdmin}>
            <ListingClaims />
          </RequireAuth>
        }
      />
      <Route
        path="/gift-cards"
        element={
          <RequireAuth>
            <GiftCards />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/gift-cards"
        element={
          <RequireAuth allowed={isAdmin}>
            <GiftCardsAdmin />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/spa-leads"
        element={
          <RequireAuth allowed={isAdmin}>
            <SpaLeads />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/payouts"
        element={
          <RequireAuth allowed={isAdmin}>
            <AdminPayouts />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/checkout-recovery"
        element={
          <RequireAuth allowed={isAdmin}>
            <div className="mx-auto max-w-5xl px-4 py-8"><CheckoutRecoveryStats /></div>
          </RequireAuth>
        }
      />
      {/* From a checkout reminder: the resume link needs the guest's account, the unsubscribe link doesn't. */}
      <Route path="/booking/resume/:token" element={<RequireAuth redirectTo="/auth/login"><ResumeCheckout /></RequireAuth>} />
      <Route path="/booking/stop-reminders" element={<StopReminders />} />
      <Route path="/owner/booking-alerts/:bookingId" element={
        <RequireAuth redirectTo="/owner/login"><SpaBookingReply owner /></RequireAuth>
      } />
      <Route path="/owner/login" element={<Login owner />} />
      <Route path="/owner/signup" element={<Register owner />} />
      <Route
        path="/owner/*"
        element={
          <RequireAuth redirectTo="/owner/login">
            <OwnerPortal />
          </RequireAuth>
        }
      />
      <Route
        path="/features/:feature"
        element={
          <RequireAuth>
            <FeaturePage />
          </RequireAuth>
        }
      />
      <Route
        path="/staff/media"
        element={
          <RequireAuth allowed={isStaff}>
            <Media />
          </RequireAuth>
        }
      />
      <Route path="/auth/password" element={<PasswordReset />} />
      {/* Anyone can browse packages; buying asks for sign-in. */}
      <Route path="/packages" element={<Packages />} />
      <Route
        path="/me/packages"
        element={
          <RequireAuth>
            <MyPackages />
          </RequireAuth>
        }
      />
      <Route path="/" element={<Home />} />
      {/* Anyone can browse spas; booking and buying ask them to sign in. */}
      <Route path="/spas" element={<SpaSearch />} />
      <Route path="/spas/:spaId" element={<SpaDetails />} />
      <Route path="/s/:spaId/:slug?" element={<ClaimedStorefront />} />
      {/* A spa answers its booking SMS here, from a private link, without an account. */}
      <Route path="/b/:token" element={<SpaBookingReply />} />
      <Route
        path="/booking/*"
        element={
          <RequireAuth redirectTo="/auth/login">
            <Booking />
          </RequireAuth>
        }
      />
      <Route path="/auth/login" element={<Login />} />
      <Route path="/auth/register" element={<Register />} />
      <Route path="/auth/email-link" element={<EmailLinkCallback />} />
      <Route path="/auth/otp" element={<OtpVerification />} />
      <Route path="/auth/social/callback" element={<SocialLoginCallback />} />
      <Route
        path="/me/bookings"
        element={
          <RequireAuth>
            <MyBookings />
          </RequireAuth>
        }
      />
      <Route
        path="/me/settings"
        element={
          <RequireAuth>
            <AccountSettings />
          </RequireAuth>
        }
      />
      <Route path="/support" element={<Support />} />
      <Route path="/admin/support" element={<RequireAuth allowed={isAdmin}><Support staff /></RequireAuth>} />
      <Route path="/about" element={<AboutPage />} />
      <Route path="/contact" element={<ContactPage />} />
      <Route path="/privacy" element={<TrustPage kind="privacy" />} />
      <Route path="/terms" element={<TrustPage kind="terms" />} />
      <Route path="/404" element={<NotFound />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

export default AppRouter;
