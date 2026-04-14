import { useState, useRef, useMemo, useCallback } from 'react';
import {
  ArrowLeft,
  Tv,
  Search,
  LogOut,
  RefreshCw,
  Wifi,
  List,
  ChevronRight,
  AlertCircle,
  Loader2,
  Signal,
} from 'lucide-react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useTheme } from '../context/ThemeContext';
import { getIPTVCredentials, saveIPTVCredentials, clearIPTVCredentials } from '../lib/storage';
import {
  IPTVCredentials,
  IPTVChannel,
  IPTVCategory,
  XtreamCredentials,
  M3UCredentials,
  XtreamUserInfo,
} from '../types';
import { Input } from './ui/input';
import { useXtreamProvider, useM3UProvider } from '../hooks/useIPTVProvider';
import { useQueryClient } from '@tanstack/react-query';

interface IPTVViewProps {
  onBack: () => void;
  onPlay: (
    tmdbId: number,
    mediaType: 'movie' | 'tv' | 'live',
    season?: number,
    episode?: number,
    embedUrl?: string,
    channelName?: string
  ) => void;
  onGoHome: () => void;
}

// ─── Virtual Channel List ─────────────────────────────────────────────────────

interface VirtualChannelListProps {
  channels: IPTVChannel[];
  onPlay: (channel: IPTVChannel) => void;
  dark: boolean;
  cardClass: string;
  textClass: string;
  subTextClass: string;
}

function VirtualChannelList({
  channels,
  onPlay,
  dark,
  cardClass,
  textClass,
  subTextClass,
}: VirtualChannelListProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  const rowVirtualizer = useVirtualizer({
    count: channels.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 68,
    overscan: 10,
  });

  if (channels.length === 0) {
    return (
      <div className={`flex flex-col items-center justify-center py-20 gap-3 ${subTextClass}`}>
        <Tv size={40} className="opacity-30" />
        <p className="text-sm">No channels found</p>
      </div>
    );
  }

  return (
    <div ref={parentRef} className="overflow-y-auto flex-1" style={{ contain: 'strict' }}>
      <div style={{ height: rowVirtualizer.getTotalSize(), width: '100%', position: 'relative' }}>
        {rowVirtualizer.getVirtualItems().map(virtualRow => {
          const channel = channels[virtualRow.index];
          return (
            <div
              key={virtualRow.key}
              data-index={virtualRow.index}
              ref={rowVirtualizer.measureElement}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${virtualRow.start}px)`,
              }}
              className="px-4 py-1.5"
            >
              <button
                onClick={() => onPlay(channel)}
                className={`group flex items-center gap-3 p-3 rounded-xl border text-left transition-all hover:border-blue-500 hover:shadow-md w-full ${cardClass}`}
              >
                <div
                  className={`w-10 h-10 rounded-lg flex-shrink-0 flex items-center justify-center overflow-hidden ${
                    dark ? 'bg-gray-800' : 'bg-gray-100'
                  }`}
                >
                  {channel.logo ? (
                    <img
                      src={channel.logo}
                      alt={channel.name}
                      className="w-full h-full object-contain"
                      onError={e => {
                        (e.currentTarget as HTMLImageElement).style.display = 'none';
                        const sibling = e.currentTarget.nextElementSibling as HTMLElement | null;
                        if (sibling) sibling.style.display = '';
                      }}
                    />
                  ) : null}
                  <Tv
                    size={18}
                    className="text-blue-500"
                    style={channel.logo ? { display: 'none' } : undefined}
                  />
                </div>

                <div className="flex-1 min-w-0">
                  <p className={`text-sm font-medium truncate ${textClass}`}>{channel.name}</p>
                  <p className={`text-xs truncate ${subTextClass}`}>{channel.category}</p>
                </div>

                <ChevronRight
                  size={16}
                  className={`flex-shrink-0 ${subTextClass} group-hover:text-blue-500 transition-colors`}
                />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

type LoginTab = 'xtream' | 'm3u';
type AppState = 'login' | 'connecting' | 'channels' | 'error';

export function IPTVView({ onBack, onPlay, onGoHome }: IPTVViewProps) {
  const { effectiveTheme } = useTheme();
  const queryClient = useQueryClient();

  const dark = effectiveTheme === 'dark';
  const bgClass = dark ? 'bg-black' : 'bg-gray-50';
  const cardClass = dark ? 'bg-gray-900/60 border-gray-800' : 'bg-white border-gray-200';
  const textClass = dark ? 'text-white' : 'text-gray-900';
  const subTextClass = dark ? 'text-gray-400' : 'text-gray-500';
  const inputClass = dark
    ? 'bg-gray-800 border-gray-700 text-white placeholder-gray-500 focus:border-blue-500'
    : 'bg-white border-gray-300 text-gray-900 placeholder-gray-400 focus:border-blue-500';

  const [loginTab, setLoginTab] = useState<LoginTab>('xtream');
  const [xtreamServer, setXtreamServer] = useState('');
  const [xtreamUser, setXtreamUser] = useState('');
  const [xtreamPass, setXtreamPass] = useState('');
  const [m3uUrl, setM3uUrl] = useState('');

  const [credentials, setCredentials] = useState<IPTVCredentials | null>(() => getIPTVCredentials());
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const xtreamCreds: XtreamCredentials | null =
    credentials?.type === 'xtream' && credentials.xtream ? credentials.xtream : null;
  const m3uUrlActive: string | null =
    credentials?.type === 'm3u' && credentials.m3u ? credentials.m3u.url : null;

  const xtreamQuery = useXtreamProvider(xtreamCreds);
  const m3uQuery = useM3UProvider(m3uUrlActive);

  const activeQuery = credentials?.type === 'xtream' ? xtreamQuery : m3uQuery;

  const categories: IPTVCategory[] = useMemo(
    () => activeQuery.data?.categories ?? [],
    [activeQuery.data]
  );
  const channels: IPTVChannel[] = useMemo(
    () => activeQuery.data?.channels ?? [],
    [activeQuery.data]
  );
  const userInfo: XtreamUserInfo | null =
    credentials?.type === 'xtream' && xtreamQuery.data ? xtreamQuery.data.userInfo : null;

  const appState: AppState = useMemo(() => {
    if (!credentials) return 'login';
    if (activeQuery.isFetching && !activeQuery.data) return 'connecting';
    if (activeQuery.isError) return 'error';
    if (activeQuery.isSuccess) return 'channels';
    return 'connecting';
  }, [credentials, activeQuery.isFetching, activeQuery.data, activeQuery.isError, activeQuery.isSuccess]);

  const errorMsg =
    activeQuery.error instanceof Error ? activeQuery.error.message : 'Connection failed';

  const filteredChannels = useMemo(() => {
    return channels.filter(ch => {
      const matchCat =
        selectedCategory === 'all' ||
        ch.category === selectedCategory ||
        ch.id === selectedCategory;
      const matchSearch =
        !searchQuery || ch.name.toLowerCase().includes(searchQuery.toLowerCase());
      return matchCat && matchSearch;
    });
  }, [channels, selectedCategory, searchQuery]);

  function handleLogin() {
    let creds: IPTVCredentials;
    if (loginTab === 'xtream') {
      if (!xtreamServer.trim() || !xtreamUser.trim() || !xtreamPass.trim()) return;
      let server = xtreamServer.trim();
      if (!server.startsWith('http://') && !server.startsWith('https://')) {
        server = `http://${server}`;
      }
      const xtream: XtreamCredentials = {
        server,
        username: xtreamUser.trim(),
        password: xtreamPass.trim(),
      };
      creds = { type: 'xtream', xtream, savedAt: new Date().toISOString() };
    } else {
      if (!m3uUrl.trim()) return;
      const m3u: M3UCredentials = { url: m3uUrl.trim() };
      creds = { type: 'm3u', m3u, savedAt: new Date().toISOString() };
    }
    saveIPTVCredentials(creds);
    setCredentials(creds);
  }

  const handleLogout = useCallback(() => {
    clearIPTVCredentials();
    setCredentials(null);
    setSelectedCategory('all');
    setSearchQuery('');
    setXtreamServer('');
    setXtreamUser('');
    setXtreamPass('');
    setM3uUrl('');
    queryClient.removeQueries({ queryKey: ['xtream'] });
    queryClient.removeQueries({ queryKey: ['m3u'] });
  }, [queryClient]);

  const handleRefresh = useCallback(() => {
    if (credentials?.type === 'xtream' && xtreamCreds) {
      void queryClient.invalidateQueries({
        queryKey: ['xtream', xtreamCreds.server, xtreamCreds.username],
      });
    } else if (credentials?.type === 'm3u' && m3uUrlActive) {
      void queryClient.invalidateQueries({ queryKey: ['m3u', m3uUrlActive] });
    }
  }, [credentials, xtreamCreds, m3uUrlActive, queryClient]);

  function handlePlayChannel(channel: IPTVChannel) {
    onPlay(0, 'live', undefined, undefined, channel.streamUrl, channel.name);
  }

  // ── Render: Login ──────────────────────────────────────────────────────────

  const renderLogin = (loginError?: string) => (
    <div className="flex-1 flex items-center justify-center p-4">
      <div className={`w-full max-w-md rounded-2xl border ${cardClass} p-8 shadow-xl`}>
        <div className="flex justify-center mb-6">
          <div className="w-16 h-16 rounded-full bg-blue-600/20 flex items-center justify-center">
            <Signal size={32} className="text-blue-500" />
          </div>
        </div>

        <h2 className={`text-2xl font-bold text-center mb-1 ${textClass}`}>IPTV</h2>
        <p className={`text-sm text-center mb-6 ${subTextClass}`}>
          Connect your IPTV service to watch live channels
        </p>

        <div
          className={`flex rounded-lg overflow-hidden border mb-6 ${
            dark ? 'border-gray-700' : 'border-gray-200'
          }`}
        >
          <button
            onClick={() => setLoginTab('xtream')}
            className={`flex-1 py-2.5 text-sm font-medium transition-colors ${
              loginTab === 'xtream'
                ? 'bg-blue-600 text-white'
                : dark
                ? 'bg-gray-800 text-gray-400 hover:text-white'
                : 'bg-gray-100 text-gray-600 hover:text-gray-900'
            }`}
          >
            Xtream Codes
          </button>
          <button
            onClick={() => setLoginTab('m3u')}
            className={`flex-1 py-2.5 text-sm font-medium transition-colors ${
              loginTab === 'm3u'
                ? 'bg-blue-600 text-white'
                : dark
                ? 'bg-gray-800 text-gray-400 hover:text-white'
                : 'bg-gray-100 text-gray-600 hover:text-gray-900'
            }`}
          >
            M3U Playlist
          </button>
        </div>

        {loginError && (
          <div className="flex items-start gap-2 mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
            <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
            <span>{loginError}</span>
          </div>
        )}

        {loginTab === 'xtream' && (
          <div className="space-y-3">
            <div>
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>Server URL</label>
              <Input
                value={xtreamServer}
                onChange={e => setXtreamServer(e.target.value)}
                placeholder="http://your-server.com:8080"
                className={inputClass}
                onKeyDown={e => e.key === 'Enter' && handleLogin()}
              />
            </div>
            <div>
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>Username</label>
              <Input
                value={xtreamUser}
                onChange={e => setXtreamUser(e.target.value)}
                placeholder="your_username"
                className={inputClass}
                autoComplete="username"
                onKeyDown={e => e.key === 'Enter' && handleLogin()}
              />
            </div>
            <div>
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>Password</label>
              <Input
                type="password"
                value={xtreamPass}
                onChange={e => setXtreamPass(e.target.value)}
                placeholder="your_password"
                className={inputClass}
                autoComplete="current-password"
                onKeyDown={e => e.key === 'Enter' && handleLogin()}
              />
            </div>
          </div>
        )}

        {loginTab === 'm3u' && (
          <div>
            <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>
              M3U Playlist URL
            </label>
            <Input
              value={m3uUrl}
              onChange={e => setM3uUrl(e.target.value)}
              placeholder="http://your-server.com/playlist.m3u"
              className={inputClass}
              onKeyDown={e => e.key === 'Enter' && handleLogin()}
            />
            <p className={`text-xs mt-1.5 ${subTextClass}`}>
              Enter the direct URL to your M3U or M3U8 playlist file
            </p>
          </div>
        )}

        <button
          onClick={handleLogin}
          className="w-full mt-5 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm transition-colors"
        >
          Connect
        </button>
      </div>
    </div>
  );

  // ── Render: Loading ────────────────────────────────────────────────────────

  const renderLoading = () => (
    <div className="flex-1 flex flex-col items-center justify-center gap-4">
      <Loader2 size={40} className="text-blue-500 animate-spin" />
      <p className={`text-base font-medium ${subTextClass}`}>Connecting to your IPTV service…</p>
    </div>
  );

  // ── Render: Channel Browser ────────────────────────────────────────────────

  const renderChannels = () => (
    <div className="flex-1 flex flex-col min-h-0">
      <div className={`px-4 py-3 border-b ${dark ? 'border-gray-800' : 'border-gray-200'}`}>
        <div className="max-w-[1920px] mx-auto flex flex-col sm:flex-row gap-3 items-start sm:items-center">
          <div className="flex items-center gap-3 flex-shrink-0">
            <div className="flex items-center gap-2">
              <Wifi size={14} className="text-green-500" />
              <span className={`text-xs ${subTextClass}`}>
                {credentials?.type === 'xtream'
                  ? `Xtream · ${userInfo?.username ?? credentials.xtream?.username}`
                  : `M3U · ${channels.length} channels`}
              </span>
            </div>
            {credentials?.type === 'xtream' && userInfo?.expDate && (
              <span className={`text-xs ${subTextClass}`}>· Expires {userInfo.expDate}</span>
            )}
          </div>

          <div className="flex-1" />

          <div className="relative w-full sm:w-64">
            <Search size={14} className={`absolute left-3 top-1/2 -translate-y-1/2 ${subTextClass}`} />
            <Input
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Search channels…"
              className={`pl-8 h-8 text-sm ${inputClass}`}
            />
          </div>
        </div>
      </div>

      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* Category sidebar */}
        <aside
          className={`hidden sm:flex flex-col flex-shrink-0 w-48 xl:w-56 border-r overflow-y-auto ${
            dark ? 'border-gray-800' : 'border-gray-200'
          }`}
        >
          <button
            onClick={() => setSelectedCategory('all')}
            className={`flex items-center justify-between px-4 py-3 text-sm font-medium text-left transition-colors ${
              selectedCategory === 'all'
                ? 'bg-blue-600 text-white'
                : dark
                ? 'text-gray-300 hover:bg-gray-800'
                : 'text-gray-700 hover:bg-gray-100'
            }`}
          >
            <span className="flex items-center gap-2">
              <List size={14} />
              All Channels
            </span>
            <span className={`text-xs ${selectedCategory === 'all' ? 'text-blue-200' : subTextClass}`}>
              {channels.length}
            </span>
          </button>

          {categories.map(cat => {
            const count = channels.filter(
              c => c.category === cat.id || c.category === cat.name
            ).length;
            return (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={`flex items-center justify-between px-4 py-2.5 text-sm text-left transition-colors ${
                  selectedCategory === cat.id
                    ? 'bg-blue-600 text-white'
                    : dark
                    ? 'text-gray-300 hover:bg-gray-800'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <span className="truncate">{cat.name}</span>
                <span
                  className={`text-xs flex-shrink-0 ml-1 ${
                    selectedCategory === cat.id ? 'text-blue-200' : subTextClass
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </aside>

        {/* Channel list (virtualized) */}
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
          {/* Mobile category bar */}
          <div className="sm:hidden flex gap-2 overflow-x-auto px-4 pb-3 pt-3 no-scrollbar flex-shrink-0">
            <button
              onClick={() => setSelectedCategory('all')}
              className={`flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                selectedCategory === 'all'
                  ? 'bg-blue-600 text-white'
                  : dark
                  ? 'bg-gray-800 text-gray-300'
                  : 'bg-gray-200 text-gray-700'
              }`}
            >
              All
            </button>
            {categories.map(cat => (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={`flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                  selectedCategory === cat.id
                    ? 'bg-blue-600 text-white'
                    : dark
                    ? 'bg-gray-800 text-gray-300'
                    : 'bg-gray-200 text-gray-700'
                }`}
              >
                {cat.name}
              </button>
            ))}
          </div>

          <VirtualChannelList
            channels={filteredChannels}
            onPlay={handlePlayChannel}
            dark={dark}
            cardClass={cardClass}
            textClass={textClass}
            subTextClass={subTextClass}
          />
        </div>
      </div>
    </div>
  );

  // ── Full render ────────────────────────────────────────────────────────────

  return (
    <div className={`min-h-screen flex flex-col ${bgClass} ${textClass}`}>
      <header
        className={`fixed top-0 left-0 right-0 z-50 ${
          dark ? 'glass-header' : 'glass-header-light'
        }`}
      >
        <div className="max-w-[1920px] mx-auto px-4 sm:px-6 py-3 flex items-center justify-between">
          <button
            onClick={onBack}
            className={`flex items-center gap-2 ${textClass} hover:text-blue-400 transition-all hover:scale-105`}
          >
            <ArrowLeft size={20} />
            <span className="font-medium text-sm sm:text-base">Back</span>
          </button>

          <button
            onClick={onGoHome}
            className="text-base sm:text-xl font-bold hover:opacity-80 transition-opacity"
          >
            <span className="text-blue-500">Simpl</span>Stream
          </button>

          <div className="flex items-center gap-2">
            {appState === 'channels' && (
              <button
                onClick={handleRefresh}
                className={`p-2 rounded-lg transition-colors ${
                  dark ? 'hover:bg-gray-800' : 'hover:bg-gray-100'
                } ${subTextClass} hover:text-blue-500`}
                title="Refresh"
              >
                <RefreshCw size={18} />
              </button>
            )}
            {(appState === 'channels' || appState === 'error') && credentials && (
              <button
                onClick={handleLogout}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-colors ${
                  dark
                    ? 'hover:bg-gray-800 text-gray-400 hover:text-red-400'
                    : 'hover:bg-gray-100 text-gray-500 hover:text-red-500'
                }`}
                title="Disconnect"
              >
                <LogOut size={15} />
                <span className="hidden sm:inline">Disconnect</span>
              </button>
            )}
          </div>
        </div>

        <div className={`px-4 sm:px-6 pb-2 flex items-center gap-2 ${subTextClass}`}>
          <Signal size={14} />
          <span className="text-sm font-medium">IPTV</span>
          {appState === 'channels' && (
            <>
              <span>·</span>
              <span className="text-sm">{filteredChannels.length} channels</span>
            </>
          )}
        </div>
      </header>

      <div className="flex flex-col flex-1 pt-20">
        {appState === 'login'
          ? renderLogin()
          : appState === 'connecting'
          ? renderLoading()
          : appState === 'channels'
          ? renderChannels()
          : renderLogin(errorMsg)}
      </div>
    </div>
  );
}
