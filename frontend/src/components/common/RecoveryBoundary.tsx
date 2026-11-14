import { Component, ReactNode } from 'react';
export default class RecoveryBoundary extends Component<
  { children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="mx-auto max-w-lg p-8">
        <h1>We couldn’t load this page</h1>
        <p role="alert">If you were booking, check My bookings before trying again.</p>
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
