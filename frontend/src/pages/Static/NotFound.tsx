import { Link } from 'react-router-dom';
import Button from '../../components/common/Button';

const NotFound = () => {
  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-4xl font-bold text-slate-900">Page not found</h1>
      <p className="max-w-xl text-slate-600">The page you're looking for may have moved or no longer exists.</p>
      <Button as={Link} to="/">
        Go home
      </Button>
    </div>
  );
};

export default NotFound;
