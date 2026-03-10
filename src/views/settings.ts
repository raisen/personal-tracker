import { loadGist, saveConfig } from '../api';
import { getToken, getGistId, disconnect, getShareableUrl } from '../auth';
import { showToast } from '../components/toast';
import { openFieldEditor } from '../components/field-editor';
import type { TrackerConfig } from '../types';

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

  // Add field
  container.querySelector('#add-field-btn')!.addEventListener('click', () => {
    openFieldEditor(null, (field) => {
      config.fields.push(field);
      renderFieldList(container, config);
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
    item.draggable = true;
    item.dataset['index'] = String(index);

    item.innerHTML = `
      <span class="drag-handle">⠿</span>
      <div class="field-info">
        <div class="field-name">${field.icon ? field.icon + ' ' : ''}${escHtml(field.label)}</div>
        <div class="field-meta">${field.type}${field.required ? ' · required' : ''}</div>
      </div>
      <div class="card-actions">
        <button class="btn btn-secondary btn-sm edit-field-btn">Edit</button>
        <button class="btn btn-danger btn-sm delete-field-btn">Del</button>
      </div>
    `;

    // Edit
    item.querySelector('.edit-field-btn')!.addEventListener('click', () => {
      openFieldEditor(field, (updated) => {
        config.fields[index] = updated;
        renderFieldList(container, config);
      });
    });

    // Delete
    item.querySelector('.delete-field-btn')!.addEventListener('click', () => {
      if (confirm(`Delete field "${field.label}"?`)) {
        config.fields.splice(index, 1);
        renderFieldList(container, config);
      }
    });

    // Drag and drop reorder
    item.addEventListener('dragstart', (e) => {
      item.classList.add('dragging');
      e.dataTransfer!.effectAllowed = 'move';
      e.dataTransfer!.setData('text/plain', String(index));
    });

    item.addEventListener('dragend', () => {
      item.classList.remove('dragging');
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

function escHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
