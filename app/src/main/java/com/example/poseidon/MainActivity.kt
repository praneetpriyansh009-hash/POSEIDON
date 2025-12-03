package com.example.poseidon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // Used for coroutines/suspend functions
import kotlinx.coroutines.launch
import ai.runanywhere.sdk.llm.RunAnywhereLLM
import ai.runanywhere.sdk.vision.RunAnywhereVision
import ai.runanywhere.sdk.voice.VoicePipeline
import ai.runanywhere.sdk.public.RunAnywhere
import ai.runanywhere.sdk.data.models.SDKEnvironment
import ai.runanywhere.sdk.llm.llamacpp.LlamaCppModule // Use modern SDK imports
import ai.runanywhere.sdk.models.RunAnywhereGenerationOptions

/**
 * MainActivity: Handles camera setup and now includes explicit, asynchronous SDK initialization 
 * and model loading (using 'dev' key to bypass file configuration).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var poseidonAnalyzer: PoseidonAnalyzer

    private val vision = RunAnywhereVision() 
    private val CAMERA_REQUEST_CODE = 100
    private val MODEL_NAME = "claude-3-haiku-on-device" // The required model for the challenge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) 
        previewView = findViewById(R.id.viewFinder)

        poseidonAnalyzer = PoseidonAnalyzer()

        // 1. Start the asynchronous setup process
        lifecycleScope.launch {
            setupRunAnywhere()
        }
    }
    
    // --- SDK Initialization and Model Loading (Addressing the API Key Issue) ---

    private suspend fun setupRunAnywhere() {
        try {
            // Register LlamaCpp module (if needed, though Claude is usually external to LlamaCpp)
            LlamaCppModule.register() 
            Log.d("SDK_SETUP", "LlamaCpp Module registered.")

            // Initialize SDK using "dev" key to bypass local.properties lookup
            RunAnywhere.initialize(
                context = applicationContext, // Pass context required for init
                apiKey = "dev-poseidon-key", // Any string works in dev mode
                baseURL = "https://api.runanywhere.ai", // Standard base URL
                environment = SDKEnvironment.DEVELOPMENT
            )
            Log.i("SDK_SETUP", "SDK Initialized in DEVELOPMENT mode.")

            // 2. Download Model (if not already downloaded)
            Log.d("SDK_SETUP", "Checking/downloading model: $MODEL_NAME...")
            RunAnywhere.downloadModel(MODEL_NAME).collect { progress ->
                // This is where you would update a UI progress bar
                Log.d("SDK_SETUP", "Download progress: ${(progress * 100).toInt()}%")
            }
            
            // 3. Load Model
            val success = RunAnywhere.loadModel(MODEL_NAME)
            if (success) {
                Log.i("SDK_SETUP", "Model $MODEL_NAME loaded successfully. Ready for inference.")
                checkCameraPermissionAndStart()
            } else {
                Toast.makeText(this, "Failed to load model $MODEL_NAME.", Toast.LENGTH_LONG).show()
                Log.e("SDK_SETUP", "Model loading failed.")
            }

        } catch (e: Exception) {
            Log.e("SDK_SETUP", "Fatal SDK Setup Error: $e")
            Toast.makeText(this, "SDK Setup Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --- Camera Handling and Vision Pipeline Setup ---

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            startVisionPipeline()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVisionPipeline()
        } else {
            Toast.makeText(this, "Camera permission required for POSEIDON.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVisionPipeline() {
        // Ensure the model is loaded before starting the vision pipeline
        if (RunAnywhere.currentModel?.name != MODEL_NAME) {
            Log.e("Vision", "Model not loaded. Cannot start vision pipeline.")
            return
        }

        Log.i("Vision", "Starting Pose Estimator and Camera Feed.")
        
        val poseEstimator = vision.createPoseEstimator(this)
        
        vision.startCamera(this, previewView) { frame ->
            
            // This frame runs continuously, passing data to the analyzer
            val poseData = poseEstimator.detect(frame)
            
            if (poseData != null) {
                poseidonAnalyzer.analyzeFrame(poseData)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vision.stopCamera(this) 
        VoicePipeline.shutdown()
        RunAnywhereLLM.shutdown()
        // Unload the model to free up resources when the app is destroyed
        lifecycleScope.launch { 
            RunAnywhere.unloadModel()
        }
    }
}
