# REEL MONSTER — DESIGN SYSTEM SPECIFICATION

**Product:** REEL MONSTER  
**Platform:** Native Android (Jetpack Compose + Material 3)  
**Package:** `com.reelmonster.app`  
**Phase:** Phase 2 Deliverable  
**Visual Reference:** 12 Stitch-Generated Screen Designs (`design_reference/`)  

---

## 1. Visual Identity & Design Principles

The REEL MONSTER design system balances playful companionship with sharp, glanceable digital wellness analytics. It uses a refreshing mint-green identity, clean white surfaces, subtle rounded card borders, and high-contrast typography.

### Core Visual Principles:
1. **Light & Refreshing:** Predominantly clean white cards against a gentle mint-tinted background (`#F6F8F3`).
2. **Glanceable Hierarchy:** Bold 24–28sp primary metrics paired with 11–13sp supportive labels and distinct icon badges.
3. **Tactile & Friendly:** 14–16dp rounded corners, subtle 1dp borders, and responsive press states.
4. **Accessible by Default:** All interactive touch targets meet or exceed 48×48dp, with explicit semantic labels and high contrast ratios (>4.5:1 for body text).

---

## 2. Color Palette & Tokens

| Token Name | Hex Code | Compose Value | Purpose |
| :--- | :---: | :--- | :--- |
| `MintPrimary` | `#76D7A5` | `Color(0xFF76D7A5)` | Canonical Stitch Mint; primary action buttons, active indicator dots, progress bars |
| `MintPrimaryBright` | `#38C88A` | `Color(0xFF38C88A)` | Bright mint accent for highlights and active icon tints |
| `MintPrimaryDark` | `#247A58` | `Color(0xFF247A58)` | Deep forest green; text on mint containers, button text, icon fills |
| `MintSurface` | `#EDFAF4` | `Color(0xFFEDFAF4)` | Soft mint tint; badge backgrounds, button containers, icon pill containers |
| `AppBackground` / `MintBackground` | `#F6F8F3` | `Color(0xFFF6F8F3)` | Scaffold background across all primary screens |
| `MintCardBorder` | `#D1FAE5` | `Color(0xFFD1FAE5)` | Subtle 1dp border stroke for white cards |
| `SurfaceWhite` | `#FFFFFF` | `Color(0xFFFFFFFF)` | Primary card and bottom bar surface |
| `PurpleAccent` | `#8B5CF6` | `Color(0xFF8B5CF6)` | Stage 3 Hungry accent & Instagram platform branding |
| `PurpleLight` | `#EDE9FE` | `Color(0xFFEDE9FE)` | Container background for Instagram & drift metrics |
| `PurpleDark` | `#5B21B6` | `Color(0xFF5B21B6)` | High-contrast text on purple containers |
| `OrangeAccent` | `#FF9800` | `Color(0xFFFF9800)` | Warm secondary accent & Stage 4 alert color |
| `OrangeLight` | `#FFF3E0` | `Color(0xFFFFF3E0)` | Container background for warnings & orange accents |
| `YouTubeRed` | `#EF4444` | `Color(0xFFEF4444)` | YouTube Shorts branding, delete/reset actions, binge spike alerts |
| `YouTubeRedLight` | `#FEE2E2` | `Color(0xFFFEE2E2)` | Container background for destructive actions and binge spike badges |
| `WarningAmber` | `#F59E0B` | `Color(0xFFF59E0B)` | Paused tracking indicator |
| `TextPrimary` | `#111827` | `Color(0xFF111827)` | Primary headlines, titles, and stat values |
| `TextSecondary` | `#4B5563` | `Color(0xFF4B5563)` | Subtitles, descriptions, and supporting information |
| `TextMuted` | `#9CA3AF` | `Color(0xFF9CA3AF)` | Inactive icons, timestamps, and secondary captions |

---

## 3. Typography Hierarchy

Defined in `core/design/Type.kt`:

| Style | Size | Weight | Line Height | Usage |
| :--- | :---: | :---: | :---: | :--- |
| `headlineLarge` | 28sp | Bold | 34sp | Large stat values, hero dashboard numbers |
| `headlineMedium` | 24sp | Bold | 28sp | Screen headers ("Hey there, scroller! 👋") |
| `titleLarge` | 18sp | SemiBold | 24sp | Section card titles, dialog titles |
| `titleMedium` | 15sp | SemiBold | 20sp | Sub-headers, button labels, list titles |
| `bodyLarge` | 15sp | Normal | 22sp | Narrative descriptions, dialog body text |
| `bodyMedium` | 13sp | Normal | 18sp | Secondary text, status descriptions |
| `labelLarge` | 14sp | Bold | 18sp | Primary button text, quick-log labels |
| `labelMedium` | 12sp | SemiBold | 16sp | Badges, card subtitles, platform ratios |
| `labelSmall` | 10sp | Medium | 14sp | Status pill tags, small timestamps |

---

## 4. Reusable Jetpack Compose Components

Located in `presentation/components/DesignSystemComponents.kt`:

### 1. `PrimaryButton`
- **Role:** High-emphasis primary action (e.g., "+1 Reel Watched", "Get Started").
- **Specs:** 14dp rounded corners, `MintPrimary` container (`#76D7A5`), `MintPrimaryDark` text (`#247A58`), min-height 48dp, supports loading spinner and leading/trailing icons.
- **Accessibility:** Explicit `Role.Button` semantic and `contentDescription`.

### 2. `SecondaryButton`
- **Role:** Medium-emphasis secondary action (e.g., "+1 YouTube Short", "Full Statistics").
- **Specs:** 14dp rounded corners, `MintSurface` container (`#EDFAF4`), 1dp `MintCardBorder` stroke, `MintPrimaryDark` text.

### 3. `AppCard`
- **Role:** Foundational container for all dashboard sections, metrics, and list items.
- **Specs:** White surface (`#FFFFFF`), 16dp rounded corners, 1dp `MintCardBorder` stroke, subtle 1dp default elevation.

### 4. `StatCard`
- **Role:** Analytics metrics block (e.g., "Today's Reels", "Estimated Drift").
- **Specs:** Shows title, prominent 24sp bold number, supportive caption, and an icon inside a 32×32dp rounded background badge.

### 5. `TrackingStatusBadge`
- **Role:** Status pill showing tracking mode and live state.
- **Specs:** 12dp rounded pill with a 7dp colored status dot (`MintPrimary` for active, `WarningAmber` for paused) and bold tracking mode caption.

### 6. `SectionHeader`
- **Role:** Card or list section header.
- **Specs:** 18sp bold title, optional subtitle, and optional interactive action text ("Full Stats", "View All").

### 7. `BottomNavigationBar`
- **Role:** Main persistent bottom navigation bar.
- **Specs:** 4 destinations (`Home`, `Stats`, `History`, `Settings`), active mint pill indicator, inactive muted icons, accessible labels, supports `rememberSaveable` state retention.

### 8. `SettingsRow`
- **Role:** Settings and preferences row item.
- **Specs:** Leading icon container, title, description, and flexible trailing slot (Switch, Chevron, or Text).

### 9. `EmptyState`
- **Role:** Empty state fallback when no data exists (e.g. 0 reels logged, no 7-day history).
- **Specs:** Shows animated or static Munchie mascot or icon, bold title, empathetic explanation, and optional action button.

### 10. `ConfirmationDialog`
- **Role:** Destructive or critical confirmation modal (e.g. data wipe, reset).
- **Specs:** Material 3 `AlertDialog` with red or mint confirm button, secondary cancel button, 16dp rounded corners.

### 11. `LoadingState`
- **Role:** Centered loading indicator during async operations.
- **Specs:** Mint circular progress spinner (36dp) with readable status message.

### 12. `ErrorState`
- **Role:** Graceful error presentation.
- **Specs:** Light red icon badge, bold error title, description, and secondary "Retry" button.

---

## 5. Verification & Component Test Coverage

All components were verified for compilation, layout constraints, and accessibility in the automated test suite:
- `MunchieMascotEngineTest` (4 tests)
- `DashboardAndNavigationTest` (3 tests)
- `ReelRepositoryTest` (4 tests)
- Total test suite: **26 passing unit tests**.
