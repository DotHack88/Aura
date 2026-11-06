import { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Image,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Search, X, Play, Music } from 'lucide-react-native';
import { Colors } from '@/constants/Colors';
import { usePlayerStore, Track, Album } from '@/store/playerStore';
import {
  searchAll,
  getSearchSuggestions,
  SearchResult,
  Artist,
} from '@/app/api/ytmusic';

const CATEGORIES = ['Tutti', 'Brani', 'Album', 'Artisti'];

export default function ExploreScreen() {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<SearchResult | null>(null);
  const [selectedCategory, setSelectedCategory] = useState('Tutti');

  const { playTrack } = usePlayerStore();

  useEffect(() => {
    if (!query.trim()) return;
    const timer = setTimeout(() => {
      void getSearchSuggestions(query).then(setSuggestions);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  const handleSearch = async (searchQuery: string) => {
    if (!searchQuery.trim()) return;
    setQuery(searchQuery);
    setSuggestions([]);
    setLoading(true);
    const data = await searchAll(searchQuery);
    setResults(data);
    setLoading(false);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Search Input Bar */}
      <View style={styles.searchBarWrapper}>
        <View style={styles.searchBar}>
          <Search size={20} color={Colors.textMuted} />
          <TextInput
            style={styles.searchInput}
            placeholder="Cerca brani, album, artisti..."
            placeholderTextColor={Colors.textMuted}
            value={query}
            onChangeText={setQuery}
            onSubmitEditing={() => void handleSearch(query)}
            returnKeyType="search"
          />
          {query.length > 0 && (
            <TouchableOpacity
              onPress={() => {
                setQuery('');
                setResults(null);
                setSuggestions([]);
              }}
            >
              <X size={18} color={Colors.textMuted} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Auto-complete Suggestions Dropdown */}
      {suggestions.length > 0 && !results && (
        <View style={styles.suggestionsList}>
          {suggestions.map((item, index) => (
            <TouchableOpacity
              key={index}
              style={styles.suggestionItem}
              onPress={() => void handleSearch(item)}
            >
              <Search size={16} color={Colors.textMuted} />
                    <Text style={styles.suggestionText}>{item}</Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {/* Category Chips (Visible when results exist) */}
      {results && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.chipsScroll}
          contentContainerStyle={styles.chipsContainer}
        >
          {CATEGORIES.map((cat) => (
            <TouchableOpacity
              key={cat}
              style={[
                styles.chip,
                selectedCategory === cat && styles.chipActive,
              ]}
              onPress={() => setSelectedCategory(cat)}
            >
              <Text
                style={[
                  styles.chipText,
                  selectedCategory === cat && styles.chipTextActive,
                ]}
              >
                {cat}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}

      {/* Loading Indicator */}
      {loading && (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      )}

      {/* Empty / Initial State */}
      {!loading && !results && suggestions.length === 0 && (
        <View style={styles.emptyContainer}>
          <Music size={48} color={Colors.borderStrong} />
          <Text style={styles.emptyTitle}>Esplora la musica</Text>
          <Text style={styles.emptySub}>
            Cerca i tuoi brani, album o artisti preferiti per iniziare
          </Text>
        </View>
      )}

      {/* Search Results Display */}
      {!loading && results && (
        <ScrollView showsVerticalScrollIndicator={false} style={styles.resultsScroll}>
          {/* Artists Section */}
          {(selectedCategory === 'Tutti' || selectedCategory === 'Artisti') &&
            results.artists.length > 0 && (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Artisti</Text>
                {results.artists.map((artist: Artist) => (
                  <TouchableOpacity key={artist.id} style={styles.artistCard}>
                    <Image source={{ uri: artist.avatarUrl }} style={styles.artistAvatar} />
                    <View style={styles.artistInfo}>
                      <Text style={styles.artistName}>{artist.name}</Text>
                      <Text style={styles.artistSub}>{artist.subscribersText}</Text>
                    </View>
                  </TouchableOpacity>
                ))}
              </View>
            )}

          {/* Tracks Section */}
          {(selectedCategory === 'Tutti' || selectedCategory === 'Brani') &&
            results.tracks.length > 0 && (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Brani</Text>
                {results.tracks.map((track: Track) => (
                  <TouchableOpacity
                    key={track.id}
                    style={styles.trackRow}
                    onPress={() => void playTrack(track, results.tracks)}
                  >
                    <Image source={{ uri: track.thumbnail }} style={styles.trackThumb} />
                    <View style={styles.trackInfo}>
                      <Text style={styles.trackTitle} numberOfLines={1}>
                        {track.title}
                      </Text>
                      <Text style={styles.trackArtist} numberOfLines={1}>
                        {track.artist}
                      </Text>
                    </View>
                    <TouchableOpacity
                      onPress={() => void playTrack(track, results.tracks)}
                      hitSlop={8}
                    >
                      <Play size={20} color={Colors.text} fill={Colors.text} />
                    </TouchableOpacity>
                  </TouchableOpacity>
                ))}
              </View>
            )}

          {/* Albums Section */}
          {(selectedCategory === 'Tutti' || selectedCategory === 'Album') &&
            results.albums.length > 0 && (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Album</Text>
                <View style={styles.albumGrid}>
                  {results.albums.map((album: Album) => (
                    <TouchableOpacity key={album.id} style={styles.albumCard}>
                      <Image source={{ uri: album.coverUrl }} style={styles.albumCover} />
                      <Text style={styles.albumTitle} numberOfLines={1}>
                        {album.title}
                      </Text>
                      <Text style={styles.albumArtist} numberOfLines={1}>
                        {album.artist}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}

          <View style={{ height: 100 }} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  searchBarWrapper: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 8 },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderRadius: 12,
    paddingHorizontal: 14,
    height: 48,
    gap: 10,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  searchInput: {
    flex: 1,
    color: Colors.text,
    fontSize: 15,
  },
  suggestionsList: {
    backgroundColor: Colors.surface,
    marginHorizontal: 20,
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: Colors.border,
  },
  suggestionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  suggestionText: {
    color: Colors.text,
    fontSize: 14,
  },
  chipsScroll: { maxHeight: 44, marginVertical: 8 },
  chipsContainer: { paddingHorizontal: 20, gap: 8 },
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  chipActive: {
    backgroundColor: Colors.text,
    borderColor: Colors.text,
  },
  chipText: {
    fontSize: 13,
    fontWeight: '600',
    color: Colors.textSecondary,
  },
  chipTextActive: {
    color: '#000',
  },
  centerContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 40,
    gap: 12,
  },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: Colors.text },
  emptySub: { fontSize: 14, color: Colors.textMuted, textAlign: 'center' },
  resultsScroll: { flex: 1 },
  section: { paddingHorizontal: 20, marginTop: 20 },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Colors.text,
    marginBottom: 12,
  },
  artistCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 8,
  },
  artistAvatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.surface,
  },
  artistInfo: { flex: 1 },
  artistName: { fontSize: 16, fontWeight: '600', color: Colors.text },
  artistSub: { fontSize: 13, color: Colors.textMuted, marginTop: 2 },
  trackRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    gap: 12,
  },
  trackThumb: {
    width: 48,
    height: 48,
    borderRadius: 6,
    backgroundColor: Colors.surface,
  },
  trackInfo: { flex: 1 },
  trackTitle: { fontSize: 14, fontWeight: '600', color: Colors.text },
  trackArtist: { fontSize: 12, color: Colors.textMuted, marginTop: 2 },
  albumGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 14 },
  albumCard: { width: 140, gap: 6 },
  albumCover: {
    width: 140,
    height: 140,
    borderRadius: 8,
    backgroundColor: Colors.surface,
  },
  albumTitle: { fontSize: 13, fontWeight: '600', color: Colors.text },
  albumArtist: { fontSize: 11, color: Colors.textMuted },
});
