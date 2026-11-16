import { useI18n } from '../i18n/I18nProvider';

// ct is Apple's campaign token, so App Analytics shows which site a download came from.
const APP_STORE_URL = 'https://apps.apple.com/app/lidhra-debrid-downloader/id6790952504?ct=odivrelo&mt=8';
const SITE_URL = 'https://lidhra.peterdsp.dev/';

/** Rides shorter than this do not get the card: a long ride is when there is time to download something to watch. */
export const LIDHRA_MIN_RIDE_MINUTES = 90;

/**
 * A card for Lidhra, another app by the same maker, shown on a journey once the
 * traveller has seen how long the ride is. It is content, not chrome: a solid
 * surface, no blur, no shadow. The two Lidhra screens (transfers on the Mac, the Add a transfer sheet on iPhone) sit at fixed aspect ratios,
 * so loading them never moves the page.
 *
 * Every asset is served from this origin, so the content security policy holds,
 * and nothing here tracks or sets a cookie. The App Store badge is Apple's
 * artwork, unmodified, in the page language where Apple publishes one (there is
 * no Albanian badge, so Albanian shows the English one). The black badge is for
 * the light theme and the white one for the dark; CSS picks between them.
 */
export function LidhraCard({ headingLevel = 2 }: { headingLevel?: 2 | 3 }) {
  const { t, language } = useI18n();
  const badgeLocale = language === 'el' ? 'el-gr' : 'en-us';
  const badgeAlt = t('app.lidhraBadgeAlt');
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <section className="od-lidhra" aria-labelledby="od-lidhra-title">
      <div className="od-lidhra__inner">
        <div className="od-lidhra__copy">
          <Heading className="od-lidhra__title" id="od-lidhra-title">
            {t('app.lidhraTitle')}
          </Heading>
          <p className="od-lidhra__line">{t('app.lidhraLine')}</p>
          <div className="od-lidhra__facts">
            <img
              className="od-lidhra__icon"
              src="/lidhra/lidhra-icon.webp"
              width={44}
              height={44}
              loading="lazy"
              decoding="async"
              alt={t('app.lidhraIconAlt')}
            />
            <p>
              <strong>{t('app.lidhraPlatforms')}</strong>
              <br />
              {t('app.lidhraPrice')}
            </p>
          </div>
          <div className="od-lidhra__actions">
            <a className="od-lidhra__badge" href={APP_STORE_URL} target="_blank" rel="noopener">
              <img className="od-lidhra__badge-light" src={`/lidhra/badge-black-${badgeLocale}.svg`} width={144} height={48} loading="lazy" decoding="async" alt={badgeAlt} />
              <img className="od-lidhra__badge-dark" src={`/lidhra/badge-white-${badgeLocale}.svg`} width={144} height={48} loading="lazy" decoding="async" alt={badgeAlt} />
            </a>
            <a className="od-link" href={SITE_URL} target="_blank" rel="noopener">
              {t('app.lidhraMore')}
            </a>
          </div>
        </div>
        <figure className="od-lidhra__shots">
          <img
            className="od-lidhra__library"
            src="/lidhra/lidhra-transfers-1200.webp"
            srcSet="/lidhra/lidhra-transfers-640.webp 640w, /lidhra/lidhra-transfers-1200.webp 1200w"
            sizes="(min-width: 900px) 420px, 92vw"
            width={1200}
            height={600}
            loading="lazy"
            decoding="async"
            alt={t('app.lidhraLibraryAlt')}
          />
          <img
            className="od-lidhra__phone"
            src="/lidhra/lidhra-add-360.webp"
            srcSet="/lidhra/lidhra-add-240.webp 240w, /lidhra/lidhra-add-360.webp 360w"
            sizes="(min-width: 900px) 120px, 26vw"
            width={360}
            height={570}
            loading="lazy"
            decoding="async"
            alt={t('app.lidhraPhoneAlt')}
          />
        </figure>
      </div>
    </section>
  );
}
