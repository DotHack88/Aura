import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Colors } from '@/constants/Colors';

export default function HomeScreen() {
  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Buonasera 👋</Text>
            <Text style={styles.subtitle}>Cosa vuoi ascoltare?</Text>
          </View>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>A</Text>
          </View>
        </View>

        {/* Hero Banner */}
        <LinearGradient colors={Colors.gradient.purple} style={styles.hero}>
          <Text style={styles.heroLabel}>✨ Consigliati per te</Text>
          <Text style={styles.heroSub}>Basato sui tuoi ascolti recenti</Text>
        </LinearGradient>

        {/* Sezioni */}
        <Text style={styles.sectionTitle}>Ascoltati di recente</Text>
        <View style={styles.placeholder}>
          <Text style={styles.placeholderText}>Le tue tracce recenti appariranno qui</Text>
        </View>

        <Text style={styles.sectionTitle}>Nuove uscite</Text>
        <View style={styles.placeholder}>
          <Text style={styles.placeholderText}>Album e singoli recenti</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 16,
  },
  greeting: { fontSize: 24, fontWeight: '700', color: Colors.text },
  subtitle: { fontSize: 14, color: Colors.textSecondary, marginTop: 2 },
  avatar: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: Colors.primaryDark,
    alignItems: 'center', justifyContent: 'center',
  },
  avatarText: { color: Colors.text, fontWeight: '700', fontSize: 16 },
  hero: {
    margin: 16, borderRadius: 20, padding: 24, height: 140,
    justifyContent: 'flex-end',
  },
  heroLabel: { color: Colors.text, fontSize: 20, fontWeight: '700' },
  heroSub: { color: Colors.textSecondary, fontSize: 13, marginTop: 4 },
  sectionTitle: {
    fontSize: 18, fontWeight: '700', color: Colors.text,
    paddingHorizontal: 20, marginTop: 28, marginBottom: 12,
  },
  placeholder: {
    marginHorizontal: 20,
    padding: 20,
    backgroundColor: Colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
  },
  placeholderText: { color: Colors.textMuted, fontSize: 13 },
});

