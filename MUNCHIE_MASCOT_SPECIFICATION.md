# MUNCHIE THE REEL MONSTER — MASCOT SPECIFICATION

**Product:** REEL MONSTER  
**Character Name:** Munchie  
**Platform:** Native Android (Canvas Vector Component in Jetpack Compose)  
**Package:** `com.reelmonster.app`  
**Phase:** Phase 2 Deliverable  

---

## 1. Character Concept & Persona

**Munchie** is a lovable, chubby, mint-green monster companion who feeds exclusively on short-form video dopamine (Instagram Reels and YouTube Shorts). 

Rather than shaming the user with harsh screen-time limits, Munchie acts as an expressive, funny mirror:
- When the user starts their day, Munchie is an innocent, tiny baby with wide sparkling eyes.
- As the user scrolls, Munchie "chomps" each video, physically expanding across 5 growth stages.
- By the time the user falls into an evening video binge, Munchie has expanded into an overfed **Megachonk** who slouchingly warns them that their homework or bedtime has disappeared.

---

## 2. Five Visual Evolution Stages

| Stage | Name | Threshold | Body Color | Eye Expression | Mouth & Accessories |
| :---: | :--- | :---: | :---: | :--- | :--- |
| **1** | **Baby Munchie** | 0–5 Reels | `#86EFAC` (Gentle Mint) | Wide, oversized kawaii eyes with double sparkles | Innocent curved smile; tiny horns |
| **2** | **Curious Munchie** | 6–15 Reels | `#76D7A5` (Stitch Mint) | Alert, inquisitive gaze with prominent rosy blush | Slightly wider smile; medium horns |
| **3** | **Hungry Munchie** | 16–30 Reels | `#38C88A` (Vivid Mint) | Focused, hungry eyes anticipating snacks | Open chomping mouth (`#EF4444`) with sharp white tooth; donut accessory |
| **4** | **Stuffed Munchie** | 31–50 Reels | `#247A58` (Deep Green) | Sleepy half-lidded eyes with lowered upper lids | Contented full-belly smile; round expanded belly |
| **5** | **Megachonk Overfed** | 51+ Reels | `#0F3E2E` (Dark Green) | Heavy, drooping eyelids; dopamine overload | Wide sleepy smile; maximum body width; struggling stubby feet |

---

## 3. Geometric Proportions & Vector Anatomy

Munchie is rendered via Android Canvas draw calls (`drawOval`, `drawCircle`, `drawArc`, `drawPath`) in `presentation/components/MunchieMascot.kt`.

### Proportional Scale Factors:
```kotlin
val scaleX = when (stage) {
    MunchieStage.BABY -> 0.86f
    MunchieStage.CURIOUS -> 0.95f
    MunchieStage.HUNGRY -> 1.05f
    MunchieStage.STUFFED -> 1.18f
    MunchieStage.MEGACHONK -> 1.28f
}
val scaleY = when (stage) {
    MunchieStage.BABY -> 0.90f
    MunchieStage.CURIOUS -> 0.96f
    MunchieStage.HUNGRY -> 1.00f
    MunchieStage.STUFFED -> 0.96f
    MunchieStage.MEGACHONK -> 0.92f
}
```

### Anatomical Layers (Rendered Back to Front):
1. **Antennas / Horns:** Left and right circular horns with 4.5f dark stroke outlines. Horn size scales proportionally, appearing compact against Megachonk's massive frame.
2. **Stubby Feet:** Two grounded ovals placed at the base (`centerY + radiusY * 0.85f`), providing balance.
3. **Main Body (Chonky Rounded Ellipse):** Scaled horizontal and vertical radii based on the current stage scale, outlined in `#0F3E2E`.
4. **Belly Highlight:** Semi-transparent white oval (`alpha = 0.28f`) highlighting Munchie's round stomach.
5. **Eyes & Eyelids:**
   - Wide kawaii circular eyes with dual light-sparkles for Stages 1–3.
   - Half-lidded drooping arcs drawn over the pupils for Stages 4–5 to convey extreme fullness and sleepiness.
6. **Blush Cheeks:** Soft pink ovals (`#F472B6`, `alpha = 0.45f`) under each eye.
7. **Mouth & Teeth:**
   - Smooth curved quadratic bezier stroke for Stages 1, 2, 4, and 5.
   - Filled red chomp mouth with an inverted triangular white tooth for Stage 3 (Hungry).

---

## 4. Funny Message Selection Engine

Implemented in `domain/model/MunchieMascotEngine.kt`.

### Stage Quotes:
- **Baby Munchie (0–5):**
  - *"Just one more?"*
  - *"Just one more? I just opened my eyes! ✨"*
  - *"A tiny reel for a tiny monster! 🌱"*
- **Curious Munchie (6–15):**
  - *"I can smell another Short!"*
  - *"I can smell another Short! What are we watching next? 👀"*
  - *"Ooh, that creator has moves! 🔍"*
- **Hungry Munchie (16–30):**
  - *"Feed me another Reel!"*
  - *"Your thumb is working overtime!"*
  - *"Feed me another Reel! Your thumb is working overtime! 🍩📱"*
  - *"Chomp chomp! Keep the dopamine flowing!"*
- **Stuffed Munchie (31–50):**
  - *"Burp! Your evening disappeared."*
  - *"Homework called. It misses you."*
  - *"Burp! Your evening disappeared... homework called, it misses you! 🛌"*
  - *"My tummy is expanding faster than your screen time!"*
- **Megachonk Overfed (51+):**
  - *"I cannot move. You watched 50+ videos. Put the phone down or I am calling your mom! 🚨"*
  - *"Emergency chonker status! Go touch grass! 🌳"*
  - *"Dopamine tank overflow! Your sleep schedule is crying! 😴"*

---

## 5. Performance & Accessibility Standards

1. **Lightweight Vector Rendering:** Zero raster bitmap dependencies or heavy Lottie/GIF assets. Rendered directly on the GPU using Compose Canvas.
2. **Performance-Tuned Animation:** Single infinite transition modulating vertical offset by ±3dp using `FastOutSlowInEasing` (1200ms cycle). Zero frame drops.
3. **Power Saving Mode:** When `isAnimated = false` (e.g. battery saver mode or low power preference), animation loops are skipped entirely.
4. **Accessibility Semantics:**
   ```kotlin
   Modifier.semantics {
       contentDescription = "Munchie mascot, Stage ${stage.level}: ${stage.stageName}, ${stage.appetiteLabel}"
   }
   ```
   Screen readers announce Munchie's exact stage and emotional state accurately.
