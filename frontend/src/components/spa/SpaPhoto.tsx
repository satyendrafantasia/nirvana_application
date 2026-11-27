import { useState, type ImgHTMLAttributes } from 'react';
import { responsiveImage } from '../../api/responsiveImage';

export default function SpaPhoto({ src, alt, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [failed, setFailed] = useState<string>();
  const available = src && src !== failed;
  const image = available ? responsiveImage(src) : { src: '/hero-spa-480.webp' };
  return (
    <img
      {...props}
      {...image}
      crossOrigin={image.srcSet ? 'anonymous' : undefined}
      alt={available ? alt : ''}
      onError={available ? () => setFailed(src) : undefined}
    />
  );
}
