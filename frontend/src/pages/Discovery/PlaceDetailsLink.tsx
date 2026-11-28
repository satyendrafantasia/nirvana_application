import { createContext, useContext } from 'react';
import { Link, useLocation, type LinkProps } from 'react-router-dom';

export const RememberSearchLink = createContext<(link: HTMLAnchorElement) => void>(() => {});

/** Only the originating history entry's key goes into browser history. */
type PlaceDetailsLinkProps = Omit<LinkProps, 'to' | 'state' | 'onClick'> & { placeId: string };

export default function PlaceDetailsLink({ placeId, ...props }: PlaceDetailsLinkProps) {
  const origin = useLocation();
  const remember = useContext(RememberSearchLink);
  return (
    <Link
      {...props}
      to={'/discover/places/' + encodeURIComponent(placeId)}
      state={{ discoveryFrom: origin.key }}
      onClick={event => {
        if (event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) {
          remember(event.currentTarget);
        }
      }}
    />
  );
}
