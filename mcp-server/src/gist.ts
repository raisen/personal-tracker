const API_BASE = "https://api.github.com";
const CONFIG_FILE = "tracker-config.json";
const DATA_FILE = "tracker-data.json";

export interface FieldConfig {
  id: string;
  type: string;
  label: string;
  icon: string;
  required: boolean;
  options?: string[];
  min?: number;
  max?: number;
  step?: number;
  multiline?: boolean;
  placeholder?: string;
  showInList?: boolean;
}

export interface InsightPrompt {
  label: string;
  prompt: string;
  dataRangeDays?: number | null;
}

export interface TrackerConfig {
  version: number;
  title: string;
  fields: FieldConfig[];
  prompts?: InsightPrompt[];
}

export interface Entry {
  _id: string;
  _created: string;
  _updated: string;
  [key: string]: unknown;
}

export interface TrackerData {
  entries: Entry[];
}

interface GistResponse {
  id: string;
  description: string;
  files: Record<string, { filename: string; content: string }>;
  html_url: string;
}

function headers(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "Content-Type": "application/json",
  };
}

async function request<T>(
  token: string,
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...headers(token),
      ...(options.headers as Record<string, string> | undefined),
    },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`GitHub API error ${res.status}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export async function loadGist(
  token: string,
  gistId: string
): Promise<{ config: TrackerConfig; data: TrackerData }> {
  const gist = await request<GistResponse>(token, `/gists/${gistId}`);
  const configFile = gist.files[CONFIG_FILE];
  const dataFile = gist.files[DATA_FILE];
  if (!configFile || !dataFile) {
    throw new Error("Gist is missing tracker files. Is this the right gist?");
  }
  return {
    config: JSON.parse(configFile.content) as TrackerConfig,
    data: JSON.parse(dataFile.content) as TrackerData,
  };
}

export async function saveData(
  token: string,
  gistId: string,
  data: TrackerData
): Promise<void> {
  await request(token, `/gists/${gistId}`, {
    method: "PATCH",
    body: JSON.stringify({
      files: {
        [DATA_FILE]: { content: JSON.stringify(data, null, 2) },
      },
    }),
  });
}

export async function listTrackerGists(
  token: string
): Promise<{ id: string; description: string | null }[]> {
  const gists = await request<GistResponse[]>(token, "/gists?per_page=100");
  return gists
    .filter((g) => g.files[CONFIG_FILE] && g.files[DATA_FILE])
    .map((g) => ({ id: g.id, description: g.description }));
}
