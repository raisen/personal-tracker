import { loadGist } from '../api';
import { getToken, getGistId } from '../auth';
import { showToast } from '../components/toast';
import type { TrackerConfig, Entry } from '../types';

function formatEntries(entries: Entry[], config: TrackerConfig): string {
  if (entries.length === 0) return 'No entries recorded yet.';

  const sorted = [...entries].sort((a, b) => {
    const dateA = (a['date'] as string) || a._created;
    const dateB = (b['date'] as string) || b._created;
    return dateA < dateB ? -1 : dateA > dateB ? 1 : 0;
  });

  const fieldIds = config.fields.map((f) => f.id);

  return sorted
    .map((entry) => {
      const parts = fieldIds
        .filter((id) => entry[id] !== undefined && entry[id] !== '')
        .map((id) => `${id}: ${entry[id]}`);
      return parts.join(' | ');
    })
    .join('\n');
}

function buildClipboardText(
  prompt: string,
  entries: Entry[],
  config: TrackerConfig,
): string {
  const dataText = formatEntries(entries, config);
  return `${prompt}\n\nHere is my tracker data (${entries.length} entries):\n\n${dataText}`;
}

export async function renderInsights(container: HTMLElement): Promise<void> {
  const token = getToken();
  const gistId = getGistId();
  if (!token || !gistId) return;

  container.innerHTML =
    '<div class="loading"><div class="spinner"></div>Loading...</div>';

  try {
    const { config, data } = await loadGist(token, gistId);
    renderInsightsView(container, config, data.entries);
  } catch (err) {
    container.innerHTML = `<div class="empty-state"><h3>Failed to load</h3><p>${err instanceof Error ? err.message : err}</p></div>`;
  }
}

function renderInsightsView(
  container: HTMLElement,
  config: TrackerConfig,
  entries: Entry[],
): void {
  const prompts = config.prompts || [];

  container.innerHTML = `
    <div class="settings-section">
      <h2>AI Insights</h2>
      <p class="text-secondary text-sm mb-16">
        Copy a prompt + your data to the clipboard, then paste it into
        <a href="https://claude.ai" target="_blank" rel="noopener">claude.ai</a>.
      </p>
      <p class="text-secondary text-sm mb-16">${entries.length} entries available for analysis.</p>
    </div>

    ${
      prompts.length > 0
        ? `
      <div class="settings-section">
        <h2>Saved Prompts</h2>
        <div id="prompt-buttons"></div>
      </div>
    `
        : `
      <div class="settings-section">
        <div class="empty-state">
          <h3>No prompts saved</h3>
          <p>Add prompts in <a href="#settings">Settings</a> to see them here.</p>
        </div>
      </div>
    `
    }

    <div class="settings-section">
      <h2>Custom Question</h2>
      <textarea id="custom-prompt" class="form-textarea" placeholder="Ask anything about your data..."></textarea>
      <button id="custom-copy-btn" class="btn btn-primary btn-block mt-8">Copy to Clipboard</button>
    </div>

    <div class="settings-section">
      <h2>Raw Data</h2>
      <p class="text-secondary text-sm mb-8">Copy just your raw data if you want to write your own prompt.</p>
      <button id="copy-raw-btn" class="btn btn-secondary btn-block">Copy Raw Data</button>
    </div>
  `;

  // Saved prompt buttons
  const btnContainer = container.querySelector('#prompt-buttons');
  if (btnContainer) {
    for (const p of prompts) {
      const btn = document.createElement('button');
      btn.className = 'btn btn-secondary mb-8 btn-block';
      btn.style.textAlign = 'left';
      btn.textContent = p.label;
      btn.addEventListener('click', () => {
        copyToClipboard(buildClipboardText(p.prompt, entries, config), p.label);
      });
      btnContainer.appendChild(btn);
    }
  }

  // Custom prompt
  container.querySelector('#custom-copy-btn')?.addEventListener('click', () => {
    const textarea = container.querySelector(
      '#custom-prompt',
    ) as HTMLTextAreaElement;
    const question = textarea.value.trim();
    if (!question) {
      showToast('Please enter a question first', 'error');
      return;
    }
    copyToClipboard(
      buildClipboardText(question, entries, config),
      'Custom question',
    );
  });

  // Raw data
  container.querySelector('#copy-raw-btn')?.addEventListener('click', () => {
    const dataText = formatEntries(entries, config);
    copyToClipboard(dataText, 'Raw data');
  });
}

async function copyToClipboard(text: string, label: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    showToast(`${label} copied! Paste into claude.ai`, 'success');
  } catch {
    showToast('Copy failed — try manually selecting the text', 'error');
  }
}
