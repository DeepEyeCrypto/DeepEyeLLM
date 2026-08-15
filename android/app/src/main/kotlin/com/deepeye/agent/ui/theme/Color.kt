package com.deepeye.agent.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════
// AEOS v2027 Futuristic Color Tokens & Palette
// ═══════════════════════════════════════════

// Primary: Deep Blue / Electric Cyan
val DeepBluePrimary = Color(0xFF0D47A1)
val DeepBluePrimaryDark = Color(0xFF1565C0)
val DeepBluePrimaryLight = Color(0xFF42A5F5)

// Secondary: Teal / Mint / Cyber Cyan
val TealCyanSecondary = Color(0xFF00BFA5)
val TealCyanSecondaryDark = Color(0xFF00E5FF)
val TealCyanSecondaryLight = Color(0xFF64FFDA)

// Accent: Amber / Solar Gold
val AmberAccent = Color(0xFFFFB300)
val AmberAccentDark = Color(0xFFFFCA28)
val AmberAccentLight = Color(0xFFFFE082)

// Policy / AI Purple
val PolicyPurple = Color(0xFF651FFF)
val PolicyPurpleDark = Color(0xFFB388FF)

// Status Colors (WCAG 2.2 Compliant >= 4.5:1)
val StatusSuccess = Color(0xFF00E676)
val StatusWarning = Color(0xFFFF9100)
val StatusError = Color(0xFFFF1744)
val StatusInfo = Color(0xFF00E5FF)

// Extended UI Colors
val LinkBlue = Color(0xFF64B5F6)
val LinkBlueDark = Color(0xFF1565C0)
val WarningAlt = Color(0xFFFFB74D)
val WarningAltDark = Color(0xFFE65100)
val DangerAlt = Color(0xFFE57373)
val DangerAltDark = Color(0xFFC62828)
val BrandOrange = Color(0xFFFF5722)
val BrandOrangeDark = Color(0xFFE64A19)

// ═══════════════════════════════════════════
// AEOS Volumetric Glassmorphism & Obsidian Tokens
// ═══════════════════════════════════════════
val ObsidianVoid = Color(0xFF070A12)
val SlateSurface = Color(0xFF0E1322)
val SlateSurfaceElevated = Color(0xFF161E33)

val CyberBackground = ObsidianVoid
val CyberSurfaceDark = SlateSurface
val CyberCyan = Color(0xFF00E5FF)
val ElectricTeal = Color(0xFF00E676)
val NeonViolet = Color(0xFFB388FF)
val AmberFlare = Color(0xFFFFB300)
val CrimsonFlare = Color(0xFFFF1744)

// Thinking Mode & Telemetry Tokens
val ThinkingMutedSlate = Color(0xFF94A3B8)
val ThinkingBorderCyan = Color(0x6600E5FF)
val ThinkingBackground = Color(0x1A00E5FF)
val TelemetryBorder = Color(0x2600E5FF)
val TelemetrySurface = Color(0x800E1322)

val GlassSurfaceCyber = Color(0xCC121826) // 80% opacity dark slate glass
val GlassSurfaceCyberElevated = Color(0xE61A233A) // 90% opacity elevated glass
val GlassBorderGlow = Color(0x6600E5FF) // Active cyber glow border
val GlassBorderSpecular = Color(0x33FFFFFF) // Specular top edge highlight

val GlassDarkBackground = CyberBackground
val GlassDarkSurface = GlassSurfaceCyber
val GlassDarkSurfaceElevated = GlassSurfaceCyberElevated
val GlassLightOverlay = Color(0x26FFFFFF) // 15% white overlay
val GlassBorder = Color(0x26FFFFFF) // 15% white border
val GlassBorderActive = GlassBorderGlow
val GlassHighlight = Color(0x19FFFFFF) // 10% specular white highlight

// ═══════════════════════════════════════════
// Legacy Compatibility Tokens
// ═══════════════════════════════════════════
val PrimaryLocal = TealCyanSecondary
val PrimaryLocalDark = TealCyanSecondaryDark
val SecondaryCloud = AmberAccent
val SecondaryCloudDark = AmberAccentDark
val SurfaceDark = Color(0x66000000)
val SurfaceLight = Color(0x66FFFFFF)
