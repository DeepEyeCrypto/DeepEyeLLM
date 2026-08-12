# DeepEyeLLM Design System v2027

## Design Principles

| Principle | Description |
|---|---|
| **Calm Interface** | Single primary action per screen, progressive disclosure, whitespace as structure |
| **Volumetric Glassmorphism** | Dynamic backdrop blur, specular highlights, Z-axis depth, semantic borders |
| **Adaptive Layouts** | Canonical layouts (list-detail, supporting pane) via NavigationSuiteScaffold |
| **AI Copilot Transparency** | AI as optional copilot — clear controls, visible state, human-in-the-loop |
| **Dynamic Color** | Material You dynamic color (Android 12+) with brand fallback |
| **Accessibility First** | WCAG 2.2 compliance, reduce transparency support, 4.5:1 contrast ratios |

---

## Color Tokens

### Primary Palette
| Token | Hex | Usage |
|---|---|---|
| `DeepBluePrimary` | `#0D47A1` | Primary brand, buttons (light) |
| `DeepBluePrimaryDark` | `#1565C0` | Primary brand (dark) |
| `DeepBluePrimaryLight` | `#42A5F5` | Primary containers (light) |

### Secondary / Cyber Cyan
| Token | Hex | Usage |
|---|---|---|
| `TealCyanSecondary` | `#00BFA5` | Secondary actions |
| `TealCyanSecondaryDark` | `#00E5FF` | Active highlights, streaming indicators |
| `TealCyanSecondaryLight` | `#64FFDA` | Secondary containers |

### Accent / Solar Gold
| Token | Hex | Usage |
|---|---|---|
| `AmberAccent` | `#FFB300` | Warnings, accent highlights |
| `AmberAccentDark` | `#FFCA28` | Accent (dark mode) |

### Status Colors (WCAG 2.2 ≥ 4.5:1)
| Token | Hex | Usage |
|---|---|---|
| `StatusSuccess` | `#00E676` | Success states, active engine |
| `StatusWarning` | `#FF9100` | Warning states |
| `StatusError` | `#FF1744` | Error states |
| `StatusInfo` | `#00E5FF` | Info, streaming, active |

### Volumetric Glass Tokens
| Token | Hex/Alpha | Usage |
|---|---|---|
| `CyberBackground` | `#070A12` | App background |
| `CyberSurfaceDark` | `#0E1322` | Opaque fallback surface |
| `GlassSurfaceCyber` | `80% #121826` | Primary glass surface |
| `GlassSurfaceCyberElevated` | `90% #1A233A` | Elevated glass surface |
| `GlassBorderGlow` | `40% #00E5FF` | Active border glow |
| `GlassBorderSpecular` | `20% #FFFFFF` | Specular top-edge highlight |
| `GlassBorder` | `15% #FFFFFF` | Default border |
| `GlassHighlight` | `10% #FFFFFF` | Subtle glass highlight |

---

## Typography Scale

| Style | Size | Weight | Line Height | Usage |
|---|---|---|---|---|
| `headlineLarge` | 22sp | Bold | 28sp | Screen titles |
| `headlineMedium` | 18sp | SemiBold | 24sp | Section titles |
| `headlineSmall` | 16sp | SemiBold | 22sp | Card titles |
| `titleLarge` | 16sp | SemiBold | 22sp | Top bar titles |
| `titleMedium` | 14sp | Medium | 20sp | Subsection titles |
| `titleSmall` | 13sp | Medium | 18sp | Labels |
| `bodyLarge` | 14sp | Normal | 20sp | Chat messages, body text |
| `bodyMedium` | 13sp | Normal | 18sp | Secondary text |
| `bodySmall` | 11sp | Normal | 16sp | Captions |
| `labelLarge` | 12sp | Medium | 16sp | Buttons |
| `labelMedium` | 11sp | Medium | 16sp | Chips |
| `labelSmall` | 10sp | Medium | 14sp | Badges, tags |

### Responsive Scaling
- **Expanded screens** (>840dp): headlineLarge → 26sp, bodyLarge → 15sp
- Uses `responsiveTypography(isExpandedScreen)` helper

---

## Spacing System

| Token | Value | Usage |
|---|---|---|
| `xs` | 4dp | Tight spacing |
| `sm` | 8dp | Between small elements |
| `md` | 12dp | Inter-card spacing |
| `lg` | 16dp | Screen padding, card padding |
| `xl` | 20dp | Section gaps |
| `xxl` | 24dp | Major section dividers |
| `xxxl` | 32dp | Empty state padding |

---

## Elevation & Depth

| Level | Elevation | Usage |
|---|---|---|
| Flat | 0dp | Default cards |
| Cards | 2dp | Standard GlassCard |
| Elevated | 4dp | GlassCardElevated, prominent panels |
| Dialogs | 8dp | Modal sheets, bottom sheets |

### Specular Highlight
- **Gradient**: 12% white → transparent, top 30px
- Applied via `drawWithContent` on GlassCard internals
- Disabled when `isReduceTransparencyEnabled` returns true

### Border Width
- Default: 1dp
- Active: 1.5dp

---

## Responsive Breakpoints

| Class | Width | Navigation | Columns | Layout |
|---|---|---|---|---|
| **Compact** (phone) | <600dp | `NavigationBar` (bottom) | 1 | Single column |
| **Medium** (tablet/foldable) | 600–840dp | `NavigationRail` (side) | 2 | List-Detail / 2-col grid |
| **Expanded** (desktop/large) | >840dp | `NavigationDrawer` (side) | 3+ | Supporting Pane / 3-col grid |

Detected via `currentUiLayoutMode()` → `UiLayoutMode.COMPACT | MEDIUM | EXPANDED`

---

## Components

### GlassCard
**File**: `ui/components/GlassCard.kt`

| Property | Default | Description |
|---|---|---|
| `isActive` | `false` | Shows glowing active border |
| `tintColor` | `glassSurface` | Surface tint override |
| `borderColor` | Auto | Border color (active vs default) |
| `shape` | `RoundedCornerShape(16.dp)` | Card shape |
| `elevation` | `0.dp` | Card elevation |

**Accessibility**: Automatically falls back to opaque `#0E1322` when reduce transparency enabled. Focus ring applied via `accessibleFocusRing()`.

### NeonStatusBadge
**File**: `ui/components/NeonStatusBadge.kt`

| Property | Default | Description |
|---|---|---|
| `text` | Required | Badge label |
| `color` | `#00E5FF` | Badge color |
| `isPulsing` | `true` | Enable pulse animation |
| `onClick` | `null` | Optional click handler with haptic |

**Accessibility**: Semantic `contentDescription` auto-set. Haptic feedback via `PerformanceUtils.triggerHaptic()`.

### BentoGrid
**File**: `ui/components/BentoGrid.kt`

| Property | Default | Description |
|---|---|---|
| `contentPadding` | `16.dp` | Grid content padding |
| `verticalSpacing` | `12.dp` | Vertical gap |
| `horizontalSpacing` | `12.dp` | Horizontal gap |

Columns auto-adjust: 1 (compact) → 2 (medium) → 3 (expanded)

---

## Screen Layouts

### ChatScreen
- **Compact**: Full-screen transcript + calm input dock with progressive disclosure (tools hidden behind `+` button)
- **Medium/Expanded**: Row layout with chat transcript (weight 1f) + Agent Inspector Pane (320dp)

### BenchmarkScreen
- **Compact**: Single-column LazyColumn with control card → results summary → prompt metrics
- **Medium/Expanded**: Row layout with primary pane (0.55f) showing control + summary, and detail pane (0.45f) showing prompt metrics

### ModelManagerScreen
- **Compact**: LazyColumn with model cards
- **Medium**: LazyVerticalGrid with 2 columns
- **Expanded**: LazyVerticalGrid with 3 columns

---

## Accessibility Checklist (WCAG 2.2)

- [x] All text maintains ≥ 4.5:1 contrast ratio against background
- [x] Focus indicators visible for keyboard/D-pad navigation (2dp cyan ring)
- [x] Reduce Transparency detected → glass surfaces degrade to opaque
- [x] All interactive elements have `contentDescription`
- [x] Heading semantics applied to screen titles via `Modifier.semantics { heading() }`
- [x] Touch targets ≥ 48dp minimum
- [x] Loading states have semantic announcements
- [x] Status badges include semantic labels

### Accessibility Utilities (`AccessibilityUtils.kt`)
- `isReduceTransparencyEnabled(context)` — detect OS setting
- `calculateContrastRatio(foreground, background)` — WCAG ratio check
- `ensureContrastRatio(fg, bg, minRatio, fallback)` — enforce minimum contrast
- `Modifier.accessibleFocusRing()` — keyboard focus ring

---

## Performance Guidelines

### Haptic Feedback (`PerformanceUtils.kt`)
| Type | Effect | Usage |
|---|---|---|
| `CLICK` | `EFFECT_CLICK` | Button taps |
| `LIGHT_IMPACT` | `EFFECT_TICK` | Badge interactions |
| `HEAVY_IMPACT` | `EFFECT_HEAVY_CLICK` | Confirmations |
| `SUCCESS` | `EFFECT_CLICK` | Positive outcomes |
| `WARNING` | `EFFECT_DOUBLE_CLICK` | Warning states |
| `ERROR` | 100ms vibration | Error states |

### LazyColumn Content Types
| Constant | Usage |
|---|---|
| `CHAT_USER_ROW` | User message bubbles |
| `CHAT_ASSISTANT_ROW` | Assistant message bubbles |
| `CHAT_ERROR_ROW` | Error message bubbles |
| `BENCHMARK_CARD` | Benchmark metric cards |
| `MODEL_CARD` | Model catalog cards |
| `BENTO_CELL` | Bento grid cells |

---

## File Structure

```
ui/
├── theme/
│   ├── Color.kt          — Color tokens
│   ├── Type.kt           — Typography scale + responsive helper
│   ├── Theme.kt          — DeepEyeTheme + dynamic color + refractive tokens
│   └── Shape.kt          — Shape tokens
├── components/
│   ├── GlassCard.kt      — Volumetric glass card
│   ├── NeonStatusBadge.kt — Animated status badge
│   ├── BentoGrid.kt      — Adaptive grid layout
│   └── CyberComponents.kt — CyberButton, CyberChip, CyberCardHeader
├── utils/
│   ├── WindowSizeUtils.kt — UiLayoutMode detection
│   ├── AccessibilityUtils.kt — WCAG helpers
│   └── PerformanceUtils.kt — Haptics + content types
├── navigation/
│   ├── AgentNavigation.kt  — Route definitions
│   ├── AgentNavGraph.kt    — Navigation graph
│   └── DeepEyeNavHost.kt   — Type-safe nav host wrapper
├── AgentAppShell.kt        — Adaptive NavigationSuiteScaffold
├── chat/
│   ├── ChatScreen.kt       — Streaming chat with list-detail
│   └── ChatViewModel.kt    — Chat state management
├── benchmark/
│   └── BenchmarkScreen.kt  — Supporting pane benchmark suite
└── settings/
    ├── ModelManagerScreen.kt — Adaptive grid model manager
    ├── SettingsScreen.kt
    └── DiagnosticsScreen.kt
```
