import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './app/App';
import { DataProvider } from './app/DataProvider';
import { ErrorBoundary } from './app/ErrorBoundary';
import { I18nProvider } from './i18n/I18nProvider';
import { AnnouncerProvider } from './components/Announcer';
import './styles/tokens.css';
import './styles/app.css';

/**
 * Entry point.
 *
 * Provider order matters. i18n is outermost because the error boundary needs the
 * document language to pick a catalogue, and the announcer sits above the router
 * so its live regions exist from first paint rather than being created at the
 * moment they first have something to say.
 */
const container = document.getElementById('root');
if (!container) throw new Error('The #root element is missing from the document.');

createRoot(container).render(
  <StrictMode>
    <I18nProvider>
      <BrowserRouter>
        <AnnouncerProvider>
          <ErrorBoundary>
            <DataProvider>
              <App />
            </DataProvider>
          </ErrorBoundary>
        </AnnouncerProvider>
      </BrowserRouter>
    </I18nProvider>
  </StrictMode>,
);
