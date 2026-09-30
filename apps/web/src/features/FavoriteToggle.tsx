import { useCallback, useEffect, useState } from 'react';
import type { Localized } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useData } from '../app/DataProvider';
import { Button } from '../components/primitives';
import { useAnnouncer } from '../components/Announcer';
import { deleteFavorite, listFavorites, putFavorite, type FavoriteKind } from '../lib/db';

export interface FavoriteToggleProps {
  readonly kind: FavoriteKind;
  /** The operator or place id this favourite points at. */
  readonly targetId: string;
  readonly label: Localized;
}

/**
 * Add or remove a favourite.
 *
 * Favourites live in IndexedDB so they survive a reload and work offline. When
 * the browser refuses storage the control says why instead of silently doing
 * nothing: a button that appears to work and does not is worse than one that
 * explains itself.
 */
export function FavoriteToggle({ kind, targetId, label }: FavoriteToggleProps) {
  const { t } = useI18n();
  const { storageAvailable } = useData();
  const { announce, alert } = useAnnouncer();
  const [saved, setSaved] = useState(false);
  const id = `${kind}:${targetId}`;

  const refresh = useCallback(async () => {
    if (!storageAvailable) return;
    const all = await listFavorites();
    setSaved(all.some((favorite) => favorite.id === id));
  }, [id, storageAvailable]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (!storageAvailable) {
    return (
      <Button tone="quiet" unavailableReason={t('saved.storageUnavailable')}>
        {t('saved.addFavorite')}
      </Button>
    );
  }

  return (
    <Button
      tone="quiet"
      aria-pressed={saved}
      onClick={async () => {
        try {
          if (saved) {
            await deleteFavorite(id);
            setSaved(false);
            announce(t('saved.removeFavorite'));
          } else {
            await putFavorite({ id, kind, ref: targetId, label, savedAt: new Date().toISOString() });
            setSaved(true);
            announce(t('saved.addFavorite'));
          }
        } catch {
          alert(t('saved.storageUnavailable'));
        }
      }}
    >
      {saved ? t('saved.removeFavorite') : t('saved.addFavorite')}
    </Button>
  );
}
