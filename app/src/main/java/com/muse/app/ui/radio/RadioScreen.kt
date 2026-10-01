package com.muse.app.ui.radio

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.muse.app.domain.model.Track
import com.muse.app.player.PlayerManager

data class RadioStation(
    val videoId: String,        // YouTube video ID oppure URL stream diretto
    val name: String,
    val description: String,
    val coverUrl: String,
    val isLive: Boolean = true,
    val region: String = "Nazionale"
)

// ─── Mappa Regione → Emoji icona ──────────────────────────────────────────────
val regionIcons: Map<String, String> = mapOf(
    "Tutte"                  to "",
    "Nazionale"              to "",
    "Abruzzo"                to "",
    "Basilicata"             to "",
    "Calabria"               to "",
    "Campania"               to "",
    "Emilia-Romagna"         to "",
    "Friuli Venezia Giulia"  to "",
    "Lazio"                  to "",
    "Liguria"                to "",
    "Lombardia"              to "",
    "Marche"                 to "",
    "Molise"                 to "",
    "Piemonte"               to "",
    "Puglia"                 to "",
    "Sardegna"               to "",
    "Sicilia"                to "",
    "Toscana"                to "",
    "Trentino-Alto Adige"    to "",
    "Umbria"                 to "",
    "Valle d'Aosta"          to "",
    "Veneto"                 to ""
)

// ─── Stazioni YouTube Live ─────────────────────────────────────────────────
val youtubeRadios = listOf(
    RadioStation(
        videoId = "UcrtmnGBUjM",
        name = "ChillYourMind Radio",
        description = "Chill House 24/7 · Summer Vibes Deep House & Tropical",
        coverUrl = "https://img.youtube.com/vi/UcrtmnGBUjM/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "rFZHOHl-L8A",
        name = "Lofi Girl – Study",
        description = "lofi hip hop radio – beats to relax/study to",
        coverUrl = "https://img.youtube.com/vi/rFZHOHl-L8A/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "nI725iVsyoQ",
        name = "Deep Sleep Music",
        description = "24/7 calm ambient to sleep & dream to",
        coverUrl = "https://img.youtube.com/vi/nI725iVsyoQ/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "4xDzrJKXOOY",
        name = "Synthwave Radio",
        description = "synthwave radio – beats to chill/game to",
        coverUrl = "https://img.youtube.com/vi/4xDzrJKXOOY/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "mdJ7zFNZU1Y",
        name = "Rainy Night Lofi",
        description = "90's Peaceful Rainy Night – Lofi Beats for Sleeping & Relaxing",
        coverUrl = "https://img.youtube.com/vi/mdJ7zFNZU1Y/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "zBRFf8gAsR8",
        name = "Rainy Cafe Lofi",
        description = "24/7 Lofi Radio – Rainy Cafe & Cozy Ocean Beats",
        coverUrl = "https://img.youtube.com/vi/zBRFf8gAsR8/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "3fOLhGDvwyM",
        name = "Rain ASMR – Deep Sleep",
        description = "Stress & anxiety relief – Increase Deep Sleep, Rain ASMR",
        coverUrl = "https://img.youtube.com/vi/3fOLhGDvwyM/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "7kdmNZwVLrw",
        name = "Summer Sunset Deep House",
        description = "LIVE 24/7 – Luxury Ocean Lounge, Tropical Chillout & Poolside Music",
        coverUrl = "https://img.youtube.com/vi/7kdmNZwVLrw/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "1J4ZnM0h3kc",
        name = "Nocturnal Flow",
        description = "24/7 Live Melodic & Progressive House | Deep House Radio",
        coverUrl = "https://img.youtube.com/vi/1J4ZnM0h3kc/hqdefault.jpg"
    ),
    RadioStation(
        videoId = "cF4ybgr_b48",
        name = "Best of Deep House 2026",
        description = "LIVE 24/7 – Chill Mix & Deep Feelings",
        coverUrl = "https://img.youtube.com/vi/cF4ybgr_b48/hqdefault.jpg"
    )
)

val lofiRadios = listOf(
    RadioStation(
        videoId = "https://ice1.somafm.com/groovesalad-128-mp3",
        name = "Groove Salad (SomaFM)",
        description = "A nicely chilled plate of ambient/downtempo beats and grooves.",
        coverUrl = "https://somafm.com/img/groovesalad120.png"
    ),
    RadioStation(
        videoId = "https://ice1.somafm.com/defcon-128-mp3",
        name = "DEF CON Radio",
        description = "Music for Hacking. The DEF CON Year-Round Channel.",
        coverUrl = "https://somafm.com/img/defcon120.png"
    ),
    RadioStation(
        videoId = "https://ice1.somafm.com/secretagent-128-mp3",
        name = "Secret Agent",
        description = "The soundtrack for your stylish, mysterious, dangerous life.",
        coverUrl = "https://somafm.com/img/secretagent120.png"
    ),
    RadioStation(
        videoId = "http://stream.zeno.fm/f3wvbbqmdg8uv",
        name = "Lofi Hip Hop Radio",
        description = "24/7 Lofi chill beats",
        coverUrl = "https://ui-avatars.com/api/?name=Lofi%20Radio&background=8E24AA&color=fff&size=150"
    ),
    RadioStation(
        videoId = "https://ice1.somafm.com/beatblender-128-mp3",
        name = "Beat Blender",
        description = "A late night blend of deep-house and downtempo chill.",
        coverUrl = "https://somafm.com/img/beatblender120.png"
    )
)

// ─── Radio Nazionali ──────────────────────────────────────────────────────────
val nazionaliRadios = listOf(
    RadioStation(
        videoId = "http://icestreaming.rai.it/1.mp3",
        name = "Rai Radio 1",
        description = "Pubblica, notizie e cultura",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icestreaming.rai.it/2.mp3",
        name = "Rai Radio 2",
        description = "Musica e intrattenimento",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/697/rai-radio-2.d26fdf24.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icestreaming.rai.it/3.mp3",
        name = "Rai Radio 3",
        description = "Classica e cultura",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/259/rai-radio-3.69d72477.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icestreaming.rai.it/6.mp3",
        name = "Rai Isoradio",
        description = "Traffico e mobilità",
        coverUrl = "https://www.rai.it/dl/img/2020/06/1592991054366_Logo_Rai_Isoradio.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://shoutcast.rtl.it/rtl-1025-aac",
        name = "RTL 102.5",
        description = "Very Normal People",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/558/rtl-1025.a3a2eab4.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://radiodeejay-lh.akamaihd.net/i/RadioDeejay_Live_1@189857/master.m3u8",
        name = "Radio Deejay",
        description = "One Nation, One Station",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/114/radio-deejay.9a74a10e.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icecast.unitedradio.it/Radio105.mp3",
        name = "Radio 105",
        description = "Proud to be different",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://icstream.rds.radio/rds",
        name = "RDS",
        description = "100% Grandi Successi",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8",
        name = "Radio Italia",
        description = "Solo Musica Italiana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/108/virgin-radio-italia.1b6b553c.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://shoutcast2.radio24.it:8000/;",
        name = "Radio 24",
        description = "Il Sole 24 Ore",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/379/radio-24.9a6e1f0e.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icecast.unitedradio.it/RMC.mp3",
        name = "RMC Radio Monte Carlo",
        description = "Il meglio della musica",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/613/rmc-radio-monte-carlo.bcde110a.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icecast.unitedradio.it/Virgin.mp3",
        name = "Virgin Radio Italia",
        description = "The first digital radio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/108/virgin-radio-italia.1b6b553c.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://icecast.unitedradio.it/r101",
        name = "R101",
        description = "Adult Contemporary & Pop",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/r101.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://m2stream.fm/radio/m2o",
        name = "m2o",
        description = "Music to move",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/915/m2o.d231e3ef.jpg",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://sportiva.inmystream.it/stream/sportiva",
        name = "Radio Sportiva",
        description = "Sport 24/7",
        coverUrl = "https://www.radiosportiva.com/wp-content/uploads/2021/09/logo-radiosportiva-white.svg",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://dreamsiteradiocp.com:8092/;",
        name = "Radio Maria",
        description = "Pace e bene",
        coverUrl = "https://radiomaria.it/wp-content/uploads/2021/04/Logo-Radio-Maria.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "http://live.radioradicale.it/live.mp3",
        name = "Radio Radicale",
        description = "Politica e parlamento",
        coverUrl = "https://www.radioradicale.it/sites/default/files/logo-radio-radicale.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://onair.armisa.it/listen/armisa/radio.mp3",
        name = "Radio Armisa",
        description = "Liscio, folk e tradizione",
        coverUrl = "https://armisa.it/assets/favicon/android-chrome-512x512.png",
        region = "Nazionale"
    ),
    RadioStation(
        videoId = "https://classichitsradio.streamingmedia.it/play",
        name = "Classic Hits Radio Italia",
        description = "70s & 80s hits HQ",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/432/radio-italia.04705bd9.png",
        region = "Nazionale"
    )
)

// ─── Abruzzo ───────────────────────────────────────────────
val abruzzoRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Abruzzo"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Abruzzo",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Abruzzo"
    )
)

// ─── Basilicata ───────────────────────────────────────────────
val basilicataRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radionorba",
        name = "Radio Norba",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/763/radio-norba.b54206c1.png",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio2",
        name = "Rai Radio 2",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/533/rai-radio-2.4ceacc48.png",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Basilicata"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Basilicata",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Basilicata"
    )
)

// ─── Calabria ───────────────────────────────────────────────
val calabriaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Calabria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio2",
        name = "Rai Radio 2",
        description = "Top radio Calabria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/533/rai-radio-2.4ceacc48.png",
        region = "Calabria"
    )
)

// ─── Campania ───────────────────────────────────────────────
val campaniaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskissnapoli",
        name = "Radio Kiss Kiss Napoli",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/077/radio-kiss-kiss-napoli.9ebfd8e6.png",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Campania"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiomarte",
        name = "Radio Marte",
        description = "Top radio Campania",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/755/radio-marte.003f3162.png",
        region = "Campania"
    )
)

// ─── Emilia-Romagna ───────────────────────────────────────────────
val emiliaRomagnaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiobruno",
        name = "Radio Bruno",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/187/radio-bruno.56b8f344.png",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Emilia-Romagna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Emilia-Romagna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Emilia-Romagna"
    )
)

// ─── Friuli Venezia Giulia ───────────────────────────────────────────────
val friuliRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rmc-radiomontecarlo",
        name = "RMC - Radio Monte Carlo",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/p5ugpgUxGM.png",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Friuli Venezia Giulia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Friuli Venezia Giulia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Friuli Venezia Giulia"
    )
)

// ─── Lazio ───────────────────────────────────────────────
val lazioRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/teleradiostereo",
        name = "Tele Radio Stereo",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/646/tele-radio-stereo.7d9873cb.png",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rdsrelax",
        name = "RDS Relax",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/698/rds-relax.00deead8.png",
        region = "Lazio"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/retesport",
        name = "Rete Sport",
        description = "Top radio Lazio",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/781/rete-sport.64a27406.png",
        region = "Lazio"
    )
)

// ─── Liguria ───────────────────────────────────────────────
val liguriaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Liguria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rmc-radiomontecarlo",
        name = "RMC - Radio Monte Carlo",
        description = "Top radio Liguria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/p5ugpgUxGM.png",
        region = "Liguria"
    )
)

// ─── Lombardia ───────────────────────────────────────────────
val lombardiaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Lombardia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Lombardia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Lombardia"
    )
)

// ─── Marche ───────────────────────────────────────────────
val marcheRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Marche"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosubasio",
        name = "Radio Subasio",
        description = "Top radio Marche",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/629/radio-subasio.cb8e49ac.jpg",
        region = "Marche"
    )
)

// ─── Molise ───────────────────────────────────────────────
val moliseRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/virginradio",
        name = "Virgin Radio",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/457/virgin-radio.89dd9417.png",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Molise"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio2",
        name = "Rai Radio 2",
        description = "Top radio Molise",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/533/rai-radio-2.4ceacc48.png",
        region = "Molise"
    )
)

// ─── Piemonte ───────────────────────────────────────────────
val piemonteRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Piemonte"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Piemonte",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Piemonte"
    )
)

// ─── Puglia ───────────────────────────────────────────────
val pugliaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Puglia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Puglia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Puglia"
    )
)

// ─── Sardegna ───────────────────────────────────────────────
val sardegnaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiolina",
        name = "Radiolina",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/617/radiolina.3352df05.png",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Sardegna"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Sardegna",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Sardegna"
    )
)

// ─── Sicilia ───────────────────────────────────────────────
val siciliaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Sicilia"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiomargherita",
        name = "Radio Margherita",
        description = "Top radio Sicilia",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/054/radio-margherita.b6d0e2f8.jpg",
        region = "Sicilia"
    )
)

// ─── Toscana ───────────────────────────────────────────────
val toscanaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiobrunofiorentina",
        name = "Radio Bruno Fiorentina",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/868/radio-bruno-fiorentina.17ee370c.png",
        region = "Toscana"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Toscana",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Toscana"
    )
)

// ─── Trentino-Alto Adige ───────────────────────────────────────────────
val trentinoRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/raisüdtirol",
        name = "RAI Südtirol",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/790/rai-sudtirol.3ae62730.jpg",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Trentino-Alto Adige"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/südtirol1",
        name = "Südtirol 1",
        description = "Top radio Trentino-Alto Adige",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/072/sudtirol-1.e4802dc4.png",
        region = "Trentino-Alto Adige"
    )
)

// ─── Umbria ───────────────────────────────────────────────
val umbriaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiokisskiss",
        name = "Radio Kiss Kiss",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/076/radio-kiss-kiss.39015a45.jpg",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosubasio",
        name = "Radio Subasio",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/629/radio-subasio.cb8e49ac.jpg",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Umbria"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Umbria",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Umbria"
    )
)

// ─── Valle d'Aosta ───────────────────────────────────────────────
val valledaostaRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radioitaliasolomusicaitaliana",
        name = "Radio Italia solomusicaitaliana",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/182/radio-italia-solomusicaitaliana.b6905137.png",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiocapital",
        name = "Radio Capital",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/676/radio-capital-live.515e2038.png",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio105",
        name = "Radio 105",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/730/radio-105.0752232f.png",
        region = "Valle d'Aosta"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/virginradio",
        name = "Virgin Radio",
        description = "Top radio Valle d'Aosta",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/457/virgin-radio.89dd9417.png",
        region = "Valle d'Aosta"
    )
)

// ─── Veneto ───────────────────────────────────────────────
val venetoRadios = listOf(
    RadioStation(
        videoId = "https://streaming.radio.it/radiodeejay",
        name = "Radio Deejay",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/299/radio-deejay.ca197396.jpg",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radio24",
        name = "Radio 24",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/712/radio-24-diretta.b8fb69ee.jpg",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rds-radiodimensionesuono",
        name = "RDS - Radio Dimensione Suono",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/838/radio-rds-diretta.fe601497.jpg",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rairadio1",
        name = "Rai Radio 1",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/532/rai-radio-1.4bf577b5.jpg",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiosportiva",
        name = "Radio Sportiva",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/489/radio-sportiva.e4fd9c34.png",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rtl102.5",
        name = "RTL 102.5",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/713/radio-rtl-1025.249f1237.png",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/radiom2o",
        name = "Radio m2o",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/298/radio-m2o.24a4d505.jpg",
        region = "Veneto"
    ),
    RadioStation(
        videoId = "https://streaming.radio.it/rmc-radiomontecarlo",
        name = "RMC - Radio Monte Carlo",
        description = "Top radio Veneto",
        coverUrl = "https://static.mytuner.mobi/media/radios-150px/p5ugpgUxGM.png",
        region = "Veneto"
    )
)

// ─── Tutte le radio italiane e internazionali aggregate ───────────────────────
val italianRadios: List<RadioStation> =
    nazionaliRadios +
    abruzzoRadios +
    basilicataRadios +
    calabriaRadios +
    campaniaRadios +
    emiliaRomagnaRadios +
    friuliRadios +
    lazioRadios +
    liguriaRadios +
    lombardiaRadios +
    marcheRadios +
    moliseRadios +
    piemonteRadios +
    pugliaRadios +
    sardegnaRadios +
    siciliaRadios +
    toscanaRadios +
    trentinoRadios +
    umbriaRadios +
    valledaostaRadios +
    venetoRadios

val predefinedRadios: List<RadioStation> = youtubeRadios + lofiRadios + italianRadios

// ─── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    playerManager: PlayerManager,
    onNavigateToPlayer: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newRadioUrl by remember { mutableStateOf("") }
    var newRadioName by remember { mutableStateOf("") }
    var customRadios by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var selectedRegion by remember { mutableStateOf("Tutte") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Radio Live", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi Radio")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        val allRadios = predefinedRadios + customRadios
        val httpRadios = allRadios.filter { it.videoId.startsWith("http") }

        val availableRegions = listOf("Tutte") +
            httpRadios.map { it.region }.distinct().sorted()

        val filteredItalianRadios = if (selectedRegion == "Tutte") {
            httpRadios
        } else {
            httpRadios.filter { it.region == selectedRegion }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sezione YouTube Live
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("YouTube Live")
            }
            items(allRadios.filter { !it.videoId.startsWith("http") }) { radio ->
                RadioItem(
                    radio = radio,
                    isYouTubeLive = true,
                    onClick = { playRadio(radio, playerManager, onNavigateToPlayer) }
                )
            }

            // Sezione Radio Italiane & Internazionali
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Radio Italiane & Internazionali")
            }

            // Filtro regione scrollabile
            item {
                RegionFilterRow(
                    regions = availableRegions,
                    selectedRegion = selectedRegion,
                    onRegionSelected = { selectedRegion = it }
                )
            }

            if (filteredItalianRadios.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nessuna radio per questa regione",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredItalianRadios) { radio ->
                    RadioItem(
                        radio = radio,
                        isYouTubeLive = false,
                        onClick = { playRadio(radio, playerManager, onNavigateToPlayer) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Aggiungi Radio Live") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Inserisci l'ID del video YouTube oppure un URL stream diretto (mp3, m3u8…).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newRadioName,
                        onValueChange = { newRadioName = it },
                        label = { Text("Nome della Radio") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRadioUrl,
                        onValueChange = { newRadioUrl = it },
                        label = { Text("YouTube Video ID o URL stream") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val videoId = extractVideoId(newRadioUrl.trim())
                        if (videoId.isNotEmpty() && newRadioName.isNotBlank()) {
                            val isHttp = videoId.startsWith("http")
                            val customRadio = RadioStation(
                                videoId = videoId,
                                name = newRadioName,
                                description = if (isHttp) "Stream Personalizzato" else "YouTube Live",
                                coverUrl = if (isHttp)
                                    "https://cdn-icons-png.flaticon.com/512/1256/1256561.png"
                                else
                                    "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                region = "Nazionale"
                            )
                            customRadios = customRadios + customRadio
                        }
                        showAddDialog = false
                        newRadioUrl = ""
                        newRadioName = ""
                    }
                ) { Text("Aggiungi") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Annulla") }
            }
        )
    }
}

private fun playRadio(
    radio: RadioStation,
    playerManager: PlayerManager,
    onNavigateToPlayer: () -> Unit
) {
    val track = Track(
        id = radio.videoId,
        title = radio.name,
        artist = radio.description,
        thumbnailUrl = radio.coverUrl,
        durationMs = -1L
    )
    playerManager.playTrack(track)
    onNavigateToPlayer()
}

fun extractVideoId(input: String): String {
    if (input.startsWith("http://") || input.startsWith("https://")) {
        return when {
            input.contains("v=") -> input.substringAfter("v=").substringBefore("&")
            input.contains("youtu.be/") -> input.substringAfter("youtu.be/").substringBefore("?")
            input.contains("youtube.com/watch") -> input.substringAfter("v=").substringBefore("&")
            else -> input
        }
    }
    if (input.length == 11 && !input.contains("/") && !input.contains(".")) return input
    return input
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// ─── Filtro per Regione con emoji ────────────────────────────────────────────
@Composable
private fun RegionFilterRow(
    regions: List<String>,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(regions) { region ->
            val isSelected = region == selectedRegion
            FilterChip(
                selected = isSelected,
                onClick = { onRegionSelected(region) },
                label = {
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun RadioItem(
    radio: RadioStation,
    isYouTubeLive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.size(88.dp)) {
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(radio.coverUrl).addHeader("Referer", "https://mytuner-radio.com/").crossfade(true).build(),
                    contentDescription = radio.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
                    loading = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                )
                if (isYouTubeLive) {
                    LiveBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = radio.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = radio.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isYouTubeLive) {
                    Spacer(modifier = Modifier.height(3.dp))
                    val regionEmoji = regionIcons[radio.region] ?: "📻"
                    Text(
                        text = "$regionEmoji  ${radio.region}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Row(
        modifier = modifier
            .background(Color(0xCCE53935), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .scale(scale)
                .background(Color.White, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}






