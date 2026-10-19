import { Link } from 'react-router-dom';
export default function SocialLoginCallback() {
  return (
    <div className="p-10">
      <p>Social sign-in is not configured for this frontend deployment.</p>
      <Link to="/auth/login">Sign in with your account</Link>
    </div>
  );
}
