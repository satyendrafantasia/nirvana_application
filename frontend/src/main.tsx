import React from 'react';
import RecoveryBoundary from './components/common/RecoveryBoundary';
import ReactDOM from 'react-dom/client';
import { showToast } from './components/common/Toast';
import { errorMessage } from './api/client';
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';
import { AuthProvider } from './contexts/AuthContext';
import { BookingProvider } from './contexts/BookingContext';
import { startMonitoring } from './monitoring';

startMonitoring();

const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onError(error, _variables, _context, mutation) {
      if (!mutation.options.onError) showToast(errorMessage(error), 'error');
    }
  })
});

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <RecoveryBoundary><QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <BookingProvider>
            <App />
          </BookingProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider></RecoveryBoundary>
  </React.StrictMode>
);
