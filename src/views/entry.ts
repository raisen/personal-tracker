import { loadGist, saveData } from '../api';
import { getToken, getGistId } from '../auth';
import { renderField, getFieldValue } from '../components/field-renderer';
import { showToast } from '../components/toast';
import type { TrackerConfig, Entry } from '../types';

export function clearEntryCache(): void {
  // Reserved for future caching
}

export async function renderEntry(container: HTMLElement): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML = '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config, data } = await loadGist(token, gistId);

    // Check if today's entry exists
    const today = new Date().toISOString().slice(0, 10);
    const existing = data.entries.find((e) => {
      const dateField = config.fields.find((f) => f.type === 'date');
      if (dateField) {
        return (e[dateField.id] as string) === today;
      }
      return e._created.startsWith(today);
    });

    renderForm(container, config, existing ?? null);
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

function renderForm(
  container: HTMLElement,
  config: TrackerConfig,
  existing: Entry | null,
): void {
  const isEdit = existing !== null;

  container.innerHTML = `
    <div class="mb-16" style="display:flex;align-items:center;justify-content:space-between">
      <h2 style="margin:0">${isEdit ? 'Edit Entry' : 'New Entry'}</h2>
      ${isEdit ? '<button id="new-entry-btn" class="btn btn-secondary btn-sm">+ New Entry</button>' : ''}
    </div>
    <form id="entry-form"></form>
    <div class="mt-16">
      <button type="submit" form="entry-form" class="btn btn-primary btn-block">
        ${isEdit ? 'Update Entry' : 'Save Entry'}
      </button>
    </div>
  `;

  const form = container.querySelector('#entry-form') as HTMLFormElement;

  const newEntryBtn = container.querySelector('#new-entry-btn');
  if (newEntryBtn) {
    newEntryBtn.addEventListener('click', () => {
      renderForm(container, config, null);
    });
  }

  for (const field of config.fields) {
    let defaultValue = existing?.[field.id];
    if (field.type === 'date' && !defaultValue) {
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
      renderForm(container, config, entry);
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
