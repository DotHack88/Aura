package com.muse.app.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    // --- StateFlow reattivi: il Composable si aggiorna automaticamente ---
    private val _currentPreset = MutableStateFlow(EqPreset.NORMAL)
    val currentPresetFlow: StateFlow<EqPreset> = _currentPreset.asStateFlow()
    var currentPreset: EqPreset
        get() = _currentPreset.value
        private set(v) { _currentPreset.value = v }

    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevelsFlow: StateFlow<List<Int>> = _bandLevels.asStateFlow()
    val bandLevels: List<Int> get() = _bandLevels.value

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
            val initial = (0 until bands).map { i ->
                equalizer?.getBandLevel(i.toShort())?.toInt() ?: 0
            }
            _bandLevels.value = initial

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
            EqPreset.PERSONALIZZATO -> {
                val saved = prefs.getString("eq_custom_bands", null)
                if (saved != null)
                    saved.split(",").mapNotNull { it.trim().toIntOrNull() }
                else
                    _bandLevels.value
            }
        }

        val bands = equalizer?.numberOfBands?.toInt() ?: 5
        val updated = _bandLevels.value.toMutableList().also {
            // Assicura che la lista abbia il numero corretto di bande
            while (it.size < bands) it.add(0)
        }

        for (i in 0 until minOf(bands, levels.size)) {
            val level = levels[i].toShort()
            try {
                equalizer?.setBandLevel(i.toShort(), level)
                updated[i] = level.toInt()
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Errore set band $i: ${e.message}")
            }
        }
        // Aggiorna il StateFlow → il Composable si ricompone automaticamente
        _bandLevels.value = updated.toList()

        // Bass boost solo per BASS_BOOST
        try {
            bassBoost?.enabled = preset == EqPreset.BASS_BOOST
            if (preset == EqPreset.BASS_BOOST) bassBoost?.setStrength(800)
        } catch (e: Exception) {
            Log.w("EqualizerManager", "BassBoost non supportato: ${e.message}")
        }
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), levelMb.toShort())
            val updated = _bandLevels.value.toMutableList()
            if (updated.size > band) updated[band] = levelMb
            _bandLevels.value = updated.toList()
            currentPreset = EqPreset.PERSONALIZZATO
            prefs.edit()
                .putString("eq_preset", EqPreset.PERSONALIZZATO.name)
                .putString("eq_custom_bands", _bandLevels.value.joinToString(","))
                .apply()
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
