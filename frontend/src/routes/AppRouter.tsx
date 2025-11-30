import { Navigate, Route, Routes } from 'react-router-dom';
import Home from '../pages/Home/Home';
import SpaSearch from '../pages/SpaSearch/SpaSearch';
import SpaDetails from '../pages/SpaDetails/SpaDetails';
import Booking from '../pages/Booking/Booking';
import Login from '../pages/Auth/Login';
import Register from '../pages/Auth/Register';
import OtpVerification from '../pages/Auth/OtpVerification';
import SocialLoginCallback from '../pages/Auth/SocialLoginCallback';
import MyBookings from '../pages/Profile/MyBookings';
import AccountSettings from '../pages/Profile/AccountSettings';
import NotFound from '../pages/Static/NotFound';
import { RequireAuth } from '../components/layout/RequireAuth';

const AppRouter = () => {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/spas" element={<SpaSearch />} />
      <Route path="/spas/:spaId" element={<SpaDetails />} />
      <Route
        path="/booking/*"
        element={
          <RequireAuth redirectTo="/auth/login?redirect=/booking">
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
      <Route path="/404" element={<NotFound />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
};

export default AppRouter;
