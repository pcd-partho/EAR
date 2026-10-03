package com.ear.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private lateinit var statusText: TextView
    private lateinit var gateLevelText: TextView

    // Gate threshold: audio below this amplitude will be muted
    private var noiseGateThreshold = 500

    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 80, 50, 50)

        statusText = TextView(this)
        statusText.text = "EAR — Ready"
        statusText.textSize = 20f
        statusText.setPadding(0, 0, 0, 30)

        val startButton = Button(this)
        startButton.text = "Start Filtered Audio"
        startButton.setOnClickListener {
            if (!isRunning) {
                checkPermissionAndStart()
            }
        }

        val stopButton = Button(this)
        stopButton.text = "Stop Audio"
        stopButton.setOnClickListener {
            stopPassthrough()
        }

        gateLevelText = TextView(this)
        gateLevelText.text = "Noise Gate Threshold: $noiseGateThreshold"
        gateLevelText.textSize = 16f
        gateLevelText.setPadding(0, 40, 0, 10)

        val thresholdSlider = SeekBar(this)
        thresholdSlider.max = 3000
        thresholdSlider.progress = noiseGateThreshold
        thresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                noiseGateThreshold = progress
                gateLevelText.text = "Noise Gate Threshold: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(gateLevelText)
        layout.addView(thresholdSlider)

        setContentView(layout)
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        } else {
            startPassthrough()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startPassthrough()
        } else {
            statusText.text = "EAR — Mic permission denied"
        }
    }

    private fun startPassthrough() {
        isRunning = true
        statusText.text = "EAR — Active (Noise Suppression ON)"

        thread {
            val minBufSizeIn = AudioRecord.getMinBufferSize(
                sampleRate, channelConfigIn, audioFormat
            )
            val minBufSizeOut = AudioTrack.getMinBufferSize(
                sampleRate, channelConfigOut, audioFormat
            )

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfigIn,
                audioFormat,
                minBufSizeIn
            )

            // Attach hardware Noise Suppressor if phone supports it
            val audioSessionId = audioRecord.audioSessionId
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }

            val audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                channelConfigOut,
                audioFormat,
                minBufSizeOut,
                AudioTrack.MODE_STREAM
            )

            val buffer = ShortArray(minBufSizeIn)

            audioRecord.startRecording()
            audioTrack.play()

            while (isRunning) {
                val readSize = audioRecord.read(buffer, 0, buffer.size)
                if (readSize > 0) {
                    // Calculate peak sound level in this buffer
                    var maxAmplitude = 0
                    for (i in 0 until readSize) {
                        val sample = abs(buffer[i].toInt())
                        if (sample > maxAmplitude) {
                            maxAmplitude = sample
                        }
                    }

                    // Apply Noise Gate: if audio is quieter than threshold, mute it
                    if (maxAmplitude < noiseGateThreshold) {
                        for (i in 0 until readSize) {
                            buffer[i] = 0
                        }
                    }

                    audioTrack.write(buffer, 0, readSize)
                }
            }

            // Clean up
            noiseSuppressor?.release()
            echoCanceler?.release()
            audioRecord.stop()
            audioRecord.release()
            audioTrack.stop()
            audioTrack.release()
        }
    }

    private fun stopPassthrough() {
        isRunning = false
        statusText.text = "EAR — Stopped"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPassthrough()
    }
}