package com.deepeye.agent.ui.vision

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.CyberCyan
import com.deepeye.agent.ui.theme.ObsidianVoid
import com.deepeye.agent.ui.theme.ThinkingMutedSlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskImageScreen(
    viewModel: AskImageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.onImageCaptured(it) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask Image", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { /* Handle back navigation */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    NeonStatusBadge(
                        text = "Vision",
                        color = CyberCyan
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianVoid
                )
            )
        },
        containerColor = ObsidianVoid
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF1A1F2E), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.selectedImageUri != null) {
                    val bitmap = remember(uiState.selectedImageUri) {
                        uiState.selectedImageUri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { 
                                BitmapFactory.decodeStream(it) 
                            }
                        }
                    }
                    
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Failed to load image", color = Color.Red)
                    }

                    FloatingActionButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = CyberCyan,
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Change Image")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Camera")
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prompt Input Field
            OutlinedTextField(
                value = uiState.prompt,
                onValueChange = viewModel::onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask a question about the image...", color = ThinkingMutedSlate) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = ThinkingMutedSlate,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Analyze Button
            Button(
                onClick = viewModel::analyzeImage,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedImageUri != null && uiState.prompt.isNotBlank() && !uiState.isStreaming && uiState.isModelReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.DarkGray,
                    disabledContentColor = Color.LightGray
                )
            ) {
                Text("Analyze")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.error != null) {
                Text(text = "Error: ${uiState.error}", color = Color.Red, modifier = Modifier.padding(8.dp))
            }

            // Response Area
            if (uiState.response.isNotEmpty() || uiState.isStreaming) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.response,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        if (uiState.tokensPerSecond > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "%.1f TPS".format(uiState.tokensPerSecond),
                                color = CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}
