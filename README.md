# SignSpeak — Android App

> Real-time ASL sign language to text communication app for Android.

SignSpeak is an Android application that enables bidirectional communication between a deaf user (using American Sign Language gestures) and a hearing user (using voice), connected through a shared room system. The deaf user's hand gestures are translated to text in real time via a computer vision backend; the hearing user's speech is transcribed and displayed live.

---

## Features

**Room System**
- Create a room with a unique 5-character code
- Share room via QR code (ZXing) or manual code entry
- Join via QR scan (ML Kit) or manual input
- Mode auto-assignment: room creator picks Sign-to-Text or Voice-to-Text; joiner gets the complementary mode

**Sign-to-Text (Deaf User)**
- Live camera feed streamed to ML backend via Socket.IO
- Real-time ASL gesture prediction with confidence score
- Sentence builder: append predicted words with "Next Word", send to Firebase
- Word builder (sub-mode): build custom words letter by letter
- Backspace support in both builder modes
- Gemini AI grammar correction before sending
- Incoming voice messages displayed live + read aloud via TextToSpeech

**Voice-to-Text (Hearing User)**
- Speech recognition via Android SpeechRecognizer
- Transcribed text sent to Firebase in real time
- Incoming sign translations displayed live

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Camera | CameraX |
| Real-time comms | Socket.IO client |
| Database | Firebase Realtime Database |
| QR Generation | ZXing |
| QR Scanning | ML Kit |
| AI | Gemini AI API |
| Speech | Android SpeechRecognizer + TextToSpeech |

---

## Project Structure

```
app/src/main/java/com/signspeak/app/
├── MainActivity.kt               # Single activity, NavHost with 4 routes
├── MainScreen.kt                 # Entry — room creation and join
├── RoomOptionsScreen.kt          # Mode selection + QR code display
├── Sign2TextScreen.kt            # Deaf user screen — camera, Socket.IO, builder, Gemini, TTS
├── ChatScreen.kt                 # Hearing user screen — speech recognition, Firebase
├── JoinRoomSheet.kt              # Bottom sheet — QR scan + manual join + Firebase validation
├── SignSpeakTopBar.kt            # Shared top bar composable
├── AppComponents.kt              # Shared UI primitives
└── ui/theme/                     # Material3 color, typography, dark/light mode
```

---

## Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26+
- A running instance of the [SignSpeak Backend](https://github.com/YOUR_USERNAME/SignSpeak-backend)
- Firebase project with Realtime Database enabled
- Gemini AI API key

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/SignSpeak.git
cd SignSpeak
```

### 2. Configure secrets

Create or update `local.properties` in the project root (this file is gitignored):

```properties
GEMINI_API_KEY=your_gemini_api_key_here
FIREBASE_URL=https://your-project.firebaseio.com
BACKEND_URL=https://your-backend-url.com
```

These are accessed at build time via `BuildConfig`. Do **not** hardcode keys in source files.

### 3. Add Firebase config

Download `google-services.json` from your Firebase console and place it in `app/`.

### 4. Build and run

Open the project in Android Studio and run on a physical device or emulator (API 26+).

> **Note:** Camera-based features require a physical device with a front-facing camera.

---

## Backend

This app requires the SignSpeak ML backend to be running and accessible. The backend handles hand landmark detection and sign classification.

See: [SignSpeak Backend](https://github.com/YOUR_USERNAME/SignSpeak-backend)

---

## Team

Developed as a Final Year Project (BE, Semester VIII) at **Vidyalankar Institute of Technology**.

| Name |
|---|
| Shubhankar Potnis |
| Riddhi Welekar |
| Aayush Samanta |
| Aishwarya Pawar |
