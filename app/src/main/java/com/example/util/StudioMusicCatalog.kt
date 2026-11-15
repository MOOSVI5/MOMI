package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

data class StudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val mood: String,
    val bpm: Int,
    val progressionHz: List<FloatArray>,
    val rhythmPulse: Boolean
) {
    val fullTitle: String
        get() = "$title · $artist"
}

object StudioMusicCatalog {

    val tracks: List<StudioTrack> = listOf(
        StudioTrack(
            id = "midnight_lofi",
            title = "Midnight Lo-Fi",
            artist = "Momi Studio",
            mood = "Chill",
            bpm = 78,
            progressionHz = listOf(
                floatArrayOf(130.81f, 261.63f, 329.63f, 392.00f),
                floatArrayOf(110.00f, 220.00f, 261.63f, 329.63f),
                floatArrayOf(174.61f, 220.00f, 261.63f, 349.23f),
                floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f)
            ),
            rhythmPulse = true
        ),
        StudioTrack(
            id = "sunset_acoustic",
            title = "Sunset Acoustic",
            artist = "Momi Studio",
            mood = "Warm",
            bpm = 96,
            progressionHz = listOf(
                floatArrayOf(146.83f, 293.66f, 369.99f, 440.00f),
                floatArrayOf(123.47f, 246.94f, 293.66f, 369.99f),
                floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f),
                floatArrayOf(220.00f, 277.18f, 329.63f, 440.00f)
            ),
            rhythmPulse = true
        ),
        StudioTrack(
            id = "silk_groove",
            title = "Silk Pop Groove",
            artist = "Momi Studio",
            mood = "Upbeat",
            bpm = 112,
            progressionHz = listOf(
                floatArrayOf(164.81f, 329.63f, 392.00f, 493.88f),
                floatArrayOf(130.81f, 261.63f, 329.63f, 392.00f),
                floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f),
                floatArrayOf(146.83f, 293.66f, 369.99f, 440.00f)
            ),
            rhythmPulse = true
        ),
        StudioTrack(
            id = "cinematic_horizon",
            title = "Cinematic Horizon",
            artist = "Momi Studio",
            mood = "Ambient",
            bpm = 72,
            progressionHz = listOf(
                floatArrayOf(110.00f, 220.00f, 329.63f, 440.00f),
                floatArrayOf(174.61f, 261.63f, 349.23f, 523.25f),
                floatArrayOf(130.81f, 261.63f, 392.00f, 523.25f),
                floatArrayOf(196.00f, 293.66f, 392.00f, 493.88f)
            ),
            rhythmPulse = false
        ),
        StudioTrack(
            id = "golden_hour",
            title = "Golden Hour",
            artist = "Momi Studio",
            mood = "Dreamy",
            bpm = 88,
            progressionHz = listOf(
                floatArrayOf(174.61f, 261.63f, 349.23f, 440.00f),
                floatArrayOf(164.81f, 246.94f, 329.63f, 493.88f),
                floatArrayOf(146.83f, 220.00f, 293.66f, 440.00f),
                floatArrayOf(130.81f, 261.63f, 329.63f, 392.00f)
            ),
            rhythmPulse = true
        )
    )

    fun findByAudioTitle(audioTitle: String): StudioTrack? {
        if (audioTitle.isBlank()) return null
        return tracks.firstOrNull { track ->
            audioTitle.equals(track.fullTitle, ignoreCase = true) ||
                audioTitle.equals(track.title, ignoreCase = true) ||
                audioTitle.contains(track.title, ignoreCase = true)
        }
    }

    fun resolvePlayableMusicUri(
        context: Context,
        localAudioPath: String?,
        audioTitle: String
    ): Uri? {
        if (!localAudioPath.isNullOrBlank()) {
            val file = File(localAudioPath)
            if (file.exists() && file.length() > 0L) {
                return Uri.fromFile(file)
            }
        }
        val preset = findByAudioTitle(audioTitle) ?: return null
        val wavFile = ensurePresetWavFile(context, preset)
        return if (wavFile.exists() && wavFile.length() > 44L) Uri.fromFile(wavFile) else null
    }

    fun copyPickedAudioToInternalStorage(context: Context, uri: Uri): Pair<String, String>? {
        return runCatching {
            val dir = File(context.filesDir, "luma_media")
            if (!dir.exists()) dir.mkdirs()

            val rawName = queryDisplayName(context, uri) ?: "Selected Track"
            val ext = rawName.substringAfterLast('.', "m4a")
                .lowercase()
                .takeIf { it in setOf("mp3", "m4a", "aac", "wav", "ogg") }
                ?: "m4a"
            val cleanTitle = rawName.substringBeforeLast('.')
                .replace('_', ' ')
                .trim()
                .ifBlank { "Selected Audio" }
                .take(80)

            val outFile = File(dir, "audio_${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!outFile.exists() || outFile.length() == 0L) return null
            outFile.absolutePath to cleanTitle
        }.getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun ensurePresetWavFile(context: Context, track: StudioTrack): File {
        val dir = File(context.cacheDir, "momi_music")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "${track.id}_v1.wav")
        if (outFile.exists() && outFile.length() > 1024L) return outFile

        runCatching {
            val sampleRate = 22050
            val durationSeconds = 8
            val totalSamples = sampleRate * durationSeconds
            val pcmBytes = ByteArray(totalSamples * 2)

            val chords = track.progressionHz
            val samplesPerChord = totalSamples / chords.size
            val beatSamples = ((60.0 / track.bpm) * sampleRate).toInt().coerceAtLeast(4000)

            for (i in 0 until totalSamples) {
                val chordIdx = (i / samplesPerChord).coerceAtMost(chords.lastIndex)
                val chordPos = i % samplesPerChord
                val chordProgress = chordPos.toDouble() / samplesPerChord.toDouble()
                val t = i.toDouble() / sampleRate.toDouble()
                val chord = chords[chordIdx]

                // Smooth pad + warm harmonic chord envelope per bar
                val fadeIn = (chordProgress / 0.08).coerceIn(0.0, 1.0)
                val fadeOut = ((1.0 - chordProgress) / 0.12).coerceIn(0.0, 1.0)
                val env = fadeIn * fadeOut

                var sample = 0.0
                sample += 0.34 * sin(2.0 * PI * chord[0] * t) // bass root
                sample += 0.24 * sin(2.0 * PI * chord[1] * t) // triad 1
                sample += 0.20 * sin(2.0 * PI * chord[2] * t) // triad 3

                // Gentle plucked arpeggio shimmer
                val arpStep = ((chordPos / (beatSamples / 2)) % 3) + 1
                val arpPos = (chordPos % (beatSamples / 2)).toDouble() / (beatSamples / 2).toDouble()
                val arpEnv = (1.0 - arpPos).coerceAtLeast(0.0) * (1.0 - arpPos)
                sample += 0.18 * arpEnv * sin(2.0 * PI * chord[arpStep.coerceAtMost(chord.lastIndex)] * t)

                // Soft warm sub-kick pulse on beats
                if (track.rhythmPulse) {
                    val beatPos = (i % beatSamples).toDouble() / sampleRate.toDouble()
                    if (beatPos < 0.14) {
                        val kickEnv = (1.0 - beatPos / 0.14)
                        val kickFreq = 58.0 + 75.0 * (1.0 - beatPos / 0.14)
                        sample += 0.28 * kickEnv * kickEnv * sin(2.0 * PI * kickFreq * beatPos)
                    }
                }

                // Loop crossfade on track edges so seamless repeat never clicks
                val loopEdge = minOf(i, totalSamples - 1 - i)
                val edgeFade = (loopEdge / (sampleRate * 0.06)).coerceIn(0.0, 1.0)

                val clamped = (sample * env * edgeFade * 0.65).coerceIn(-0.98, 0.98)
                val intSample = (clamped * Short.MAX_VALUE).toInt()
                pcmBytes[i * 2] = (intSample and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((intSample shr 8) and 0xFF).toByte()
            }

            val header = buildWavHeader(pcmBytes.size, sampleRate, 1, 16)
            outFile.outputStream().use { out ->
                out.write(header)
                out.write(pcmBytes)
            }
        }
        return outFile
    }

    private fun buildWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + 36
        val baos = ByteArrayOutputStream(44)
        fun writeAscii(s: String) = baos.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) {
            baos.write(v and 0xFF)
            baos.write((v shr 8) and 0xFF)
            baos.write((v shr 16) and 0xFF)
            baos.write((v shr 24) and 0xFF)
        }
        fun writeShortLE(v: Int) {
            baos.write(v and 0xFF)
            baos.write((v shr 8) and 0xFF)
        }

        writeAscii("RIFF")
        writeIntLE(totalDataLen)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16)
        writeShortLE(1) // PCM
        writeShortLE(channels)
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(blockAlign)
        writeShortLE(bitsPerSample)
        writeAscii("data")
        writeIntLE(pcmDataSize)
        return baos.toByteArray()
    }
}
