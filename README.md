# Aura 🎵 — Native Android Music Player

![Version](https://img.shields.io/badge/versione-1.0.14-blueviolet?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green?style=flat-square)
![Build](https://img.shields.io/badge/build-debug-orange?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

**Aura** è un'applicazione musicale nativa per Android, progettata con un'interfaccia minimale, scura, elegante e focalizzata sull'ascolto immersivo. Utilizza **YouTube IFrame Player API** (conforme alle ToS ufficiali di YouTube), **YouTube Data API v3**, **Jetpack Compose + Material 3**, testi sincronizzati in tempo reale in stile Karaoke/Spotify, e sincronizzazione multi-device tramite **Firebase**.

---

## 📦 Download APK

> Installa direttamente sul tuo dispositivo Android senza bisogno di compilare il progetto.

| # | Versione | Tipo | File | Note |
|---|----------|------|------|------|
| 1 | 1.0.14 | 🐛 Debug | [`app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk) | Test/sviluppo, debugger USB |
| 2 | 1.0.14 | 🚀 Release | [`Aura-1.0.14-release.apk`](app/build/outputs/apk/release/Aura-1.0.14-release.apk) | Ottimizzato R8, ~30-50% più piccolo |

### ⚠️ Tipo di build: Debug vs Release

| Caratteristica | 🐛 Debug (`Aura-x.x.x-debug.apk`) | 🚀 Release |
|---|---|---|
| **Scopo** | Test e sviluppo | Distribuzione pubblica |
| **Firmatura** | Chiave debug automatica (Android SDK) | Keystore personale |
| **Ottimizzazione** | ❌ Nessuna | ✅ ProGuard / R8 |
| **Debugger USB** | ✅ Abilitato (`isDebuggable = true`) | ❌ Disabilitato |
| **Distribuibile** | Solo sideload (install manuale) | ✅ Google Play Store |
| **Performance** | Leggermente più lenta | Ottimizzata |

> **Come installare l'APK debug**:
> 1. Abilita **"Origini sconosciute"** nelle Impostazioni Android → Sicurezza.
> 2. Copia il file APK sul dispositivo via USB o condivisione file.
> 3. Apri il file dall'app **File Manager** del dispositivo e premi **Installa**.
> 4. Oppure via ADB: `adb install app-debug.apk`

---

## ✨ Funzionalità Chiave

- 🎨 **Design Moderno & Minimale**: Interfaccia pulita, contrasto curato, gradienti e micro-interazioni fluide con Material 3.
- ⚡ **Player Centrale con Gesti Intuitivi**:
  - **Copertina in evidenza** con controlli essenziali (Play/Pause, Prev, Next, Shuffle, Repeat, Preferiti).
  - **Switch Modalità Video**: Passa con un tocco tra la copertina e il player video di YouTube incorporato.
  - **Swipe Up verso i Testi**: Scorri verso l'alto dalla schermata player per accedere istantaneamente ai testi sincronizzati.
  - **Swipe Down da Testi**: Scorri verso il basso per tornare al player.
- 🎤 **Lyrics Sincronizzati (Karaoke / Spotify style)**:
  - Testi con timing ad alta precisione (formato LRC standard).
  - Auto-scroll dinamico con evidenziazione ed ingrandimento della riga corrente in base ai secondi di riproduzione.
  - Tocco rapido su qualsiasi riga per effettuare un `seek` istantaneo alla strofa desiderata.
- 🔍 **Ricerca YouTube Intelligente**:
  - Ricerca istantanea con debounce (400ms) per preservare la quota API.
  - Titolo, artista/canale, durata e thumbnail ad alta risoluzione.
- 📚 **Libreria Personale**:
  - Brani Preferiti con persistenza offline Room.
  - Playlist personali: creazione, aggiunta e gestione.
  - Cronologia dei brani ascoltati di recente.
- ☁️ **Sincronizzazione Cloud & Multi-Device (Firebase)**:
  - Accesso con Email & Password o Google Sign-In.
  - Sincronizzazione automatica su Cloud Firestore: preferiti, playlist e stato dell'ultimo brano ascoltato.
  - Possibilità di riprendere l'ascolto esattamente da dove lo avevi interrotto su un altro dispositivo.

---

## 🛠️ Stack Tecnologico

- **Linguaggio**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose + Material 3
- **Architettura**: MVVM + Clean Architecture / Repository Pattern
- **Database Locale**: Room 2.6
- **Networking**: Retrofit 2 + OkHttp + Gson
- **Immagini**: Coil Compose
- **Backend & Cloud**: Firebase Authentication + Cloud Firestore
- **Riproduzione Streaming**: YouTube IFrame Player (Web-bridged conforme ToS) + NewPipeExtractor
- **Cast**: Google Cast SDK (Chromecast / Google Home / Nest Audio)
- **Min SDK**: 26 (Android 8.0 Oreo) | **Target SDK**: 34+

---

## 🚀 Come Compilare il Progetto

1. Apri **Android Studio** (Hedgehog, Iguana, Ladybug o successivo).
2. Seleziona **Open** e scegli la cartella `c:\Users\Emanuele\Muse`.
3. Attendi la sincronizzazione di Gradle.
4. Consulta le guide di configurazione delle credenziali:
   - [docs/SETUP_YOUTUBE.md](docs/SETUP_YOUTUBE.md) per inserire la tua chiave YouTube Data API.
   - [docs/SETUP_FIREBASE.md](docs/SETUP_FIREBASE.md) per collegare il tuo file `google-services.json`.
5. Seleziona un emulatore Android o un dispositivo fisico con debug USB abilitato e premi **Run (Shift + F10)**.

> **Output APK**: dopo ogni build l'APK viene generato automaticamente in  
> `app/build/outputs/apk/debug/Aura-{versione}-debug.apk`

---

## 📋 Changelog

| Versione | Note |
|----------|------|
| **1.0.14** | Testo orizzontale, fix disconnessione, radio automatica |
| **1.0.12** | Build precedente — debug |
