import { loadGist, saveData } from '../api';
import { getToken, getGistId } from '../auth';
import { showToast } from '../components/toast';
import { exportToCsv } from '../utils';
import type { TrackerConfig, TrackerData, Entry } from '../types';

export async function renderHistory(container: HTMLElement): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML = '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config, data } = await loadGist(token, gistId);
    renderHistoryView(container, config, data);
  } catch (err) {
    container.innerHTML = `
      <div class="empty-state">
        <h3>Failed to load</h3>
        <p>${err instanceof Error ? err.message : 'Unknown error'}</p>
      </div>
    `;
  }
}

function renderHistoryView(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
): void {
  const sorted = [...data.entries].sort(
    (a, b) => new Date(b._created).getTime() - new Date(a._created).getTime(),
  );

  container.innerHTML = `
    <div class="history-header">
      <h2>History <span class="text-secondary text-sm">(${sorted.length})</span></h2>
      <div>
        <button id="export-btn" class="btn btn-secondary btn-sm" ${sorted.length === 0 ? 'disabled' : ''}>
          Export CSV
        </button>
      </div>
    </div>
    <div id="entries-list"></div>
  `;

  const list = container.querySelector('#entries-list') as HTMLDivElement;

  if (sorted.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <h3>No entries yet</h3>
        <p>Go to <a href="#entry">Entry</a> to add your first entry.</p>
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
                  ? '✓'
                  : '✗'
                : String(val);
            return `<div class="entry-field">
              <div class="entry-field-label">${f.icon} ${f.label}</div>
              ${display}
            </div>`;
          })
          .join('')}
      </div>
      <div class="card-actions">
        <button class="btn btn-secondary btn-sm edit-entry-btn">Edit</button>
        <button class="btn btn-danger btn-sm delete-entry-btn">Delete</button>
      </div>
    `;

    row.querySelector('.edit-entry-btn')!.addEventListener('click', (e) => {
      e.stopPropagation();
      editEntry(entry);
    });

    row.querySelector('.delete-entry-btn')!.addEventListener('click', (e) => {
      e.stopPropagation();
      deleteEntry(container, config, entry);
    });

    list.appendChild(row);
  }

  container.querySelector('#export-btn')!.addEventListener('click', () => {
    exportToCsv(config.fields, sorted);
    showToast('CSV exported!', 'success');
  });
}

function editEntry(entry: Entry): void {
  sessionStorage.setItem('pt_edit_entry', JSON.stringify(entry));
  window.location.hash = '#entry';
}

async function deleteEntry(
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
    renderHistoryView(container, config, { entries });
  } catch (err) {
    showToast(
      `Failed to delete: ${err instanceof Error ? err.message : err}`,
      'error',
    );
  }
}
