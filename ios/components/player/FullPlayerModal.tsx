import { useState, useEffect, useRef } from 'react';
import {
  Modal,
  View,
  Text,
  Image,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { BlurView } from 'expo-blur';
import {
  ChevronDown,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
} from 'lucide-react-native';
import { usePlayerStore } from '@/store/playerStore';
import { Colors } from '@/constants/Colors';
import { fetchLyrics } from '@/app/api/ytmusic';

const { width } = Dimensions.get('window');

export default function FullPlayerModal() {
  const {
    currentTrack,
    isPlaying,
    progress,
    positionSec,
    durationSec,
    showFullPlayer,
    queue,
    togglePlay,
    next,
    previous,
    seekTo,
    setShowFullPlayer,
    playTrack,
  } = usePlayerStore();

  const [activeTab, setActiveTab] = useState<'player' | 'queue' | 'lyrics'>('player');
  const [lyrics, setLyrics] = useState<string | null>(null);
  const [loadingLyrics, setLoadingLyrics] = useState(false);

  const lyricsRef = useRef<string | null>(null);
  const lyricsLoadingRef = useRef(false);

  useEffect(() => {
    if (activeTab === 'lyrics' && currentTrack) {
      let cancelled = false;
      lyricsLoadingRef.current = true;
      void fetchLyrics(currentTrack.title, currentTrack.artist).then((text) => {
        if (!cancelled) {
          lyricsRef.current = text;
          setLyrics(text);
          lyricsLoadingRef.current = false;
          setLoadingLyrics(false);
        }
      });
      return () => {
        cancelled = true;
        lyricsLoadingRef.current = false;
        setLoadingLyrics(false);
      };
    } else {
      if (lyricsLoadingRef.current) {
        lyricsRef.current = null;
        setLyrics(null);
        lyricsLoadingRef.current = false;
        setLoadingLyrics(false);
      }
    }
  }, [activeTab, currentTrack]);

  if (!currentTrack) return null;

  const formatTime = (sec: number) => {
    if (!sec || isNaN(sec)) return '0:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <Modal
      visible={showFullPlayer}
      animationType="slide"
      transparent={false}
      onRequestClose={() => setShowFullPlayer(false)}
    >
      <View style={styles.container}>
        {/* Background Blur Image */}
        <Image
          source={{ uri: currentTrack.thumbnail }}
          style={StyleSheet.absoluteFill}
          blurRadius={60}
        />
        <BlurView intensity={90} tint="dark" style={StyleSheet.absoluteFill} />

        <SafeAreaView style={styles.inner}>
          {/* Top Bar */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => setShowFullPlayer(false)} hitSlop={12}>
              <ChevronDown color={Colors.text} size={28} />
            </TouchableOpacity>

            <View style={styles.headerTabs}>
              <TouchableOpacity
                style={[styles.headerTab, activeTab === 'player' && styles.headerTabActive]}
                onPress={() => setActiveTab('player')}
              >
                <Text style={[styles.headerTabText, activeTab === 'player' && styles.headerTabTextActive]}>
                  Brano
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.headerTab, activeTab === 'lyrics' && styles.headerTabActive]}
                onPress={() => setActiveTab('lyrics')}
              >
                <Text style={[styles.headerTabText, activeTab === 'lyrics' && styles.headerTabTextActive]}>
                  Testo
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.headerTab, activeTab === 'queue' && styles.headerTabActive]}
                onPress={() => setActiveTab('queue')}
              >
                <Text style={[styles.headerTabText, activeTab === 'queue' && styles.headerTabTextActive]}>
                  Coda
                </Text>
              </TouchableOpacity>
            </View>

            <View style={{ width: 28 }} />
          </View>

          {/* TAB 1: PLAYER */}
          {activeTab === 'player' && (
            <View style={styles.playerContent}>
              {/* Artwork Cover */}
              <View style={styles.coverWrapper}>
                <Image source={{ uri: currentTrack.thumbnail }} style={styles.cover} resizeMode="cover" />
              </View>

              {/* Title & Artist */}
              <View style={styles.infoRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.title} numberOfLines={1}>
                    {currentTrack.title}
                  </Text>
                  <Text style={styles.artist} numberOfLines={1}>
                    {currentTrack.artist}
                  </Text>
                </View>
              </View>

              {/* Progress Bar */}
              <View style={styles.progressContainer}>
                <TouchableOpacity
                  style={styles.progressBarBg}
                  activeOpacity={1}
                  onPress={(e) => {
                    const x = e.nativeEvent.locationX;
                    const barWidth = width - 40;
                    seekTo(Math.max(0, Math.min(1, x / barWidth)));
                  }}
                >
                  <View style={[styles.progressBarFill, { width: `${(progress * 100).toFixed(1)}%` }]} />
                </TouchableOpacity>
                <View style={styles.timeRow}>
                  <Text style={styles.timeText}>{formatTime(positionSec)}</Text>
                  <Text style={styles.timeText}>{formatTime(durationSec)}</Text>
                </View>
              </View>

              {/* Controls */}
              <View style={styles.controlsRow}>
                <TouchableOpacity hitSlop={12}>
                  <Shuffle color={Colors.textMuted} size={22} />
                </TouchableOpacity>

                <TouchableOpacity onPress={previous} hitSlop={12}>
                  <SkipBack color={Colors.text} size={32} fill={Colors.text} />
                </TouchableOpacity>

                <TouchableOpacity
                  onPress={togglePlay}
                  style={styles.playButton}
                  activeOpacity={0.8}
                >
                  {isPlaying ? (
                    <Pause color="#000" size={32} fill="#000" />
                  ) : (
                    <Play color="#000" size={32} fill="#000" style={{ marginLeft: 3 }} />
                  )}
                </TouchableOpacity>

                <TouchableOpacity onPress={next} hitSlop={12}>
                  <SkipForward color={Colors.text} size={32} fill={Colors.text} />
                </TouchableOpacity>

                <TouchableOpacity hitSlop={12}>
                  <Repeat color={Colors.textMuted} size={22} />
                </TouchableOpacity>
              </View>
            </View>
          )}

          {/* TAB 2: LYRICS */}
          {activeTab === 'lyrics' && (
            <ScrollView style={styles.scrollContent} contentContainerStyle={styles.scrollPadding}>
              <Text style={styles.sectionHeading}>Testo brano</Text>
              {loadingLyrics ? (
                <Text style={styles.lyricsText}>Caricamento testo...</Text>
              ) : lyrics ? (
                <Text style={styles.lyricsText}>{lyrics}</Text>
              ) : (
                <Text style={styles.lyricsText}>
                  Testo non disponibile per questa canzone.
                </Text>
              )}
            </ScrollView>
          )}

          {/* TAB 3: QUEUE */}
          {activeTab === 'queue' && (
            <ScrollView style={styles.scrollContent} contentContainerStyle={styles.scrollPadding}>
              <Text style={styles.sectionHeading}>In riproduzione da</Text>
              {queue.map((item, idx) => {
                const isCurrent = item.id === currentTrack.id;
                return (
                  <TouchableOpacity
                    key={`${item.id}_${idx}`}
                    style={[styles.queueItem, isCurrent && styles.queueItemActive]}
                    onPress={() => playTrack(item, queue)}
                  >
                    <Image source={{ uri: item.thumbnail }} style={styles.queueThumb} />
                    <View style={{ flex: 1 }}>
                      <Text
                        style={[styles.queueTitle, isCurrent && styles.queueTitleActive]}
                        numberOfLines={1}
                      >
                        {item.title}
                      </Text>
                      <Text style={styles.queueArtist} numberOfLines={1}>
                        {item.artist}
                      </Text>
                    </View>
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          )}
        </SafeAreaView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  inner: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  headerTabs: {
    flexDirection: 'row',
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: 20,
    padding: 3,
  },
  headerTab: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 16,
  },
  headerTabActive: {
    backgroundColor: Colors.text,
  },
  headerTabText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textSecondary,
  },
  headerTabTextActive: {
    color: '#000',
  },
  playerContent: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'space-around',
    paddingBottom: 20,
  },
  coverWrapper: {
    alignItems: 'center',
    marginVertical: 20,
  },
  cover: {
    width: width - 80,
    height: width - 80,
    borderRadius: 20,
    backgroundColor: Colors.surface,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    color: Colors.text,
  },
  artist: {
    fontSize: 16,
    color: Colors.textSecondary,
    marginTop: 4,
  },
  progressContainer: {
    marginVertical: 10,
  },
  progressBarBg: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: 4,
    backgroundColor: Colors.text,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  timeText: {
    fontSize: 12,
    color: Colors.textMuted,
  },
  controlsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 10,
  },
  playButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: Colors.text,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrollContent: {
    flex: 1,
  },
  scrollPadding: {
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  sectionHeading: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.text,
    marginBottom: 16,
  },
  lyricsText: {
    fontSize: 18,
    lineHeight: 32,
    color: Colors.text,
    fontWeight: '500',
  },
  queueItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    gap: 12,
    borderRadius: 8,
    paddingHorizontal: 8,
  },
  queueItemActive: {
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  queueThumb: {
    width: 48,
    height: 48,
    borderRadius: 6,
    backgroundColor: Colors.surface,
  },
  queueTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: Colors.text,
  },
  queueTitleActive: {
    color: Colors.primary,
  },
  queueArtist: {
    fontSize: 13,
    color: Colors.textMuted,
    marginTop: 2,
  },
});
