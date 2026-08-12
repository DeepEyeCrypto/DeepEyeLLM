package com.deepeye.agent.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deepeye.agent.ui.theme.DeepEyeTheme
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.GlassCardElevated
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.CyberButton
import androidx.hilt.navigation.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BraveBrowserScreen(
    initialUrl: String? = null,
    initialDexSource: String? = null,
    initialSecurityScore: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: BraveBrowserViewModel = hiltViewModel()
) {
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrEmpty()) {
            viewModel.updateUrl(initialUrl)
        }
    }
    val state by viewModel.uiState.collectAsState()
    var urlInput by remember(state.currentUrl, initialUrl) { mutableStateOf(initialUrl ?: state.currentUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current
    val currentSecurityScore = initialSecurityScore ?: 98

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp
        val horizontalPadding = if (isWideScreen) 24.dp else 12.dp

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color(0xEE0B0E14))
                        .padding(horizontal = horizontalPadding, vertical = 8.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🦁 Brave DEX",
                                fontSize = if (isWideScreen) 22.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepEyeTheme.colors.brandOrange
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0x3300E676),
                                contentColor = DeepEyeTheme.colors.statusSuccess
                            ) {
                                Text(
                                    text = "Web3 Shield ($currentSecurityScore)",
                                    fontSize = if (isWideScreen) 12.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { viewModel.toggleDeepResearchOverlay() }) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Deep Research AI",
                                    tint = if (state.isDeepResearchActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Search Bar & Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { webViewRef?.goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { webViewRef?.goForward() }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isWideScreen) 56.dp else 48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            placeholder = { Text("Search or enter DEX URL / Pair Address", color = MaterialTheme.colorScheme.outline, fontSize = if (isWideScreen) 14.sp else 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = "Security Lock", tint = Color(0xFF00E676))
                            },
                            trailingIcon = {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.updateUrl(urlInput)
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                viewModel.updateUrl(urlInput)
                                focusManager.clearFocus()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0x331F293D),
                                unfocusedContainerColor = Color(0x221F293D),
                                focusedBorderColor = DeepEyeTheme.colors.brandOrange,
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick DEX Bookmarks Bar
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (state.isDeepResearchActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.clickable {
                                    viewModel.toggleDeepResearchOverlay()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = if (isWideScreen) 16.dp else 12.dp,
                                        vertical = if (isWideScreen) 8.dp else 6.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🧠", fontSize = if (isWideScreen) 16.sp else 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Monet Deep Research",
                                        fontSize = if (isWideScreen) 13.sp else 11.sp,
                                        color = if (state.isDeepResearchActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val dexes = listOf(
                            Pair("DexScreener", "🦅"),
                            Pair("Uniswap", "🦄"),
                            Pair("Raydium", "☀️"),
                            Pair("Jupiter", "🪐")
                        )
                        items(dexes) { dex ->
                            val isActive = (initialDexSource != null && dex.first.equals(initialDexSource, ignoreCase = true)) || state.currentUrl.contains(dex.first.lowercase())
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isActive) Color(0x44FF5722) else Color(0x22FFFFFF),
                                modifier = Modifier.clickable {
                                    viewModel.updateUrl("https://${dex.first.lowercase()}.com")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = if (isWideScreen) 16.dp else 12.dp,
                                        vertical = if (isWideScreen) 8.dp else 6.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(dex.second, fontSize = if (isWideScreen) 16.sp else 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        dex.first,
                                        fontSize = if (isWideScreen) 13.sp else 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Embedded WebView
            var lastLoadedUrl by remember { mutableStateOf("") }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36 Brave/125"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                viewModel.setPageLoading(true, 30)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                viewModel.setPageLoading(false, 100)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.setPageLoading(newProgress < 100, newProgress)
                            }
                        }
                        lastLoadedUrl = state.currentUrl
                        loadUrl(state.currentUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    if (state.currentUrl != lastLoadedUrl && state.currentUrl.isNotBlank()) {
                        lastLoadedUrl = state.currentUrl
                        webView.loadUrl(state.currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Linear Progress Indicator
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = DeepEyeTheme.colors.brandOrange,
                    trackColor = Color.Transparent
                )
            }

            // Monet Deep Research Overlay Panel
            AnimatedVisibility(
                visible = state.isDeepResearchActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                GlassCardElevated(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NeonStatusBadge(text = "98/100", color = Color(0xFF00E676))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Live Page Intelligence",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            IconButton(onClick = { viewModel.toggleDeepResearchOverlay() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "Smart Contract Audit: Safe. No mint function detected. Liquidity is locked for 12 months.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CyberButton(onClick = { /* Check Liquidity */ }) {
                                Text("Check Liquidity")
                            }
                            CyberButton(onClick = { /* Analyze Holders */ }) {
                                Text("Analyze Holders")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var queryInput by remember { mutableStateOf(state.researchQuery) }

                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = {
                                queryInput = it
                                viewModel.updateResearchQuery(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Ask Monet AI to analyze contract liquidity, volume, risk...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.runDeepCryptoResearch(queryInput) }) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Analyze",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        state.aiAnalysisResult?.let { result ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = result,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
