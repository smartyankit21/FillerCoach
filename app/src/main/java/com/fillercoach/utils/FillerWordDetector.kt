package com.fillercoach.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object FillerWordDetector {

    private const val PREFS_NAME = "filler_prefs"
    private const val KEY_FILLER_WORDS = "filler_words"

    val DEFAULT_FILLERS = listOf("um", "uh", "like", "you know", "basically", "literally",
        "actually", "so", "right", "okay", "well", "kind of", "sort of")

    fun getFillerWords(context: Context): List<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_FILLER_WORDS, null)
        return if (saved != null) saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else DEFAULT_FILLERS
    }

    fun saveFillerWords(context: Context, words: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FILLER_WORDS, words.joinToString(",")).apply()
    }

    data class AnalysisResult(
        val transcript: String,
        val totalWords: Int,
        val fillerCount: Int,
        val fillerBreakdown: Map<String, Int>,
        val fillerPositions: List<Int>,
        val wpm: Int,
        val pauseCount: Int,
        val cleanStreakSeconds: Int,
        val durationSeconds: Int
    )

    fun analyze(transcript: String, durationSeconds: Int, fillerWords: List<String>): AnalysisResult {
        val lowerTranscript = transcript.lowercase()
        val words = lowerTranscript.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val totalWords = words.size
        val breakdown = mutableMapOf<String, Int>()
        val positions = mutableListOf<Int>()
        val sortedFillers = fillerWords.sortedByDescending { it.length }

        words.forEachIndexed { index, word ->
            val cleanWord = word.replace(Regex("[^a-z]"), "")
            for (filler in sortedFillers) {
                if (filler.contains(" ")) {
                    val fillerTokens = filler.split(" ")
                    if (index + fillerTokens.size <= words.size) {
                        val slice = words.subList(index, index + fillerTokens.size)
                            .joinToString(" ") { it.replace(Regex("[^a-z]"), "") }
                        if (slice == filler) { breakdown[filler] = (breakdown[filler] ?: 0) + 1; positions.add(index); break }
                    }
                } else {
                    if (cleanWord == filler) { breakdown[filler] = (breakdown[filler] ?: 0) + 1; positions.add(index); break }
                }
            }
        }

        val fillerCount = breakdown.values.sum()
        val wpm = if (durationSeconds > 0) (totalWords * 60) / durationSeconds else 0
        val pauseCount = transcript.split(Regex("[.!?]{2,}|…|\\.{3,}")).size - 1
        val cleanStreakSeconds = if (positions.size <= 1) durationSeconds
        else {
            val wordsPerSecond = if (durationSeconds > 0) totalWords.toFloat() / durationSeconds else 1f
            var maxGap = 0
            for (i in 1 until positions.size) {
                val gap = ((positions[i] - positions[i - 1]) / wordsPerSecond).toInt()
                if (gap > maxGap) maxGap = gap
            }
            maxGap
        }

        return AnalysisResult(transcript, totalWords, fillerCount, breakdown, positions, wpm, pauseCount, cleanStreakSeconds, durationSeconds)
    }

    fun breakdownToJson(breakdown: Map<String, Int>): String {
        val obj = JSONObject()
        breakdown.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun breakdownFromJson(json: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try { val obj = JSONObject(json); obj.keys().forEach { key -> result[key] = obj.getInt(key) } } catch (e: Exception) {}
        return result
    }

    fun highlightFillers(transcript: String, fillerWords: List<String>): String {
        var result = transcript
        fillerWords.sortedByDescending { it.length }.forEach { filler ->
            result = Regex("\\b${Regex.escape(filler)}\\b", RegexOption.IGNORE_CASE).replace(result) { "**${it.value}**" }
        }
        return result
    }
}
