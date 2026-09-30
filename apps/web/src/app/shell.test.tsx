import { describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { I18nProvider } from '../i18n/I18nProvider';
import { AnnouncerProvider } from '../components/Announcer';
import { DataProvider } from './DataProvider';
import { Layout } from './Layout';
import { Welcome } from '../routes/Welcome';
import { Coverage } from '../routes/Coverage';
import { Operators } from '../routes/Operators';
import { NotFound } from '../routes/NotFound';
import { StaticPackSource } from '../data/StaticPackSource';
import { makeStaticFetch, releaseIsPresent } from '../test/release';
import type { Language } from '../data/contract';

/**
 * The application shell, rendered for real.
 *
 * These are the checks that only make sense against a mounted tree: the landmark
 * structure, exactly one `h1` per page, the non-dismissible demonstration notice,
 * the `noindex` that goes with it, and the language switch actually changing every
 * visible string and the document language with it.
 */

const hasRelease = releaseIsPresent();
const describeRelease = hasRelease ? describe : describe.skip;

function Harness({ children, language = 'el', path = '/' }: { children: ReactNode; language?: Language; path?: string }) {
  const source = new StaticPackSource({ baseUrl: '/data/', fetchImpl: makeStaticFetch() });
  return (
    <I18nProvider initialLanguage={language}>
      <MemoryRouter initialEntries={[path]}>
        <AnnouncerProvider>
          <DataProvider source={source}>
            <Routes>
              <Route element={<Layout />}>
                <Route path="*" element={children} />
              </Route>
            </Routes>
          </DataProvider>
        </AnnouncerProvider>
      </MemoryRouter>
    </I18nProvider>
  );
}

describeRelease('the application shell', () => {
  it('provides the landmarks a screen reader navigates by', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    // Each navigation region has its own accessible name, so they are tellable apart.
    const navs = screen.getAllByRole('navigation');
    expect(navs.length).toBeGreaterThan(0);
    const names = navs.map((nav) => nav.getAttribute('aria-label'));
    expect(new Set(names).size).toBe(names.length);
    for (const name of names) expect(name).toBeTruthy();
  });

  it('offers a skip link that targets the main landmark', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    const skip = screen.getByRole('link', { name: /μετάβαση στο κύριο περιεχόμενο/i });
    expect(skip).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  });

  it('has exactly one h1 on a page', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('never skips a heading level', async () => {
    render(
      <Harness>
        <Coverage />
      </Harness>,
    );
    await waitFor(() => expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1));
    const levels = screen
      .getAllByRole('heading')
      .map((heading) => Number(heading.tagName.slice(1)))
      .filter((level) => Number.isFinite(level));
    let previous = levels[0]!;
    for (const level of levels.slice(1)) {
      expect(level - previous, `heading jumped from h${previous} to h${level}`).toBeLessThanOrEqual(1);
      previous = level;
    }
  });

  it('has live regions in the document from first paint', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});

describeRelease('the demonstration notice', () => {
  it('appears on every page while the release is demonstration data', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    const banner = await screen.findByTestId('demo-banner');
    expect(banner).toBeInTheDocument();
    // It says the region does not exist, in the active language.
    expect(within(banner).getByText(/δεν υπάρχει/i)).toBeInTheDocument();
  });

  it('cannot be dismissed, because there is no control that would dismiss it', async () => {
    render(
      <Harness>
        <Welcome />
      </Harness>,
    );
    const banner = await screen.findByTestId('demo-banner');
    // The only interactive things inside it are the explanation disclosure and a
    // link to the coverage page. Nothing closes it.
    const buttons = within(banner).queryAllByRole('button');
    for (const button of buttons) {
      expect(button.textContent ?? '').not.toMatch(/κλείσιμο|απόρριψη|close|dismiss/i);
    }
  });

  it('marks the document noindex while the data is invented', async () => {
    render(
      <Harness>
        <Operators />
      </Harness>,
    );
    await waitFor(() => {
      const robots = document.head.querySelector('meta[name="robots"]');
      expect(robots).not.toBeNull();
      // The operators page asks to be indexable; demo mode overrides that.
      expect(robots!.getAttribute('content')).toBe('noindex, nofollow');
    });
  });

  it('still emits a canonical link and Open Graph tags', async () => {
    render(
      <Harness>
        <Operators />
      </Harness>,
    );
    await waitFor(() => {
      expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
        'https://poravia.peterdsp.dev/operators',
      );
      expect(document.head.querySelector('meta[property="og:title"]')).not.toBeNull();
    });
  });

  it('emits structured data as text, never as parsed markup', async () => {
    render(
      <Harness>
        <Operators />
      </Harness>,
    );
    await waitFor(() => {
      const script = document.head.querySelector('script[type="application/ld+json"]');
      expect(script).not.toBeNull();
      expect(() => JSON.parse(script!.textContent ?? '')).not.toThrow();
      // The payload was assigned with textContent, so it holds no child elements.
      expect(script!.children).toHaveLength(0);
    });
  });
});

describeRelease('language', () => {
  it('renders every visible string in the chosen language', async () => {
    const { unmount } = render(
      <Harness language="el">
        <Welcome />
      </Harness>,
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Ταξίδεψε με βεβαιότητα');
    unmount();

    render(
      <Harness language="sq">
        <Welcome />
      </Harness>,
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Udhëto me siguri');
  });

  it('updates the document language so assistive technology uses the right voice', async () => {
    render(
      <Harness language="sq">
        <Welcome />
      </Harness>,
    );
    await waitFor(() => expect(document.documentElement.lang).toBe('sq'));
  });

  it('switches language from the welcome page and keeps the page working', async () => {
    const user = userEvent.setup();
    render(
      <Harness language="el">
        <Welcome />
      </Harness>,
    );
    await user.click(screen.getByRole('radio', { name: 'English' }));
    await waitFor(() => expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Travel with certainty'));
    await waitFor(() => expect(document.documentElement.lang).toBe('en'));
  });
});

describeRelease('an unknown deep link', () => {
  it('explains itself and offers a way forward rather than showing a blank page', async () => {
    render(
      <Harness path="/journey/definitely-not-a-real-link">
        <NotFound />
      </Harness>,
    );
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByText('/journey/definitely-not-a-real-link')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /αναζήτηση/i }).length).toBeGreaterThan(0);
  });
});
