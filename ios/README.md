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

## Come generare l'IPA (da Windows)

### Metodo 1: GitHub Actions (Consigliato al 100% — Gratuito e Senza Mac)
Abbiamo configurato una pipeline GitHub Actions automatizzata su macchine macOS con Xcode:

1. Vai su GitHub su **`DotHack88/Aura`** → Scheda **Actions**.
2. Nel menu a sinistra seleziona il workflow **Build iOS IPA (Aura)**.
3. Clicca su **Run workflow** (sul ramo `main`).
4. GitHub Actions avvierà la build su runner Mac ufficiale (`macos-latest`):
   - Prebuild nativo Expo + CocoaPods
   - Compilazione Release con `xcodebuild` (Unsigned)
   - Creazione del pacchetto `Aura.ipa`
5. Al termine (circa 8-12 minuti), troverai l'IPA pronto nella sezione **Artifacts** (o nella release) scaricabile direttamente.

### Metodo 2: EAS Build (Cloud Expo)
```bash
npx eas login
npm run build:ios
```

---

## Come installare l'IPA su iPhone (Sideloading)

Con l'IPA scaricato puoi installarlo sul tuo iPhone con qualsiasi Apple ID gratuito:

### Con Sideloadly (Metodo più semplice e veloce da Windows)
1. Scarica e installa **[Sideloadly](https://sideloadly.io/)** su Windows.
2. Collega il tuo iPhone al PC via USB (e accetta "Autorizza questo computer" se richiesto).
3. Apri Sideloadly: trascina il file `Aura.ipa` nella finestra.
4. Inserisci il tuo **Apple ID** (qualsiasi account gratuito).
5. Premi **Start**. Sideloadly firmerà l'IPA e installerà l'app nativa sul tuo iPhone in 1 minuto.
6. Sull'iPhone: vai in *Impostazioni > Generali > VPN e gestione dispositivo* e tocca "Autorizza" sul tuo profilo sviluppatore.

### Con AltStore
1. Apri **AltStore** sul tuo iPhone.
2. Vai su **My Apps**, tocca il `+` in alto a sinistra.
3. Seleziona il file `Aura.ipa` (trasferito via AirDrop, iCloud Drive o USB).
4. AltStore firmerà e installerà l'app direttamente.

---

## Bundle ID
`io.aura.app`

## Expo Account
`tidus291` — [expo.dev/accounts/tidus291](https://expo.dev/accounts/tidus291)

