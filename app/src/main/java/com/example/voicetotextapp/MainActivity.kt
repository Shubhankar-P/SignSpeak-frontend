package com.example.voicetotextapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import com.google.firebase.database.FirebaseDatabase
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val btnJoin = findViewById<Button>(R.id.btnJoin)

        btnCreate.setOnClickListener {
            val roomCode = generateRoomCode()
            val dbRef = FirebaseDatabase.getInstance().getReference("rooms/$roomCode")
            dbRef.child("message").setValue("Room created.")
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("roomCode", roomCode)
            intent.putExtra("role", "sender")
            startActivity(intent)
        }

        btnJoin.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter Room Code"
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Join Room")
                .setView(input)
                .setPositiveButton("Join") { _, _ ->
                    val roomCode = input.text.toString().trim()
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("roomCode", roomCode)
                    intent.putExtra("role", "receiver")
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .create()
            dialog.show()
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..5).map { chars.random() }.joinToString("")
    }
}
