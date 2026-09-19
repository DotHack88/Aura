package com.muse.app.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log

enum class EqPreset(val displayName: String) {
    NORMAL("Normale"),
    BASS_BOOST("Bass Boost"),
    ROCK("Rock"),
    POP("Pop"),
    JAZZ("Jazz"),
    CLASSICA("Classica"),
    ELETTRONICA("Elettronica"),
    PERSONALIZZATO("Personalizzato")
}

class EqualizerManager(private val context: Context) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_eq_prefs", Context.MODE_PRIVATE)

    var currentPreset: EqPreset = EqPreset.NORMAL
        private set

    // Valori correnti delle bande in milliBel (-1500 .. +1500)
    private val _bandLevels = mutableListOf<Int>()
    val bandLevels: List<Int> get() = _bandLevels.toList()

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 5
    val bandFrequencies: List<String>
        get() = (0 until bandCount).map { i ->
            val hz = equalizer?.getCenterFreq(i.toShort())?.div(1000) ?: 0
            if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
        }
    val minLevel: Int get() = equalizer?.bandLevelRange?.get(0)?.toInt() ?: -1500
    val maxLevel: Int get() = equalizer?.bandLevelRange?.get(1)?.toInt() ?: 1500

    fun init(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = false }

            val bands = equalizer?.numberOfBands?.toInt() ?: 5
            _bandLevels.clear()
            repeat(bands) { i ->
                _bandLevels.add(equalizer?.getBandLevel(i.toShort())?.toInt() ?: 0)
            }

            val savedPreset = prefs.getString("eq_preset", EqPreset.NORMAL.name)
            val preset = EqPreset.values().find { it.name == savedPreset } ?: EqPreset.NORMAL
            applyPreset(preset)
            Log.d("EqualizerManager", "EQ inizializzato con $bands bande, preset=$preset")
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Errore init EQ: ${e.message}")
        }
    }

    fun applyPreset(preset: EqPreset) {
        currentPreset = preset
        prefs.edit().putString("eq_preset", preset.name).apply()

        val levels = when (preset) {
            EqPreset.NORMAL       -> listOf(0, 0, 0, 0, 0)
            EqPreset.BASS_BOOST   -> listOf(800, 600, 0, -200, -400)
            EqPreset.ROCK         -> listOf(500, 300, -200, 300, 500)
            EqPreset.POP          -> listOf(-100, 200, 500, 300, 0)
            EqPreset.JAZZ         -> listOf(300, 100, 0, 200, 300)
            EqPreset.CLASSICA     -> listOf(600, 300, -200, 200, 500)
            EqPreset.ELETTRONICA  -> listOf(500, 300, 0, 300, 600)
            EqPreset.PERSONALIZZATO -> _bandLevels.toList() // mantieni i livelli correnti
        }

        val bands = equalizer?.numberOfBands?.toInt() ?: 5
        for (i in 0 until minOf(bands, levels.size)) {
            val level = levels[i].toShort()
            try {
                equalizer?.setBandLevel(i.toShort(), level)
                if (_bandLevels.size > i) _bandLevels[i] = level.toInt()
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Errore set band $i: ${e.message}")
            }
        }

        // Bass boost solo per BASS_BOOST preset
        try {
            bassBoost?.enabled = preset == EqPreset.BASS_BOOST
            if (preset == EqPreset.BASS_BOOST) {
                bassBoost?.setStrength(800)
            }
        } catch (e: Exception) {
            Log.w("EqualizerManager", "BassBoost non supportato: ${e.message}")
        }
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), levelMb.toShort())
            if (_bandLevels.size > band) _bandLevels[band] = levelMb
            currentPreset = EqPreset.PERSONALIZZATO
            prefs.edit().putString("eq_preset", EqPreset.PERSONALIZZATO.name).apply()
        } catch (e: Exception) {
            Log.w("EqualizerManager", "setBandLevel error: ${e.message}")
        }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
    }
}
