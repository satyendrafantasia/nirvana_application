import { Suspense } from 'react';
import { useLocation } from 'react-router-dom';
import HomeHero from './pages/Home/HomeHero';
import PageHead from './seo/PageHead';
import AppRouter from './routes/AppRouter';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import Toast from './components/common/Toast';
import PendingReferral from './components/rewards/PendingReferral';

function App() {
  const { pathname } = useLocation();
  return (
    <div className="min-h-screen flex flex-col bg-[#f7f8f2]">
      <PageHead /><a className="sr-only focus:not-sr-only" href="#main">Skip to content</a>
      <Navbar />
      <main id="main" className="flex-1 min-w-0">
        <Suspense fallback={pathname === '/' ? (
          <div className="home-shell mx-auto max-w-6xl px-4 py-10"><HomeHero /></div>
        ) : <p className="min-h-screen p-6" role="status">Loading page…</p>}>
          <AppRouter />
        </Suspense>
      </main>
      <Footer />
      <Toast />
      <PendingReferral />
    </div>
  );
}

export default App;
