package com.example.voicetotextapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import com.google.firebase.database.FirebaseDatabase
import android.widget.Toast
import android.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCreate = findViewById<Button>(R.id.btnCreate)
        val btnJoin = findViewById<Button>(R.id.btnJoin)

        btnCreate.setOnClickListener {
            val roomCode = generateRoomCode()
            val dbRef = FirebaseDatabase.getInstance( "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("rooms/$roomCode")
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
                    if (roomCode.isEmpty()) {
                        Toast.makeText(this, "Please enter a room code", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Create reference to that room in Firebase
                    val dbRef = FirebaseDatabase.getInstance(
                        "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/"
                    ).getReference("rooms/$roomCode")

                    //  Check if such a room actually exists
                    dbRef.get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            // Room exists → join it
                            val intent = Intent(this, ChatActivity::class.java)
                            intent.putExtra("roomCode", roomCode)
                            intent.putExtra("role", "receiver")
                            startActivity(intent)
                        } else {
                            // ❌ Room doesn’t exist
                            Toast.makeText(this, "Invalid Room Code", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(this, "Error checking room", Toast.LENGTH_SHORT).show()
                    }
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
