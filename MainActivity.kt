package com.fillercoach.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fillercoach.data.AppDatabase
import com.fillercoach.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadStats()

        binding.btnRecord.setOnClickListener {
            startActivity(Intent(this, RecordingActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val totalSessions = db.sessionDao().getTotalSessions()
            val avgFillers = db.sessionDao().getAvgFillerCount()
            val avgWpm = db.sessionDao().getAvgWpm()

            binding.tvTotalSessions.text = totalSessions.toString()
            binding.tvAvgFillers.text = if (avgFillers.isNaN()) "—" else String.format("%.1f", avgFillers)
            binding.tvAvgWpm.text = if (avgWpm.isNaN()) "—" else String.format("%.0f", avgWpm)
        }
    }
}
