/**
 * Wraps a raw IPTV stream URL in the Vercel proxy route so the browser avoids
 * CORS restrictions imposed by most IPTV providers.
 *
 * In local development (localhost / 127.0.0.1) the proxy is skipped and the
 * stream URL is returned as-is, which avoids an unnecessary round-trip during
 * development where CORS is usually not enforced.
 */
export function proxyStreamUrl(rawUrl: string): string {
  if (!rawUrl) return rawUrl;

  const isLocal =
    typeof window !== 'undefined' &&
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

  if (isLocal) return rawUrl;

  return `/api/proxy?url=${encodeURIComponent(rawUrl)}`;
}
