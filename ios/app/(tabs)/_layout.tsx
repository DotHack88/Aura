import { Tabs } from 'expo-router';
import { View, StyleSheet } from 'react-native';
import { Home, Search, Radio, Library } from 'lucide-react-native';
import { Colors } from '@/constants/Colors';
import MiniPlayer from '@/components/player/MiniPlayer';
import FullPlayerModal from '@/components/player/FullPlayerModal';

export default function TabLayout() {
  return (
    <View style={styles.container}>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarStyle: styles.tabBar,
          tabBarActiveTintColor: Colors.primary,
          tabBarInactiveTintColor: Colors.textMuted,
          tabBarLabelStyle: styles.tabLabel,
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: 'Home',
            tabBarIcon: ({ color, size }) => <Home color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="explore"
          options={{
            title: 'Esplora',
            tabBarIcon: ({ color, size }) => <Search color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="live"
          options={{
            title: 'Live',
            tabBarIcon: ({ color, size }) => <Radio color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="library"
          options={{
            title: 'Libreria',
            tabBarIcon: ({ color, size }) => <Library color={color} size={size} />,
          }}
        />
      </Tabs>
      <MiniPlayer />
      <FullPlayerModal />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  tabBar: {
    backgroundColor: Colors.background,
    borderTopColor: Colors.border,
    borderTopWidth: 1,
    height: 84,
    paddingBottom: 24,
    paddingTop: 8,
  },
  tabLabel: {
    fontSize: 11,
    fontWeight: '500',
  },
});
