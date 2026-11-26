import { Suspense } from 'react';
import PageHead from './seo/PageHead';
import AppRouter from './routes/AppRouter';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import Toast from './components/common/Toast';
import PendingReferral from './components/rewards/PendingReferral';

function App() {
  return (
    <div className="min-h-screen flex flex-col bg-[#f7f8f2]">
      <PageHead /><a className="sr-only focus:not-sr-only" href="#main">Skip to content</a>
      <Navbar />
      <main id="main" className="flex-1 min-w-0">
        <Suspense fallback={<p className="p-6" role="status">Loading page…</p>}><AppRouter /></Suspense>
      </main>
      <Footer />
      <Toast />
      <PendingReferral />
    </div>
  );
}

export default App;
