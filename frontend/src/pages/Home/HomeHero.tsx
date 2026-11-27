import { Link } from 'react-router-dom';

export default function HomeHero() {
  return (
    <section className="wellness-hero">
      <div className="hero-copy">
        <p className="eyebrow">
          <span /> YOUR EVERYDAY ESCAPE
        </p>
        <h1>
          A little pause.
          <br />
          <span>A better you.</span>
        </h1>
        <p className="hero-description">
          Make room for feeling good. Discover spa experiences, find your moment, and leave the
          everyday behind.
        </p>
        <Link to="/spas" className="hero-discover">
          Book an appointment <span aria-hidden="true">↗</span>
        </Link>
        <div className="hero-footnote">
          <span className="tiny-spark" aria-hidden="true">
            ✳
          </span>{' '}
          A little time for yourself changes everything.
        </div>
      </div>
      <div className="hero-art">
        <picture>
          <source
            type="image/webp"
            srcSet="/hero-spa-480.webp 480w, /hero-spa-800.webp 800w, /hero-spa.webp 1100w"
            sizes="(max-width: 1023px) 100vw, 560px"
          />
          <img
            className="hero-photo"
            src="/hero-spa-800.jpg"
            width="1100"
            height="1467"
            fetchPriority="high"
            srcSet="/hero-spa-480.jpg 480w, /hero-spa-800.jpg 800w, /hero-spa.jpg 1100w"
            sizes="(max-width: 1023px) 100vw, 560px"
            alt="A guest resting on a massage table during a hot stone treatment in a softly lit spa room"
          />
        </picture>
        <span className="art-topline">SPA &amp; MASSAGE, BOOKED IN MINUTES</span>
        <div className="art-caption">
          <span>
            Less rush.
            <br />
            More ritual.
          </span>
          <span className="art-star" aria-hidden="true">
            ✳
          </span>
        </div>
        <span className="art-number">NIRVANA / WELLNESS, REIMAGINED</span>
      </div>
    </section>
  );
}
