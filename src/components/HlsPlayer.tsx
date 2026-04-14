import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { Loader2, AlertCircle, RefreshCw } from 'lucide-react';

interface HlsPlayerProps {
  src: string;
  channelName?: string;
  className?: string;
}

type PlayerState = 'loading' | 'playing' | 'error';

export function HlsPlayer({ src, channelName, className = '' }: HlsPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [state, setState] = useState<PlayerState>('loading');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;

    setState('loading');
    setErrorMsg('');

    function cleanup() {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    }

    // Prefer Content-Type detection; fall back to URL extension heuristic.
    // `.m3u8` extension is the reliable signal; treat unknown extensions as HLS
    // rather than relying on path segments like `/live/` that are ambiguous.
    const isHls =
      src.includes('.m3u8') ||
      (!src.match(/\.(mp4|webm|ogg|ts)(\?|$)/i) &&
        video.canPlayType('application/vnd.apple.mpegurl') === '');

    if (isHls && Hls.isSupported()) {
      cleanup();
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        backBufferLength: 30,
      });
      hlsRef.current = hls;

      hls.loadSource(src);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => {
          // Autoplay blocked — the user will click play manually
        });
        setState('playing');
      });

      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        let msg = 'Stream playback error.';
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          msg =
            'Cannot reach the stream. The channel may be offline, or CORS/access restrictions are in effect.';
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          msg = 'Media decoding error. The stream format may be unsupported.';
        }
        setErrorMsg(msg);
        setState('error');
        cleanup();
      });
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari native HLS
      video.src = src;
      video.addEventListener('loadedmetadata', () => {
        video.play().catch(() => {});
        setState('playing');
      });
      video.addEventListener('error', () => {
        setErrorMsg('Stream playback failed. The channel may be offline or inaccessible.');
        setState('error');
      });
    } else {
      // Non-HLS native video
      video.src = src;
      video.addEventListener('loadedmetadata', () => {
        video.play().catch(() => {});
        setState('playing');
      });
      video.addEventListener('error', () => {
        setErrorMsg('Unsupported stream format or playback error.');
        setState('error');
      });
    }

    return cleanup;
  }, [src]);

  function handleRetry() {
    // Destroy any existing HLS instance before creating a new one
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    setState('loading');
    setErrorMsg('');

    const video = videoRef.current;
    if (!video) return;

    if (Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 });
      hlsRef.current = hls;
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => {});
        setState('playing');
      });
      hls.on(Hls.Events.ERROR, (_e, data) => {
        if (!data.fatal) return;
        setErrorMsg('Stream playback error. The channel may be offline.');
        setState('error');
      });
    } else {
      video.load();
    }
  }

  return (
    <div className={`relative bg-black flex items-center justify-center ${className}`}>
      <video
        ref={videoRef}
        className="w-full h-full"
        controls
        playsInline
        title={channelName}
      />

      {state === 'loading' && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/80 gap-3">
          <Loader2 size={40} className="text-blue-500 animate-spin" />
          <p className="text-white text-sm">Loading stream…</p>
        </div>
      )}

      {state === 'error' && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/90 gap-4 p-6 text-center">
          <AlertCircle size={40} className="text-red-500" />
          <p className="text-white text-sm max-w-sm">{errorMsg}</p>
          <button
            onClick={handleRetry}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
          >
            <RefreshCw size={14} />
            Retry
          </button>
        </div>
      )}
    </div>
  );
}
