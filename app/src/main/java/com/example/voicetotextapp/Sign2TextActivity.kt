package com.example.voicetotextapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executors
import android.util.Log
import com.google.firebase.database.*

class Sign2TextActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    lateinit var roomCodeDisplay: TextView
    lateinit var translatedText: EditText
    lateinit var cameraPreview: PreviewView
    lateinit var resetButton: Button
    lateinit var speakButton: Button

    lateinit var chatMessageBox: TextView
    lateinit var dbRef: DatabaseReference

    lateinit var textToSpeech: TextToSpeech
    lateinit var socket: Socket

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastSentTime = 0L

    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign2text)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.customToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        roomCodeDisplay = findViewById(R.id.roomCodeDisplay)
        cameraPreview = findViewById(R.id.cameraPreview)
        translatedText = findViewById(R.id.translatedText)
        chatMessageBox = findViewById(R.id.chatMessageBox)
        resetButton = findViewById(R.id.resetButton)
        speakButton = findViewById(R.id.speakButton)

//        val roomCode = intent.getStringExtra("roomCode")
//        roomCodeDisplay.text = "Room Code : $roomCode"

        val roomCode = intent.getStringExtra("roomCode") ?: "----"
        roomCodeDisplay.text = "Room Code : $roomCode"
        dbRef = FirebaseDatabase.getInstance(
            "https://voicetotextapp-1e3eb-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("rooms/$roomCode")
        dbRef.child("voiceMessage").addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val msg = snapshot.getValue(String::class.java)

                if (msg != null && msg != "Room created.") {
                    chatMessageBox.text = msg
                }
            }

            override fun onCancelled(error: DatabaseError) {}

        })

        // Text to Speech
        textToSpeech = TextToSpeech(this, this)

        speakButton.setOnClickListener {
            val text = translatedText.text.toString()
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        resetButton.setOnClickListener {
            translatedText.setText("")
        }

        // SOCKET CONNECTION
        socket = IO.socket("https://postesophageal-incompactly-lucille.ngrok-free.dev")
        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread {
                Log.d("SOCKET", "Backend Connected")
            }
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) {
            runOnUiThread {
                Log.e("SOCKET", "Backend Connection Error")
            }
        }

        socket.connect()

        socket.on("prediction") {
            val data = it[0] as JSONObject
            runOnUiThread {
                Log.d("SOCKET", "Prediction Received: $data")
                translatedText.setText(data.getString("text"))

                // send sign text to chat activity
                dbRef.child("signMessage").setValue(data.getString("text"))
            }
        }

        checkCameraPermission()
    }


    private fun checkCameraPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun startCamera() {


        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { image ->

                val now = System.currentTimeMillis()
                if (now - lastSentTime < 500) {
                    image.close()
                    return@setAnalyzer
                }

                lastSentTime = now

                try {

                    var bitmap = imageProxyToBitmap(image)

// FIX CAMERA ROTATION (same as previous project)
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    if (rotationDegrees != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        bitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                    }

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        320,
                        (bitmap.height * (320f / bitmap.width)).toInt(),
                        true
                    )



                    val outputStream = ByteArrayOutputStream()

                    scaledBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        60,
                        outputStream
                    )

                    val base64 = Base64.encodeToString(
                        outputStream.toByteArray(),
                        Base64.NO_WRAP
                    )

                    Log.d("FRAME", "Frame Sent")

                    socket.emit(
                        "frame",
                        "data:image/jpeg;base64,$base64"
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }

            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            }
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()

        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            60,
            out
        )

        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.disconnect()
        textToSpeech.shutdown()
        cameraExecutor.shutdown()
    }
}