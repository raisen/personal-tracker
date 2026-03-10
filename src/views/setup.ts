import { validateToken, createTrackerGist, listGists } from '../api';
import { setToken, setGistId } from '../auth';
import { showToast } from '../components/toast';

export function renderSetup(
  container: HTMLElement,
  onComplete: () => void,
): void {
  container.innerHTML = `
    <div class="setup-container">
      <h1>📊 Personal Tracker</h1>
      <p>Track anything, your way. Data stored securely in a private GitHub Gist.</p>

      <div class="setup-steps">
        <div class="setup-step">
          <h3>1. Create a GitHub Token</h3>
          <p>
            Create a fine-grained Personal Access Token with <strong>Gist</strong>
            read &amp; write permission.
            <a href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noopener">
              Create token →
            </a>
          </p>
        </div>

        <div class="setup-step">
          <h3>2. Enter Your Token</h3>
          <p>Your token is stored only in this browser and never sent to any server except GitHub's API.</p>
          <input
            type="password"
            id="token-input"
            class="form-input"
            placeholder="github_pat_..."
            autocomplete="off"
          />
          <button id="connect-btn" class="btn btn-primary btn-block">Connect</button>
        </div>

        <div id="gist-step" class="setup-step" hidden>
          <h3>3. Choose Your Tracker</h3>
          <div id="gist-options"></div>
        </div>
      </div>
    </div>
  `;

  const tokenInput = container.querySelector('#token-input') as HTMLInputElement;
  const connectBtn = container.querySelector('#connect-btn') as HTMLButtonElement;
  const gistStep = container.querySelector('#gist-step') as HTMLDivElement;
  const gistOptions = container.querySelector('#gist-options') as HTMLDivElement;

  connectBtn.addEventListener('click', async () => {
    const token = tokenInput.value.trim();
    if (!token) {
      showToast('Please enter a token', 'error');
      return;
    }

    connectBtn.disabled = true;
    connectBtn.textContent = 'Connecting...';

    try {
      const user = await validateToken(token);
      showToast(`Connected as ${user.login}`, 'success');
      setToken(token);

      // Check for existing tracker gists
      const existing = await listGists(token);
      gistStep.hidden = false;

      if (existing.length > 0) {
        gistOptions.innerHTML = `
          <p class="mb-16">Found existing tracker(s):</p>
          ${existing
            .map(
              (g) => `
            <button class="btn btn-secondary btn-block mb-8 existing-gist" data-id="${g.id}">
              ${g.description || 'Personal Tracker'} <span class="text-secondary text-sm">(${g.id.slice(0, 8)}...)</span>
            </button>
          `,
            )
            .join('')}
          <div class="mt-16">
            <p class="text-secondary text-sm mb-8">Or start fresh:</p>
            <button id="create-new-btn" class="btn btn-primary btn-block">Create New Tracker</button>
          </div>
          <div class="mt-16">
            <p class="text-secondary text-sm mb-8">Or enter a gist ID:</p>
            <input type="text" id="gist-id-input" class="form-input" placeholder="Gist ID" />
            <button id="use-gist-btn" class="btn btn-secondary btn-block mt-8">Use This Gist</button>
          </div>
        `;

        gistOptions.querySelectorAll('.existing-gist').forEach((btn) => {
          btn.addEventListener('click', () => {
            const id = (btn as HTMLElement).dataset['id'];
            if (id) {
              setGistId(id);
              onComplete();
            }
          });
        });
      } else {
        gistOptions.innerHTML = `
          <button id="create-new-btn" class="btn btn-primary btn-block">Create New Tracker</button>
          <div class="mt-16">
            <p class="text-secondary text-sm mb-8">Or enter an existing gist ID:</p>
            <input type="text" id="gist-id-input" class="form-input" placeholder="Gist ID" />
            <button id="use-gist-btn" class="btn btn-secondary btn-block mt-8">Use This Gist</button>
          </div>
        `;
      }

      const createBtn = gistOptions.querySelector('#create-new-btn');
      createBtn?.addEventListener('click', async () => {
        (createBtn as HTMLButtonElement).disabled = true;
        (createBtn as HTMLButtonElement).textContent = 'Creating...';
        try {
          const gistId = await createTrackerGist(token);
          setGistId(gistId);
          showToast('Tracker created!', 'success');
          onComplete();
        } catch (err) {
          showToast(`Failed to create: ${err instanceof Error ? err.message : err}`, 'error');
          (createBtn as HTMLButtonElement).disabled = false;
          (createBtn as HTMLButtonElement).textContent = 'Create New Tracker';
        }
      });

      const useGistBtn = gistOptions.querySelector('#use-gist-btn');
      const gistIdInput = gistOptions.querySelector('#gist-id-input') as HTMLInputElement | null;
      useGistBtn?.addEventListener('click', () => {
        const id = gistIdInput?.value.trim();
        if (id) {
          setGistId(id);
          onComplete();
        } else {
          showToast('Please enter a gist ID', 'error');
        }
      });
    } catch (err) {
      showToast(
        `Connection failed: ${err instanceof Error ? err.message : err}`,
        'error',
      );
      connectBtn.disabled = false;
      connectBtn.textContent = 'Connect';
    }
  });

  // Allow Enter key on token input
  tokenInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') connectBtn.click();
  });
}
