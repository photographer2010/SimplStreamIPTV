import { useState, useEffect, useCallback } from 'react';
import {
  ArrowLeft,
  Tv,
  Search,
  LogOut,
  RefreshCw,
  Play,
  Wifi,
  List,
  ChevronRight,
  AlertCircle,
  Loader2,
  Signal,
} from 'lucide-react';
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

// ─── M3U Parser ──────────────────────────────────────────────────────────────

function parseM3U(text: string): IPTVChannel[] {
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

    // Find the next non-comment, non-empty line as the stream URL
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

// ─── Xtream API helpers ───────────────────────────────────────────────────────

function xtreamApiUrl(creds: XtreamCredentials, params: Record<string, string>) {
  const base = creds.server.replace(/\/$/, '');
  const query = new URLSearchParams({ username: creds.username, password: creds.password, ...params });
  return `${base}/player_api.php?${query.toString()}`;
}

function xtreamStreamUrl(creds: XtreamCredentials, streamId: string | number, ext = 'm3u8') {
  const base = creds.server.replace(/\/$/, '');
  return `${base}/live/${creds.username}/${creds.password}/${streamId}.${ext}`;
}

async function xtreamAuth(
  creds: XtreamCredentials
): Promise<{ userInfo: XtreamUserInfo; serverInfo: Record<string, unknown> }> {
  const url = xtreamApiUrl(creds, {});
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Server returned ${res.status}`);
  const data = await res.json();
  if (!data.user_info) throw new Error('Invalid server response — not an Xtream Codes server?');
  if (data.user_info.auth === 0) throw new Error('Invalid username or password');

  return {
    userInfo: {
      username: data.user_info.username,
      password: data.user_info.password,
      status: data.user_info.status,
      expDate: data.user_info.exp_date
        ? new Date(Number(data.user_info.exp_date) * 1000).toLocaleDateString()
        : undefined,
      isTrial: data.user_info.is_trial,
      activeCons: data.user_info.active_cons,
      maxConnections: data.user_info.max_connections,
    },
    serverInfo: data.server_info ?? {},
  };
}

async function xtreamGetLiveCategories(creds: XtreamCredentials): Promise<IPTVCategory[]> {
  const res = await fetch(xtreamApiUrl(creds, { action: 'get_live_categories' }));
  const data = await res.json();
  return (Array.isArray(data) ? data : []).map((c: { category_id: string; category_name: string }) => ({
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

  const res = await fetch(xtreamApiUrl(creds, params));
  const data = await res.json();
  return (Array.isArray(data) ? data : []).map(
    (ch: {
      stream_id: number;
      name: string;
      stream_icon?: string;
      category_id?: string;
      epg_channel_id?: string;
    }) => ({
      id: String(ch.stream_id),
      name: ch.name,
      streamUrl: xtreamStreamUrl(creds, ch.stream_id),
      logo: ch.stream_icon || undefined,
      category: ch.category_id || '',
      epgChannelId: ch.epg_channel_id || undefined,
    })
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

type LoginTab = 'xtream' | 'm3u';
type AppState = 'login' | 'loading' | 'channels' | 'error';

export function IPTVView({ onBack, onPlay, onGoHome }: IPTVViewProps) {
  const { effectiveTheme } = useTheme();

  // Theme helpers
  const dark = effectiveTheme === 'dark';
  const bgClass = dark ? 'bg-black' : 'bg-gray-50';
  const cardClass = dark ? 'bg-gray-900/60 border-gray-800' : 'bg-white border-gray-200';
  const textClass = dark ? 'text-white' : 'text-gray-900';
  const subTextClass = dark ? 'text-gray-400' : 'text-gray-500';
  const inputClass = dark
    ? 'bg-gray-800 border-gray-700 text-white placeholder-gray-500 focus:border-blue-500'
    : 'bg-white border-gray-300 text-gray-900 placeholder-gray-400 focus:border-blue-500';

  // Login form
  const [loginTab, setLoginTab] = useState<LoginTab>('xtream');
  const [xtreamServer, setXtreamServer] = useState('');
  const [xtreamUser, setXtreamUser] = useState('');
  const [xtreamPass, setXtreamPass] = useState('');
  const [m3uUrl, setM3uUrl] = useState('');

  // App state
  const [appState, setAppState] = useState<AppState>('login');
  const [errorMsg, setErrorMsg] = useState('');
  const [credentials, setCredentials] = useState<IPTVCredentials | null>(null);
  const [userInfo, setUserInfo] = useState<XtreamUserInfo | null>(null);

  // Channel data
  const [categories, setCategories] = useState<IPTVCategory[]>([]);
  const [channels, setChannels] = useState<IPTVChannel[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');

  // Load saved credentials on mount
  useEffect(() => {
    const saved = getIPTVCredentials();
    if (saved) {
      setCredentials(saved);
      void connectWithCredentials(saved);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const connectWithCredentials = useCallback(async (creds: IPTVCredentials) => {
    setAppState('loading');
    setErrorMsg('');
    try {
      if (creds.type === 'xtream' && creds.xtream) {
        const { userInfo: ui } = await xtreamAuth(creds.xtream);
        setUserInfo(ui);
        const cats = await xtreamGetLiveCategories(creds.xtream);
        const all = await xtreamGetLiveStreams(creds.xtream);
        setCategories(cats);
        setChannels(all);
      } else if (creds.type === 'm3u' && creds.m3u) {
        const res = await fetch(creds.m3u.url);
        if (!res.ok) throw new Error(`Failed to fetch M3U: ${res.status}`);
        const text = await res.text();
        const parsed = parseM3U(text);
        if (parsed.length === 0) throw new Error('No channels found in the M3U playlist');
        const uniqueCategories = Array.from(new Set(parsed.map(c => c.category))).map(name => ({
          id: name,
          name,
        }));
        setCategories(uniqueCategories);
        setChannels(parsed);
      }
      saveIPTVCredentials(creds);
      setAppState('channels');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Connection failed';
      setErrorMsg(msg);
      setAppState('error');
    }
  }, []);

  async function handleLogin() {
    let creds: IPTVCredentials;

    if (loginTab === 'xtream') {
      if (!xtreamServer.trim() || !xtreamUser.trim() || !xtreamPass.trim()) {
        setErrorMsg('Please fill in all Xtream fields');
        setAppState('error');
        return;
      }
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
      if (!m3uUrl.trim()) {
        setErrorMsg('Please enter an M3U URL');
        setAppState('error');
        return;
      }
      const m3u: M3UCredentials = { url: m3uUrl.trim() };
      creds = { type: 'm3u', m3u, savedAt: new Date().toISOString() };
    }

    setCredentials(creds);
    await connectWithCredentials(creds);
  }

  function handleLogout() {
    clearIPTVCredentials();
    setCredentials(null);
    setUserInfo(null);
    setChannels([]);
    setCategories([]);
    setSelectedCategory('all');
    setSearchQuery('');
    setXtreamServer('');
    setXtreamUser('');
    setXtreamPass('');
    setM3uUrl('');
    setErrorMsg('');
    setAppState('login');
  }

  function handleRefresh() {
    if (credentials) void connectWithCredentials(credentials);
  }

  function handlePlayChannel(channel: IPTVChannel) {
    onPlay(0, 'live', undefined, undefined, channel.streamUrl, channel.name);
  }

  const filteredChannels = channels.filter(ch => {
    const matchCat =
      selectedCategory === 'all' ||
      ch.category === selectedCategory ||
      ch.id === selectedCategory;
    const matchSearch =
      !searchQuery || ch.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchCat && matchSearch;
  });

  // ── Render: Login ────────────────────────────────────────────────────────────
  const renderLogin = () => (
    <div className="flex-1 flex items-center justify-center p-4">
      <div className={`w-full max-w-md rounded-2xl border ${cardClass} p-8 shadow-xl`}>
        {/* Icon */}
        <div className="flex justify-center mb-6">
          <div className="w-16 h-16 rounded-full bg-blue-600/20 flex items-center justify-center">
            <Signal size={32} className="text-blue-500" />
          </div>
        </div>

        <h2 className={`text-2xl font-bold text-center mb-1 ${textClass}`}>IPTV</h2>
        <p className={`text-sm text-center mb-6 ${subTextClass}`}>
          Connect your IPTV service to watch live channels
        </p>

        {/* Tab selector */}
        <div className={`flex rounded-lg overflow-hidden border mb-6 ${dark ? 'border-gray-700' : 'border-gray-200'}`}>
          <button
            onClick={() => { setLoginTab('xtream'); setErrorMsg(''); setAppState('login'); }}
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
            onClick={() => { setLoginTab('m3u'); setErrorMsg(''); setAppState('login'); }}
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

        {/* Error */}
        {appState === 'error' && errorMsg && (
          <div className="flex items-start gap-2 mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 text-sm">
            <AlertCircle size={16} className="mt-0.5 flex-shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Xtream form */}
        {loginTab === 'xtream' && (
          <div className="space-y-3">
            <div>
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>
                Server URL
              </label>
              <Input
                value={xtreamServer}
                onChange={e => setXtreamServer(e.target.value)}
                placeholder="http://your-server.com:8080"
                className={inputClass}
                onKeyDown={e => e.key === 'Enter' && handleLogin()}
              />
            </div>
            <div>
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>
                Username
              </label>
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
              <label className={`text-xs font-medium mb-1 block ${subTextClass}`}>
                Password
              </label>
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

        {/* M3U form */}
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

  // ── Render: Loading ──────────────────────────────────────────────────────────
  const renderLoading = () => (
    <div className="flex-1 flex flex-col items-center justify-center gap-4">
      <Loader2 size={40} className="text-blue-500 animate-spin" />
      <p className={`text-base font-medium ${subTextClass}`}>Connecting to your IPTV service…</p>
    </div>
  );

  // ── Render: Channel Browser ──────────────────────────────────────────────────
  const renderChannels = () => (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Search + info bar */}
      <div className={`px-4 py-3 border-b ${dark ? 'border-gray-800' : 'border-gray-200'}`}>
        <div className="max-w-[1920px] mx-auto flex flex-col sm:flex-row gap-3 items-start sm:items-center">
          {/* Account info */}
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

          {/* Spacer */}
          <div className="flex-1" />

          {/* Search */}
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

      {/* Body: categories + channel grid */}
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
            const count = channels.filter(c => c.category === cat.id || c.category === cat.name).length;
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

        {/* Mobile: horizontal category scroll */}
        <div className="sm:hidden absolute top-0 left-0 right-0 z-10" />

        {/* Channel list */}
        <div className="flex-1 overflow-y-auto p-4">
          {/* Mobile category bar */}
          <div className="sm:hidden flex gap-2 overflow-x-auto pb-3 mb-3 no-scrollbar">
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

          {filteredChannels.length === 0 ? (
            <div className={`flex flex-col items-center justify-center py-20 gap-3 ${subTextClass}`}>
              <Tv size={40} className="opacity-30" />
              <p className="text-sm">No channels found</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-3">
              {filteredChannels.map(channel => (
                <button
                  key={channel.id}
                  onClick={() => handlePlayChannel(channel)}
                  className={`group flex items-center gap-3 p-3 rounded-xl border text-left transition-all hover:border-blue-500 hover:shadow-md ${cardClass}`}
                >
                  {/* Logo or placeholder */}
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
                          (e.currentTarget.nextSibling as HTMLElement | null)?.removeAttribute('style');
                        }}
                      />
                    ) : null}
                    <Tv
                      size={18}
                      className={`text-blue-500 ${channel.logo ? 'hidden' : ''}`}
                      style={channel.logo ? { display: 'none' } : undefined}
                    />
                  </div>

                  {/* Name */}
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm font-medium truncate ${textClass}`}>{channel.name}</p>
                    <p className={`text-xs truncate ${subTextClass}`}>{channel.category}</p>
                  </div>

                  {/* Play icon */}
                  <ChevronRight
                    size={16}
                    className={`flex-shrink-0 ${subTextClass} group-hover:text-blue-500 transition-colors`}
                  />
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );

  // ── Full render ──────────────────────────────────────────────────────────────
  return (
    <div className={`min-h-screen flex flex-col ${bgClass} ${textClass}`}>
      {/* Header */}
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

          {/* Right actions */}
          <div className="flex items-center gap-2">
            {appState === 'channels' && (
              <button
                onClick={handleRefresh}
                className={`p-2 rounded-lg transition-colors ${dark ? 'hover:bg-gray-800' : 'hover:bg-gray-100'} ${subTextClass} hover:text-blue-500`}
                title="Refresh"
              >
                <RefreshCw size={18} />
              </button>
            )}
            {(appState === 'channels' || appState === 'error') && credentials && (
              <button
                onClick={handleLogout}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-colors ${
                  dark ? 'hover:bg-gray-800 text-gray-400 hover:text-red-400' : 'hover:bg-gray-100 text-gray-500 hover:text-red-500'
                }`}
                title="Disconnect"
              >
                <LogOut size={15} />
                <span className="hidden sm:inline">Disconnect</span>
              </button>
            )}
          </div>
        </div>

        {/* Section label */}
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

      {/* Content (offset for fixed header ~72px) */}
      <div className="flex flex-col flex-1 pt-20">
        {appState === 'login' || (appState === 'error' && !credentials)
          ? renderLogin()
          : appState === 'loading'
          ? renderLoading()
          : appState === 'channels'
          ? renderChannels()
          : /* error with credentials — show login again */ renderLogin()}
      </div>
    </div>
  );
}
