import { useEffect, useRef } from 'react';
import { MediaPlayer, MediaProvider, type MediaPlayerInstance } from '@vidstack/react';
import { DefaultVideoLayout, defaultLayoutIcons } from '@vidstack/react/player/layouts/default';
import { proxyStreamUrl } from '../lib/iptvProxy';

import '@vidstack/react/player/styles/base.css';
import '@vidstack/react/player/styles/default/theme.css';
import '@vidstack/react/player/styles/default/layouts/video.css';

interface IPTVPlayerProps {
  /** Raw IPTV stream URL (will be proxied automatically in production) */
  src: string;
  channelName?: string;
  className?: string;
}

export function IPTVPlayer({ src, channelName, className = '' }: IPTVPlayerProps) {
  const playerRef = useRef<MediaPlayerInstance>(null);

  // Proper cleanup when component unmounts to prevent HLS memory leaks.
  // The ref value is captured in a local variable so the cleanup runs against
  // the correct instance even if the ref has changed by then.
  useEffect(() => {
    const player = playerRef.current;
    return () => {
      if (player) {
        player.destroy();
      }
    };
  }, []);

  const proxiedSrc = proxyStreamUrl(src);

  return (
    <div
      className={`iptv-player-wrapper ${className}`}
      style={{ '--media-brand': '#3b82f6' } as React.CSSProperties}
    >
      <MediaPlayer
        ref={playerRef}
        src={proxiedSrc}
        title={channelName ?? 'Live Channel'}
        autoPlay
        playsInline
        className="w-full h-full"
        crossOrigin="anonymous"
        streamType="live"
        liveEdgeTolerance={10}
      >
        <MediaProvider />
        <DefaultVideoLayout
          icons={defaultLayoutIcons}
          thumbnails={undefined}
        />
      </MediaPlayer>
    </div>
  );
}

