package com.fillercoach.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fillercoach.data.AppDatabase
import com.fillercoach.data.Session
import com.fillercoach.databinding.ActivityRecordingBinding
import com.fillercoach.utils.FillerWordDetector
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val transcriptBuilder = StringBuilder()
    private var startTime = 0L
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var videoFilePath: String = ""
    private var isRecording = false

    companion object {
        private const val PERMISSION_REQUEST = 100
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (hasPermissions()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST)
        }

        binding.btnRecord.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e("RecordingActivity", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val vc = videoCapture ?: return

        val fileName = "FC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val outputFile = File(getExternalFilesDir(null), fileName)
        videoFilePath = outputFile.absolutePath

        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        recording = vc.output
            .prepareRecording(this, outputOptions)
            .apply { if (hasPermissions()) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        runOnUiThread {
                            isRecording = true
                            startTime = System.currentTimeMillis()
                            binding.btnRecord.text = "Stop"
                            binding.btnRecord.setBackgroundColor(
                                ContextCompat.getColor(this, android.R.color.holo_red_dark))
                            startTimer()
                            startSpeechRecognition()
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        runOnUiThread {
                            if (event.hasError()) {
                                Toast.makeText(this, "Recording error: ${event.error}", Toast.LENGTH_SHORT).show()
                            } else {
                                analyzeAndSave()
                            }
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        speechRecognizer?.stopListening()
        isRecording = false
        stopTimer()
        binding.btnRecord.text = "Record"
        binding.btnRecord.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_light))
        binding.tvStatus.text = "Analyzing..."
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Restart if still recording
                if (isRecording) startSpeechListening()
            }
            override fun onError(error: Int) {
                if (isRecording) {
                    Handler(Looper.getMainLooper()).postDelayed({ startSpeechListening() }, 500)
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    transcriptBuilder.append(matches[0]).append(" ")
                    updateFillerCount()
                }
                if (isRecording) startSpeechListening()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    binding.tvTranscript.text = transcriptBuilder.toString() + partial[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        startSpeechListening()
    }

    private fun startSpeechListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun updateFillerCount() {
        val fillerWords = FillerWordDetector.getFillerWords(this)
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        val result = FillerWordDetector.analyze(transcriptBuilder.toString(), elapsed, fillerWords)
        binding.tvFillerCount.text = "Fillers: ${result.fillerCount}"
    }

    private fun analyzeAndSave() {
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
        val transcript = transcriptBuilder.toString().trim()
        val fillerWords = FillerWordDetector.getFillerWords(this)
        val result = FillerWordDetector.analyze(transcript, elapsed, fillerWords)

        val session = Session(
            videoPath = videoFilePath,
            transcript = transcript,
            durationSeconds = elapsed,
            fillerCount = result.fillerCount,
            fillerWords = FillerWordDetector.breakdownToJson(result.fillerBreakdown),
            wordCount = result.totalWords,
            wpm = result.wpm,
            cleanStreakSeconds = result.cleanStreakSeconds,
            pauseCount = result.pauseCount
        )

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val sessionId = db.sessionDao().insert(session)
            runOnUiThread {
                val intent = Intent(this@RecordingActivity, PlaybackActivity::class.java)
                intent.putExtra("session_id", sessionId)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                binding.tvTimer.text = String.format("%02d:%02d", min, sec)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        stopTimer()
    }
}
