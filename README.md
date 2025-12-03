🔱 POSEIDON: Privacy-First, Zero-Latency Biomechanics Coach

Project for The Claude Challenge (Powered by RunAnywhere AI)

1. 🎯 Problem Statement

Cloud AI fails to meet the requirements of biomechanics coaching: real-time safety correction and user data privacy. This gap leads to dangerous latency (200ms+) and unnecessary data exposure (uploading workout videos). POSEIDON solves this by processing all data on-device for sub-80ms feedback.

2. 🤖 Models Used

POSEIDON utilizes a multimodal, two-stage local AI pipeline:

Model

Type

Purpose

Pose Estimator (RunAnywhere Vision)

Vision (Pre-Trained)

Extracts 33 key joint coordinates from the live camera feed every frame.

claude-3-haiku-on-device

LLM (Text Generation)

Analyzes the joint coordinates to diagnose biomechanical errors (e.g., Knee Valgus) and outputs the corresponding correction.

3. 🏆 Scoring Criterion & SDK Feature Defense

POSEIDON is engineered to run 100% of its critical analysis locally, ensuring a maximum score in the following evaluation categories:

SDK Feature

Implementation in POSEIDON

Advantage

On-Device Vision

SDK's Pose Estimator extracts 33 keypoints from the live camera feed in MainActivity.kt. (Line 61)

Provides structured input for the LLM without ever storing raw video files.

Structured Output

The LLM call in PoseidonAnalyzer.kt uses jsonMode = true and a JSON schema System Prompt. (Line 40)

Transforms the LLM into a deterministic analysis engine, ensuring reliable, machine-readable decisions.

Voice Pipeline

The JSON's .correction field is instantly fed to VoicePipeline.speak(). (Line 43)

Achieves Zero-Latency Voice Interface—the correction is spoken mid-rep, ensuring user safety.

Privacy

Core inference runs entirely on the device.

Absolute data security, as user movements and video never leave the phone.

4. 💻 Implementation Details (Kotlin Logic)

Initialization: The MainActivity.kt explicitly initializes the SDK in development mode (apiKey = "dev-poseidon-key") and manages the asynchronous download and loading of the claude-3-haiku-on-device model before starting the camera. (Lines 41-53)

Analysis Flow:

Input: MainActivity.kt captures a frame and passes the PoseData (key joint coordinates) to the PoseidonAnalyzer.

Analysis: PoseidonAnalyzer.kt formats the coordinate data (e.g., Knee Width, Hip Width) into a concise prompt, defining the problem: KneeWidth: 150, HipWidth: 180.

Inference: The data is sent to RunAnywhereLLM.generate(...) with jsonMode=true.

Action: The resulting JSON is parsed. If status is "error," VoicePipeline.speak() is called instantly with the correction message.
