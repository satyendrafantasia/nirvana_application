import { Link } from 'react-router-dom';
import Button from '../../components/common/Button';
export default function NotFound() {
  return (
    <section className="error-page">
      <div className="error-card">
        <p className="eyebrow">404</p>
        <h1>Page not found</h1>
        <p>This link may have moved. You can return home or find a spa near you.</p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Button as={Link} to="/">
            Go home
          </Button>
          <Button as={Link} to="/spas" variant="secondary">
            Explore services
          </Button>
        </div>
      </div>
    </section>
  );
}
