import { Component, ErrorInfo, ReactNode } from 'react';
import { reportError } from '../../monitoring';
export default class RecoveryBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch(error: Error, info: ErrorInfo) {
    reportError(error, info.componentStack ?? undefined);
  }
  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="mx-auto max-w-lg p-8">
        <h1>We couldn’t load this page</h1>
        <p role="alert">If you were booking, check My bookings before trying again.</p>
        <button className="underline" type="button" onClick={() => window.location.reload()}>
          Try again
        </button>{' '}
        ·{' '}
        <a className="underline" href="/me/bookings">
          My bookings
        </a>{' '}
        ·{' '}
        <a className="underline" href="/">
          Return home
        </a>
      </main>
    );
  }
}
