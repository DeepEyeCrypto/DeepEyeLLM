package com.deepeye.agent.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════
// AEOS v2027 Design Tokens
// ═══════════════════════════════════════════

// Primary: Deep Blue
val DeepBluePrimary = Color(0xFF0D47A1)
val DeepBluePrimaryDark = Color(0xFF1565C0)
val DeepBluePrimaryLight = Color(0xFF42A5F5)

// Secondary: Teal / Cyan
val TealCyanSecondary = Color(0xFF00BFA5)
val TealCyanSecondaryDark = Color(0xFF1DE9B6)
val TealCyanSecondaryLight = Color(0xFF64FFDA)

// Accent: Amber
val AmberAccent = Color(0xFFFFB300)
val AmberAccentDark = Color(0xFFFFCA28)
val AmberAccentLight = Color(0xFFFFE082)

// Policy: Purple
val PolicyPurple = Color(0xFF651FFF)
val PolicyPurpleDark = Color(0xFFB388FF)

// Status Colors
val StatusSuccess = Color(0xFF00C853)
val StatusWarning = Color(0xFFFF6D00)
val StatusError = Color(0xFFD50000)
val StatusInfo = Color(0xFF2979FF)

// ═══════════════════════════════════════════
// Glassmorphism Tokens
// ═══════════════════════════════════════════
val GlassDarkBackground = Color(0xFF0A0E1A)
val GlassDarkSurface = Color(0xCC1A1A2E) // 80% opacity
val GlassDarkSurfaceElevated = Color(0xE61A1A2E) // 90% opacity
val GlassLightOverlay = Color(0x26FFFFFF) // 15% white
val GlassBorder = Color(0x1AFFFFFF) // 10% white
val GlassBorderActive = Color(0x4DFFFFFF) // 30% white
val GlassHighlight = Color(0x0DFFFFFF) // 5% white subtle highlight

// ═══════════════════════════════════════════
// Legacy Compatibility (keep existing names)
// ═══════════════════════════════════════════
val PrimaryLocal = TealCyanSecondary
val PrimaryLocalDark = TealCyanSecondaryDark
val SecondaryCloud = AmberAccent
val SecondaryCloudDark = AmberAccentDark
val SurfaceDark = Color(0x66000000) // 40% Black (Glass) — legacy
val SurfaceLight = Color(0x66FFFFFF) // 40% White (Glass) — legacy
