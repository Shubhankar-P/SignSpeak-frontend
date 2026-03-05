package com.example.voicetotextapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class Sign2TextActivity : AppCompatActivity() {

    lateinit var roomCodeDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign2text)

        roomCodeDisplay = findViewById(R.id.roomCodeDisplay)

        val roomCode = intent.getStringExtra("roomCode")

        roomCodeDisplay.text = "Room Code : $roomCode"
    }

}