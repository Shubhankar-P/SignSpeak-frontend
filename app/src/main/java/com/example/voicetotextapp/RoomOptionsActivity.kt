package com.example.voicetotextapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class RoomOptionsActivity : AppCompatActivity() {

    private lateinit var roomCodeText: TextView
    private lateinit var btnSign2Text: Button
    private lateinit var btnVoice2Text: Button
    private var roomCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_options)

        roomCodeText = findViewById(R.id.roomCodeText)
        btnSign2Text = findViewById(R.id.btnSign2Text)
        btnVoice2Text = findViewById(R.id.btnVoice2Text)

        roomCode = intent.getStringExtra("roomCode") ?: ""
        roomCodeText.text = "Room Code: $roomCode"

        btnSign2Text.setOnClickListener {

            val dbRef = FirebaseDatabase.getInstance(
                "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/"
            ).getReference("rooms/$roomCode")

            dbRef.child("mode").setValue("sign")

            val intent = Intent(this, Sign2TextActivity::class.java)
            intent.putExtra("roomCode", roomCode)
            startActivity(intent)
        }

        btnVoice2Text.setOnClickListener {

            val dbRef = FirebaseDatabase.getInstance(
                "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/"
            ).getReference("rooms/$roomCode")

            dbRef.child("mode").setValue("voice")

            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("roomCode", roomCode)
            intent.putExtra("role", "sender")
            startActivity(intent)
        }
    }
}
