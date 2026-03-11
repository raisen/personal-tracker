import { loadGist, saveData } from '../api';
import { getToken, getGistId } from '../auth';
import { renderField, getFieldValue } from '../components/field-renderer';
import { showToast } from '../components/toast';
import { exportToCsv } from '../utils';
import type { TrackerConfig, TrackerData, Entry } from '../types';

const EDIT_STATE_KEY = 'pt_editing_entry';
const HOLD_DURATION = 600; // ms

/** Save which entry is being edited so we can restore on reload */
export function saveEditState(entryId: string | null): void {
  if (entryId) {
    localStorage.setItem(EDIT_STATE_KEY, entryId);
  } else {
    localStorage.removeItem(EDIT_STATE_KEY);
  }
}

export function getSavedEditState(): string | null {
  return localStorage.getItem(EDIT_STATE_KEY);
}

export function clearEditState(): void {
  localStorage.removeItem(EDIT_STATE_KEY);
}

export async function renderEntries(container: HTMLElement): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML = '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config, data } = await loadGist(token, gistId);

    // Check if we should restore a previous editing session
    const savedId = getSavedEditState();
    // Also check sessionStorage for edit from old flow (backwards compat)
    const sessionEdit = sessionStorage.getItem('pt_edit_entry');

    if (sessionEdit) {
      sessionStorage.removeItem('pt_edit_entry');
      const entry = JSON.parse(sessionEdit) as Entry;
      renderEditForm(container, config, data, entry);
    } else if (savedId === '__new__') {
      renderEditForm(container, config, data, null);
    } else if (savedId) {
      const entry = data.entries.find((e) => e._id === savedId);
      if (entry) {
        renderEditForm(container, config, data, entry);
      } else {
        clearEditState();
        renderEntriesList(container, config, data);
      }
    } else {
      renderEntriesList(container, config, data);
    }
  } catch (err) {
    container.innerHTML = `
      <div class="empty-state">
        <h3>Failed to load</h3>
        <p>${err instanceof Error ? err.message : 'Unknown error'}</p>
        <button class="btn btn-primary mt-16" onclick="location.reload()">Retry</button>
      </div>
    `;
  }
}

function renderEntriesList(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
): void {
  clearEditState();

  const sorted = [...data.entries].sort(
    (a, b) => new Date(b._created).getTime() - new Date(a._created).getTime(),
  );

  container.innerHTML = `
    <div class="history-header">
      <h2>Entries <span class="text-secondary text-sm">(${sorted.length})</span></h2>
      <div style="display:flex;gap:6px">
        <button id="new-entry-btn" class="btn btn-primary btn-sm">+ New</button>
        <button id="export-btn" class="btn btn-secondary btn-sm" ${sorted.length === 0 ? 'disabled' : ''}>
          Export
        </button>
      </div>
    </div>
    <div id="entries-list"></div>
  `;

  const list = container.querySelector('#entries-list') as HTMLDivElement;

  // New entry button
  container.querySelector('#new-entry-btn')!.addEventListener('click', () => {
    renderEditForm(container, config, data, null);
  });

  // Export
  container.querySelector('#export-btn')!.addEventListener('click', () => {
    exportToCsv(config.fields, sorted);
    showToast('CSV exported!', 'success');
  });

  if (sorted.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <h3>No entries yet</h3>
        <p>Tap "+ New" to add your first entry.</p>
      </div>
    `;
    return;
  }

  const summaryFields = config.fields.slice(0, 4);

  for (const entry of sorted) {
    const row = document.createElement('div');
    row.className = 'entry-row';
    row.innerHTML = `
      <div class="entry-summary">
        ${summaryFields
          .map((f) => {
            const val = entry[f.id];
            if (val === undefined || val === null) return '';
            const display =
              f.type === 'checkbox'
                ? val
                  ? '\u2713'
                  : '\u2717'
                : String(val);
            return `<div class="entry-field">
              <div class="entry-field-label">${f.icon} ${f.label}</div>
              ${escHtml(display)}
            </div>`;
          })
          .join('')}
      </div>
    `;

    // Tap to edit
    let holdTimer: ReturnType<typeof setTimeout> | null = null;
    let held = false;

    const startHold = () => {
      held = false;
      holdTimer = setTimeout(() => {
        held = true;
        row.classList.add('hold-active');
        handleDelete(container, config, entry);
      }, HOLD_DURATION);
    };

    const cancelHold = () => {
      if (holdTimer) {
        clearTimeout(holdTimer);
        holdTimer = null;
      }
      row.classList.remove('hold-active');
    };

    // Touch events
    row.addEventListener('touchstart', startHold, { passive: true });
    row.addEventListener('touchend', (e) => {
      cancelHold();
      if (!held) {
        e.preventDefault();
        renderEditForm(container, config, data, entry);
      }
    });
    row.addEventListener('touchmove', cancelHold, { passive: true });

    // Mouse events
    row.addEventListener('mousedown', startHold);
    row.addEventListener('mouseup', () => {
      cancelHold();
      if (!held) {
        renderEditForm(container, config, data, entry);
      }
    });
    row.addEventListener('mouseleave', cancelHold);

    list.appendChild(row);
  }
}

async function handleDelete(
  container: HTMLElement,
  config: TrackerConfig,
  entry: Entry,
): Promise<void> {
  if (!confirm('Delete this entry?')) return;

  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  try {
    const latest = await loadGist(token, gistId);
    const entries = latest.data.entries.filter((e) => e._id !== entry._id);
    await saveData(token, gistId, { entries });
    showToast('Entry deleted', 'success');
    renderEntriesList(container, config, { entries });
  } catch (err) {
    showToast(
      `Failed to delete: ${err instanceof Error ? err.message : err}`,
      'error',
    );
  }
}

function renderEditForm(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
  existing: Entry | null,
): void {
  const isEdit = existing !== null;

  // Save state so we can restore on reload
  saveEditState(isEdit ? existing._id : '__new__');

  container.innerHTML = `
    <div class="mb-16" style="display:flex;align-items:center;gap:12px">
      <button id="back-btn" class="btn btn-secondary btn-sm">&larr; Back</button>
      <h2 style="margin:0">${isEdit ? 'Edit Entry' : 'New Entry'}</h2>
    </div>
    <form id="entry-form"></form>
    <div class="mt-16">
      <button type="submit" form="entry-form" class="btn btn-primary btn-block">
        ${isEdit ? 'Update Entry' : 'Save Entry'}
      </button>
    </div>
  `;

  // Back button
  container.querySelector('#back-btn')!.addEventListener('click', () => {
    clearEditState();
    renderEntriesList(container, config, data);
  });

  const form = container.querySelector('#entry-form') as HTMLFormElement;

  for (const field of config.fields) {
    let defaultValue = existing?.[field.id];
    if (field.type === 'date' && !defaultValue && !isEdit) {
      defaultValue = new Date().toISOString().slice(0, 10);
    }
    form.appendChild(renderField(field, defaultValue));
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

    const submitBtn = container.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    submitBtn.disabled = true;
    submitBtn.textContent = 'Saving...';

    try {
      const token = getToken()!;
      const gistId = getGistId()!;

      const latest = await loadGist(token, gistId);
      const entries = [...latest.data.entries];

      const now = new Date().toISOString();
      const entry: Entry = {
        _id: existing?._id ?? now,
        _created: existing?._created ?? now,
        _updated: now,
      };

      for (const field of config.fields) {
        const val = getFieldValue(form, field);
        if (val !== undefined) {
          entry[field.id] = val;
        }
      }

      if (isEdit) {
        const idx = entries.findIndex((e) => e._id === existing._id);
        if (idx >= 0) {
          entries[idx] = entry;
        } else {
          entries.push(entry);
        }
      } else {
        entries.push(entry);
      }

      await saveData(token, gistId, { entries });
      showToast(isEdit ? 'Entry updated!' : 'Entry saved!', 'success');

      // Go back to list with fresh data
      clearEditState();
      renderEntriesList(container, config, { entries });
    } catch (err) {
      showToast(
        `Failed to save: ${err instanceof Error ? err.message : err}`,
        'error',
      );
      submitBtn.disabled = false;
      submitBtn.textContent = isEdit ? 'Update Entry' : 'Save Entry';
    }
  });
}

function escHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
