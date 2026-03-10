import type { TrackerConfig, TrackerData, GistResponse } from './types';

const API_BASE = 'https://api.github.com';
const CONFIG_FILE = 'tracker-config.json';
const DATA_FILE = 'tracker-data.json';

function headers(token: string): HeadersInit {
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'Content-Type': 'application/json',
  };
}

async function request<T>(
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { ...headers(token), ...(options.headers as Record<string, string> | undefined) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`GitHub API error ${res.status}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export async function validateToken(token: string): Promise<{ login: string }> {
  return request<{ login: string }>(token, '/user');
}

const DEFAULT_CONFIG: TrackerConfig = {
  version: 1,
  title: 'My Tracker',
  fields: [
    { id: 'date', type: 'date', label: 'Date', icon: '📅', required: true },
    {
      id: 'mood',
      type: 'select',
      label: 'Mood',
      icon: '😊',
      required: true,
      options: ['Great', 'Good', 'Okay', 'Bad', 'Terrible'],
    },
    { id: 'notes', type: 'text', label: 'Notes', icon: '📝', required: false, multiline: true },
  ],
};

const DEFAULT_DATA: TrackerData = { entries: [] };

export async function createTrackerGist(token: string): Promise<string> {
  const gist = await request<GistResponse>(token, '/gists', {
    method: 'POST',
    body: JSON.stringify({
      description: 'Personal Tracker Data',
      public: false,
      files: {
        [CONFIG_FILE]: { content: JSON.stringify(DEFAULT_CONFIG, null, 2) },
        [DATA_FILE]: { content: JSON.stringify(DEFAULT_DATA, null, 2) },
      },
    }),
  });
  return gist.id;
}

export async function loadGist(
  token: string,
  gistId: string,
): Promise<{ config: TrackerConfig; data: TrackerData }> {
  const gist = await request<GistResponse>(token, `/gists/${gistId}`);
  const configFile = gist.files[CONFIG_FILE];
  const dataFile = gist.files[DATA_FILE];
  if (!configFile || !dataFile) {
    throw new Error('Gist is missing tracker files. Is this the right gist?');
  }
  return {
    config: JSON.parse(configFile.content) as TrackerConfig,
    data: JSON.parse(dataFile.content) as TrackerData,
  };
}

export async function saveConfig(
  token: string,
  gistId: string,
  config: TrackerConfig,
): Promise<void> {
  await request(token, `/gists/${gistId}`, {
    method: 'PATCH',
    body: JSON.stringify({
      files: {
        [CONFIG_FILE]: { content: JSON.stringify(config, null, 2) },
      },
    }),
  });
}

export async function saveData(
  token: string,
  gistId: string,
  data: TrackerData,
): Promise<void> {
  await request(token, `/gists/${gistId}`, {
    method: 'PATCH',
    body: JSON.stringify({
      files: {
        [DATA_FILE]: { content: JSON.stringify(data, null, 2) },
      },
    }),
  });
}

export async function listGists(
  token: string,
): Promise<{ id: string; description: string | null }[]> {
  const gists = await request<GistResponse[]>(token, '/gists?per_page=100');
  return gists
    .filter((g) => g.files[CONFIG_FILE] && g.files[DATA_FILE])
    .map((g) => ({ id: g.id, description: g.description }));
}
