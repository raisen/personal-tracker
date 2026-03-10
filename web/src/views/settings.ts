import { loadGist, saveConfig } from '../api';
import { getToken, getGistId, disconnect, getShareableUrl } from '../auth';
import { showToast } from '../components/toast';
import { openFieldEditor } from '../components/field-editor';
import { createModal } from '../components/modal';
import type { TrackerConfig, InsightPrompt } from '../types';

let currentConfig: TrackerConfig | null = null;

export async function renderSettings(container: HTMLElement): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML = '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config } = await loadGist(token, gistId);
    currentConfig = config;
    renderSettingsView(container);
  } catch (err) {
    container.innerHTML = `
      <div class="empty-state">
        <h3>Failed to load</h3>
        <p>${err instanceof Error ? err.message : 'Unknown error'}</p>
      </div>
    `;
  }
}

function enableSwipeToDelete(
  item: HTMLElement,
  onDelete: () => void,
): void {
  let startX = 0;
  let currentX = 0;
  let isSwiping = false;
  const THRESHOLD = 100;

  const content = item.querySelector('.field-list-item-content') as HTMLElement;
  if (!content) return;

  // Touch events for mobile
  content.addEventListener('touchstart', (e) => {
    if ((e.target as HTMLElement).closest('.drag-handle')) return;
    startX = e.touches[0]!.clientX;
    currentX = 0;
    isSwiping = true;
    content.style.transition = 'none';
  });

  content.addEventListener('touchmove', (e) => {
    if (!isSwiping) return;
    currentX = e.touches[0]!.clientX - startX;
    if (currentX < 0) currentX = 0;
    if (currentX > 10) e.preventDefault();
    content.style.transform = `translateX(${currentX}px)`;
    const bg = item.querySelector('.swipe-delete-bg') as HTMLElement;
    if (bg) bg.style.opacity = currentX > THRESHOLD ? '1' : String(Math.min(0.6, currentX / THRESHOLD));
  }, { passive: false });

  content.addEventListener('touchend', () => {
    if (!isSwiping) return;
    isSwiping = false;
    if (currentX >= THRESHOLD) {
      content.style.transition = 'transform 0.2s ease-out, opacity 0.2s';
      content.style.transform = 'translateX(100%)';
      content.style.opacity = '0';
      content.addEventListener('transitionend', () => onDelete(), { once: true });
    } else {
      content.style.transition = 'transform 0.2s ease-out';
      content.style.transform = 'translateX(0)';
    }
  });

  // Mouse events for desktop
  content.addEventListener('mousedown', (e) => {
    if ((e.target as HTMLElement).closest('.drag-handle')) return;
    startX = e.clientX;
    currentX = 0;
    isSwiping = true;
    content.style.transition = 'none';

    const onMouseMove = (e: MouseEvent) => {
      if (!isSwiping) return;
      currentX = e.clientX - startX;
      if (currentX < 0) currentX = 0;
      content.style.transform = `translateX(${currentX}px)`;
      const bg = item.querySelector('.swipe-delete-bg') as HTMLElement;
      if (bg) bg.style.opacity = currentX > THRESHOLD ? '1' : String(Math.min(0.6, currentX / THRESHOLD));
    };

    const onMouseUp = () => {
      isSwiping = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      if (currentX >= THRESHOLD) {
        content.style.transition = 'transform 0.2s ease-out, opacity 0.2s';
        content.style.transform = 'translateX(100%)';
        content.style.opacity = '0';
        content.addEventListener('transitionend', () => onDelete(), { once: true });
      } else {
        content.style.transition = 'transform 0.2s ease-out';
        content.style.transform = 'translateX(0)';
      }
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  });
}

function renderSettingsView(container: HTMLElement): void {
  if (!currentConfig) return;
  const config = currentConfig;
  const shareUrl = getShareableUrl();

  container.innerHTML = `
    <div class="settings-section">
      <h2>Tracker Settings</h2>
      <div class="form-group">
        <label class="form-label">Tracker Title</label>
        <input type="text" class="form-input" id="tracker-title" value="${escHtml(config.title)}" />
      </div>
    </div>

    <div class="settings-section">
      <h2>Fields</h2>
      <div id="field-list"></div>
      <button id="add-field-btn" class="btn btn-secondary btn-block mt-16">+ Add Field</button>
    </div>

    <div class="settings-section">
      <h2>Insight Prompts</h2>
      <p class="text-secondary text-sm mb-8">Prompts that appear on the Insights tab. Each copies your data + the prompt to clipboard for any AI chatbot.</p>
      <div id="prompt-list"></div>
      <button id="add-prompt-btn" class="btn btn-secondary btn-block mt-16">+ Add Prompt</button>
    </div>

    <div class="settings-section">
      <h2>Save</h2>
      <button id="save-settings-btn" class="btn btn-primary btn-block">Save Settings</button>
    </div>

    <div class="settings-section">
      <h2>Share</h2>
      <p class="text-secondary text-sm mb-8">
        Use this link on another browser. You'll still need to enter your token.
      </p>
      <div class="flex-between" style="gap:8px">
        <input type="text" class="form-input" value="${escHtml(shareUrl)}" readonly id="share-url" />
        <button class="btn btn-secondary btn-sm" id="copy-url-btn">Copy</button>
      </div>
    </div>

    <div class="settings-section">
      <h2>Connection</h2>
      <p class="text-secondary text-sm mb-8">Gist ID: ${getGistId()}</p>
      <button id="disconnect-btn" class="btn btn-danger btn-block">Disconnect</button>
    </div>
  `;

  renderFieldList(container, config);

  // Initialize prompts array if missing
  if (!config.prompts) config.prompts = [];
  renderPromptList(container, config);

  // Add field
  container.querySelector('#add-field-btn')!.addEventListener('click', () => {
    openFieldEditor(null, (field) => {
      config.fields.push(field);
      renderFieldList(container, config);
    });
  });

  // Add prompt
  container.querySelector('#add-prompt-btn')!.addEventListener('click', () => {
    openPromptEditor(null, (prompt) => {
      config.prompts!.push(prompt);
      renderPromptList(container, config);
    });
  });

  // Save settings
  container.querySelector('#save-settings-btn')!.addEventListener('click', async () => {
    const titleInput = container.querySelector('#tracker-title') as HTMLInputElement;
    config.title = titleInput.value.trim() || 'My Tracker';

    const btn = container.querySelector('#save-settings-btn') as HTMLButtonElement;
    btn.disabled = true;
    btn.textContent = 'Saving...';

    try {
      await saveConfig(getToken()!, getGistId()!, config);
      // Update nav title
      const navTitle = document.getElementById('nav-title');
      if (navTitle) navTitle.textContent = config.title;
      showToast('Settings saved!', 'success');
    } catch (err) {
      showToast(`Failed to save: ${err instanceof Error ? err.message : err}`, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Save Settings';
    }
  });

  // Copy share URL
  container.querySelector('#copy-url-btn')!.addEventListener('click', () => {
    const input = container.querySelector('#share-url') as HTMLInputElement;
    navigator.clipboard.writeText(input.value).then(
      () => showToast('URL copied!', 'success'),
      () => {
        input.select();
        showToast('Select and copy manually', 'info');
      },
    );
  });

  // Disconnect
  container.querySelector('#disconnect-btn')!.addEventListener('click', () => {
    if (confirm('Disconnect from this tracker? Your data in the gist will remain safe.')) {
      disconnect();
      window.location.hash = '';
      window.location.reload();
    }
  });
}

function renderFieldList(container: HTMLElement, config: TrackerConfig): void {
  const list = container.querySelector('#field-list') as HTMLDivElement;
  list.innerHTML = '';

  config.fields.forEach((field, index) => {
    const item = document.createElement('div');
    item.className = 'field-list-item';
    item.dataset['index'] = String(index);

    item.innerHTML = `
      <div class="swipe-delete-bg"><span>Delete</span></div>
      <div class="field-list-item-content">
        <span class="drag-handle">⠿</span>
        <div class="field-info">
          <span class="field-name">${field.required ? '<span class="field-required-dot"></span>' : ''}${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</span>
          <span class="field-meta">${field.type}</span>
        </div>
      </div>
    `;

    // Tap field-info to edit
    item.querySelector('.field-info')!.addEventListener('click', () => {
      openFieldEditor(field, (updated) => {
        config.fields[index] = updated;
        renderFieldList(container, config);
      });
    });

    // Swipe to delete
    enableSwipeToDelete(item, () => {
      const deletedField = config.fields[index]!;
      const deletedIndex = index;
      config.fields.splice(index, 1);
      renderFieldList(container, config);
      showToast(`"${deletedField.label}" deleted`, 'info', {
        label: 'Undo',
        callback: () => {
          config.fields.splice(deletedIndex, 0, deletedField);
          renderFieldList(container, config);
        },
      });
    });

    // Drag and drop reorder (only from drag handle)
    const dragHandle = item.querySelector('.drag-handle') as HTMLElement;

    dragHandle.addEventListener('mousedown', () => {
      item.draggable = true;
    });

    item.addEventListener('dragstart', (e) => {
      item.classList.add('dragging');
      e.dataTransfer!.effectAllowed = 'move';
      e.dataTransfer!.setData('text/plain', String(index));
    });

    item.addEventListener('dragend', () => {
      item.classList.remove('dragging');
      item.draggable = false;
    });

    item.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.dataTransfer!.dropEffect = 'move';
    });

    item.addEventListener('drop', (e) => {
      e.preventDefault();
      const fromIndex = Number(e.dataTransfer!.getData('text/plain'));
      const toIndex = index;
      if (fromIndex !== toIndex) {
        const [moved] = config.fields.splice(fromIndex, 1);
        if (moved) {
          config.fields.splice(toIndex, 0, moved);
          renderFieldList(container, config);
        }
      }
    });

    list.appendChild(item);
  });
}

function renderPromptList(container: HTMLElement, config: TrackerConfig): void {
  const list = container.querySelector('#prompt-list') as HTMLDivElement;
  list.innerHTML = '';

  const prompts = config.prompts || [];

  if (prompts.length === 0) {
    list.innerHTML = '<p class="text-secondary text-sm">No prompts yet. Add one to get started.</p>';
    return;
  }

  prompts.forEach((prompt, index) => {
    const item = document.createElement('div');
    item.className = 'field-list-item';

    item.innerHTML = `
      <div class="swipe-delete-bg"><span>Delete</span></div>
      <div class="field-list-item-content">
        <div class="field-info">
          <div class="field-name">${escHtml(prompt.label)}</div>
          <div class="field-meta">${getDataRangeLabel(prompt.dataRangeDays)} · ${escHtml(prompt.prompt.slice(0, 60))}${prompt.prompt.length > 60 ? '...' : ''}</div>
        </div>
      </div>
    `;

    // Tap to edit
    item.querySelector('.field-info')!.addEventListener('click', () => {
      openPromptEditor(prompt, (updated) => {
        config.prompts![index] = updated;
        renderPromptList(container, config);
      });
    });

    // Swipe to delete
    enableSwipeToDelete(item, () => {
      const deletedPrompt = config.prompts![index]!;
      const deletedIndex = index;
      config.prompts!.splice(index, 1);
      renderPromptList(container, config);
      showToast(`"${deletedPrompt.label}" deleted`, 'info', {
        label: 'Undo',
        callback: () => {
          config.prompts!.splice(deletedIndex, 0, deletedPrompt);
          renderPromptList(container, config);
        },
      });
    });

    list.appendChild(item);
  });
}

const DATA_RANGE_PRESETS = [
  { value: '', label: 'All data' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '365', label: 'Last year' },
  { value: 'custom', label: 'Custom...' },
];

function getDataRangeLabel(days: number | null | undefined): string {
  if (!days) return 'All data';
  const preset = DATA_RANGE_PRESETS.find((p) => p.value === String(days));
  return preset ? preset.label : `Last ${days} days`;
}

function openPromptEditor(
  existing: InsightPrompt | null,
  onSave: (prompt: InsightPrompt) => void,
): void {
  const modal = createModal(existing ? 'Edit Prompt' : 'Add Prompt');

  const existingRange = existing?.dataRangeDays;
  const isPreset = !existingRange || ['7', '30', '90', '365'].includes(String(existingRange));
  const selectValue = existingRange ? (isPreset ? String(existingRange) : 'custom') : '';

  modal.body.innerHTML = `
    <div class="form-group">
      <label class="form-label">Label</label>
      <input type="text" class="form-input" id="prompt-label" value="${existing ? escHtml(existing.label) : ''}" placeholder="e.g. Weekly Summary" />
    </div>
    <div class="form-group">
      <label class="form-label">Prompt</label>
      <textarea class="form-textarea" id="prompt-text" rows="6" placeholder="e.g. Analyze my mood trends over the past week...">${existing ? escHtml(existing.prompt) : ''}</textarea>
    </div>
    <div class="form-group">
      <label class="form-label">Data Range</label>
      <select class="form-select" id="prompt-range">
        ${DATA_RANGE_PRESETS.map((p) => `<option value="${p.value}"${p.value === selectValue ? ' selected' : ''}>${escHtml(p.label)}</option>`).join('')}
      </select>
    </div>
    <div class="form-group" id="custom-range-group" style="display:${selectValue === 'custom' ? 'block' : 'none'}">
      <label class="form-label">Number of days</label>
      <input type="number" class="form-input" id="prompt-range-custom" min="1" value="${existingRange && !isPreset ? existingRange : ''}" placeholder="e.g. 14" />
    </div>
  `;

  // Toggle custom input visibility
  const rangeSelect = modal.dialog.querySelector('#prompt-range') as HTMLSelectElement;
  const customGroup = modal.dialog.querySelector('#custom-range-group') as HTMLElement;
  rangeSelect.addEventListener('change', () => {
    customGroup.style.display = rangeSelect.value === 'custom' ? 'block' : 'none';
  });

  const saveBtn = document.createElement('button');
  saveBtn.className = 'btn btn-primary';
  saveBtn.textContent = existing ? 'Update' : 'Add';

  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn btn-secondary';
  cancelBtn.textContent = 'Cancel';

  modal.actions.appendChild(cancelBtn);
  modal.actions.appendChild(saveBtn);

  cancelBtn.addEventListener('click', () => modal.close());

  saveBtn.addEventListener('click', () => {
    const label = (modal.dialog.querySelector('#prompt-label') as HTMLInputElement).value.trim();
    const prompt = (modal.dialog.querySelector('#prompt-text') as HTMLTextAreaElement).value.trim();

    if (!label) {
      showToast('Label is required', 'error');
      return;
    }
    if (!prompt) {
      showToast('Prompt text is required', 'error');
      return;
    }

    const rangeValue = rangeSelect.value;
    let dataRangeDays: number | null = null;
    if (rangeValue === 'custom') {
      const customInput = (modal.dialog.querySelector('#prompt-range-custom') as HTMLInputElement).value.trim();
      if (!customInput || Number(customInput) < 1) {
        showToast('Please enter a valid number of days', 'error');
        return;
      }
      dataRangeDays = Number(customInput);
    } else if (rangeValue) {
      dataRangeDays = Number(rangeValue);
    }

    onSave({ label, prompt, dataRangeDays });
    modal.close();
  });

  modal.show();
}

function escHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
