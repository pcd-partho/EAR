package com.ear.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private lateinit var statusText: TextView

    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 100, 40, 40)

        statusText = TextView(this)
        statusText.text = "EAR — Ready"
        statusText.textSize = 22f

        val startButton = Button(this)
        startButton.text = "Start Passthrough"
        startButton.setOnClickListener {
            if (!isRunning) {
                checkPermissionAndStart()
            }
        }

        val stopButton = Button(this)
        stopButton.text = "Stop Passthrough"
        stopButton.setOnClickListener {
            isRunning = false
            statusText.text = "EAR — Stopped"
        }

        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)

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
        statusText.text = "EAR — Running (listening...)"

        thread {
            val minBufSizeIn = AudioRecord.getMinBufferSize(
                sampleRate, channelConfigIn, audioFormat
            )
            val minBufSizeOut = AudioTrack.getMinBufferSize(
                sampleRate, channelConfigOut, audioFormat
            )

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                audioFormat,
                minBufSizeIn
            )

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
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
                    audioTrack.write(buffer, 0, readSize)
                }
            }

            audioRecord.stop()
            audioRecord.release()
            audioTrack.stop()
            audioTrack.release()
        }
    }
}