import { useCallback, useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { Badge, Button, Card, Fact, FactList, Section, TextField } from '../components/primitives';
import { StateBlock } from '../components/states';
import { useAnnouncer } from '../components/Announcer';
import { formatBytes } from '../lib/digest';
import { deleteWalletItem, listWalletItems, putWalletItem, StorageFullError, type WalletItem } from '../lib/db';
import {
  ACCEPT_ATTRIBUTE,
  MAX_WALLET_BYTES,
  newWalletItemId,
  safeFileName,
  TYPE_LABEL,
  validateWalletFile,
  type AcceptedType,
} from '../features/walletFiles';

/**
 * The travel wallet.
 *
 * Rendering is the careful part. A PDF is shown in a sandboxed iframe from an
 * object URL, with scripts, plugins, forms and top-level navigation all denied, so
 * nothing embedded in a ticket can run or reach the rest of the app. An image is
 * shown as an `<img>`, which cannot execute anything at all. Nothing is ever
 * injected as markup: there is no `innerHTML` anywhere in this app, and the lint
 * configuration fails the build if one appears.
 *
 * The object URL is revoked as soon as the viewer closes, so the bytes are not
 * left addressable, and the URL is never navigated to or put in the address bar.
 */
export function Wallet() {
  const { t, language, formatDateTime } = useI18n();
  const { storageAvailable } = useData();
  const dataMode = useDataMode();
  const { announce, alert } = useAnnouncer();

  const [items, setItems] = useState<WalletItem[] | null>(null);
  const [label, setLabel] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewing, setViewing] = useState<{ item: WalletItem; url: string } | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);
  const viewerRef = useRef<HTMLDivElement | null>(null);

  useHead({ title: t('meta.wallet.title'), description: t('wallet.intro'), path: '/wallet', language, dataMode });

  const refresh = useCallback(async () => {
    setItems(await listWalletItems());
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // The object URL must not outlive the viewer.
  useEffect(() => {
    return () => {
      if (viewing) URL.revokeObjectURL(viewing.url);
    };
  }, [viewing]);

  useEffect(() => {
    if (viewing) viewerRef.current?.focus();
  }, [viewing]);

  const onPick = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    setBusy(true);
    try {
      const verdict = await validateWalletFile(file);
      if (!verdict.ok) {
        const reason = verdict.reason;
        const message =
          reason.kind === 'type'
            ? t('wallet.rejectedType')
            : reason.kind === 'empty'
              ? t('wallet.rejectedEmpty')
              : reason.kind === 'size'
                ? t('wallet.rejectedSize', {
                    size: formatBytes(reason.bytes, language),
                    max: formatBytes(MAX_WALLET_BYTES, language),
                  })
                : t('wallet.rejectedContent', { type: TYPE_LABEL[reason.declared] });
        setError(message);
        alert(message);
        return;
      }
      const bytes = await file.arrayBuffer();
      const item: WalletItem = {
        id: newWalletItemId(),
        fileName: safeFileName(file.name),
        // The sniffed type, not the one the picker claimed, so what is stored is
        // what was actually verified.
        mediaType: verdict.type,
        bytes: file.size,
        data: bytes,
        importedAt: new Date().toISOString(),
        label: label.trim().slice(0, 120),
      };
      await putWalletItem(item);
      setLabel('');
      announce(t('wallet.imported', { when: formatDateTime(item.importedAt) }));
      await refresh();
    } catch (cause) {
      const message = cause instanceof StorageFullError ? t('offline.storageFullBody') : t('wallet.storageUnavailable');
      setError(message);
      alert(message);
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  /** Builds a Blob from the stored bytes, tagged with the type that was verified. */
  const blobOf = (item: WalletItem) => new Blob([item.data], { type: item.mediaType });

  const open = (item: WalletItem) => {
    if (viewing) URL.revokeObjectURL(viewing.url);
    setViewing({ item, url: URL.createObjectURL(blobOf(item)) });
  };

  const close = () => {
    if (viewing) URL.revokeObjectURL(viewing.url);
    setViewing(null);
  };

  const remove = async (item: WalletItem) => {
    // A real confirmation, because this is irreversible and there is no copy.
    if (!globalThis.confirm(t('wallet.deleteConfirm'))) return;
    if (viewing?.item.id === item.id) close();
    await deleteWalletItem(item.id);
    announce(t('wallet.deleted'));
    await refresh();
  };

  return (
    <div className="od-page od-page--narrow">
      <h1 className="od-page__title">{t('wallet.title')}</h1>
      <p className="od-page__lede">{t('wallet.intro')}</p>

      <Card tone="muted">
        <h2 className="od-card__title">{t('wallet.privacyTitle')}</h2>
        <ul className="od-list od-list--check">
          <li>{t('wallet.privacy1')}</li>
          <li>{t('wallet.privacy2')}</li>
          <li>{t('wallet.privacy3')}</li>
        </ul>
        <p className="od-notice od-notice--warning">{t('wallet.privacy4')}</p>
      </Card>

      {!storageAvailable ? (
        <StateBlock
          kind="unavailable"
          title={t('wallet.storageUnavailable')}
          body={<p>{t('offline.storageUnavailableBody')}</p>}
        />
      ) : (
        <Section title={t('wallet.import')} level={2} description={t('wallet.importHelp', { max: formatBytes(MAX_WALLET_BYTES, language) })}>
          <TextField
            id="wallet-label"
            label={`${t('wallet.label')} (${t('app.optional')})`}
            placeholder={t('wallet.labelPlaceholder')}
            value={label}
            maxLength={120}
            onChange={(event) => setLabel(event.target.value)}
          />
          <div className="od-field">
            <label className="od-field__label" htmlFor="wallet-file">
              {t('wallet.import')}
            </label>
            <input
              id="wallet-file"
              ref={fileRef}
              className="od-input od-input--file"
              type="file"
              accept={ACCEPT_ATTRIBUTE}
              disabled={busy}
              aria-describedby="wallet-file-hint"
              onChange={(event) => void onPick(event.target.files?.[0])}
            />
            <p className="od-field__hint" id="wallet-file-hint">
              {t('wallet.importHelp', { max: formatBytes(MAX_WALLET_BYTES, language) })}
            </p>
            {error ? <p className="od-field__error">{error}</p> : null}
            {busy ? <p className="od-muted">{t('wallet.importing')}</p> : null}
          </div>
        </Section>
      )}

      <Section title={t('wallet.title')} level={2}>
        {items === null ? (
          <p className="od-muted">{t('app.loading')}</p>
        ) : items.length === 0 ? (
          <StateBlock kind="empty" headingLevel={3} title={t('state.empty')} body={<p>{t('wallet.empty')}</p>} announce={false} />
        ) : (
          <ul className="od-list od-list--cards">
            {items.map((item) => (
              <li key={item.id}>
                <Card as="article">
                  <h3 className="od-card__title">{item.label || item.fileName}</h3>
                  <p>
                    <Badge tone="info">{TYPE_LABEL[item.mediaType as AcceptedType] ?? item.mediaType}</Badge>{' '}
                    <Badge tone="neutral">{formatBytes(item.bytes, language)}</Badge>
                  </p>
                  <FactList>
                    <Fact label={t('wallet.imported', { when: '' }).trim()}>
                      <time dateTime={item.importedAt}>{formatDateTime(item.importedAt)}</time>
                    </Fact>
                    <Fact label={t('wallet.size')}>{formatBytes(item.bytes, language)}</Fact>
                  </FactList>
                  <div className="od-pack__actions">
                    <Button tone="secondary" onClick={() => open(item)} aria-expanded={viewing?.item.id === item.id}>
                      {t('wallet.open')}
                    </Button>
                    {/* A download uses a fresh object URL and revokes it straight
                        away, so the bytes are never left addressable. */}
                    <Button
                      tone="quiet"
                      onClick={() => {
                        const url = URL.createObjectURL(blobOf(item));
                        const anchor = globalThis.document.createElement('a');
                        anchor.href = url;
                        anchor.download = item.fileName;
                        anchor.rel = 'noopener';
                        anchor.click();
                        URL.revokeObjectURL(url);
                      }}
                    >
                      {t('wallet.download')}
                    </Button>
                    <Button tone="danger" onClick={() => void remove(item)}>
                      {t('app.delete')}
                    </Button>
                  </div>
                  <p className="od-muted">{t('wallet.openHelp')}</p>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </Section>

      {viewing ? (
        <Section title={t('wallet.viewerTitle')} level={2} id="wallet-viewer">
          <Card>
            <div ref={viewerRef} tabIndex={-1} className="od-viewer">
              <p className="od-viewer__name">{viewing.item.label || viewing.item.fileName}</p>
              {viewing.item.mediaType === 'application/pdf' ? (
                <>
                  <p className="od-muted">{t('wallet.viewerPdf')}</p>
                  <iframe
                    className="od-viewer__frame"
                    title={t('wallet.viewerTitle')}
                    src={viewing.url}
                    /* An empty allow-list: no scripts, no plugins, no forms, no
                       same-origin access, no top-level navigation. */
                    sandbox=""
                    referrerPolicy="no-referrer"
                  />
                  <p className="od-muted">{t('wallet.viewerFallback')}</p>
                </>
              ) : (
                <img className="od-viewer__image" src={viewing.url} alt={t('wallet.viewerImage')} />
              )}
              <Button tone="secondary" onClick={close}>
                {t('app.close')}
              </Button>
            </div>
          </Card>
        </Section>
      ) : null}
    </div>
  );
}
