import { useState, useMemo } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Image,
  TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Search, Radio as RadioIcon, Play, X } from 'lucide-react-native';
import { Colors } from '@/constants/Colors';
import { usePlayerStore } from '@/store/playerStore';

import rawRadiosData from '@/assets/radios.json';

interface RadioStation {
  id: string;
  name: string;
  url: string;
  logo: string;
  region: string;
}

// Parse and clean radio data
const ALL_STATIONS: RadioStation[] = [];
const REGIONS_SET = new Set<string>();

Object.entries(rawRadiosData as Record<string, { name: string; url: string; logo: string }[]>).forEach(
  ([regionKey, stations]) => {
    const formattedRegion = regionKey
      .split('-')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(' ');
    REGIONS_SET.add(formattedRegion);

    stations.forEach((st, idx) => {
      if (!st.name || st.name.toLowerCase().includes('vai alla homepage')) return;
      ALL_STATIONS.push({
        id: `${regionKey}-${idx}-${st.name.replace(/\s+/g, '-').toLowerCase()}`,
        name: st.name,
        url: st.url,
        logo: st.logo,
        region: formattedRegion,
      });
    });
  }
);

const REGIONS = ['Tutti', ...Array.from(REGIONS_SET).sort()];

export default function LiveScreen() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedRegion, setSelectedRegion] = useState('Tutti');

  const { playUrl, currentTrack } = usePlayerStore();

  const filteredStations = useMemo(() => {
    return ALL_STATIONS.filter((station) => {
      const matchesSearch = station.name
        .toLowerCase()
        .includes(searchQuery.trim().toLowerCase());
      const matchesRegion =
        selectedRegion === 'Tutti' || station.region === selectedRegion;
      return matchesSearch && matchesRegion;
    });
  }, [searchQuery, selectedRegion]);

  const handlePlayStation = (station: RadioStation) => {
    void playUrl(station.url, station.name, `Radio Live - ${station.region}`);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.liveBadge}>
            <View style={styles.liveDot} />
            <Text style={styles.liveBadgeText}>STAZIONI LIVE 24/7</Text>
          </View>
          <Text style={styles.title}>Radio in Diretta</Text>
          <Text style={styles.subtitle}>
            Ascolta le tue stazioni radio regionali e nazionali preferite
          </Text>

          {/* Search Bar */}
          <View style={styles.searchBar}>
            <Search size={18} color={Colors.textMuted} />
            <TextInput
              style={styles.searchInput}
              placeholder="Cerca stazione radio..."
              placeholderTextColor={Colors.textMuted}
              value={searchQuery}
              onChangeText={setSearchQuery}
              returnKeyType="search"
            />
            {searchQuery.length > 0 && (
              <TouchableOpacity onPress={() => setSearchQuery('')}>
                <X size={18} color={Colors.textMuted} />
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Region Filter Chips */}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.chipsScroll}
          contentContainerStyle={styles.chipsContainer}
        >
          {REGIONS.map((region) => {
            const isActive = selectedRegion === region;
            return (
              <TouchableOpacity
                key={region}
                style={[styles.chip, isActive && styles.chipActive]}
                onPress={() => setSelectedRegion(region)}
                activeOpacity={0.7}
              >
                <Text style={[styles.chipText, isActive && styles.chipTextActive]}>
                  {region}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>

        {/* Radio Station Grid / Count */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>
            <RadioIcon size={18} color={Colors.primary} />
            {' '}Stazioni ({filteredStations.length})
          </Text>
        </View>

        {filteredStations.length === 0 ? (
          <View style={styles.emptyContainer}>
            <RadioIcon size={48} color={Colors.textMuted} />
            <Text style={styles.emptyTitle}>Nessuna radio trovata</Text>
            <Text style={styles.emptySub}>
              Prova a cercare con un altro nome o seleziona un&apos;altra regione.
            </Text>
          </View>
        ) : (
          <View style={styles.gridContainer}>
            {filteredStations.map((station) => {
              const isPlayingThis = currentTrack?.id === station.id;
              return (
                <TouchableOpacity
                  key={station.id}
                  style={[styles.card, isPlayingThis && styles.cardActive]}
                  onPress={() => handlePlayStation(station)}
                  activeOpacity={0.8}
                >
                  <Image
                    source={{ uri: station.logo }}
                    style={styles.cardLogo}
                    resizeMode="cover"
                  />
                  <View style={styles.cardContent}>
                    <Text style={styles.cardTitle} numberOfLines={1}>
                      {station.name}
                    </Text>
                    <Text style={styles.cardRegion} numberOfLines={1}>
                      {station.region}
                    </Text>
                  </View>
                  <TouchableOpacity
                    style={styles.playButton}
                    onPress={() => handlePlayStation(station)}
                  >
                    <Play size={16} color={Colors.text} fill={Colors.text} />
                  </TouchableOpacity>
                </TouchableOpacity>
              );
            })}
          </View>
        )}

        <View style={{ height: 40 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 16 },
  liveBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.4)',
    marginBottom: 8,
  },
  liveDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#ef4444',
  },
  liveBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    color: '#ef4444',
    letterSpacing: 0.5,
  },
  title: { fontSize: 28, fontWeight: '800', color: Colors.text, marginBottom: 4 },
  subtitle: { fontSize: 14, color: Colors.textSecondary, marginBottom: 16 },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderRadius: 12,
    paddingHorizontal: 14,
    height: 44,
    gap: 10,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  searchInput: {
    flex: 1,
    color: Colors.text,
    fontSize: 14,
  },
  chipsScroll: { marginBottom: 16 },
  chipsContainer: { paddingHorizontal: 20, gap: 8 },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 18,
    backgroundColor: Colors.surface,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  chipActive: {
    backgroundColor: Colors.text,
    borderColor: Colors.text,
  },
  chipText: { fontSize: 13, fontWeight: '500', color: Colors.textSecondary },
  chipTextActive: { color: '#000', fontWeight: '700' },
  sectionHeader: { paddingHorizontal: 20, marginBottom: 12 },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: Colors.text },
  gridContainer: {
    paddingHorizontal: 20,
    gap: 10,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    padding: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Colors.border,
    gap: 12,
  },
  cardActive: {
    borderColor: Colors.primary,
    backgroundColor: Colors.surfaceElevated,
  },
  cardLogo: {
    width: 48,
    height: 48,
    borderRadius: 8,
    backgroundColor: '#000',
  },
  cardContent: { flex: 1 },
  cardTitle: { fontSize: 14, fontWeight: '600', color: Colors.text },
  cardRegion: { fontSize: 12, color: Colors.textMuted, marginTop: 2 },
  playButton: {
    padding: 8,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 40,
    paddingHorizontal: 20,
    gap: 12,
  },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: Colors.text },
  emptySub: { fontSize: 13, color: Colors.textMuted, textAlign: 'center' },
});
