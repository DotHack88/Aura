import { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Image,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { Play } from 'lucide-react-native';
import { Colors } from '@/constants/Colors';
import { usePlayerStore, Track, Album } from '@/store/playerStore';
import {
  getQuickPicks,
  getNewReleases,
  getSuggestedTracks,
} from '@/app/api/ytmusic';

// ── Chip filter row (mirrors Android filter chips) ────────────────────────────
const CHIPS = ['Energica', 'Relax', 'Allenamento', 'Concentrazione', 'Viaggio', 'Festa'];

// ── Sub-components ─────────────────────────────────────────────────────────────

function SectionHeader({ title, strapline }: { title: string; strapline?: string }) {
  return (
    <View style={styles.sectionHeader}>
      {strapline ? <Text style={styles.sectionStrap}>{strapline}</Text> : null}
      <Text style={styles.sectionTitle}>{title}</Text>
    </View>
  );
}

function TrackRowItem({
  track,
  onPlay,
}: {
  track: Track;
  onPlay: () => void;
}) {
  return (
    <TouchableOpacity
      style={styles.trackRow}
      activeOpacity={0.7}
    >
      <Image
        source={{ uri: track.thumbnail || undefined }}
        style={styles.trackThumb}
        resizeMode="cover"
      />
      <View style={styles.trackInfo}>
        <Text style={styles.trackTitle} numberOfLines={1}>
          {track.title}
        </Text>
        <Text style={styles.trackArtist} numberOfLines={1}>
          {track.artist}
          {track.duration ? ` · ${Math.floor(track.duration / 60)}:${String(track.duration % 60).padStart(2, '0')}` : ''}
        </Text>
      </View>
      <TouchableOpacity
        style={styles.playBtnRow}
        onPress={(e) => {
          e.stopPropagation();
          onPlay();
        }}
        hitSlop={8}
      >
        <Play size={18} color={Colors.text} fill={Colors.text} />
      </TouchableOpacity>
    </TouchableOpacity>
  );
}

function TrackCarousel({
  tracks,
  loading,
}: {
  tracks: Track[];
  loading: boolean;
}) {
  const { playTrack } = usePlayerStore();
  if (loading) {
    return (
      <View style={styles.skeletonRow}>
        {[1, 2, 3, 4].map((i) => (
          <View key={i} style={styles.skeletonCard} />
        ))}
      </View>
    );
  }
  if (tracks.length === 0) return null;
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.carouselScroll}>
      <View style={styles.carouselInner}>
        {tracks.map((t) => (
          <TouchableOpacity
            key={t.id}
            style={styles.card}
            onPress={() => playTrack(t, tracks)}
            activeOpacity={0.85}
          >
            <Image
              source={{ uri: t.thumbnail || undefined }}
              style={styles.cardImage}
              resizeMode="cover"
            />
            <View style={styles.cardPlayBadge}>
              <Play size={14} color={Colors.text} fill={Colors.text} />
            </View>
            <Text style={styles.cardTitle} numberOfLines={1}>
              {t.title}
            </Text>
            <Text style={styles.cardArtist} numberOfLines={1}>
              {t.artist}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
}

function AlbumCarousel({
  albums,
  loading,
}: {
  albums: Album[];
  loading: boolean;
}) {
  if (loading) {
    return (
      <View style={styles.skeletonRow}>
        {[1, 2, 3, 4].map((i) => (
          <View key={i} style={styles.skeletonAlbum} />
        ))}
      </View>
    );
  }
  if (albums.length === 0) return null;
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.carouselScroll}>
      <View style={styles.carouselInner}>
        {albums.map((a) => (
          <TouchableOpacity key={a.id} style={styles.albumCard} activeOpacity={0.85}>
            <Image
              source={{ uri: a.coverUrl || undefined }}
              style={styles.albumImage}
              resizeMode="cover"
            />
            {a.type ? (
              <View style={styles.albumTypeBadge}>
                <Text style={styles.albumTypeText}>{a.type.toUpperCase()}</Text>
              </View>
            ) : null}
            <Text style={styles.albumTitle} numberOfLines={1}>
              {a.title}
            </Text>
            <Text style={styles.albumMeta} numberOfLines={1}>
              {a.year}{a.artist ? ` · ${a.artist}` : ''}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
}

function QuickPickRows({
  tracks,
  loading,
  onPlay,
}: {
  tracks: Track[];
  loading: boolean;
  onPlay?: (track: Track, queue: Track[]) => void;
}) {
  const { playTrack } = usePlayerStore();
  if (loading) {
    return (
      <View style={styles.skeletonList}>
        {[1, 2, 3, 4].map((i) => (
          <View key={i} style={styles.skeletonItem} />
        ))}
      </View>
    );
  }
  if (tracks.length === 0) return null;
  return (
    <View>
      {tracks.map((t) => (
        <TrackRowItem
          key={t.id}
          track={t}
          onPlay={() => onPlay?.(t, tracks) ?? playTrack(t, tracks)}
        />
      ))}
    </View>
  );
}

// ── Stats card (mirrors Android gradient stat card) ───────────────────────────

function StatsCard({ totalMinutes, topTrack }: { totalMinutes: number; topTrack: string }) {
  return (
    <TouchableOpacity
      style={styles.statsCard}
      activeOpacity={0.8}
    >
      <LinearGradient
        colors={[Colors.primaryDark, '#1e3a8a']}
        style={styles.statsGradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <View style={styles.statsRow}>
          <Text style={styles.statsEmoji}>🎵</Text>
          <Text style={styles.statsLabel}>Le tue statistiche</Text>
        </View>
        <Text style={styles.statsMinutes}>
          {totalMinutes > 0 ? `${totalMinutes} min ascoltati` : 'Inizia ad ascoltare!'}
        </Text>
        {topTrack ? (
          <Text style={styles.statsTop} numberOfLines={1}>{topTrack}</Text>
        ) : null}
      </LinearGradient>
    </TouchableOpacity>
  );
}

// ── Main screen ────────────────────────────────────────────────────────────────

export default function HomeScreen() {
  const [quickPicks, setQuickPicks] = useState<Track[]>([]);
  const [suggested, setSuggested] = useState<Track[]>([]);
  const [newReleases, setNewReleases] = useState<Album[]>([]);
  const [recentTracks, setRecentTracks] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [totalMinutes, setTotalMinutes] = useState(0);
  const [topTrack, setTopTrack] = useState('');

  const { getHistory, getTotalListeningTime } = usePlayerStore();

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        setLoading(true);
        const [picks, suggestions, releases, history, totalMs] = await Promise.all([
          getQuickPicks(),
          getSuggestedTracks(),
          getNewReleases(),
          getHistory(),
          getTotalListeningTime(),
        ]);
        if (!cancelled) {
          setQuickPicks(picks.slice(0, 12));
          setSuggested(suggestions.slice(0, 8));
          setNewReleases(releases.slice(0, 6));
          setRecentTracks(history);
          setTotalMinutes(Math.round(totalMs / 60000));
          if (history.length > 0) {
            setTopTrack(`${history[0].title} · ${history[0].artist}`);
          }
          setLoading(false);
        }
      } catch {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      const [picks, suggestions, releases, history, totalMs] = await Promise.all([
        getQuickPicks(),
        getSuggestedTracks(),
        getNewReleases(),
        getHistory(),
        getTotalListeningTime(),
      ]);
      setQuickPicks(picks.slice(0, 12));
      setSuggested(suggestions.slice(0, 8));
      setNewReleases(releases.slice(0, 6));
      setRecentTracks(history);
      setTotalMinutes(Math.round(totalMs / 60000));
      if (history.length > 0) {
        setTopTrack(`${history[0].title} · ${history[0].artist}`);
      }
    } catch {
      // Ignore refresh errors
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const timeGreeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Buongiorno 👋';
    if (h < 18) return 'Buon pomeriggio 👋';
    return 'Buonasera 👋';
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={Colors.primary}
            colors={[Colors.primary]}
          />
        }
      >
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>{timeGreeting()}</Text>
            <Text style={styles.subtitle}>Cosa vuoi ascoltare?</Text>
          </View>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>A</Text>
          </View>
        </View>

        {/* Filter chips */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.chipsScroll}
          contentContainerStyle={styles.chipsContainer}
        >
          {CHIPS.map((chip) => (
            <TouchableOpacity
              key={chip}
              style={styles.chip}
              activeOpacity={0.7}
            >
              <Text style={styles.chipText}>{chip}</Text>
            </TouchableOpacity>
          ))}
          <TouchableOpacity style={[styles.chip, styles.chipAll]} activeOpacity={0.7}>
            <Text style={[styles.chipText, styles.chipAllText]}>Tutti i generi…</Text>
          </TouchableOpacity>
        </ScrollView>

        {/* Stats card */}
        <View style={styles.sectionPaddingTop}>
          <StatsCard totalMinutes={totalMinutes} topTrack={topTrack} />
        </View>

        {/* Recent tracks */}
        {recentTracks.length > 0 && (
          <View style={styles.sectionPaddingTop}>
            <SectionHeader
              strapline="RIASCOLTA"
              title="Ascoltati di recente"
            />
            <QuickPickRows tracks={recentTracks} loading={false} />
          </View>
        )}

        {/* Quick picks – row list */}
        <View style={styles.sectionPaddingTop}>
          <SectionHeader
            strapline="INIZIA A SENTIRE LA RADIO DA UN BRANO"
            title="Scelte rapide"
          />
          <QuickPickRows tracks={quickPicks} loading={loading} />
        </View>

        {/* Suggested for you */}
        {suggested.length > 0 && (
          <View style={styles.sectionPaddingTop}>
            <SectionHeader
              strapline="IN BASE AI TUOI ASCOLTI"
              title="Consigliati per te"
            />
            <TrackCarousel tracks={suggested} loading={loading} />
          </View>
        )}

        {/* New releases */}
        {newReleases.length > 0 && (
          <View style={styles.sectionPaddingTop}>
            <SectionHeader
              strapline="NOVITÀ"
              title="Nuove uscite"
            />
            <AlbumCarousel albums={newReleases} loading={loading} />
          </View>
        )}

        <View style={{ height: 20 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
  },
  greeting: {
    fontSize: 24,
    fontWeight: '700',
    color: Colors.text,
  },
  subtitle: {
    fontSize: 14,
    color: Colors.textSecondary,
    marginTop: 2,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: Colors.text,
    fontWeight: '700',
    fontSize: 16,
  },
  chipsScroll: {
    marginLeft: 0,
  },
  chipsContainer: {
    paddingHorizontal: 16,
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 8,
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.border,
    marginRight: 8,
  },
  chipAll: {
    backgroundColor: Colors.surfaceElevated,
  },
  chipAllText: {
    fontWeight: '600',
  },
  chipText: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.text,
  },
  sectionPaddingTop: {
    marginTop: 28,
  },
  sectionHeader: {
    paddingHorizontal: 20,
    marginBottom: 12,
  },
  sectionStrap: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    color: Colors.textMuted,
    marginBottom: 2,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.text,
  },
  statsCard: {
    marginHorizontal: 20,
    borderRadius: 20,
    overflow: 'hidden',
  },
  statsGradient: {
    padding: 20,
    borderRadius: 20,
  },
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  statsEmoji: {
    fontSize: 20,
  },
  statsLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: 'rgba(255,255,255,0.8)',
  },
  statsMinutes: {
    fontSize: 20,
    fontWeight: '700',
    color: Colors.text,
    marginTop: 8,
  },
  statsTop: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.7)',
    marginTop: 4,
  },
  // Carousel
  carouselScroll: {
    marginLeft: 0,
  },
  carouselInner: {
    paddingHorizontal: 20,
    gap: 14,
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  card: {
    width: 130,
    gap: 6,
  },
  cardImage: {
    width: 130,
    height: 130,
    borderRadius: 8,
    backgroundColor: Colors.surface,
  },
  cardPlayBadge: {
    position: 'absolute',
    bottom: 108,
    right: 12,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardTitle: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.text,
    marginTop: 4,
  },
  cardArtist: {
    fontSize: 11,
    color: Colors.textMuted,
  },
  // Album cards
  albumCard: {
    width: 130,
    gap: 6,
  },
  albumImage: {
    width: 130,
    height: 130,
    borderRadius: 8,
    backgroundColor: Colors.surface,
    position: 'relative',
  },
  albumTypeBadge: {
    position: 'absolute',
    top: 6,
    left: 6,
    backgroundColor: 'rgba(0,0,0,0.7)',
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 2,
  },
  albumTypeText: {
    fontSize: 8,
    fontWeight: '700',
    color: Colors.text,
    letterSpacing: 0.5,
  },
  albumTitle: {
    fontSize: 13,
    fontWeight: '500',
    color: Colors.text,
    marginTop: 4,
  },
  albumMeta: {
    fontSize: 11,
    color: Colors.textMuted,
  },
  // Track rows (Quick Picks)
  trackRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 6,
    gap: 12,
  },
  trackThumb: {
    width: 56,
    height: 56,
    borderRadius: 4,
    backgroundColor: Colors.surface,
  },
  trackInfo: {
    flex: 1,
    minWidth: 0,
  },
  trackTitle: {
    fontSize: 14,
    fontWeight: '500',
    color: Colors.text,
  },
  trackArtist: {
    fontSize: 12,
    color: Colors.textMuted,
    marginTop: 2,
  },
  playBtnRow: {
    padding: 6,
  },
  // Skeleton placeholders
  skeletonRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    gap: 14,
  },
  skeletonCard: {
    width: 130,
    height: 130,
    borderRadius: 8,
    backgroundColor: Colors.surface,
  },
  skeletonAlbum: {
    width: 130,
    height: 130,
    borderRadius: 8,
    backgroundColor: Colors.surface,
  },
  skeletonList: {
    paddingHorizontal: 20,
    gap: 10,
  },
  skeletonItem: {
    height: 68,
    borderRadius: 8,
    backgroundColor: Colors.surface,
  },
});
