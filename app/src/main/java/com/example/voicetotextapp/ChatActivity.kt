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
import android.widget.Toast
import android.content.Intent
import android.view.View
import android.view.MenuItem


class ChatActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textOutput: TextView
    private lateinit var btnSpeak: Button
    private lateinit var dbRef: DatabaseReference
    private var role: String = ""
    private var roomCode: String = ""
    private lateinit var roomCodeText: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.customToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // we already have title in XML
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // enable back arrow

        roomCodeText = findViewById(R.id.roomCodeText)
        textOutput = findViewById(R.id.textOutput)
        btnSpeak = findViewById(R.id.btnSpeak)

        roomCode = intent.getStringExtra("roomCode") ?: ""
        role = intent.getStringExtra("role") ?: ""
        dbRef = FirebaseDatabase.getInstance(
            "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("rooms/$roomCode")

        roomCodeText.text = "Room Code: $roomCode"

        // 🔹 When a receiver joins → notify sender + show toast
        if (role == "receiver") {
            val deviceName = android.os.Build.MODEL ?: "Unknown Device"
            dbRef.child("joined").setValue(deviceName)
            Toast.makeText(this, "Connected to room successfully", Toast.LENGTH_SHORT).show()
        }

        // 🔹 Listen for new messages
        dbRef.child("message").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null && msg != "Room created.") {
                    textOutput.text = msg
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // 🔹 Notify sender when someone joins
        dbRef.child("joined").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val deviceName = snapshot.getValue(String::class.java)
                if (deviceName != null && role == "sender") {
                    Toast.makeText(
                        this@ChatActivity,
                        "$deviceName has joined the room",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // 🔹 If sender → enable speech input
        if (role == "sender") {
            textOutput.text = "Room created. Share this code with your friend."
            setupSpeechRecognizer()

            btnSpeak.setOnClickListener {
                textOutput.text = "Speak now..."
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
                btnSpeak.isEnabled = false
                speechRecognizer.startListening(intent)
            }
        } else {
            btnSpeak.visibility = View.GONE
            textOutput.text = "Waiting for message..."
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {  // back arrow pressed
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun setupSpeechRecognizer() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                textOutput.text = "Ready to listen..."
            }

            override fun onBeginningOfSpeech() {
                textOutput.text = "Listening..."
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                textOutput.text = "Processing..."
            }

            override fun onError(error: Int) {
                textOutput.text = "Error. Try again!"
                btnSpeak.isEnabled = true
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    textOutput.text = text
                    dbRef.child("message").setValue(text)
                }
                btnSpeak.isEnabled = true
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onDestroy() {
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        super.onDestroy()
    }
}
