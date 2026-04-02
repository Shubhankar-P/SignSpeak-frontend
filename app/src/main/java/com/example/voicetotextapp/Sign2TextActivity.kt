package com.example.voicetotextapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import com.example.voicetotextapp.BuildConfig

class Sign2TextActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
        private const val BASE_URL = BuildConfig.NGROK_URL
        private const val FIREBASE_URL = BuildConfig.FIREBASE_URL
    }

    // ── Existing views ────────────────────────────────────────────
    private lateinit var roomCodeDisplay: TextView
    private lateinit var translatedText: EditText
    private lateinit var cameraPreview: PreviewView
    private lateinit var resetButton: Button
    private lateinit var speakButton: Button
    private lateinit var chatMessageBox: TextView
    private lateinit var dbRef: DatabaseReference
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var socket: Socket

    // ── New views ─────────────────────────────────────────────────
    private lateinit var modeIndicator: TextView
    private lateinit var sentenceSection: LinearLayout
    private lateinit var wordSection: LinearLayout
    private lateinit var sentenceDisplay: TextView
    private lateinit var buildingWordDisplay: TextView
    private lateinit var wordFormationButton: Button
    private lateinit var nextWordButton: Button
    private lateinit var sendButton: Button
    private lateinit var nextLetterButton: Button
    private lateinit var doneButton: Button
    private lateinit var aiCorrectedText: TextView

    // ── Camera internals ──────────────────────────────────────────
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastSentTime = 0L
    private val CAMERA_PERMISSION_CODE = 101

    // ── State machine ─────────────────────────────────────────────
    private enum class Mode { SENTENCE, WORD }
    private var currentMode = Mode.SENTENCE

    // Word Formation: each element = one prediction added via "Next Letter"
    // e.g. ["H", "E", "L", "L"]  →  displayed as "H · E · L · L"  →  joined as "HELL"
    private val currentLetterList = mutableListOf<String>()

    // Sentence Formation: each element = one word
    // e.g. ["I", "WANT", "WATER"]  →  joined as "I WANT WATER"
    private val sentenceWords = mutableListOf<String>()

    // The most recent prediction emitted by the backend (updated every frame)
    private var lastPrediction = ""

    // OkHttp client (reused — do NOT create one per request)
    private val okHttpClient = OkHttpClient()

    // ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign2text)

        // Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.customToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ── Bind existing views ───────────────────────────────────
        roomCodeDisplay = findViewById(R.id.roomCodeDisplay)
        cameraPreview   = findViewById(R.id.cameraPreview)
        translatedText  = findViewById(R.id.translatedText)
        chatMessageBox  = findViewById(R.id.chatMessageBox)
        resetButton     = findViewById(R.id.resetButton)
        speakButton     = findViewById(R.id.speakButton)

        // ── Bind new views ────────────────────────────────────────
        modeIndicator       = findViewById(R.id.modeIndicator)
        sentenceSection     = findViewById(R.id.sentenceSection)
        wordSection         = findViewById(R.id.wordSection)
        sentenceDisplay     = findViewById(R.id.sentenceDisplay)
        buildingWordDisplay = findViewById(R.id.buildingWordDisplay)
        wordFormationButton = findViewById(R.id.wordFormationButton)
        nextWordButton      = findViewById(R.id.nextWordButton)
        sendButton          = findViewById(R.id.sendButton)
        nextLetterButton    = findViewById(R.id.nextLetterButton)
        doneButton          = findViewById(R.id.doneButton)
        aiCorrectedText     = findViewById(R.id.aiCorrectedText)

        // ── Firebase setup ────────────────────────────────────────
        val roomCode = intent.getStringExtra("roomCode") ?: "----"
        roomCodeDisplay.text = "Room Code : $roomCode"

        dbRef = FirebaseDatabase.getInstance(FIREBASE_URL).getReference("rooms/$roomCode")

        // Listen for voice messages coming FROM the hearing person
        dbRef.child("voiceMessage").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val msg = snapshot.getValue(String::class.java)
                if (msg != null && msg != "Room created.") {
                    chatMessageBox.text = msg
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // ── TTS ───────────────────────────────────────────────────
        textToSpeech = TextToSpeech(this, this)

        // ─────────────────────────────────────────────────────────
        //  EXISTING BUTTON HANDLERS (unchanged behaviour)
        // ─────────────────────────────────────────────────────────

        speakButton.setOnClickListener {
            val text = translatedText.text.toString()
            if (text.isNotBlank()) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        // Reset clears EVERYTHING — live prediction, word builder, sentence builder, AI text
        resetButton.setOnClickListener {
            translatedText.setText("")
            lastPrediction = ""
            currentLetterList.clear()
            sentenceWords.clear()
            updateBuildingWordDisplay()
            updateSentenceDisplay()
            aiCorrectedText.visibility = View.GONE
            switchToSentenceMode()
            Toast.makeText(this, "Reset!", Toast.LENGTH_SHORT).show()
        }

        // ─────────────────────────────────────────────────────────
        //  NEW BUTTON HANDLERS
        // ─────────────────────────────────────────────────────────

        // [Word Formation] → enter Word Mode
        wordFormationButton.setOnClickListener {
            switchToWordMode()
        }

        // [Next Word] → add live prediction as next word in the sentence
        nextWordButton.setOnClickListener {
            if (lastPrediction.isBlank()) {
                Toast.makeText(this, "No gesture detected yet — hold a sign in front of the camera", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sentenceWords.add(lastPrediction)
            updateSentenceDisplay()
            Toast.makeText(this, "\"$lastPrediction\" added to sentence", Toast.LENGTH_SHORT).show()
        }

        // [Send] → call Gemini to fix grammar, then push to Firebase
        sendButton.setOnClickListener {
            if (sentenceWords.isEmpty()) {
                Toast.makeText(this, "Sentence is empty! Add words first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val gloss = sentenceWords.joinToString(" ")
            callGeminiAPI(gloss)
        }

        // [Next Letter] → append live prediction to the word being built
        nextLetterButton.setOnClickListener {
            if (lastPrediction.isBlank()) {
                Toast.makeText(this, "No gesture detected yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentLetterList.add(lastPrediction)
            updateBuildingWordDisplay()
            Toast.makeText(this, "\"$lastPrediction\" added", Toast.LENGTH_SHORT).show()
        }

        // [Done] → finalize the built word, return to Sentence Mode
        doneButton.setOnClickListener {
            if (currentLetterList.isEmpty()) {
                // Nothing was built — just go back
                switchToSentenceMode()
                Toast.makeText(this, "Word formation cancelled", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val builtWord = currentLetterList.joinToString("")   // ["H","E","L","L"] → "HELL"
            sentenceWords.add(builtWord)
            currentLetterList.clear()
            updateBuildingWordDisplay()
            updateSentenceDisplay()
            switchToSentenceMode()
            Toast.makeText(this, "Word \"$builtWord\" added to sentence!", Toast.LENGTH_SHORT).show()
        }

        // ─────────────────────────────────────────────────────────
        //  SOCKET (unchanged — only prediction display is affected)
        // ─────────────────────────────────────────────────────────
        socket = IO.socket(BASE_URL)

        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread { Log.d("SOCKET", "Backend Connected") }
        }
        socket.on(Socket.EVENT_CONNECT_ERROR) {
            runOnUiThread { Log.e("SOCKET", "Backend Connection Error") }
        }

        socket.on("prediction") { args ->
            val data = args[0] as JSONObject
            val text = data.getString("text")
            lastPrediction = text          // store for button handlers to use
            runOnUiThread {
                translatedText.setText(text)
                // ⚠️ We intentionally do NOT push to Firebase here anymore.
                //    Firebase is only updated when the user presses [Send],
                //    after the full sentence has been AI-corrected.
            }
        }

        socket.connect()
        checkCameraPermission()
    }

    // ─────────────────────────────────────────────────────────────
    //  MODE HELPERS
    // ─────────────────────────────────────────────────────────────

    private fun switchToSentenceMode() {
        currentMode = Mode.SENTENCE
        sentenceSection.visibility = View.VISIBLE
        wordSection.visibility = View.GONE
        modeIndicator.text = "📝  SENTENCE FORMATION MODE"
        modeIndicator.setBackgroundColor(Color.parseColor("#3949AB"))
    }

    private fun switchToWordMode() {
        currentMode = Mode.WORD
        sentenceSection.visibility = View.GONE
        wordSection.visibility = View.VISIBLE
        modeIndicator.text = "🔤  WORD FORMATION MODE"
        modeIndicator.setBackgroundColor(Color.parseColor("#00695C"))
    }

    private fun updateSentenceDisplay() {
        sentenceDisplay.text = if (sentenceWords.isEmpty()) {
            "(tap 'Next Word' to add current prediction)"
        } else {
            sentenceWords.joinToString("   ")   // e.g.  "I   WANT   WATER"
        }
    }

    private fun updateBuildingWordDisplay() {
        buildingWordDisplay.text = if (currentLetterList.isEmpty()) {
            "(sign a letter, then tap 'Next Letter')"
        } else {
            currentLetterList.joinToString(" · ")   // e.g.  "H · E · L · L"
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GEMINI API CALL
    // ─────────────────────────────────────────────────────────────

    private fun callGeminiAPI(glossSentence: String) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY"

        val promptText =
            "You are an ASL (American Sign Language) interpreter. " +
                    "Convert the following ASL gloss into one natural, grammatically correct English sentence. " +
                    "Reply with ONLY the final English sentence — no explanations, no punctuation notes.\n\n" +
                    "Gloss: $glossSentence"

        // Build the JSON body safely using JSONObject (avoids string-escaping bugs)
        val bodyJson = JSONObject()
            .put("contents", JSONArray()
                .put(JSONObject()
                    .put("parts", JSONArray()
                        .put(JSONObject()
                            .put("text", promptText)
                        )
                    )
                )
            )
            .toString()

        val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        // Show a loading state immediately
        runOnUiThread {
            aiCorrectedText.text = "⏳ Converting to English..."
            aiCorrectedText.visibility = View.VISIBLE
        }

        okHttpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e("GEMINI", "API call failed: ${e.message}")
                runOnUiThread {
                    // Fallback: show raw gloss sentence, still send it
                    aiCorrectedText.text = "✨ $glossSentence   (sent — AI unavailable)"
                    aiCorrectedText.visibility = View.VISIBLE
                    dbRef.child("signMessage").setValue(glossSentence)
                    sentenceWords.clear()
                    updateSentenceDisplay()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                try {
                    // Parse Gemini response:
                    // { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
                    val corrected = JSONObject(bodyStr)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    runOnUiThread {
                        // Show below the sentence — small, non-distracting
                        aiCorrectedText.text = "✨ $corrected"
                        aiCorrectedText.visibility = View.VISIBLE

                        // Push the AI-corrected sentence to Firebase
                        // → ChatActivity will receive and display it to the hearing person
                        dbRef.child("signMessage").setValue(corrected)

                        // Clear sentence builder for the next sentence
                        sentenceWords.clear()
                        updateSentenceDisplay()

                        Toast.makeText(
                            this@Sign2TextActivity,
                            "Sent! ✅",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {
                    Log.e("GEMINI", "Parse error: ${e.message} | body: $bodyStr")
                    runOnUiThread {
                        // Still send the raw gloss as fallback
                        aiCorrectedText.text = "✨ $glossSentence   (AI parse error — raw sent)"
                        aiCorrectedText.visibility = View.VISIBLE
                        dbRef.child("signMessage").setValue(glossSentence)
                        sentenceWords.clear()
                        updateSentenceDisplay()
                    }
                }
            }
        })
    }

    // ─────────────────────────────────────────────────────────────
    //  CAMERA (100% unchanged from original)
    // ─────────────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
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
                if (now - lastSentTime < 500) { image.close(); return@setAnalyzer }
                lastSentTime = now

                try {
                    var bitmap = imageProxyToBitmap(image)
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    if (rotationDegrees != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                    }
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap, 320, (bitmap.height * (320f / bitmap.width)).toInt(), true
                    )
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    Log.d("FRAME", "Frame Sent")
                    socket.emit("frame", "data:image/jpeg;base64,$base64")
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
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
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 60, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ─────────────────────────────────────────────────────────────
    //  TTS / LIFECYCLE (unchanged)
    // ─────────────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) textToSpeech.language = Locale.US
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