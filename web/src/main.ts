import './style.css';
import { isAuthenticated, getToken, getGistId } from './auth';
import { loadGist, saveData } from './api';
import { renderSetup } from './views/setup';
import { renderEntry, clearEntryCache } from './views/entry';
import { renderHistory } from './views/history';
import { renderSettings } from './views/settings';
import { renderInsights } from './views/insights';
import { renderField, getFieldValue } from './components/field-renderer';
import { showToast } from './components/toast';
import type { Entry } from './types';

type View = 'entry' | 'history' | 'insights' | 'settings';

const app = document.getElementById('app')!;
const nav = document.getElementById('nav')!;

function getView(): View {
  const hash = window.location.hash.replace('#', '').split('?')[0]!;
  if (['entry', 'history', 'insights', 'settings'].includes(hash)) return hash as View;
  // Check for gist= in hash (shareable link)
  if (hash.startsWith('gist=')) return 'entry';
  return 'entry';
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
      window.location.hash = '#entry';
      updateNavTitle();
      renderView();
    });
    return;
  }

  nav.hidden = false;
  const view = getView();
  updateNavActive(view);

  // Check for edit entry from history
  const editData = sessionStorage.getItem('pt_edit_entry');
  let editEntry: Entry | null = null;
  if (editData && view === 'entry') {
    sessionStorage.removeItem('pt_edit_entry');
    editEntry = JSON.parse(editData) as Entry;
  }

  switch (view) {
    case 'entry':
      if (editEntry) {
        await renderEntryWithEdit(app, editEntry);
      } else {
        clearEntryCache();
        await renderEntry(app);
      }
      break;
    case 'history':
      await renderHistory(app);
      break;
    case 'insights':
      await renderInsights(app);
      break;
    case 'settings':
      await renderSettings(app);
      break;
  }
}

async function renderEntryWithEdit(
  container: HTMLElement,
  entry: Entry,
): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML = '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config } = await loadGist(token, gistId);

    container.innerHTML = `
      <h2 class="mb-16">Edit Entry</h2>
      <form id="entry-form"></form>
      <div class="mt-16">
        <button type="submit" form="entry-form" class="btn btn-primary btn-block">Update Entry</button>
      </div>
    `;

    const form = container.querySelector('#entry-form') as HTMLFormElement;
    for (const field of config.fields) {
      form.appendChild(renderField(field, entry[field.id]));
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      for (const field of config.fields) {
        const val = getFieldValue(form, field);
        if (field.required && (val === undefined || val === '')) {
          showToast(`${field.label} is required`, 'error');
          return;
        }
      }

      const btn = container.querySelector('button[type="submit"]') as HTMLButtonElement;
      btn.disabled = true;
      btn.textContent = 'Saving...';

      try {
        const latest = await loadGist(token, gistId);
        const entries = [...latest.data.entries];
        const now = new Date().toISOString();

        const updated: Entry = {
          _id: entry._id,
          _created: entry._created,
          _updated: now,
        };
        for (const field of config.fields) {
          const val = getFieldValue(form, field);
          if (val !== undefined) updated[field.id] = val;
        }

        const idx = entries.findIndex((e) => e._id === entry._id);
        if (idx >= 0) entries[idx] = updated;
        else entries.push(updated);

        await saveData(token, gistId, { entries });
        showToast('Entry updated!', 'success');
        window.location.hash = '#history';
      } catch (err) {
        showToast(`Failed: ${err instanceof Error ? err.message : err}`, 'error');
        btn.disabled = false;
        btn.textContent = 'Update Entry';
      }
    });
  } catch (err) {
    container.innerHTML = `<div class="empty-state"><h3>Failed to load</h3><p>${err instanceof Error ? err.message : err}</p></div>`;
  }
}

// Init
window.addEventListener('hashchange', () => renderView());
updateNavTitle();
renderView();
