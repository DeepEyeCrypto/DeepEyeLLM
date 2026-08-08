# 05 — Design System & Visual Tokens: DeepEyeLLM

## Aesthetic Direction: Vision Pro Dark Glassmorphism
DeepEyeLLM utilizes a premium, dark visionOS-inspired aesthetic featuring deep space dark backgrounds (`#0A0C14`), translucent frosted glass cards, subtle neon borders, and clean typography.

---

## Color Tokens

```kotlin
object DeepEyeColors {
    val BackgroundDark = Color(0xFF0A0C14)
    val GlassCardBg = Color(0xCC121624)      // 80% alpha dark glass
    val GlassBorder = Color(0x33FFFFFF)      // 20% alpha white border
    
    // Engine Status Accent Colors
    val PrimaryLocal = Color(0xFF00E676)     // Vibrant Cyan-Green (LiteRT Active)
    val GgufAccent = Color(0xFFFFB74D)       // Warm Amber (GGUF Active)
    val ErrorAccent = Color(0xFFFF5252)      // Coral Red (Engine Error / OOM)
    val NeutralInactive = Color(0xFF757575)  // Cool Slate Gray
    
    // Text Hierarchy
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF90A4AE)
    val TextSubtle = Color(0xFF607D8B)
}
```

---

## Typography Standards
- **Font Family**: Roboto / Inter / System Monospace for code blocks.
- **Header Large**: 22sp, Medium, Letter spacing 0.15px (`TextPrimary`).
- **Body Medium**: 14sp, Normal, Letter spacing 0.25px (`TextSecondary`).
- **Code Snippet**: 13sp, Monospace, Line height 18sp.

---

## Glassmorphism Guidelines
1. **Background Image**: Vision Pro atmospheric radial background (`vision_pro_bg.png`).
2. **Card Composables**:
   - `containerColor = Color.Black.copy(alpha = 0.65f)`
   - `border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))`
   - `shape = RoundedCornerShape(16.dp)`
3. **Floating Capsules**:
   - Bottom input capsule styled with `RoundedCornerShape(28.dp)` and semi-transparent blur.

---

## Accessibility Tokens
- **Minimum Touch Target**: $48\text{dp} \times 48\text{dp}$ for all interactive buttons and filter chips.
- **Color Contrast**: All primary text elements maintain $\ge 4.5:1$ contrast ratio against dark glass backgrounds.
