import { create } from 'zustand';
import { Audio } from 'expo-av';
import { fetchStreamUrl } from '@/app/api/ytmusic';
import { Storage } from './storage';

export interface Track {
  id: string;
  title: string;
  artist: string;
  thumbnail: string;
  videoId: string;
  duration?: number; // seconds
  album?: string;
  isExplicit?: boolean;
}

export interface Album {
  id: string;
  title: string;
  artist: string;
  coverUrl: string;
  year?: string;
  type?: string; // Album, Singolo, EP
  browseId?: string | null;
  isExplicit?: boolean;
}

interface PlayerState {
  currentTrack: Track | null;
  isPlaying: boolean;
  isLoading: boolean;
  progress: number; // 0–1
  positionSec: number;
  durationSec: number;
  sound: Audio.Sound | null;
  queue: Track[];
  showFullPlayer: boolean;
  error: string | null;

  playTrack: (track: Track, queue?: Track[]) => Promise<void>;
  playUrl: (url: string, title?: string, artist?: string) => Promise<void>;
  togglePlay: () => Promise<void>;
  next: () => void;
  previous: () => void;
  seekTo: (fraction: number) => Promise<void>;
  setShowFullPlayer: (show: boolean) => void;
  setQueue: (tracks: Track[]) => void;
  recordPlayback: (durationSec: number) => Promise<void>;
  getHistory: () => Promise<Track[]>;
  getFavorites: () => Promise<string[]>;
  toggleFavorite: (trackId: string) => Promise<boolean>;
  isFavorite: (trackId: string) => Promise<boolean>;
  getTotalListeningTime: () => Promise<number>;
}

export const usePlayerStore = create<PlayerState>((set, get) => ({
  currentTrack: null,
  isPlaying: false,
  isLoading: false,
  progress: 0,
  positionSec: 0,
  durationSec: 0,
  sound: null,
  queue: [],
  showFullPlayer: false,
  error: null,

  playUrl: async (url: string, title?: string, artist?: string) => {
    const { sound: prevSound } = get();
    if (prevSound) {
      try { await prevSound.unloadAsync(); } catch { /* ignore */ }
    }

    set({ isLoading: true, error: null, queue: [] });

    try {
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: true,
        playsInSilentModeIOS: true,
      });

      const track: Track = {
        id: url,
        title: title || 'Radio Live',
        artist: artist || '',
        thumbnail: '',
        videoId: '',
      };

      const { sound: newSound } = await Audio.Sound.createAsync(
        { uri: url },
        { shouldPlay: true },
        (status) => {
          if (!status.isLoaded) return;
          const dur = status.durationMillis ? status.durationMillis / 1000 : Infinity;
          const pos = status.positionMillis / 1000;
          set({
            currentTrack: track,
            positionSec: pos,
            durationSec: dur,
            progress: dur > 0 ? pos / dur : 0,
            isPlaying: status.isPlaying,
          });
        }
      );

      set({ sound: newSound, isPlaying: true, isLoading: false, error: null });
    } catch (e) {
      set({ isLoading: false, error: 'Errore durante il caricamento dello stream' });
    }
  },

  playTrack: async (track: Track, queue?: Track[]) => {
    const { sound: prevSound } = get();
    if (prevSound) {
      try {
        await prevSound.unloadAsync();
      } catch {
        // Ignore unload error
      }
    }

    if (queue) set({ queue });

    set({
      currentTrack: track,
      isPlaying: false,
      isLoading: true,
      progress: 0,
      positionSec: 0,
      sound: null,
    });

    try {
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        staysActiveInBackground: true,
        playsInSilentModeIOS: true,
      });

      const streamUrl = await fetchStreamUrl(track.videoId);
      if (!streamUrl) {
        set({ isLoading: false });
        return;
      }

      const { sound: newSound } = await Audio.Sound.createAsync(
        { uri: streamUrl },
        { shouldPlay: true },
        (status) => {
          if (!status.isLoaded) return;
          const pos = status.positionMillis / 1000;
          const dur = (status.durationMillis || 1) / 1000;
          set({
            positionSec: pos,
            durationSec: dur,
            progress: pos / dur,
            isPlaying: status.isPlaying,
          });
          if (status.didJustFinish) {
            void get().recordPlayback(dur).then(() => get().next());
          }
        }
      );

      set({ sound: newSound, isPlaying: true, isLoading: false });
    } catch {
      set({ isLoading: false, error: 'Stream non trovato' });
    }
  },

  togglePlay: async () => {
    const { sound, isPlaying, error } = get();
    if (!sound || error) return;
    try {
      if (isPlaying) {
        await sound.pauseAsync();
      } else {
        await sound.playAsync();
      }
      set({ isPlaying: !isPlaying });
    } catch {
      // Ignore audio error
    }
  },

  next: () => {
    const { queue, currentTrack } = get();
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex((t) => t.id === currentTrack.id);
    const nextTrack = queue[(idx + 1) % queue.length];
    if (nextTrack) void get().playTrack(nextTrack, queue);
  },

  previous: () => {
    const { queue, currentTrack } = get();
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex((t) => t.id === currentTrack.id);
    const prevTrack = queue[(idx - 1 + queue.length) % queue.length];
    if (prevTrack) void get().playTrack(prevTrack, queue);
  },

  seekTo: async (fraction) => {
    const { sound, durationSec } = get();
    if (!sound || durationSec <= 0) return;
    try {
      await sound.setPositionAsync(fraction * durationSec * 1000);
    } catch {
      // Ignore seek error
    }
  },

  setShowFullPlayer: (show) => set({ showFullPlayer: show }),
  setQueue: (tracks) => set({ queue: tracks }),

  recordPlayback: async (durationSec: number) => {
    const { currentTrack } = get();
    if (!currentTrack) return;
    await Storage.addHistory(currentTrack, durationSec);
  },

  getHistory: async () => Storage.getRecentTracks(),
  getFavorites: async () => Storage.getFavoriteIds(),
  toggleFavorite: async (trackId: string) => Storage.toggleFavorite(trackId),
  isFavorite: async (trackId: string) => Storage.isFavorite(trackId),
  getTotalListeningTime: async () => Storage.getTotalListeningTime(),
}));
