import { lazy } from 'react';
import TrustPage from '../pages/Static/TrustPage';
const Discovery = lazy(() => import('../pages/Discovery/Discovery'));
import { Route, Routes } from 'react-router-dom';
const PlacePage = lazy(() => import('../pages/Discovery/PlacePage'));
const Packages = lazy(() => import('../pages/Packages/Packages'));
const MyPackages = lazy(() => import('../pages/Packages/MyPackages'));
const FeaturePage = lazy(() => import('../features/FeaturePage'));
const PasswordReset = lazy(() => import('../pages/Auth/PasswordReset'));
const Media = lazy(() => import('../pages/Staff/Media'));
const CityDiscovery = lazy(() => import('../pages/Staff/CityDiscovery'));
const ListingClaims = lazy(() => import('../pages/Staff/ListingClaims'));
const SpaLeads = lazy(() => import('../pages/Staff/SpaLeads'));
const Home = lazy(() => import('../pages/Home/Home'));
const SpaSearch = lazy(() => import('../pages/SpaSearch/SpaSearch'));
const SpaDetails = lazy(() => import('../pages/SpaDetails/SpaDetails'));
const Booking = lazy(() => import('../pages/Booking/Booking'));
const Login = lazy(() => import('../pages/Auth/Login'));
const Register = lazy(() => import('../pages/Auth/Register'));
const OtpVerification = lazy(() => import('../pages/Auth/OtpVerification'));
const SocialLoginCallback = lazy(() => import('../pages/Auth/SocialLoginCallback'));
const MyBookings = lazy(() => import('../pages/Profile/MyBookings'));
const AccountSettings = lazy(() => import('../pages/Profile/AccountSettings'));
const OwnerPortal = lazy(() => import('../pages/Owner/OwnerPortal'));
const NotFound = lazy(() => import('../pages/Static/NotFound'));
import { RequireAuth } from '../components/layout/RequireAuth';

const AppRouter = () => {
  return (
    <Routes>
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
          <RequireAuth>
            <CityDiscovery />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/listing-claims"
        element={
          <RequireAuth>
            <ListingClaims />
          </RequireAuth>
        }
      />
      <Route
        path="/admin/spa-leads"
        element={
          <RequireAuth>
            <SpaLeads />
          </RequireAuth>
        }
      />
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
          <RequireAuth>
            <Media />
          </RequireAuth>
        }
      />
      <Route path="/auth/password" element={<PasswordReset />} />
      <Route
        path="/packages"
        element={
          <RequireAuth>
            <Packages />
          </RequireAuth>
        }
      />
      <Route
        path="/me/packages"
        element={
          <RequireAuth>
            <MyPackages />
          </RequireAuth>
        }
      />
      <Route path="/" element={<Home />} />
      <Route
        path="/spas"
        element={
          <RequireAuth>
            <SpaSearch />
          </RequireAuth>
        }
      />
      <Route
        path="/spas/:spaId"
        element={
          <RequireAuth>
            <SpaDetails />
          </RequireAuth>
        }
      />
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
      <Route path="/privacy" element={<TrustPage kind="privacy" />} />
      <Route path="/terms" element={<TrustPage kind="terms" />} />
      <Route path="/404" element={<NotFound />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default AppRouter;
