# Aura 🎵 — Native Android Music Player

![Version](https://img.shields.io/badge/versione-1.0.18-blueviolet?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green?style=flat-square)
![Build](https://img.shields.io/badge/build-release-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

**Aura** è un'applicazione musicale nativa per Android, progettata con un'interfaccia minimale, scura, elegante e focalizzata sull'ascolto immersivo. Costruita in **Jetpack Compose + Material 3**, offre testi sincronizzati in tempo reale e sincronizzazione multi-device tramite **Firebase**.

L'app utilizza un'architettura ibrida per l'estrazione dei dati:
- **YouTube Music (InnerTube API)**: per le ricerche veloci (con cache locale tramite Room) e per recuperare i brani correlati basati sugli algoritmi ufficiali di YouTube.
- **NewPipeExtractor**: per estrarre direttamente i flussi audio bypassando le limitazioni ufficiali, permettendo la riproduzione in background nativa via `Media3` (ExoPlayer).

> **Nota Legale**: Questo progetto è a solo scopo educativo. L'uso di NewPipeExtractor per estrarre i flussi audio diretti non è conforme alle Terms of Service ufficiali di YouTube, che richiedono l'uso dell'IFrame Player in primo piano per la riproduzione.

---

## 📦 Download APK

> Installa direttamente sul tuo dispositivo Android senza bisogno di compilare il progetto.

| # | Versione | Tipo | File | Note |
|---|----------|------|------|------|
| 1 | 1.0.18 | 🐛 Debug | [`app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk) | Test/sviluppo, debugger USB |
| 2 | 1.0.18 | 🚀 Release | [`Aura-1.0.18-release.apk`](app/build/outputs/apk/release/Aura-1.0.18-release.apk) | Ottimizzato R8, ~30-50% più piccolo |

### ⚠️ Tipo di build: Debug vs Release

| Caratteristica | 🐛 Debug (`Aura-x.x.x-debug.apk`) | 🚀 Release |
|---|---|---|
| **Scopo** | Test e sviluppo | Distribuzione pubblica |
| **Ottimizzazione** | ❌ Nessuna | ✅ ProGuard / R8 |
| **Debugger USB** | ✅ Abilitato (`isDebuggable = true`) | ❌ Disabilitato |

---

## ✨ Funzionalità Chiave

- 🎨 **Design Moderno & Minimale**: Interfaccia pulita, contrasto curato, gradienti e micro-interazioni fluide con Material 3.
- ⚡ **Player in Background**: Riproduzione nativa dell'audio a schermo spento grazie a ExoPlayer (Media3).
- 🎤 **Lyrics Sincronizzati**: Testi LRC con auto-scroll dinamico.
- 🔍 **Ricerca Intelligente & Caching**: Ricerca veloce tramite InnerTube, con cache locale Room (TTL 7 giorni) per limitare le chiamate di rete.
- 📚 **Libreria Personale**: Brani Preferiti con persistenza offline Room. Playlist personali.
- ☁️ **Sincronizzazione Cloud (Firebase)**: Accesso con Google Sign-In e sincronizzazione automatica su Firestore.
- 📻 **Aura Radio**: Coda dinamica alimentata dall'API InnerTube di YouTube Music, arricchita da filtri mood/genere ("A seguire").

---

## 🛠️ Stack Tecnologico

- **Linguaggio**: Kotlin 1.9+
- **UI Framework**: Jetpack Compose + Material 3
- **Architettura**: MVVM + Repository Pattern
- **Database Locale**: Room (con gestione cache ricerche)
- **Networking**: Retrofit 2 + OkHttp + Gson
- **Immagini**: Coil Compose
- **Backend & Cloud**: Firebase Authentication + Cloud Firestore
- **Estrazione Audio**: NewPipeExtractor
- **Riproduzione Media**: Media3 (ExoPlayer)
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34+

---

## 🚀 Come Compilare il Progetto

1. Apri **Android Studio**.
2. Apri la cartella del progetto.
3. Configura le API (necessario `google-services.json` per Firebase). I file di credenziali sono ignorati da Git per sicurezza.
4. Premi **Run (Shift + F10)**.

---

## 🎙️ Aura Radio — Il motore dei brani correlati

Aura implementa un sistema di radio dinamica (`PlayerManager.kt`) ispirato alle code infinite:
1. **Recupero Candidati**: Quando la coda scende a ≤3 brani, Aura interroga YouTube Music (via InnerTube) per recuperare i brani consigliati (Watch Next) a partire dall'ultimo brano riprodotto.
2. **Context & History**: I brani estratti vengono mixati con lo storico d'ascolto locale (Room).
3. **Chip Filter ("A seguire")**: L'utente può selezionare un "mood" (es. Relax, Anni '90). L'app applica il playlistId (`RDAMVM`) associato per ricaricare la coda secondo il nuovo contesto.
