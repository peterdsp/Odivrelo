import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { en } from '../i18n/en';
import { catalogues } from '../i18n/catalogues';
import { isLanguage } from '../data/contract';

interface Props {
  readonly children: ReactNode;
  /**
   * True when this boundary is already inside the `main` landmark, which is the
   * case for the route-scoped one. It then renders a plain container rather than
   * a second `main`, because two main landmarks on a page is itself a failure.
   */
  readonly insideMain?: boolean;
}

interface State {
  readonly error: Error | null;
}

/**
 * The last line of defence.
 *
 * It cannot use the i18n hook, because the thing that failed may be the provider
 * itself, so it reads the catalogue directly from the document language and falls
 * back to the canonical one. It reports the real error rather than a cheerful
 * nothing, and it always offers a way back to search: a dead end with no exit is
 * worse than the bug.
 */
export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    // Deliberately console-only. There is no error-reporting service, because
    // there is no third party in this app at all.
    console.error('Odivrelo caught an unhandled error', error, info.componentStack);
  }

  override render(): ReactNode {
    const { error } = this.state;
    if (!error) return this.props.children;

    const tag = globalThis.document?.documentElement.lang ?? 'el';
    const catalogue = isLanguage(tag) ? catalogues[tag] : en;

    const Container = this.props.insideMain ? 'div' : 'main';
    const containerProps = this.props.insideMain
      ? { className: 'od-page od-page--narrow' }
      : { className: 'od-main od-main--narrow', id: 'main' };

    return (
      <Container {...containerProps}>
        <div className="od-state od-state--error" data-state="server_error">
          <h1 className="od-state__title">{catalogue['app.errorTitle']}</h1>
          <div className="od-state__body">
            <p>{catalogue['app.errorBody']}</p>
            <details className="od-state__detail">
              <summary>{catalogue['app.errorDetail']}</summary>
              <p className="od-mono">{error.message}</p>
            </details>
          </div>
          <div className="od-state__actions">
            <button type="button" className="od-button od-button--primary" onClick={() => globalThis.location.reload()}>
              {catalogue['state.reload']}
            </button>
            <Link className="od-button od-button--secondary" to="/search" onClick={() => this.setState({ error: null })}>
              {catalogue['app.goToSearch']}
            </Link>
          </div>
        </div>
      </Container>
    );
  }
}
