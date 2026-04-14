import type { VercelRequest, VercelResponse } from '@vercel/node';

// Maximum response body to pipe (protect against memory exhaustion)
const MAX_RESPONSE_SIZE = 50 * 1024 * 1024; // 50 MB

// Hop-by-hop headers that must not be forwarded
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'host',
]);

export default async function handler(req: VercelRequest, res: VercelResponse) {
  // Only allow GET/HEAD — no POST/PUT proxying
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const rawUrl = req.query['url'];
  const streamUrl = Array.isArray(rawUrl) ? rawUrl[0] : rawUrl;

  if (!streamUrl) {
    res.status(400).json({ error: 'Missing `url` query parameter' });
    return;
  }

  // Validate that the target is a proper HTTP(S) URL
  let target: URL;
  try {
    target = new URL(streamUrl);
  } catch {
    res.status(400).json({ error: 'Invalid URL' });
    return;
  }

  if (target.protocol !== 'http:' && target.protocol !== 'https:') {
    res.status(400).json({ error: 'Only http/https URLs are allowed' });
    return;
  }

  try {
    const upstream = await fetch(target.toString(), {
      method: req.method,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (SmartTV; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        Accept: '*/*',
        'Accept-Language': 'en-US,en;q=0.9',
        // Forward Referer if provided by client
        ...(req.headers['referer'] ? { Referer: req.headers['referer'] as string } : {}),
      },
      // Don't follow redirects automatically — pass them through
      redirect: 'manual',
    });

    // Forward status
    res.status(upstream.status);

    // Forward safe upstream headers
    upstream.headers.forEach((value, key) => {
      if (!HOP_BY_HOP.has(key.toLowerCase())) {
        res.setHeader(key, value);
      }
    });

    // CORS headers so the browser can load the proxied stream
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Type');

    if (req.method === 'HEAD' || !upstream.body) {
      res.end();
      return;
    }

    // Stream the body in chunks to avoid buffering the whole thing in memory
    const reader = upstream.body.getReader();
    let totalBytes = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      totalBytes += value.byteLength;
      if (totalBytes > MAX_RESPONSE_SIZE) {
        // Inform client with a 413 before closing. Because headers are already
        // sent, we can only terminate the stream at this point.
        console.warn('[proxy] Response exceeded MAX_RESPONSE_SIZE; terminating stream');
        reader.cancel().catch(() => {});
        res.end();
        break;
      }

      res.write(Buffer.from(value));
    }

    res.end();
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Proxy error';
    if (!res.headersSent) {
      res.status(502).json({ error: message });
    } else {
      res.end();
    }
  }
}
