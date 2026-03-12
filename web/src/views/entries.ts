import { loadGist, saveData } from '../api';
import { getToken, getGistId } from '../auth';
import { renderField, getFieldValue } from '../components/field-renderer';
import { showToast } from '../components/toast';
import { exportToCsv } from '../utils';
import type { TrackerConfig, TrackerData, Entry, FieldConfig } from '../types';

const EDIT_STATE_KEY = 'pt_editing_entry';
const LAYOUT_KEY = 'pt_layout_compact';
const HOLD_DURATION = 600; // ms

// Date range filter options (matching Android)
type DateRangeOption = { label: string; days: number | null; id: string };
const DATE_RANGES: DateRangeOption[] = [
  { id: 'all', label: 'All', days: null },
  { id: '7d', label: '7d', days: 7 },
  { id: '30d', label: '30d', days: 30 },
  { id: '90d', label: '90d', days: 90 },
  { id: 'custom', label: 'Custom', days: null },
];

// Filter state persists within the session
let selectedDateRange = 'all';
let customStartDate = '';
let customEndDate = '';
let showFilters = false;
let fieldFilters: Record<string, unknown> = {};

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

    const savedId = getSavedEditState();
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

// --- Helpers ---

function getEntryDate(entry: Entry, config: TrackerConfig): string | null {
  const dateField = config.fields.find((f) => f.type === 'date');
  if (dateField) {
    const val = entry[dateField.id];
    if (typeof val === 'string' && val) return val.slice(0, 10);
  }
  if (entry._created) {
    return entry._created.slice(0, 10);
  }
  return null;
}

function parseDate(s: string): Date | null {
  const d = new Date(s + 'T00:00:00');
  return isNaN(d.getTime()) ? null : d;
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86400000);
}

function formatEntryDate(dateStr: string): string {
  const d = parseDate(dateStr);
  if (!d) return dateStr;
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${days[d.getDay()]}, ${months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}

function getRelativeDate(dateStr: string): string | null {
  const d = parseDate(dateStr);
  if (!d) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = daysBetween(d, today);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  if (diff >= 2 && diff <= 6) return `${diff} days ago`;
  if (diff >= 7 && diff <= 13) return '1 week ago';
  return null;
}

function computeStreak(entries: Entry[], config: TrackerConfig): number {
  const dates = entries
    .map((e) => getEntryDate(e, config))
    .filter((d): d is string => d !== null)
    .map((d) => d.slice(0, 10));
  const unique = [...new Set(dates)].sort().reverse();

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  let streak = 0;
  let checkDate = new Date(today);

  for (const dateStr of unique) {
    const checkStr = checkDate.toISOString().slice(0, 10);
    if (dateStr === checkStr) {
      streak++;
      checkDate.setDate(checkDate.getDate() - 1);
    } else if (dateStr < checkStr) {
      if (streak === 0) {
        const yesterdayStr = new Date(today.getTime() - 86400000).toISOString().slice(0, 10);
        if (dateStr === yesterdayStr) {
          streak = 1;
          checkDate = new Date(today.getTime() - 2 * 86400000);
        } else {
          break;
        }
      } else {
        break;
      }
    }
  }
  return streak;
}

function countLast7Days(entries: Entry[], config: TrackerConfig): number {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const cutoff = new Date(today.getTime() - 6 * 86400000);
  const cutoffStr = cutoff.toISOString().slice(0, 10);

  const dates = entries
    .map((e) => getEntryDate(e, config))
    .filter((d): d is string => d !== null && d >= cutoffStr);
  return new Set(dates).size;
}

function countActiveFilters(): number {
  let count = 0;
  for (const [, v] of Object.entries(fieldFilters)) {
    if (v === null || v === undefined) continue;
    if (typeof v === 'string' && v.trim() === '') continue;
    if (v instanceof Set && v.size === 0) continue;
    if (Array.isArray(v) && v.every((x) => x === null)) continue;
    count++;
  }
  return count;
}

function applyFilters(entries: Entry[], config: TrackerConfig): Entry[] {
  let result = entries;

  // Date range filter
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (selectedDateRange === 'custom') {
    const start = customStartDate ? parseDate(customStartDate) : null;
    const end = customEndDate ? parseDate(customEndDate) : null;
    if (start || end) {
      result = result.filter((entry) => {
        const ds = getEntryDate(entry, config);
        if (!ds) return true;
        const d = parseDate(ds);
        if (!d) return true;
        if (start && d < start) return false;
        if (end && d > end) return false;
        return true;
      });
    }
  } else {
    const range = DATE_RANGES.find((r) => r.id === selectedDateRange);
    if (range?.days) {
      const cutoff = new Date(today.getTime() - range.days * 86400000);
      result = result.filter((entry) => {
        const ds = getEntryDate(entry, config);
        if (!ds) return true;
        const d = parseDate(ds);
        if (!d) return true;
        return d >= cutoff;
      });
    }
  }

  // Field filters
  if (Object.keys(fieldFilters).length > 0) {
    result = result.filter((entry) => {
      return Object.entries(fieldFilters).every(([fieldId, filterValue]) => {
        const entryValue = entry[fieldId];
        const field = config.fields.find((f) => f.id === fieldId);
        if (!field) return true;
        if (filterValue === null || filterValue === undefined) return true;

        switch (field.type) {
          case 'text': {
            const search = filterValue as string;
            if (!search.trim()) return true;
            return String(entryValue ?? '').toLowerCase().includes(search.toLowerCase());
          }
          case 'number':
          case 'range': {
            const [min, max] = filterValue as [number | null, number | null];
            if (min === null && max === null) return true;
            const num = typeof entryValue === 'number' ? entryValue : Number(entryValue);
            if (isNaN(num)) return true;
            if (min !== null && num < min) return false;
            if (max !== null && num > max) return false;
            return true;
          }
          case 'select':
          case 'radio': {
            const selected = filterValue as Set<string>;
            if (selected.size === 0) return true;
            return selected.has(String(entryValue));
          }
          case 'checkbox': {
            const filterBool = filterValue as boolean;
            return entryValue === filterBool;
          }
          default:
            return true;
        }
      });
    });
  }

  return result;
}

// --- Main list render ---

function renderEntriesList(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
): void {
  clearEditState();

  const allSorted = [...data.entries].sort(
    (a, b) => new Date(b._created).getTime() - new Date(a._created).getTime(),
  );

  const filtered = applyFilters(allSorted, config);
  const isCompact = localStorage.getItem(LAYOUT_KEY) !== 'expanded';
  const activeCount = countActiveFilters();
  const streak = computeStreak(allSorted, config);
  const last7 = countLast7Days(allSorted, config);

  const countLabel =
    filtered.length !== allSorted.length
      ? `${filtered.length} of ${allSorted.length}`
      : `${allSorted.length} total`;

  container.innerHTML = `
    <div class="history-header">
      <div>
        <h2>Entries</h2>
        <span class="text-secondary text-sm">${countLabel}</span>
      </div>
      <div style="display:flex;gap:6px;align-items:center">
        <button id="filter-btn" class="btn btn-secondary btn-sm btn-icon${showFilters || activeCount > 0 ? ' btn-active' : ''}" title="Filters">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 3H2l8 9.46V19l4 2v-8.54L22 3z"/></svg>
          ${activeCount > 0 ? `<span class="filter-badge">${activeCount}</span>` : ''}
        </button>
        <button id="layout-btn" class="btn btn-secondary btn-sm btn-icon" title="Toggle layout">
          ${isCompact
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>'
          }
        </button>
        <button id="new-entry-btn" class="btn btn-primary btn-sm">+ New</button>
        <button id="export-btn" class="btn btn-secondary btn-sm" ${allSorted.length === 0 ? 'disabled' : ''}>Export</button>
      </div>
    </div>

    ${allSorted.length > 0 ? `
      <div class="stats-strip">
        <div class="stat-card stat-primary">
          <div class="stat-value">${streak}</div>
          <div class="stat-label">day streak</div>
        </div>
        <div class="stat-card stat-secondary">
          <div class="stat-value">${last7}/7</div>
          <div class="stat-label">last 7 days</div>
        </div>
      </div>
    ` : ''}

    <div class="filter-chips" id="date-chips">
      ${DATE_RANGES.map((r) => `<button class="chip${selectedDateRange === r.id ? ' chip-selected' : ''}" data-range="${r.id}">${r.label}</button>`).join('')}
    </div>

    ${selectedDateRange === 'custom' ? `
      <div class="custom-date-row">
        <input type="date" class="form-input" id="custom-start" value="${escHtml(customStartDate)}" placeholder="From" />
        <input type="date" class="form-input" id="custom-end" value="${escHtml(customEndDate)}" placeholder="To" />
      </div>
    ` : ''}

    <div id="field-filters-panel" style="display:${showFilters ? 'block' : 'none'}"></div>
    <div id="entries-list"></div>
  `;

  // Wire up date chips
  container.querySelectorAll('#date-chips .chip').forEach((btn) => {
    btn.addEventListener('click', () => {
      selectedDateRange = (btn as HTMLElement).dataset['range']!;
      renderEntriesList(container, config, data);
    });
  });

  // Custom date inputs
  if (selectedDateRange === 'custom') {
    container.querySelector('#custom-start')?.addEventListener('change', (e) => {
      customStartDate = (e.target as HTMLInputElement).value;
      renderEntriesList(container, config, data);
    });
    container.querySelector('#custom-end')?.addEventListener('change', (e) => {
      customEndDate = (e.target as HTMLInputElement).value;
      renderEntriesList(container, config, data);
    });
  }

  // Filter button
  container.querySelector('#filter-btn')!.addEventListener('click', () => {
    showFilters = !showFilters;
    renderEntriesList(container, config, data);
  });

  // Layout toggle
  container.querySelector('#layout-btn')!.addEventListener('click', () => {
    localStorage.setItem(LAYOUT_KEY, isCompact ? 'expanded' : 'compact');
    renderEntriesList(container, config, data);
  });

  // New entry
  container.querySelector('#new-entry-btn')!.addEventListener('click', () => {
    renderEditForm(container, config, data, null);
  });

  // Export
  container.querySelector('#export-btn')!.addEventListener('click', () => {
    exportToCsv(config.fields, allSorted);
    showToast('CSV exported!', 'success');
  });

  // Render field filters panel
  if (showFilters) {
    renderFieldFilters(container, config, data);
  }

  // Render entries
  const list = container.querySelector('#entries-list') as HTMLDivElement;

  if (filtered.length === 0) {
    list.innerHTML = `
      <div class="empty-state">
        <h3>${allSorted.length === 0 ? 'No entries yet' : 'No entries match your filters'}</h3>
        <p>${allSorted.length === 0 ? 'Tap "+ New" to add your first entry.' : 'Try adjusting your filters.'}</p>
      </div>
    `;
    return;
  }

  const summaryFields = config.fields.filter((f) => f.showInList !== false);

  for (const entry of filtered) {
    const row = document.createElement('div');
    row.className = isCompact ? 'entry-row entry-compact' : 'entry-row entry-expanded';

    const entryDateStr = getEntryDate(entry, config);
    const formattedDate = entryDateStr ? formatEntryDate(entryDateStr) : '';
    const relativeDate = entryDateStr ? getRelativeDate(entryDateStr) : null;

    row.innerHTML = `
      <div class="entry-date-header">
        <span class="entry-date-text">${escHtml(formattedDate)}</span>
        ${relativeDate ? `<span class="entry-relative-date">${escHtml(relativeDate)}</span>` : ''}
      </div>
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

    // Tap to edit / hold to delete
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

    row.addEventListener('touchstart', startHold, { passive: true });
    row.addEventListener('touchend', (e) => {
      cancelHold();
      if (!held) {
        e.preventDefault();
        renderEditForm(container, config, data, entry);
      }
    });
    row.addEventListener('touchmove', cancelHold, { passive: true });

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

// --- Field filters panel ---

function renderFieldFilters(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
): void {
  const panel = container.querySelector('#field-filters-panel') as HTMLDivElement;
  const filterableFields = config.fields.filter(
    (f) => f.showInList !== false && f.type !== 'date' && f.type !== 'time',
  );

  if (filterableFields.length === 0) {
    panel.innerHTML = '<p class="text-secondary text-sm" style="padding:8px 0">No filterable fields.</p>';
    return;
  }

  const activeCount = countActiveFilters();

  let html = `
    <div class="filters-panel">
      <div class="filters-header">
        <span class="filters-title">Filters</span>
        ${activeCount > 0 ? '<button id="clear-filters-btn" class="btn btn-secondary btn-sm">Clear all</button>' : ''}
      </div>
  `;

  for (const field of filterableFields) {
    html += renderFilterForField(field);
  }

  html += '</div>';
  panel.innerHTML = html;

  // Clear all button
  panel.querySelector('#clear-filters-btn')?.addEventListener('click', () => {
    fieldFilters = {};
    renderEntriesList(container, config, data);
  });

  // Wire up filter inputs
  for (const field of filterableFields) {
    wireFilterEvents(panel, field, container, config, data);
  }
}

function renderFilterForField(field: FieldConfig): string {
  switch (field.type) {
    case 'text': {
      const current = (fieldFilters[field.id] as string) ?? '';
      return `
        <div class="filter-group">
          <label class="filter-label">${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</label>
          <input type="text" class="form-input form-input-sm" data-filter-field="${field.id}" data-filter-type="text" value="${escHtml(current)}" placeholder="Search..." />
        </div>
      `;
    }
    case 'number':
    case 'range': {
      const range = (fieldFilters[field.id] as [number | null, number | null]) ?? [null, null];
      return `
        <div class="filter-group">
          <label class="filter-label">${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</label>
          <div class="filter-range-row">
            <input type="number" class="form-input form-input-sm" data-filter-field="${field.id}" data-filter-type="range-min" value="${range[0] !== null ? range[0] : ''}" placeholder="Min" />
            <input type="number" class="form-input form-input-sm" data-filter-field="${field.id}" data-filter-type="range-max" value="${range[1] !== null ? range[1] : ''}" placeholder="Max" />
          </div>
        </div>
      `;
    }
    case 'select':
    case 'radio': {
      const selected = (fieldFilters[field.id] as Set<string>) ?? new Set<string>();
      const options = field.options ?? [];
      return `
        <div class="filter-group">
          <label class="filter-label">${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</label>
          <div class="filter-chips">
            ${options.map((opt) => `<button class="chip chip-sm${selected.has(opt) ? ' chip-selected' : ''}" data-filter-field="${field.id}" data-filter-type="select" data-filter-option="${escHtml(opt)}">${escHtml(opt)}</button>`).join('')}
          </div>
        </div>
      `;
    }
    case 'checkbox': {
      const filterVal = fieldFilters[field.id] as boolean | undefined;
      return `
        <div class="filter-group">
          <label class="filter-label">${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</label>
          <div class="filter-chips">
            <button class="chip chip-sm${filterVal === undefined ? ' chip-selected' : ''}" data-filter-field="${field.id}" data-filter-type="checkbox" data-filter-option="any">Any</button>
            <button class="chip chip-sm${filterVal === true ? ' chip-selected' : ''}" data-filter-field="${field.id}" data-filter-type="checkbox" data-filter-option="yes">Yes</button>
            <button class="chip chip-sm${filterVal === false ? ' chip-selected' : ''}" data-filter-field="${field.id}" data-filter-type="checkbox" data-filter-option="no">No</button>
          </div>
        </div>
      `;
    }
    default:
      return '';
  }
}

function wireFilterEvents(
  panel: HTMLElement,
  field: FieldConfig,
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
): void {
  switch (field.type) {
    case 'text': {
      const input = panel.querySelector(`[data-filter-field="${field.id}"][data-filter-type="text"]`) as HTMLInputElement | null;
      input?.addEventListener('input', () => {
        if (input.value.trim()) {
          fieldFilters[field.id] = input.value;
        } else {
          delete fieldFilters[field.id];
        }
        renderEntriesList(container, config, data);
      });
      break;
    }
    case 'number':
    case 'range': {
      const minInput = panel.querySelector(`[data-filter-field="${field.id}"][data-filter-type="range-min"]`) as HTMLInputElement | null;
      const maxInput = panel.querySelector(`[data-filter-field="${field.id}"][data-filter-type="range-max"]`) as HTMLInputElement | null;
      const handler = () => {
        const min = minInput?.value ? Number(minInput.value) : null;
        const max = maxInput?.value ? Number(maxInput.value) : null;
        if (min === null && max === null) {
          delete fieldFilters[field.id];
        } else {
          fieldFilters[field.id] = [min, max];
        }
        renderEntriesList(container, config, data);
      };
      minInput?.addEventListener('input', handler);
      maxInput?.addEventListener('input', handler);
      break;
    }
    case 'select':
    case 'radio': {
      panel.querySelectorAll(`[data-filter-field="${field.id}"][data-filter-type="select"]`).forEach((btn) => {
        btn.addEventListener('click', () => {
          const option = (btn as HTMLElement).dataset['filterOption']!;
          const current = (fieldFilters[field.id] as Set<string>) ?? new Set<string>();
          const newSet = new Set(current);
          if (newSet.has(option)) {
            newSet.delete(option);
          } else {
            newSet.add(option);
          }
          if (newSet.size === 0) {
            delete fieldFilters[field.id];
          } else {
            fieldFilters[field.id] = newSet;
          }
          renderEntriesList(container, config, data);
        });
      });
      break;
    }
    case 'checkbox': {
      panel.querySelectorAll(`[data-filter-field="${field.id}"][data-filter-type="checkbox"]`).forEach((btn) => {
        btn.addEventListener('click', () => {
          const option = (btn as HTMLElement).dataset['filterOption']!;
          if (option === 'any') {
            delete fieldFilters[field.id];
          } else {
            fieldFilters[field.id] = option === 'yes';
          }
          renderEntriesList(container, config, data);
        });
      });
      break;
    }
  }
}

// --- Delete handler ---

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

// --- Edit form ---

function renderEditForm(
  container: HTMLElement,
  config: TrackerConfig,
  data: TrackerData,
  existing: Entry | null,
): void {
  const isEdit = existing !== null;

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
