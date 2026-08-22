package com.deepeye.agent.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.CyberCyan
import com.deepeye.agent.ui.theme.ObsidianVoid
import com.deepeye.agent.ui.theme.StatusError
import com.deepeye.agent.ui.theme.StatusSuccess
import com.deepeye.agent.ui.theme.ThinkingMutedSlate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScribeScreen(
    viewModel: AudioScribeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importAudioFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Scribe", color = CyberCyan) },
                actions = {
                    NeonStatusBadge(
                        text = if (uiState.isModelReady) "Transcribe" else "Model Not Ready",
                        color = if (uiState.isModelReady) StatusSuccess else ThinkingMutedSlate
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianVoid
                )
            )
        },
        containerColor = ObsidianVoid
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Card
            item {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (uiState.isRecording) 1.2f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAnimation"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(if (uiState.isRecording) StatusError else CyberCyan)
                                .clickable {
                                    if (uiState.isRecording) {
                                        viewModel.stopRecording()
                                    } else {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            viewModel.startRecording()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isRecording) "■" else "●",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val seconds = uiState.recordingDurationMs / 1000
                        val minutes = seconds / 60
                        val remainingSeconds = seconds % 60
                        
                        Text(
                            text = String.format("%02d:%02d", minutes, remainingSeconds),
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!uiState.isRecording) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("audio/*") },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = CyberCyan
                                )
                            ) {
                                Text("Import Audio File")
                            }
                        }
                        
                        uiState.error?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = error, color = StatusError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Transcript Card
            item {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Transcription", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (uiState.transcript.isEmpty() && !uiState.isTranscribing) {
                            Text(
                                text = "Record or import audio to transcribe",
                                color = ThinkingMutedSlate,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = uiState.transcript + if (uiState.isTranscribing) " █" else "",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (uiState.audioFilePath != null && !uiState.isTranscribing) {
                            Button(
                                onClick = { viewModel.transcribeAudio() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianVoid
                                ),
                                enabled = uiState.isModelReady
                            ) {
                                Text("Transcribe Audio")
                            }
                        }
                    }
                }
            }
            
            // Translation Card
            if (uiState.transcript.isNotEmpty()) {
                item {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Translation", style = MaterialTheme.typography.titleMedium, color = CyberCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val languages = listOf("English", "Spanish", "French", "Hindi", "Arabic", "Chinese")
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                languages.forEach { lang ->
                                    CyberChip(
                                        label = lang,
                                        selected = uiState.targetLanguage == lang,
                                        onClick = { viewModel.translateTranscript(lang) }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (uiState.translatedText != null || uiState.isTranslating) {
                                Text(
                                    text = (uiState.translatedText ?: "") + if (uiState.isTranslating) " █" else "",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            
                            Button(
                                onClick = { viewModel.translateTranscript(uiState.targetLanguage) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianVoid
                                ),
                                enabled = uiState.isModelReady && !uiState.isTranslating
                            ) {
                                Text("Translate")
                            }
                        }
                    }
                }
            }
            
            // Privacy Footer
            item {
                Text(
                    text = "🔒 100% On-Device • Your audio never leaves this device",
                    color = ThinkingMutedSlate,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
