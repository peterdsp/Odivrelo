interface Props {
  readonly src: string;
  readonly alt: string;
  /** The picture's own title, as its source records it. */
  readonly title: string;
  /** The person who made it, as the licence requires them to be named. */
  readonly creator: string;
  /** The licence's short name, for example "CC BY-SA 4.0". */
  readonly licence: string;
  /** The licence deed. */
  readonly licenceHref: string;
  /** The collection it came from, for example "Wikimedia Commons". */
  readonly source: string;
  /** The picture's page at that collection. */
  readonly href: string;
  readonly className?: string;
}

/**
 * Editorial travel imagery: context only, never a source of transport data.
 *
 * The credit is built from the picture's own record rather than written by
 * hand. This component used to append the words "· Unsplash" to every caption,
 * which became a false statement the moment a picture came from somewhere else:
 * these are Wikimedia Commons photographs under CC BY and CC BY-SA, and those
 * licences require the title, the creator and the licence to be named. None of
 * them shows Odivrelo's own work, and none is altered here.
 */
export function GreekTravelImage({
  src,
  alt,
  title,
  creator,
  licence,
  licenceHref,
  source,
  href,
  className = '',
}: Props) {
  return (
    <figure className={['od-travel-image', className].filter(Boolean).join(' ')}>
      <img src={src} alt={alt} loading="lazy" decoding="async" referrerPolicy="no-referrer" />
      <figcaption className="od-travel-image__credit">
        <a href={href} target="_blank" rel="noreferrer">
          {title}
        </a>
        {' · '}
        {creator}
        {' · '}
        <a href={licenceHref} target="_blank" rel="noreferrer">
          {licence}
        </a>
        {' · '}
        {source}
      </figcaption>
    </figure>
  );
}
