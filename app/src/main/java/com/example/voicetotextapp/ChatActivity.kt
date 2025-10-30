package com.example.voicetotextapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.database.*

import java.util.*


class ChatActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textOutput: TextView
    private lateinit var btnSpeak: Button
    private lateinit var dbRef: DatabaseReference
    private var role: String = ""
    private var roomCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        textOutput = findViewById(R.id.textOutput)
        btnSpeak = findViewById(R.id.btnSpeak)

        roomCode = intent.getStringExtra("roomCode") ?: ""
        role = intent.getStringExtra("role") ?: ""
        dbRef = FirebaseDatabase.getInstance().getReference("rooms/$roomCode")

        textOutput.text = "Room Code: $roomCode"

        if (role == "sender") {
            setupSpeechRecognizer()
            btnSpeak.setOnClickListener {
                speechRecognizer.startListening(
                    android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    }
                )
            }
        } else {
            btnSpeak.isEnabled = false
        }

        // Listen for incoming text updates
        dbRef.child("message").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null) {
                    textOutput.text = msg
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupSpeechRecognizer() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    dbRef.child("message").setValue(text)
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onDestroy() {
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        super.onDestroy()
    }
}
