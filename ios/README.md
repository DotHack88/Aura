# Aura iOS 🍎

App nativa iOS di **Aura Music** — costruita con **React Native + Expo**.

## Stack

| Tecnologia | Scopo |
|---|---|
| React Native 0.88 | UI nativa iOS |
| Expo 58 | Toolchain + build |
| Expo Router | Navigazione file-based |
| expo-av | Player audio nativo |
| expo-blur | Effetti glassmorphism |
| Zustand | State management |
| EAS Build | Compilazione IPA su cloud Mac |

## Struttura

```
ios/
├── app/
│   ├── _layout.tsx          ← Root layout (Stack)
│   └── (tabs)/
│       ├── _layout.tsx      ← Tab navigator
│       ├── index.tsx        ← Home
│       ├── explore.tsx      ← Esplora
│       ├── live.tsx         ← Live Radio
│       └── library.tsx      ← Libreria
├── components/
│   └── player/
│       └── MiniPlayer.tsx   ← Mini player persistente
├── store/
│   └── playerStore.ts       ← Zustand store audio
├── constants/
│   └── Colors.ts            ← Palette colori Aura
├── assets/                  ← Icone e splash
├── app.json                 ← Config Expo (Bundle ID: io.aura.app)
├── eas.json                 ← Profili build EAS
└── package.json
```

## Come fare la build IPA (da Windows)

### 1. Login su Expo
```bash
npx eas login
# inserisci: tidus291
```

### 2. Inizializza il progetto su EAS (solo la prima volta)
```bash
npx eas init
```

### 3. Build IPA per test (sideloading)
```bash
npm run build:ios
# oppure: npx eas build --platform ios --profile preview
```
> La build avviene su Mac nel cloud (~15-20 min)  
> Riceverai un link per scaricare l'IPA

### 4. Installa su iPhone
- **AltStore** → trascina l'IPA
- **Sideloadly** → drag & drop + Apple ID

## Bundle ID
`io.aura.app`

## Expo Account
`tidus291` — [expo.dev/accounts/tidus291](https://expo.dev/accounts/tidus291)

