import AppRouter from './routes/AppRouter';
import Navbar from './components/layout/Navbar';
import Footer from './components/layout/Footer';
import Toast from './components/common/Toast';

function App() {
  return (
    <div className="min-h-screen flex flex-col bg-[#f7f8f2]">
      <Navbar />
      <main className="flex-1">
        <AppRouter />
      </main>
      <Footer />
      <Toast />
    </div>
  );
}

export default App;
