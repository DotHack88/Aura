import { useEffect, useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Image,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Heart, Clock, Play } from 'lucide-react-native';
import { Colors } from '@/constants/Colors';
import { usePlayerStore, Track } from '@/store/playerStore';
import { Storage } from '@/store/storage';

export default function LibraryScreen() {
  const [history, setHistory] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const { playTrack } = usePlayerStore();

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        setLoading(true);
        const recent = await Storage.getRecentTracks(20);
        if (!cancelled) {
          setHistory(recent);
          setLoading(false);
        }
      } catch {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>La tua Libreria</Text>
          <Text style={styles.subtitle}>Preferiti e cronologia ascolti</Text>
        </View>

        {loading ? (
          <View style={styles.centerLoader}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        ) : (
          <>
            {/* Favorites Section */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>
                <Heart size={18} color={Colors.primary} fill={Colors.primary} />
                {' '}Preferiti
              </Text>
              <View style={styles.emptyState}>
                <Heart size={48} color={Colors.textMuted} />
                <Text style={styles.emptyTitle}>Nessun preferito ancora</Text>
                <Text style={styles.emptySub}>
                  Tocca il cuore su un brano per aggiungerlo qui
                </Text>
              </View>
            </View>

            {/* History Section */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>
                <Clock size={18} color={Colors.cyan} />
                {' '}Cronologia Ascolti
              </Text>
              {history.length === 0 ? (
                <View style={styles.emptyState}>
                  <Clock size={48} color={Colors.textMuted} />
                  <Text style={styles.emptyTitle}>Nessun brano ascoltato</Text>
                  <Text style={styles.emptySub}>
                    Inizia ad ascoltare musica per vedere la cronologia qui
                  </Text>
                </View>
              ) : (
                history.map((track) => (
                  <TouchableOpacity
                    key={track.id}
                    style={styles.trackRow}
                    onPress={() => playTrack(track, history)}
                    activeOpacity={0.8}
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
                      </Text>
                    </View>
                    <TouchableOpacity hitSlop={8}>
                      <Play size={20} color={Colors.textSecondary} fill={Colors.textSecondary} />
                    </TouchableOpacity>
                  </TouchableOpacity>
                ))
              )}
            </View>
          </>
        )}

        <View style={{ height: 40 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 20 },
  title: { fontSize: 28, fontWeight: '800', color: Colors.text, marginBottom: 4 },
  subtitle: { fontSize: 15, color: Colors.textSecondary },
  section: { marginTop: 24 },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.text,
    paddingHorizontal: 20,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  centerLoader: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyState: {
    paddingHorizontal: 20,
    alignItems: 'center',
    paddingVertical: 40,
    gap: 12,
  },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: Colors.text },
  emptySub: { fontSize: 13, color: Colors.textMuted, textAlign: 'center' },
  trackRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 10,
    gap: 12,
  },
  trackThumb: {
    width: 52,
    height: 52,
    borderRadius: 6,
    backgroundColor: Colors.surface,
  },
  trackInfo: { flex: 1 },
  trackTitle: { fontSize: 14, fontWeight: '500', color: Colors.text },
  trackArtist: { fontSize: 12, color: Colors.textMuted, marginTop: 2 },
});
