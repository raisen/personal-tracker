import './style.css';
import { isAuthenticated, getToken, getGistId } from './auth';
import { loadGist } from './api';
import { renderSetup } from './views/setup';
import { renderEntries } from './views/entries';
import { renderSettings } from './views/settings';
import { renderInsights } from './views/insights';

type View = 'entries' | 'insights' | 'settings';

const app = document.getElementById('app')!;
const nav = document.getElementById('nav')!;

function getView(): View {
  const hash = window.location.hash.replace('#', '').split('?')[0]!;
  if (['entries', 'insights', 'settings'].includes(hash)) return hash as View;
  // Legacy routes
  if (hash === 'entry' || hash === 'history') return 'entries';
  // Check for gist= in hash (shareable link)
  if (hash.startsWith('gist=')) return 'entries';
  return 'entries';
}

function updateNavActive(view: View): void {
  nav.querySelectorAll('.nav-link').forEach((link) => {
    link.classList.toggle(
      'active',
      (link as HTMLAnchorElement).dataset['view'] === view,
    );
  });
}

async function updateNavTitle(): Promise<void> {
  const navTitle = document.getElementById('nav-title');
  if (!navTitle) return;
  try {
    const token = getToken();
    const gistId = getGistId();
    if (token && gistId) {
      const { config } = await loadGist(token, gistId);
      navTitle.textContent = config.title;
    }
  } catch {
    // Keep default title
  }
}

async function renderView(): Promise<void> {
  if (!isAuthenticated()) {
    nav.hidden = true;
    renderSetup(app, () => {
      nav.hidden = false;
      window.location.hash = '#entries';
      updateNavTitle();
      renderView();
    });
    return;
  }

  nav.hidden = false;
  const view = getView();
  updateNavActive(view);

  switch (view) {
    case 'entries':
      await renderEntries(app);
      break;
    case 'insights':
      await renderInsights(app);
      break;
    case 'settings':
      await renderSettings(app);
      break;
  }
}

// Init
window.addEventListener('hashchange', () => renderView());
updateNavTitle();
renderView();
