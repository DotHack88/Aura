import { create } from 'zustand';
import { Audio } from 'expo-av';

export interface Track {
  id: string;
  title: string;
  artist: string;
  thumbnail: string;
  videoId: string;
  duration?: number;
  isLive?: boolean;
}

interface PlayerStore {
  currentTrack: Track | null;
  isPlaying: boolean;
  progress: number;
  duration: number;
  queue: Track[];
  showFullPlayer: boolean;
  sound: Audio.Sound | null;

  playTrack: (track: Track) => Promise<void>;
  togglePlay: () => Promise<void>;
  next: () => void;
  previous: () => void;
  seekTo: (position: number) => Promise<void>;
  setShowFullPlayer: (show: boolean) => void;
  setQueue: (tracks: Track[]) => void;
}

export const usePlayerStore = create<PlayerStore>((set, get) => ({
  currentTrack: null,
  isPlaying: false,
  progress: 0,
  duration: 0,
  queue: [],
  showFullPlayer: false,
  sound: null,

  playTrack: async (track: Track) => {
    const { sound: prevSound } = get();
    if (prevSound) await prevSound.unloadAsync();

    await Audio.setAudioModeAsync({
      allowsRecordingIOS: false,
      staysActiveInBackground: true,
      playsInSilentModeIOS: true,
    });

    set({ currentTrack: track, isPlaying: true });
    // TODO: integrate stream URL fetch from your API backend
  },

  togglePlay: async () => {
    const { sound, isPlaying } = get();
    if (!sound) return;
    if (isPlaying) await sound.pauseAsync();
    else await sound.playAsync();
    set({ isPlaying: !isPlaying });
  },

  next: () => {
    const { queue, currentTrack } = get();
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex((t) => t.id === currentTrack.id);
    const next = queue[(idx + 1) % queue.length];
    if (next) get().playTrack(next);
  },

  previous: () => {
    const { queue, currentTrack } = get();
    if (!currentTrack || queue.length === 0) return;
    const idx = queue.findIndex((t) => t.id === currentTrack.id);
    const prev = queue[(idx - 1 + queue.length) % queue.length];
    if (prev) get().playTrack(prev);
  },

  seekTo: async (position: number) => {
    const { sound, duration } = get();
    if (!sound) return;
    await sound.setPositionAsync(position * duration);
  },

  setShowFullPlayer: (show) => set({ showFullPlayer: show }),
  setQueue: (tracks) => set({ queue: tracks }),
}));

