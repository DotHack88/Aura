import { Track, Album } from '@/store/playerStore';

export interface Artist {
  id: string;
  name: string;
  avatarUrl: string;
  subscribersText?: string;
  browseId?: string;
}

export interface SearchResult {
  tracks: Track[];
  albums: Album[];
  artists: Artist[];
}

// ── YouTube Music Innertube client ──────────────────────────────────────────────

const CONTEXT = JSON.stringify({
  client: {
    clientName: 'WEB_REMIX',
    clientVersion: '1.20260901.01.00',
    hl: 'it',
    gl: 'IT',
  },
});

async function innertube<T>(endpoint: string, body: string): Promise<T | null> {
  try {
    const payload = `{
  ${CONTEXT},
  ${body}
}`;
    const res = await fetch(
      `https://music.youtube.com/youtubei/v1/${endpoint}?prettyPrint=false`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
          'Referer': 'https://music.youtube.com/',
          'Origin': 'https://music.youtube.com',
        },
        body: payload,
      }
    );
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

function parseDuration(text: string): number {
  const m = text.match(/(\d+):(\d+)/);
  if (!m) return 0;
  return parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
}

// ── Types ──────────────────────────────────────────────────────────────────────

interface InnertubeRun {
  text?: string;
  navigationEndpoint?: { browseEndpoint?: { browseId?: string } };
}


interface InnertubeSearchResponse {
  contents?: {
    twoColumnSearchResultRenderer?: {
      primaryContents?: {
        sectionListRenderer?: {
          contents?: {
            itemSectionRenderer?: {
              contents?: Record<string, unknown>[];
            };
          }[];
        };
      };
    };
  };
}

// ── Search & Discovery ─────────────────────────────────────────────────────────

export async function getSearchSuggestions(query: string): Promise<string[]> {
  if (!query.trim()) return [];
  try {
    const res = await fetch(
      `https://suggestqueries-clients6.youtube.com/complete/search?client=youtube-music&q=${encodeURIComponent(
        query
      )}&hl=it&gl=IT`
    );
    const text = await res.text();
    const match = text.match(/window\.google\.ac\.h\((.*)\)/);
    if (match && match[1]) {
      const data = JSON.parse(match[1]);
      const suggestions = (data[1] as any[])?.map((s: any) => s[0]) ?? [];
      return suggestions.slice(0, 6);
    }
  } catch {
    // Fallback if JSONP regex misses
  }
  return [];
}

export async function searchTracks(query: string): Promise<Track[]> {
  const data = await innertube<InnertubeSearchResponse>('search', `"query":"${query.replace(/"/g, '\\"')}"`);
  const tracks: Track[] = [];
  const contents = data?.contents?.twoColumnSearchResultRenderer?.primaryContents?.sectionListRenderer?.contents ?? [];

  for (const section of contents) {
    const items = section.itemSectionRenderer?.contents ?? [];
    for (const item of items) {
      const r = (item as any).musicResponsiveListItemRenderer;
      if (!r?.videoId) continue;
      const title = r.title?.runs?.[0]?.text ?? r.videoId;
      const artist = r.artists?.[0]?.runs?.[0]?.text ?? '';
      const durationText = r.duration?.runs?.[0]?.text;
      const thumbArr = (r.thumbnails as any[]) ?? [];
      const thumb = thumbArr.find((t: any) => t['musicThumbnailRenderer'])
        ? (((thumbArr as any[]).find((t: any) => t['musicThumbnailRenderer']) as any)?.thumbnail?.thumbnails as any[])?.find((t: any) => t['url'])?.url
        : (thumbArr[0] as any)?.url ?? '';
      tracks.push({
        id: r.videoId,
        title,
        artist,
        thumbnail: thumb,
        videoId: r.videoId,
        duration: durationText ? parseDuration(durationText) : undefined,
      });
    }
  }
  return tracks;
}

export async function searchAlbums(query: string): Promise<Album[]> {
  const data = await innertube<InnertubeSearchResponse>('search', `"query":"${query.replace(/"/g, '\\"')} album"`);
  const albums: Album[] = [];
  const contents = data?.contents?.twoColumnSearchResultRenderer?.primaryContents?.sectionListRenderer?.contents ?? [];
  for (const section of contents) {
    const items = section.itemSectionRenderer?.contents ?? [];
    for (const item of items) {
      const r = (item as any).musicTwoRowItemRenderer;
      if (!r) continue;
      const track = r.musicResponsiveListItemRenderer;
      if (!track?.videoId) continue;
      const header = r.musicTwoRowThumbnailHeaderRenderer;
      const title = header?.title?.runs?.[0]?.text ?? track.title?.runs?.[0]?.text ?? '';
      const subtitleRuns = header?.subtitle?.runs ?? track.artists?.[0]?.runs ?? [];
      const artist = subtitleRuns[0]?.text ?? '';
      const thumbArr = (header?.thumbnails ?? track.thumbnails) as any[];
      const thumb = thumbArr.find((t: any) => t['url'])?.url ?? '';
      const browseId = header?.navigationEndpoint?.browseEndpoint?.browseId
        ?? r.playNavigationEndpoint?.browseEndpoint?.browseId
        ?? '';
      const yearMatch = subtitleRuns.find((r: InnertubeRun) => r.text?.match(/^\d{4}$/));
      albums.push({
        id: browseId || `album_${track.videoId}`,
        title,
        artist,
        coverUrl: thumb,
        year: yearMatch?.text,
        browseId: browseId || null,
      });
    }
  }
  return albums;
}

export async function searchAll(query: string): Promise<SearchResult> {
  const [tracks, albums] = await Promise.all([
    searchTracks(query),
    searchAlbums(query),
  ]);

  // Extract artists from top tracks
  const artistMap = new Map<string, Artist>();
  tracks.forEach((t) => {
    if (t.artist && !artistMap.has(t.artist)) {
      artistMap.set(t.artist, {
        id: `artist_${t.artist.replace(/\s+/g, '_').toLowerCase()}`,
        name: t.artist,
        avatarUrl: t.thumbnail,
        subscribersText: 'Artista ufficiale',
      });
    }
  });

  return {
    tracks,
    albums,
    artists: Array.from(artistMap.values()).slice(0, 5),
  };
}

export async function getQuickPicks(): Promise<Track[]> {
  const tracks = await searchTracks('Top 50 Italia');
  if (tracks.length > 0) return tracks;
  const fallback = await searchTracks('hit italia 2026');
  return fallback.slice(0, 20);
}

export async function getNewReleases(): Promise<Album[]> {
  const albums = await searchAlbums('nuove uscite italia 2026');
  if (albums.length > 0) return albums.slice(0, 8);
  return [
    {
      id: 'vita_morte_miracoli',
      title: 'Vita Morte Miracoli',
      artist: 'J-Ax',
      coverUrl: 'https://yt3.googleusercontent.com/6X3x4G2z-Dy6bMTNGaOWgfhkNUGEumlEArxxF7QaZ_lZjlv4rkxFUiWZqDEnSY2iDeB4v0nq41Ntr98=w544-h544-l90-rj',
      year: '2026',
      browseId: 'MPREb_DxSV04uEv1U',
    },
    {
      id: 'giannagold50',
      title: 'GiannaGold 50',
      artist: 'Gianna Nannini',
      coverUrl: 'https://yt3.googleusercontent.com/dwyHtOrAOneeat10rYaxIPpc5Dq0JSIYpBq4shRfKIRMTgkHk8V3OwePDBHgGhdb0VNlKaSTMif4kac=w544-h544-l90-rj',
      year: '2026',
      browseId: 'MPREb_VG60q78Yf08',
    },
    {
      id: '3xjo8h14gCI',
      title: 'Vangelo',
      artist: 'Shiva',
      coverUrl: 'https://yt3.googleusercontent.com/QadVc9_pLaa-RtOcJyMWcnNJCHtc9cSoHwlwZvecqeXKN9Yw5filYDj0R9SfLsjVsbiQhzET7B1j9TOy=w544-h544-l90-rj',
      year: '2026',
      browseId: 'MPREb_BlB8gIZE23T',
    },
    {
      id: 'G35c59nL61U',
      title: 'Serenamente',
      artist: 'Juli e Bresh',
      coverUrl: 'https://yt3.googleusercontent.com/lHa3qu8YIXkjvQVSQ1vro2cHQP-GZbvm4srECUQmUuN6ZI861tHBdU4fPnXApwoc5o1FcJTMJ-8cXOFP=w544-h544-l90-rj',
      year: '2026',
      browseId: 'MPREb_5Zy5VmcLXZF',
    },
    {
      id: 'Ow8JYT_Oarc',
      title: 'Genova e Napoli',
      artist: 'Alfa e Gigi D\'Alessio',
      coverUrl: 'https://yt3.googleusercontent.com/0yUoYwu-nRRV5alxplvaEgZ8x4OLSIJV3r0pE-yxx-ypKlNO4Sb-vXlEnhfp1oab12HjCoGfX2GAQss=w544-h544-l90-rj',
      year: '2026',
      browseId: 'MPREb_wqQnkbUk48L',
    },
  ];
}

export async function getSuggestedTracks(): Promise<Track[]> {
  return searchTracks('canzoni italiane 2026');
}

// ── Audio Streaming Resolver ───────────────────────────────────────────────────

export async function fetchStreamUrl(videoId: string): Promise<string | null> {
  const PIPED_INSTANCES = [
    'https://pipedapi.kavin.rocks',
    'https://api.piped.privacydev.net',
    'https://pipedapi.mha.fi',
  ];

  for (const base of PIPED_INSTANCES) {
    try {
      const res = await fetch(`${base}/streams/${videoId}`);
      if (!res.ok) continue;
      const data = await res.json();
      const audioStreams = data.audioStreams as any[];
      if (audioStreams && audioStreams.length > 0) {
        // Find best quality m4a / webm audio stream
        const best =
          audioStreams.find((s) => s.mimeType?.includes('audio/mp4')) ||
          audioStreams[0];
        if (best?.url) return best.url;
      }
    } catch {
      // Try next instance
    }
  }

  // Fallback to Invidious API
  try {
    const res = await fetch(`https://vid.puffyan.us/api/v1/videos/${videoId}`);
    if (res.ok) {
      const data = await res.json();
      const adaptive = data.adaptiveFormats as any[];
      const audio = adaptive?.find((f) => f.type?.startsWith('audio/'));
      if (audio?.url) return audio.url;
    }
  } catch {
    // End of fallbacks
  }

  return null;
}

// ── Lyrics Resolver ─────────────────────────────────────────────────────────────

export async function fetchLyrics(
  title: string,
  artist: string
): Promise<string | null> {
  try {
    const res = await fetch(`https://api.lyrics.ovh/v1/${encodeURIComponent(artist)}/${encodeURIComponent(title)}`);
    if (res.ok) {
      const data = await res.json();
      if (data.lyrics) return data.lyrics;
    }
  } catch {
    // Ignore error and return null fallback
  }
  return null;
}
