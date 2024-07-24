/*
 * MIT License
 *
 * Copyright (c) 2019 squti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.squti.androidwaverecordersample

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.squti.androidwaverecorder.RecorderState
import com.github.squti.androidwaverecorder.WaveRecorder
import com.github.squti.androidwaverecordersample.databinding.ActivityMainBinding
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 77

    private lateinit var waveRecorder: WaveRecorder
    private lateinit var filePath: String
    private var isRecording = false
    private var isPaused = false
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecorder(isSaveToExternalStorage = false)

        binding.saveToExternalStorageSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                initRecorder(isSaveToExternalStorage = true)
            } else {
                initRecorder(isSaveToExternalStorage = false)
            }
        }

        binding.startStopRecordingButton.setOnClickListener {

            if (!isRecording) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        )
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            PERMISSIONS_REQUEST_RECORD_AUDIO
                        )
                    } else {
                        showPermissionSettingsDialog()
                    }
                } else {
                    waveRecorder.startRecording()
                }
            } else {
                waveRecorder.stopRecording()
            }
        }

        binding.pauseResumeRecordingButton.setOnClickListener {
            if (!isPaused) {
                waveRecorder.pauseRecording()
            } else {
                waveRecorder.resumeRecording()
            }
        }
        binding.showAmplitudeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.amplitudeTextView.text = "Amplitude : 0"
                binding.amplitudeTextView.visibility = View.VISIBLE
                waveRecorder.onAmplitudeListener = {
                    binding.amplitudeTextView.text = "Amplitude : $it"
                }

            } else {
                waveRecorder.onAmplitudeListener = null
                binding.amplitudeTextView.visibility = View.GONE
            }
        }

        binding.silenceDetectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            waveRecorder.silenceDetection = isChecked
            if (isChecked)
                Toast.makeText(this, "Noise Suppressor Activated", Toast.LENGTH_SHORT).show()

        }

        binding.noiseSuppressorSwitch.setOnCheckedChangeListener { _, isChecked ->
            waveRecorder.noiseSuppressorActive = isChecked
            if (isChecked)
                Toast.makeText(this, "Noise Suppressor Activated", Toast.LENGTH_SHORT).show()

        }
    }

    private fun initRecorder(isSaveToExternalStorage: Boolean) {
        if (isSaveToExternalStorage)
            initWithExternalStorage("audioFile")
        else
            initWithInternalStorage("audioFile")

        waveRecorder.onStateChangeListener = {
            Log.d("RecorderState : ", it.name)

            when (it) {
                RecorderState.RECORDING -> startRecording()
                RecorderState.STOP -> stopRecording()
                RecorderState.PAUSE -> pauseRecording()
                RecorderState.SKIPPING_SILENCE -> skipRecording()
            }
        }
        waveRecorder.onTimeElapsedInMillis = {
            binding.timeTextView.text = formatTimeUnit(it)
        }
    }


    private fun startRecording() {
        Log.d(TAG, "Recording Started")
        isRecording = true
        isPaused = false
        binding.messageTextView.visibility = View.GONE
        binding.recordingTextView.text = "Recording..."
        binding.recordingTextView.visibility = View.VISIBLE
        binding.startStopRecordingButton.text = "STOP"
        binding.pauseResumeRecordingButton.text = "PAUSE"
        binding.pauseResumeRecordingButton.visibility = View.VISIBLE
        binding.noiseSuppressorSwitch.isEnabled = false
    }

    private fun skipRecording() {
        Log.d(TAG, "Recording Skipped")
        isRecording = true
        isPaused = false
        binding.messageTextView.visibility = View.GONE
        binding.recordingTextView.text = "Skipping..."
        binding.recordingTextView.visibility = View.VISIBLE
        binding.startStopRecordingButton.text = "STOP"
        binding.pauseResumeRecordingButton.visibility = View.INVISIBLE
        binding.noiseSuppressorSwitch.isEnabled = false
    }

    private fun stopRecording() {
        Log.d(TAG, "Recording Stopped")
        isRecording = false
        isPaused = false
        binding.recordingTextView.visibility = View.GONE
        binding.messageTextView.visibility = View.VISIBLE
        binding.pauseResumeRecordingButton.visibility = View.GONE
        binding.showAmplitudeSwitch.isChecked = false
        binding.startStopRecordingButton.text = "START"
        binding.noiseSuppressorSwitch.isEnabled = true
        Toast.makeText(this, "File saved at : $filePath", Toast.LENGTH_LONG).show()
    }

    private fun pauseRecording() {
        Log.d(TAG, "Recording Paused")
        binding.recordingTextView.text = "PAUSE"
        binding.pauseResumeRecordingButton.text = "RESUME"
        isPaused = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            waveRecorder.startRecording()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private fun formatTimeUnit(timeInMilliseconds: Long): String {
        return try {
            String.format(
                Locale.getDefault(),
                "%02d:%02d:%03d",
                TimeUnit.MILLISECONDS.toMinutes(timeInMilliseconds),
                TimeUnit.MILLISECONDS.toSeconds(timeInMilliseconds) - TimeUnit.MINUTES.toSeconds(
                    TimeUnit.MILLISECONDS.toMinutes(timeInMilliseconds)
                ),
                timeInMilliseconds % 1000
            )
        } catch (e: Exception) {
            "00:00:000"
        }
    }

    private fun initWithExternalStorage(fileName: String): Boolean {
        val folderName = "Android-Wave-Recorder"
        val audioUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$folderName")
        }
        return try {
            contentResolver.insert(audioUri, contentValues)?.also { uri ->
                waveRecorder = WaveRecorder(uri, context = this)
                    .configureWaveSettings {
                        sampleRate = 44100
                        channels = AudioFormat.CHANNEL_IN_STEREO
                        audioEncoding = AudioFormat.ENCODING_PCM_32BIT
                    }.configureSilenceDetection {
                        minAmplitudeThreshold = 80
                        bufferDurationInMillis = 1500
                        preSilenceDurationInMillis = 1500
                    }
                filePath = "/Music/$folderName/$fileName.wav"
            } ?: throw IOException("Couldn't create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun initWithInternalStorage(fileName: String) {

        filePath = filesDir.absolutePath + "/$fileName.wav"

        waveRecorder = WaveRecorder(filePath)
            .configureWaveSettings {
                sampleRate = 44100
                channels = AudioFormat.CHANNEL_IN_STEREO
                audioEncoding = AudioFormat.ENCODING_PCM_32BIT
            }.configureSilenceDetection {
                minAmplitudeThreshold = 80
                bufferDurationInMillis = 1500
                preSilenceDurationInMillis = 1500
            }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs audio recording permission to function. Please grant the permission in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}