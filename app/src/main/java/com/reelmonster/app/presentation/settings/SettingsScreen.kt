package com.reelmonster.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.WarningAmber
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.core.design.YouTubeRedLight
import com.reelmonster.app.core.util.OverlayPermissionHelper
import com.reelmonster.app.data.preferences.UserPreferencesManager
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.MunchieStage
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.ReactionStyle
import com.reelmonster.app.domain.model.TrackingMode
import com.reelmonster.app.presentation.backup.BackupRestoreSection
import com.reelmonster.app.presentation.components.AccessibilityDisclosureDialog
import com.reelmonster.app.presentation.components.MunchieMascot
import com.reelmonster.app.presentation.components.OverlayPermissionDialog
import com.reelmonster.app.presentation.components.ReelMonsterTopBar
import com.reelmonster.app.presentation.components.openAccessibilitySettings
import com.reelmonster.app.service.overlay.FloatingMonsterOverlayService
import com.reelmonster.app.service.tracking.ReelAccessibilityService

@Composable
fun SettingsScreen(
    repository: ReelRepository,
    modifier: Modifier = Modifier
) {
    val preferences = repository.preferencesManager
    val isGlobalTrackingEnabled by repository.isGlobalTrackingEnabled.collectAsState()
    val remainingDailyResets by repository.remainingDailyResets.collectAsState()
    val trackingMode by preferences.trackingMode.collectAsState()
    val isOverlayEnabled by preferences.isOverlayEnabled.collectAsState()

    val soundFx by preferences.isSoundFxEnabled.collectAsState()
    val haptic by preferences.isHapticEnabled.collectAsState()
    val edgeSnapping by preferences.isEdgeSnappingEnabled.collectAsState()
    val monsterTheme by preferences.monsterTheme.collectAsState()
    val reactionStyle by preferences.reactionStyle.collectAsState()
    val isMascotAnimationEnabled by preferences.isMascotAnimationEnabled.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var showDailyResetDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var pendingOverlayStart by remember { mutableStateOf(false) }
    var pendingAccessibilityModeSwitch by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(ReelAccessibilityService.isAccessibilityPermissionGranted(context))
    }
    val isAccessibilityRunning by ReelAccessibilityService.isServiceRunning.collectAsState()

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
                val nowHasAccessibility = ReelAccessibilityService.isAccessibilityPermissionGranted(context)
                hasOverlayPermission = nowHasOverlay
                hasAccessibilityPermission = nowHasAccessibility

                if (pendingOverlayStart) {
                    pendingOverlayStart = false
                    if (nowHasOverlay) {
                        preferences.setOverlayEnabled(true)
                        if (!FloatingMonsterOverlayService.isRunning) {
                            FloatingMonsterOverlayService.startService(context)
                        }
                    } else {
                        preferences.setOverlayEnabled(false)
                    }
                }

                if (pendingAccessibilityModeSwitch) {
                    pendingAccessibilityModeSwitch = false
                    if (nowHasAccessibility) {
                        preferences.setTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        ReelMonsterTopBar(title = "Settings")

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "• SYSTEM PREFERENCES • LOCAL-FIRST DESIGN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Settings & Privacy",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Fine-tune your floating monster, tracking modes, and local storage.",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // GLOBAL TRACKING MASTER CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isGlobalTrackingEnabled) MintSurface else Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isGlobalTrackingEnabled) MintPrimary else Color(0xFF6B7280)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Global Tracking Switch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = if (isGlobalTrackingEnabled) "Tracking active" else "Tracking completely disabled",
                                    fontSize = 11.sp,
                                    color = if (isGlobalTrackingEnabled) MintPrimaryDark else WarningAmber
                                )
                            }
                        }

                        Switch(
                            checked = isGlobalTrackingEnabled,
                            onCheckedChange = { preferences.setGlobalTrackingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MintPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD1D5DB)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Master control that stops all Reel and Short detection, viewing session trackers, and floating counter increments when turned OFF.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // TRACKING ENGINE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = MintPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Tracking Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Current Mode: ${trackingMode.label}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (trackingMode == TrackingMode.LEVEL_1_MANUAL) {
                            "Tap Munchie on-screen whenever you complete a reel. Minimal battery usage & zero accessibility permissions required."
                        } else {
                            "Automatic detection via AccessibilityService events. 100% offline layout inspection for Instagram Reels and YouTube Shorts."
                        },
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (trackingMode == TrackingMode.LEVEL_2_AUTOMATIC) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MintSurface)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility Service Status",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark
                                )
                                Text(
                                    text = if (!hasAccessibilityPermission) {
                                        "Permission Required • Tap Configure"
                                    } else if (isAccessibilityRunning) {
                                        "Connected & Listening (Instagram & YouTube)"
                                    } else {
                                        "Permission Granted • Service Standing By"
                                    },
                                    fontSize = 11.sp,
                                    color = if (!hasAccessibilityPermission) WarningAmber else TextSecondary
                                )
                            }
                            if (!hasAccessibilityPermission) {
                                Button(
                                    onClick = { showAccessibilityDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintPrimary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(text = "Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (trackingMode == TrackingMode.LEVEL_1_MANUAL) {
                                    if (!hasAccessibilityPermission) {
                                        pendingAccessibilityModeSwitch = true
                                        showAccessibilityDialog = true
                                    } else {
                                        preferences.setTrackingMode(TrackingMode.LEVEL_2_AUTOMATIC)
                                    }
                                } else {
                                    preferences.setTrackingMode(TrackingMode.LEVEL_1_MANUAL)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MintSurface,
                                contentColor = MintPrimaryDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (trackingMode == TrackingMode.LEVEL_1_MANUAL) "Switch to Level 2 (Auto)" else "Switch to Level 1 (Manual)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }

                        if (trackingMode == TrackingMode.LEVEL_2_AUTOMATIC) {
                            Button(
                                onClick = { showAccessibilityDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintSurface,
                                    contentColor = MintPrimaryDark
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Details",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MONSTER CUSTOMIZATION & STYLE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MintSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = MintPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Munchie Customization",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Personalize colors, reactions & animation",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Live preview of Munchie with current selected theme & animation
                        MunchieMascot(
                            stage = MunchieStage.BABY,
                            size = 46.dp,
                            theme = monsterTheme,
                            isAnimated = isMascotAnimationEnabled,
                            reactionStyle = reactionStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "THEME & COLOR VARIANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 Color Theme Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MunchieTheme.entries.forEach { theme ->
                            val isSelected = (theme == monsterTheme)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) theme.secondaryColor else MintSurface.copy(alpha = 0.5f))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) theme.primaryColor else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { preferences.setMonsterTheme(theme) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(theme.primaryColor)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = theme.displayName.replace(" Munchie", ""),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TextPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mascot Animation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mascot Animations",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enable idle float and reactive movement",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = isMascotAnimationEnabled,
                            onCheckedChange = { preferences.setMascotAnimationEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MintPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "INTERACTION REACTION STYLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Reaction style selector rows
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReactionStyle.entries.forEach { style ->
                            val isSelected = (style == reactionStyle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MintSurface else Color(0xFFF9FAFB))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) MintPrimary else Color(0xFFE5E7EB),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { preferences.setReactionStyle(style) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MintPrimaryDark else TextPrimary
                                    )
                                    Text(
                                        text = style.description,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontWeight = FontWeight.Bold,
                                        color = MintPrimaryDark,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // FLOATING COMPANION & WINDOWMANAGER CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Layers, contentDescription = null, tint = MintPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Floating Companion (Overlay)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable Floating Munchie", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                text = if (hasOverlayPermission) "Floats above Instagram and YouTube" else "Permission required in Android settings",
                                fontSize = 11.sp,
                                color = if (hasOverlayPermission) TextMuted else WarningAmber
                            )
                        }
                        Switch(
                            checked = isOverlayEnabled && hasOverlayPermission,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (hasOverlayPermission) {
                                        preferences.setOverlayEnabled(true)
                                        FloatingMonsterOverlayService.startService(context)
                                    } else {
                                        pendingOverlayStart = true
                                        showPermissionDialog = true
                                    }
                                } else {
                                    preferences.setOverlayEnabled(false)
                                    FloatingMonsterOverlayService.stopService(context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MintPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Overlay Permission", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(
                                text = if (hasOverlayPermission) "Status: Granted ✅" else "Status: Missing ⚠️",
                                fontSize = 11.sp,
                                color = if (hasOverlayPermission) MintPrimaryDark else WarningAmber
                            )
                        }

                        if (!hasOverlayPermission) {
                            Button(
                                onClick = {
                                    pendingOverlayStart = true
                                    showPermissionDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // FEEDBACK & SENSORY CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Feedback & Sensory", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "Delightful physical crunch & vibrations (opt-in)", fontSize = 12.sp, color = TextMuted)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Tactile Munch Haptic", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(text = "Gentle buzz when you tap +1 or monster evolves (default off)", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = haptic,
                            onCheckedChange = { preferences.setHapticEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MintPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Chewing Sound FX", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(text = "Cute crunch sound on every reel logged (default off)", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = soundFx,
                            onCheckedChange = { preferences.setSoundFxEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MintPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Magnetic Edge Snapping", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(text = "Monster docks smoothly to left/right screen edges", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = edgeSnapping,
                            onCheckedChange = { preferences.setEdgeSnappingEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MintPrimary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DAILY RESET CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Daily Reset Control", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reset today's Reel counts and actual viewing times. Up to 5 resets allowed per day (renews at local midnight). Historical days remain saved.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$remainingDailyResets of ${UserPreferencesManager.MAX_DAILY_RESETS} resets remaining today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remainingDailyResets > 0) MintPrimaryDark else WarningAmber
                        )
                        Button(
                            onClick = { showDailyResetDialog = true },
                            enabled = remainingDailyResets > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (remainingDailyResets > 0) Color(0xFFFEF3C7) else Color(0xFFF3F4F6),
                                contentColor = if (remainingDailyResets > 0) Color(0xFF92400E) else Color(0xFF9CA3AF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = "Restart Today", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LOCAL DATABASE STATUS CARD
            // Phase 15: Offline backup & restore (proposal §8)
            BackupRestoreSection(repository = repository)

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MintSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = MintPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Local SQLite Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Zero internet. 100% On-device SQLite storage. Session logs and monster stage survive device reboots.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YouTubeRedLight,
                            contentColor = YouTubeRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Wipe All History & Reset Monster", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (showResetDialog) {
                        com.reelmonster.app.presentation.components.ConfirmationDialog(
                            title = "Reset All Tracking Data?",
                            message = "This will erase all local SQLite session logs, 7-day velocity records, and reset Munchie back to Baby stage. This action cannot be undone.",
                            confirmText = "Wipe Everything",
                            dismissText = "Keep Data",
                            isDestructive = true,
                            onConfirm = {
                                showResetDialog = false
                                repository.clearAllData()
                            },
                            onDismiss = { showResetDialog = false }
                        )
                    }

                    if (showDailyResetDialog) {
                        com.reelmonster.app.presentation.components.ConfirmationDialog(
                            title = "Restart today's tracking?",
                            message = "Today's count and viewing-time totals will reset. Previous days' history will remain saved.",
                            confirmText = "Restart Today",
                            dismissText = "Cancel",
                            isDestructive = false,
                            onConfirm = {
                                showDailyResetDialog = false
                                repository.performDailyReset()
                            },
                            onDismiss = { showDailyResetDialog = false }
                        )
                    }
                }
            }

            if (showPermissionDialog) {
                OverlayPermissionDialog(
                    onDismissRequest = {
                        pendingOverlayStart = false
                        showPermissionDialog = false
                    },
                    onGrantPermission = {
                        showPermissionDialog = false
                        OverlayPermissionHelper.requestOverlayPermission(context)
                    }
                )
            }

            if (showAccessibilityDialog) {
                AccessibilityDisclosureDialog(
                    onDismissRequest = {
                        pendingAccessibilityModeSwitch = false
                        showAccessibilityDialog = false
                    },
                    onOpenSettings = {
                        showAccessibilityDialog = false
                        openAccessibilitySettings(context)
                    },
                    onFallbackToManual = {
                        pendingAccessibilityModeSwitch = false
                        showAccessibilityDialog = false
                        preferences.setTrackingMode(TrackingMode.LEVEL_1_MANUAL)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
