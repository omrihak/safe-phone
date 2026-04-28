// Read-only client for the GitHub Gist REST API. Mirrors the surface of
// app/src/main/java/com/safephone/cloud/GistClient.kt that the Android app
// uses (DEFAULT_FILE, fallback-to-first-file, version pinning), minus
// createGist/updateGist — the extension never writes back.

const API_BASE = "https://api.github.com";
export const DEFAULT_FILE = "safephone-policy.json";

const TIMEOUT_MS = 15_000;

/**
 * @returns {Promise<{
 *   ok: true, content: string, updatedAt: string,
 * } | {
 *   ok: false, httpCode: number, message: string,
 * }>}
 */
export async function readGist({ token, gistId, fileName = DEFAULT_FILE }) {
  if (!token) {
    return { ok: false, httpCode: 0, message: "No GitHub token configured" };
  }
  if (!gistId || !gistId.trim()) {
    return { ok: false, httpCode: 0, message: "No gist id configured" };
  }

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  let res;
  try {
    res = await fetch(`${API_BASE}/gists/${encodeURIComponent(gistId.trim())}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "SafePhone-Chrome",
      },
      signal: ctrl.signal,
    });
  } catch (e) {
    clearTimeout(timer);
    return { ok: false, httpCode: -1, message: shortError(e) };
  }
  clearTimeout(timer);

  if (!res.ok) {
    const body = await safeText(res);
    return { ok: false, httpCode: res.status, message: shortDescription(body) };
  }

  let json;
  try {
    json = await res.json();
  } catch (e) {
    return { ok: false, httpCode: res.status, message: "Gist payload was not JSON" };
  }

  const files = json?.files;
  if (!files || typeof files !== "object" || Object.keys(files).length === 0) {
    return { ok: false, httpCode: res.status, message: "Gist has no readable files" };
  }
  const file = files[fileName] ?? Object.values(files)[0];
  const content = file?.content;
  if (typeof content !== "string" || content.length === 0) {
    return { ok: false, httpCode: res.status, message: "Gist file content was empty" };
  }
  return { ok: true, content, updatedAt: json.updated_at ?? "" };
}

/**
 * Translate a Gist failure into the same human-readable wording the Android
 * CloudSyncManager.formatHttpError surfaces, so users see consistent errors
 * across phone and browser.
 */
export function formatGistError(action, code, body) {
  switch (code) {
    case 0:
      return body || `Cannot ${action}: missing credentials`;
    case -1:
      return `Could not reach GitHub during ${action}: ${body || "network error"}`;
    case 401:
      return "GitHub rejected the token (check the gist scope)";
    case 403:
      return "GitHub rate-limit or permission error";
    case 404:
      return "Gist not found — check the configured gist id";
    default:
      return `GitHub ${action} failed (HTTP ${code}): ${body || ""}`.trim();
  }
}

async function safeText(res) {
  try {
    return await res.text();
  } catch (_) {
    return "";
  }
}

function shortDescription(body) {
  if (!body) return "(no response body)";
  return body.length <= 240 ? body : body.slice(0, 240) + "…";
}

function shortError(e) {
  if (!e) return "I/O error";
  if (e.name === "AbortError") return "Timed out talking to GitHub";
  return e.message || String(e);
}
