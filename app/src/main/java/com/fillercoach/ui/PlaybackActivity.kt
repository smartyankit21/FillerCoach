package com.fillercoach.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fillercoach.data.AppDatabase
import com.fillercoach.databinding.ActivityPlaybackBinding
import com.fillercoach.utils.FillerWordDetector
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PlaybackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getLongExtra("session_id", -1L)
        if (sessionId == -1L) { finish(); return }

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val session = db.sessionDao().getById(sessionId) ?: run { finish(); return@launch }

            runOnUiThread {
                // Video playback
                val videoFile = File(session.videoPath)
                if (videoFile.exists()) {
                    val mc = MediaController(this@PlaybackActivity)
                    mc.setAnchorView(binding.videoView)
                    binding.videoView.setMediaController(mc)
                    binding.videoView.setVideoURI(Uri.fromFile(videoFile))
                    binding.videoView.requestFocus()
                }

                // Date
                val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                binding.tvDate.text = sdf.format(Date(session.timestamp))

                // Metrics cards
                binding.tvFillerCount.text = session.fillerCount.toString()
                binding.tvWpm.text = "${session.wpm} WPM"
                binding.tvDuration.text = "${session.durationSeconds}s"
                binding.tvCleanStreak.text = "${session.cleanStreakSeconds}s"
                binding.tvWordCount.text = "${session.wordCount} words"
                binding.tvPauses.text = "${session.pauseCount} pauses"

                // Filler breakdown
                val breakdown = FillerWordDetector.breakdownFromJson(session.fillerWords)
                if (breakdown.isNotEmpty()) {
                    val breakdownText = breakdown.entries
                        .sortedByDescending { it.value }
                        .joinToString("   ") { "\"${it.key}\": ${it.value}×" }
                    binding.tvFillerBreakdown.text = breakdownText
                } else {
                    binding.tvFillerBreakdown.text = "No fillers detected 🎉"
                }

                // Highlighted transcript
                val fillerWords = FillerWordDetector.getFillerWords(this@PlaybackActivity)
                val highlighted = buildHighlightedTranscript(session.transcript, fillerWords)
                binding.tvTranscript.text = highlighted

                // Performance tip
                binding.tvTip.text = getTip(session.fillerCount, session.wpm, session.durationSeconds)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnHistory.setOnClickListener {
            startActivity(android.content.Intent(this, HistoryActivity::class.java))
            finish()
        }
    }

    private fun buildHighlightedTranscript(transcript: String, fillerWords: List<String>): SpannableString {
        val spannable = SpannableString(transcript)
        val sortedFillers = fillerWords.sortedByDescending { it.length }
        for (filler in sortedFillers) {
            val pattern = Regex("\\b${Regex.escape(filler)}\\b", RegexOption.IGNORE_CASE)
            for (match in pattern.findAll(transcript)) {
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#FFEB3B")),
                    match.range.first, match.range.last + 1, 0
                )
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#B71C1C")),
                    match.range.first, match.range.last + 1, 0
                )
            }
        }
        return spannable
    }

    private fun getTip(fillerCount: Int, wpm: Int, duration: Int): String {
        return when {
            fillerCount == 0 -> "🏆 Perfect session! Zero fillers detected. Keep it up!"
            fillerCount <= 3 -> "✅ Great job! Only $fillerCount filler word(s). Try to eliminate them entirely next time."
            fillerCount <= 8 -> "👍 Good effort. $fillerCount fillers found. Focus on pausing instead of filling silence."
            wpm > 170 -> "⚡ You're speaking at ${wpm} WPM — try slowing down to 140–160 WPM. Speed often causes fillers."
            wpm < 110 -> "🐢 At ${wpm} WPM, you may be losing your audience. Aim for 130–160 WPM."
            else -> "💪 $fillerCount fillers in ${duration}s. Practice pausing silently when you need a moment to think."
        }
    }
}
