package com.example.exifeditor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ExifApp()
                }
            }
        }
    }
}

@Composable
fun ExifApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var logMessage by remember { mutableStateOf("Ready to start.") }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            logMessage = "Selected ${uris.size} photos."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "EXIF Tool", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedUris.isEmpty()) "Select Photos" else "Reselect Photos (${selectedUris.size})")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (selectedUris.isNotEmpty()) {
            Text("Modify & Save As:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            processImages(context, selectedUris, "LEICA", "Q3") { msg -> logMessage = msg }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text("Q3 Mode")
                }

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            processImages(context, selectedUris, "XIAOMI", "17U") { msg -> logMessage = msg }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text("Xiaomi Mode")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isProcessing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(text = logMessage, style = MaterialTheme.typography.bodyMedium)
    }
}

suspend fun processImages(
    context: Context, 
    uris: List<Uri>, 
    mode: String, 
    modelSuffix: String,
    updateLog: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        var successCount = 0
        val targetMake = if (mode == "LEICA") "LEICA CAMERA AG" else "Xiaomi"
        val targetModel = if (mode == "LEICA") "LEICA Q3" else "Xiaomi 17 Ultra"
        val filenameSuffix = if (mode == "LEICA") "_Leica" else "_Mi"

        uris.forEachIndexed { index, uri ->
            updateLog("Processing ${index + 1}/${uris.size}...")
            
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}_${index}$filenameSuffix.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ExifTool")
                    }
                }
                
                val newImageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                if (newImageUri != null) {
                    resolver.openInputStream(uri)?.use { input ->
                        resolver.openOutputStream(newImageUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }

                    resolver.openFileDescriptor(newImageUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(ExifInterface.TAG_MAKE, targetMake)
                        exif.setAttribute(ExifInterface.TAG_MODEL, targetModel)
                        exif.saveAttributes()
                    }
                    successCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateLog("Done! Saved $successCount photos to Gallery/Pictures.")
    }
}
