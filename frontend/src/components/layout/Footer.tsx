const Footer = () => {
  return (
    <footer className="border-t border-gray-100 bg-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-6 text-sm text-slate-600 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="font-semibold text-primary-700">Nirvana Wellness</p>
          <p className="text-slate-500">Book spa experiences with a Fresha-inspired interface.</p>
        </div>
        <div className="flex gap-4">
          <a href="/">Privacy</a>
          <a href="/">Terms</a>
          <a href="/">Support</a>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
