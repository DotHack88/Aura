import AsyncStorage from '@react-native-async-storage/async-storage';
import { Track } from './playerStore';

const HISTORY_KEY = '@aura_history';
const FAVORITES_KEY = '@aura_favorites';

export interface HistoryEntry {
  track: Track;
  playedAt: number;
  durationMs: number;
  positionMs: number;
  playCount: number;
}

async function readJSON<T>(key: string, fallback: T): Promise<T> {
  try {
    const raw = await AsyncStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

async function writeJSON<T>(key: string, data: T): Promise<void> {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(data));
  } catch {
    // Ignore write errors
  }
}

export const Storage = {
  async getHistory(): Promise<HistoryEntry[]> {
    return readJSON<HistoryEntry[]>(HISTORY_KEY, []);
  },

  async addHistory(track: Track, durationSec: number): Promise<void> {
    const history = await this.getHistory();
    const existing = history.find((h) => h.track.id === track.id);
    if (existing) {
      existing.playCount += 1;
      existing.durationMs = durationSec * 1000;
      existing.playedAt = Date.now();
    } else {
      history.unshift({
        track,
        playedAt: Date.now(),
        durationMs: durationSec * 1000,
        positionMs: 0,
        playCount: 1,
      });
    }
    // Keep last 200 entries
    await writeJSON(HISTORY_KEY, history.slice(0, 200));
  },

  async getRecentTracks(limit = 20): Promise<Track[]> {
    const history = await this.getHistory();
    return history.map((h) => h.track).slice(0, limit);
  },

  async getFavoriteIds(): Promise<string[]> {
    return readJSON<string[]>(FAVORITES_KEY, []);
  },

  async toggleFavorite(trackId: string): Promise<boolean> {
    const ids = await this.getFavoriteIds();
    const isFav = ids.includes(trackId);
    const next = isFav ? ids.filter((id) => id !== trackId) : [...ids, trackId];
    await writeJSON(FAVORITES_KEY, next);
    return !isFav;
  },

  async isFavorite(trackId: string): Promise<boolean> {
    const ids = await this.getFavoriteIds();
    return ids.includes(trackId);
  },

  async getTotalListeningTime(): Promise<number> {
    const history = await this.getHistory();
    return history.reduce((sum, h) => sum + h.durationMs * h.playCount, 0);
  },
};
