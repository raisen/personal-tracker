const TOKEN_KEY = 'pt_github_token';
const GIST_ID_KEY = 'pt_gist_id';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function getGistId(): string | null {
  // Check URL hash first, then localStorage
  const hash = window.location.hash;
  const match = hash.match(/gist=([a-f0-9]+)/);
  if (match?.[1]) {
    setGistId(match[1]);
    return match[1];
  }
  return localStorage.getItem(GIST_ID_KEY);
}

export function setGistId(id: string): void {
  localStorage.setItem(GIST_ID_KEY, id);
}

export function clearGistId(): void {
  localStorage.removeItem(GIST_ID_KEY);
}

export function isAuthenticated(): boolean {
  return getToken() !== null && getGistId() !== null;
}

export function disconnect(): void {
  clearToken();
  clearGistId();
}

export function getShareableUrl(): string {
  const gistId = getGistId();
  if (!gistId) return window.location.origin + window.location.pathname;
  return `${window.location.origin}${window.location.pathname}#gist=${gistId}`;
}
