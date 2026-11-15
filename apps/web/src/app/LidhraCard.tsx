import { useI18n } from '../i18n/I18nProvider';

const APP_STORE_URL = 'https://apps.apple.com/app/lidhra-debrid-downloader/id6790952504';
const SITE_URL = 'https://lidhra.peterdsp.dev/';

/**
 * A quiet card in the footer for Lidhra, another app by the same maker.
 *
 * Every asset is served from this origin, so the content security policy holds,
 * and nothing here tracks or sets a cookie. The App Store badge is Apple's
 * artwork, unmodified, in the page language where Apple publishes one (there is
 * no Albanian badge, so Albanian shows the English one). The black badge is for
 * the light theme and the white one for the dark; CSS picks between them.
 */
export function LidhraCard() {
  const { t, language } = useI18n();
  const badgeLocale = language === 'el' ? 'el-gr' : 'en-us';
  const badgeAlt = t('app.lidhraBadgeAlt');
  return (
    <section className="lidhra-card od-lidhra" aria-labelledby="od-lidhra-title">
      <p className="od-lidhra__eyebrow">{t('app.lidhraEyebrow')}</p>
      <div className="od-lidhra__body">
        <img className="od-lidhra__icon" src="/lidhra/lidhra-icon.webp" width={56} height={56} alt={t('app.lidhraIconAlt')} />
        <div className="od-lidhra__text">
          <h2 className="od-lidhra__name" id="od-lidhra-title">
            Lidhra
          </h2>
          <p className="od-lidhra__line">{t('app.lidhraLine')}</p>
        </div>
      </div>
      <div className="od-lidhra__actions">
        <a className="od-lidhra__badge" href={APP_STORE_URL} target="_blank" rel="noopener">
          <img className="od-lidhra__badge-light" src={`/lidhra/badge-black-${badgeLocale}.svg`} height={40} alt={badgeAlt} />
          <img className="od-lidhra__badge-dark" src={`/lidhra/badge-white-${badgeLocale}.svg`} height={40} alt={badgeAlt} />
        </a>
        <a className="od-link" href={SITE_URL} target="_blank" rel="noopener">
          lidhra.peterdsp.dev
        </a>
      </div>
    </section>
  );
}
