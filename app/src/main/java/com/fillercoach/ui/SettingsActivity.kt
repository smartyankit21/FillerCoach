package com.fillercoach.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fillercoach.databinding.ActivitySettingsBinding
import com.fillercoach.utils.FillerWordDetector

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load current filler words
        val current = FillerWordDetector.getFillerWords(this)
        binding.etFillerWords.setText(current.joinToString(", "))

        binding.btnSave.setOnClickListener {
            val text = binding.etFillerWords.text.toString()
            val words = text.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                Toast.makeText(this, "Please enter at least one filler word", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FillerWordDetector.saveFillerWords(this, words)
            Toast.makeText(this, "Filler words saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            binding.etFillerWords.setText(FillerWordDetector.DEFAULT_FILLERS.joinToString(", "))
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
