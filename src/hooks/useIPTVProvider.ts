import { useQuery } from '@tanstack/react-query';
import { IPTVChannel, IPTVCategory, XtreamCredentials, XtreamUserInfo, XtreamServerInfo } from '../types';
import { proxyStreamUrl } from '../lib/iptvProxy';

// ─── M3U Parser ───────────────────────────────────────────────────────────────

export function parseM3U(text: string): IPTVChannel[] {
  if (!text.startsWith('#EXTM3U') || !text.includes('#EXTINF')) {
    throw new Error('Malformed M3U playlist: missing #EXTM3U header or #EXTINF entries');
  }

  const channels: IPTVChannel[] = [];
  const lines = text.split('\n').map(l => l.trim());

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!line.startsWith('#EXTINF')) continue;

    const namePart = line.split(',').slice(1).join(',').trim();
    const tvgName = line.match(/tvg-name="([^"]*)"/)?.[1] || namePart;
    const tvgLogo = line.match(/tvg-logo="([^"]*)"/)?.[1] || '';
    const groupTitle = line.match(/group-title="([^"]*)"/)?.[1] || 'General';
    const tvgId = line.match(/tvg-id="([^"]*)"/)?.[1] || '';

    let streamUrl = '';
    for (let j = i + 1; j < lines.length; j++) {
      if (lines[j] && !lines[j].startsWith('#')) {
        streamUrl = lines[j];
        break;
      }
    }

    if (!streamUrl) continue;

    channels.push({
      id: `m3u-${i}`,
      name: tvgName || namePart,
      streamUrl,
      logo: tvgLogo || undefined,
      category: groupTitle,
      epgChannelId: tvgId || undefined,
    });
  }

  return channels;
}

// ─── Network error helpers ────────────────────────────────────────────────────

/** Extracts just the hostname from a URL for safe inclusion in error messages. */
function safeHostname(url: string): string {
  try { return new URL(url).hostname; } catch { return url; }
}

/**
 * Converts a network-level fetch failure (TypeError: Failed to fetch, etc.)
 * into an actionable error message that includes the hostname and likely causes.
 */
function networkError(url: string, err: unknown): Error {
  const cause = err instanceof Error ? err.message : String(err);
  return new Error(
    `Network error reaching "${safeHostname(url)}" — ${cause}. ` +
    `Possible causes: the server is offline, the URL is wrong, DNS cannot resolve the hostname, or a firewall/CORS policy is blocking the request.`
  );
}

// ─── Xtream API helpers ───────────────────────────────────────────────────────

function xtreamApiUrl(creds: XtreamCredentials, params: Record<string, string>) {
  const base = creds.server.replace(/\/$/, '');
  const query = new URLSearchParams({ username: creds.username, password: creds.password, ...params });
  return `${base}/player_api.php?${query.toString()}`;
}

export function xtreamStreamUrl(creds: XtreamCredentials, streamId: string | number, ext = 'm3u8') {
  const base = creds.server.replace(/\/$/, '');
  return `${base}/live/${creds.username}/${creds.password}/${streamId}.${ext}`;
}

async function fetchXtream<T>(url: string): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url);
  } catch (networkErr) {
    throw networkError(url, networkErr);
  }
  if (res.status === 403) {
    throw new Error('Access denied (403 Forbidden). Your subscription may be expired or your IP is blocked.');
  }
  if (!res.ok) {
    throw new Error(`Server returned ${res.status} ${res.statusText}`);
  }
  try {
    return await res.json() as T;
  } catch (parseErr) {
    const detail = parseErr instanceof Error ? parseErr.message : String(parseErr);
    throw new Error(
      `Server returned a non-JSON response (HTTP ${res.status}): ${detail}. ` +
      `The server URL may be incorrect, this may not be an Xtream Codes server, or the server returned an error page.`
    );
  }
}

export interface XtreamAuthResult {
  userInfo: XtreamUserInfo;
  serverInfo: XtreamServerInfo;
}

export async function xtreamAuth(creds: XtreamCredentials): Promise<XtreamAuthResult> {
  const data = await fetchXtream<Record<string, unknown>>(xtreamApiUrl(creds, {}));
  if (!data.user_info) throw new Error('Invalid server response — not an Xtream Codes server?');

  const ui = data.user_info as Record<string, unknown>;
  if (ui['auth'] === 0) throw new Error('Invalid username or password');

  const status = String(ui['status'] ?? '');
  if (status === 'Expired') {
    throw new Error('Your IPTV subscription has expired. Please renew to continue watching.');
  }

  const si = (data.server_info ?? {}) as Record<string, unknown>;

  return {
    userInfo: {
      username: String(ui['username'] ?? ''),
      password: String(ui['password'] ?? ''),
      status,
      expDate: ui['exp_date']
        ? new Date(Number(ui['exp_date']) * 1000).toLocaleDateString()
        : undefined,
      isTrial: String(ui['is_trial'] ?? ''),
      activeCons: String(ui['active_cons'] ?? ''),
      maxConnections: String(ui['max_connections'] ?? ''),
    },
    serverInfo: {
      url: String(si['url'] ?? ''),
      port: String(si['port'] ?? ''),
      httpsPort: si['https_port'] ? String(si['https_port']) : undefined,
      serverProtocol: String(si['server_protocol'] ?? 'http'),
      rtmpPort: si['rtmp_port'] ? String(si['rtmp_port']) : undefined,
      timezone: si['timezone'] ? String(si['timezone']) : undefined,
      timestampNow: si['timestamp_now'] ? Number(si['timestamp_now']) : undefined,
      timeNow: si['time_now'] ? String(si['time_now']) : undefined,
    },
  };
}

async function xtreamGetLiveCategories(creds: XtreamCredentials): Promise<IPTVCategory[]> {
  const data = await fetchXtream<Array<{ category_id: string; category_name: string }>>(
    xtreamApiUrl(creds, { action: 'get_live_categories' })
  );
  return (Array.isArray(data) ? data : []).map(c => ({
    id: String(c.category_id),
    name: c.category_name,
  }));
}

async function xtreamGetLiveStreams(
  creds: XtreamCredentials,
  categoryId?: string
): Promise<IPTVChannel[]> {
  const params: Record<string, string> = { action: 'get_live_streams' };
  if (categoryId) params['category_id'] = categoryId;

  const data = await fetchXtream<
    Array<{
      stream_id: number;
      name: string;
      stream_icon?: string;
      category_id?: string;
      epg_channel_id?: string;
    }>
  >(xtreamApiUrl(creds, params));

  return (Array.isArray(data) ? data : []).map(ch => ({
    id: String(ch.stream_id),
    name: ch.name,
    streamUrl: xtreamStreamUrl(creds, ch.stream_id),
    logo: ch.stream_icon || undefined,
    category: ch.category_id || '',
    epgChannelId: ch.epg_channel_id || undefined,
  }));
}

// ─── TanStack Query Hooks ─────────────────────────────────────────────────────

export interface XtreamProviderResult {
  userInfo: XtreamUserInfo;
  serverInfo: XtreamServerInfo;
  categories: IPTVCategory[];
  channels: IPTVChannel[];
}

export function useXtreamProvider(creds: XtreamCredentials | null) {
  return useQuery<XtreamProviderResult>({
    queryKey: ['xtream', creds?.server, creds?.username],
    enabled: !!creds,
    staleTime: 5 * 60 * 1000,
    retry: (failureCount, error) => {
      // Don't retry on auth errors or expired subscriptions
      const msg = error instanceof Error ? error.message : '';
      if (msg.includes('expired') || msg.includes('Invalid username') || msg.includes('403')) {
        return false;
      }
      return failureCount < 2;
    },
    queryFn: async () => {
      if (!creds) throw new Error('No credentials');
      const { userInfo, serverInfo } = await xtreamAuth(creds);
      const [categories, channels] = await Promise.all([
        xtreamGetLiveCategories(creds),
        xtreamGetLiveStreams(creds),
      ]);
      return { userInfo, serverInfo, categories, channels };
    },
  });
}

export interface M3UProviderResult {
  categories: IPTVCategory[];
  channels: IPTVChannel[];
}

export function useM3UProvider(url: string | null) {
  return useQuery<M3UProviderResult>({
    queryKey: ['m3u', url],
    enabled: !!url,
    staleTime: 10 * 60 * 1000,
    retry: (failureCount, error) => {
      const msg = error instanceof Error ? error.message : '';
      if (msg.includes('403') || msg.includes('Malformed')) {
        return false;
      }
      return failureCount < 2;
    },
    queryFn: async () => {
      if (!url) throw new Error('No URL');

      const isExternal = /^https?:\/\//i.test(url);
      const fetchUrl = isExternal ? proxyStreamUrl(url) : url;

      let res: Response;
      try {
        res = await fetch(fetchUrl);
      } catch (networkErr) {
        throw networkError(url, networkErr);
      }
      if (res.status === 403) {
        throw new Error(
          'Access denied (403 Forbidden). The playlist URL may require authentication or a specific User-Agent.'
        );
      }
      if (!res.ok) {
        throw new Error(`Failed to fetch M3U playlist: server returned ${res.status} ${res.statusText}`);
      }
      const text = await res.text();
      const channels = parseM3U(text);
      if (channels.length === 0) {
        throw new Error('No channels found in the M3U playlist. The file may be empty or malformed.');
      }
      const categories: IPTVCategory[] = Array.from(new Set(channels.map(c => c.category))).map(
        name => ({ id: name, name })
      );
      return { categories, channels };
    },
  });
}
