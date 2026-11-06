import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import { BlurView } from 'expo-blur';
import { Play, Pause, SkipForward } from 'lucide-react-native';
import { usePlayerStore } from '@/store/playerStore';
import { Colors } from '@/constants/Colors';

export default function MiniPlayer() {
  const { currentTrack, isPlaying, progress, togglePlay, next, setShowFullPlayer } = usePlayerStore();

  if (!currentTrack) return null;

  return (
    <TouchableOpacity
      activeOpacity={0.9}
      onPress={() => setShowFullPlayer(true)}
      style={styles.wrapper}
    >
      <BlurView intensity={80} tint="dark" style={styles.blur}>
        {/* Progress bar */}
        <View style={styles.progressBg}>
          <View style={[styles.progressFill, { width: `${(progress * 100).toFixed(1)}%` }]} />
        </View>

        <View style={styles.row}>
          <Image source={{ uri: currentTrack.thumbnail }} style={styles.cover} />

          <View style={styles.info}>
            <Text style={styles.title} numberOfLines={1}>{currentTrack.title}</Text>
            <Text style={styles.artist} numberOfLines={1}>{currentTrack.artist}</Text>
          </View>

          <View style={styles.controls}>
            <TouchableOpacity onPress={(e) => { e.stopPropagation?.(); togglePlay(); }} hitSlop={10}>
              {isPlaying
                ? <Pause color={Colors.text} size={22} fill={Colors.text} />
                : <Play color={Colors.text} size={22} fill={Colors.text} />
              }
            </TouchableOpacity>
            <TouchableOpacity onPress={(e) => { e.stopPropagation?.(); next(); }} hitSlop={10} style={{ marginLeft: 10 }}>
              <SkipForward color={Colors.textSecondary} size={20} fill={Colors.textSecondary} />
            </TouchableOpacity>
          </View>
        </View>
      </BlurView>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    bottom: 84,
    left: 12,
    right: 12,
    borderRadius: 18,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.45,
    shadowRadius: 18,
    elevation: 12,
  },
  blur: {
    borderRadius: 18,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: Colors.borderStrong,
  },
  progressBg: {
    height: 2,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  progressFill: {
    height: 2,
    backgroundColor: Colors.primary,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 12,
  },
  cover: {
    width: 46,
    height: 46,
    borderRadius: 12,
    backgroundColor: Colors.surfaceElevated,
  },
  info: { flex: 1, minWidth: 0 },
  title: {
    color: Colors.text,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  artist: {
    color: Colors.textSecondary,
    fontSize: 12,
    marginTop: 2,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});

