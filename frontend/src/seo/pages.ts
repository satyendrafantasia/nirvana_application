export interface PageMeta {
  title: string;
  summary: string;
  index?: boolean;
}
export const pageMeta: Record<string, PageMeta> = {
  '/about': {
    title: 'About Nirvana Wellness',
    summary:
      'Learn how Nirvana Wellness helps people find spas, compare treatments and book appointments across India.',
    index: true
  },
  '/packages': {
    title: 'Spa packages and wellness passes',
    summary:
      'Compare wellness passes, included sessions, participating spas and package prices before choosing a plan.',
    index: true
  },
  '/contact': {
    title: 'Contact Nirvana Wellness',
    summary:
      'Contact our team about appointments, payments, refunds or privacy, or open a booking support case.',
    index: true
  },
  '/privacy': {
    title: 'Privacy policy',
    summary:
      'Read how Nirvana handles account, appointment and payment information and how to make a privacy request.',
    index: true
  },
  '/terms': {
    title: 'Booking terms',
    summary:
      'Review the terms for appointments, spa services, cancellations, packages and payments on Nirvana.',
    index: true
  },
  '/auth/login': {
    title: 'Customer sign in',
    summary:
      'Sign in to continue a booking, view your appointments and manage your wellness passes.'
  },
  '/auth/register': {
    title: 'Create a customer account',
    summary: 'Verify your email or phone to create your Nirvana Wellness account.'
  },
  '/auth/otp': {
    title: 'Sign in with a code',
    summary: 'Verify your identity to continue to your Nirvana account.'
  },
  '/auth/password': {
    title: 'Reset your password',
    summary: 'Recover access to your Nirvana Wellness account.'
  },
  '/auth/email-link': {
    title: 'Verify your email link',
    summary: 'Complete your secure Nirvana email verification.'
  },
  '/auth/social/callback': {
    title: 'Complete sign-in',
    summary: 'Finish connecting your sign-in provider to Nirvana.'
  },
  '/owner/login': {
    title: 'Spa owner sign in',
    summary: 'Sign in to manage your spa, team and appointment schedule.'
  },
  '/owner/signup': {
    title: 'Register as a spa owner',
    summary: 'Create an owner account to manage your spa on Nirvana.'
  },
  '/support': {
    title: 'Booking support',
    summary: 'Ask for help with a booking, a payment or a refund.'
  },
  '/spas': {
    title: 'Browse spas and treatments',
    summary: 'Find spas by city and compare their services, prices and availability.'
  },
  '/404': {
    title: 'Page not found',
    summary: 'Return to Nirvana Wellness or browse spas and treatments.'
  }
};
export const staticPagePaths = Object.keys(pageMeta).filter(
  (path) => path !== '/spas' && path !== '/404'
);
