interface Props {
  readonly src: string;
  readonly alt: string;
  readonly credit: string;
  readonly href: string;
  readonly className?: string;
}

/** Editorial travel imagery: context only, never a source of transport data. */
export function GreekTravelImage({ src, alt, credit, href, className = '' }: Props) {
  return (
    <figure className={['od-travel-image', className].filter(Boolean).join(' ')}>
      <img src={src} alt={alt} loading="lazy" decoding="async" referrerPolicy="no-referrer" />
      <figcaption>
        <a href={href} target="_blank" rel="noreferrer" className="od-travel-image__credit">
          {credit} · Unsplash
        </a>
      </figcaption>
    </figure>
  );
}
