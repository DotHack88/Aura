# Aura 🎵 — Native Android Music Player

![Version](https://img.shields.io/badge/versione-1.0.17-blueviolet?style=flat-square)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green?style=flat-square)
![Build](https://img.shields.io/badge/build-release-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

**Aura** è un'applicazione musicale nativa per Android, progettata con un'interfaccia minimale, scura, elegante e focalizzata sull'ascolto immersivo. Utilizza **YouTube IFrame Player API** (conforme alle ToS ufficiali di YouTube), **YouTube Data API v3**, **Jetpack Compose + Material 3**, testi sincronizzati in tempo reale in stile Karaoke/Spotify, e sincronizzazione multi-device tramite **Firebase**.

---

## 📦 Download APK

> Installa direttamente sul tuo dispositivo Android senza bisogno di compilare il progetto.

| # | Versione | Tipo | File | Note |
|---|----------|------|------|------|
| 1 | 1.0.17 | 🐛 Debug | [`app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk) | Test/sviluppo, debugger USB |
| 2 | 1.0.17 | 🚀 Release | [`Aura-1.0.17-release.apk`](app/build/outputs/apk/release/Aura-1.0.17-release.apk) | Ottimizzato R8, ~30-50% più piccolo |

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
| **1.0.17** | 🐛 Fix chip «A seguire» nella ricerca: i tag mood/genere (Familiare, R&B, Relax…) ora appaiono sempre nella coda, anche riproducendo brani dalla ricerca o da un album. Introdotto parametro `appendTracks` in `generateRadioQueue` per separare il recupero dei chip dall'aggiunta di brani radio |
| **1.0.16** | 🎛️ Chip Filter «A seguire»: filtri mood/genere nella coda radio (Familiare, R&B, Relax, Anni '90…), coda ridisegnata stile YouTube Music con sezione «In riproduzione» + «A seguire», playlistId RDAMVM per attivare il chip cloud |
| **1.0.15** | ✨ Muse Radio Engine v1: coda dinamica intelligente, ranking dei preferiti, supporto profili di sessione |
| **1.0.14** | Testo orizzontale, fix disconnessione, radio statica iniziale |
| **1.0.12** | Build precedente — debug |

---

## 🎙️ Muse Radio — Related Tracks Engine

Questa sezione descrive il sistema di raccomandazione musicale di Muse, ispirato al comportamento esterno della Spotify Radio.

### Come funziona Spotify Radio

Spotify distingue la **Spotify Radio** dalla normale **riproduzione automatica**:

- **Radio**: avviata da un brano, genera una raccolta continuamente aggiornata.
- **Autoplay**: entra in gioco al termine di una selezione, continuando con brani simili.

Spotify utilizza sistemi di **nearest-neighbor search** per trovare elementi simili tra brani, artisti e album. Gli algoritmi considerano:
- Attributi audio dei brani
- Relazioni tra brani (co-ascolto)
- Segnali del comportamento degli ascoltatori (skip, like, salvataggi)

### Architettura del sistema a 3 livelli

#### Livello 1 — Similarità audio

Ogni brano ha un **vettore di caratteristiche**:

```
Riders on the Storm
  genre:        rock = 0.90 | psychedelic = 0.82 | blues = 0.54
  energy:       0.32
  danceability: 0.41
  valence:      0.38
  tempo:        104 BPM
  acousticness: 0.28
  era:          1971
```

I brani vengono confrontati tramite **cosine similarity**:

```
similarity(A, B) = cosine_similarity(vectorA, vectorB)
```

Esempio di risultato:

```
Riders on the Storm
  └─ The End              94%
  └─ Light My Fire        91%
  └─ People Are Strange   88%
  └─ Nights in White Satin 79%
  └─ White Room           76%
```

#### Livello 2 — Collaborative Filtering

Il sistema osserva i pattern di co-ascolto degli utenti:

> "Gli utenti che ascoltano A spesso ascoltano B."

```
Riders on the Storm
  ├── The End             0.91
  ├── Nights in White Satin 0.83
  ├── Light My Fire       0.81
  └── White Room          0.63
```

Non è necessario sapere *perché* i brani sono correlati — è sufficiente osservare la correlazione comportamentale.

#### Livello 3 — Profilo personale (Taste Profile)

Muse costruisce un profilo utente dinamico basato sulla cronologia di ascolto:

```json
{
  "generi":  { "rock": 0.91, "alternative": 0.72, "blues": 0.65, "electronic": 0.43 },
  "artisti": { "doors": 0.94, "pink_floyd": 0.88, "queen": 0.74, "depeche_mode": 0.69 }
}
```

### Flusso del candidato → brano successivo

```
BRANO CORRENTE
      │
      ▼
┌─────────────────┐
│ Candidate Engine │
└────────┬────────┘
         │
   ┌─────┴──────┬───────────┐
   ▼            ▼           ▼
Similarità   Co-ascolto  Profilo
  audio       utenti     personale
   │            │           │
   └─────┬──────┴───────────┘
         ▼
   100–500 candidati
         │
         ▼
  FILTRO DUPLICATI
         │
         ▼
  RANKING ENGINE
         │
   ┌─────┴──────┬──────────┐
   ▼            ▼          ▼
 qualità     interesse  diversità
   │            │          │
   └─────┬──────┴──────────┘
         ▼
      TOP 20–50
         │
         ▼
    NEXT TRACK
```

### Evoluzione della sessione

Il sistema non genera una lista fissa, ma aggiorna dinamicamente la coda in base agli ascolti correnti.

**Esempio — inizio sessione:**
```
🎵 Riders on the Storm →  1. The End
                           2. Light My Fire
                           3. People Are Strange
                           4. White Room
                           5. Echoes
```

**Dopo aver ascoltato** `The End` → `Echoes`, il sistema rileva interesse per il rock psichedelico/progressivo:
```
🎵 Echoes →  1. Shine On You Crazy Diamond
             2. Us and Them
             3. The End
             4. No Quarter
```

La coda viene rigenerata ogni 2–3 brani, mai all'inizio in blocco:

```
Coda dinamica:
  [1] The End         ← in riproduzione
  [2] Light My Fire
  [3] Echoes
  [4] White Room
  [5] No Quarter
  ...
  [3] → Muse genera 10–20 nuovi candidati e aggiorna [4..N]
```

### Segnali raccolti e pesi

| Evento | Peso indicativo |
|--------|----------------|
| Play | +1 |
| Ascolto > 30 sec | +2 |
| Ascolto > 50% | +3 |
| Ascolto completo | +4 |
| Replay | +5 |
| Like / Preferito | +8 |
| Aggiunto a playlist | +7 |
| Skip < 10 sec | −5 |
| Skip < 30 sec | −3 |
| Dislike | −10 |
| Ricerca del brano | +1 |

> I pesi sono indicativi e calibrabili; non replicano i valori interni di Spotify.

### Scoring multi-sorgente con contesto

```
CORRELATI
      │
  ┌───┴────────┬────────────┐
  ▼            ▼            ▼
AUDIO       COMMUNITY    PERSONALE
similarity  co-listening  taste
  │            │            │
  └───┬────────┴────────────┘
      ▼
CONTEXT SCORE
  (ora del giorno, mood della sessione)
      ▼
NEXT SONG
```

**Contesto temporale (esempio):**

| Fascia oraria | Caratteristica consigliata |
|---------------|---------------------------|
| 06:00 – 09:00 | Musica tranquilla / acustica |
| 12:00 – 16:00 | Musica energica |
| 21:00 – 00:00 | Musica rilassante |

### ✅ Stato dell'Implementazione (Muse Radio Engine v1)

L'algoritmo descritto è stato **completamente implementato** nell'app (vedi `PlayerManager.kt` e `MusicRepository.kt`):

1. **Coda Dinamica Evolutiva (`generateRadioQueue`)**: La radio non genera una lista statica all'inizio. Quando la coda scende a ≤3 brani rimanenti, viene effettuato un *refill* asincrono interrogando le API partendo dall'**ultimo brano ascoltato in modo sostanziale** (il *Seed*), non dal brano originale. Questo permette alla radio di "seguire" i cambi di genere dell'utente.
2. **Tracciamento Segnali (`recordListenSignal`)**: Ogni azione dell'utente (skip veloce <10s, skip lungo <30s, ascolto >30s, ascolto >50%, ascolto completo, replay) viene catturata dal player e tradotta in una variazione di punteggio (`playCount`) salvata nel database locale Room (`HistoryEntity`).
3. **Taste Profile & Sessione Corrente**: Il sistema tiene traccia degli artisti ascoltati nella sessione corrente (`sessionArtists`). Durante il ranking dei nuovi candidati forniti dal motore collaborativo globale (le API di YouTube Music), l'algoritmo riordina i brani privilegiando quelli con un alto punteggio storico locale e applica un **bonus matematico (+3)** agli artisti già presenti nella sessione, creando un'esperienza fluida e contestuale.
4. **Chip Filter «A seguire» (`selectChip`)**: Il pannello coda mostra i filtri mood/genere forniti direttamente da YouTube Music (es. *Familiare*, *R&B*, *Relax*, *Anni '90*, *Ritmata*, *Romantica*…). Selezionando un chip, la radio ricarica istantaneamente la coda con brani coerenti con il filtro scelto, usando il `playlistId` `RDAMVM{videoId}` per attivare il chip cloud nell'API Innertube.
