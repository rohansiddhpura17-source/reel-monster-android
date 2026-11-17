package com.reelmonster.app.presentation.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintBackground
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.domain.model.goal.DailyGoal
import com.reelmonster.app.domain.model.goal.GoalScope
import com.reelmonster.app.domain.model.goal.GoalType
import com.reelmonster.app.presentation.components.PrimaryButton
import com.reelmonster.app.presentation.components.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalConfigurationSheet(
    activeGoals: List<DailyGoal>,
    onSaveGoal: (GoalType, GoalScope, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val countCombined = activeGoals.firstOrNull { it.type == GoalType.REEL_COUNT && it.scope == GoalScope.COMBINED }
    val timeCombined = activeGoals.firstOrNull { it.type == GoalType.VIEWING_TIME_MINUTES && it.scope == GoalScope.COMBINED }

    var targetReels by remember(activeGoals) { mutableIntStateOf(countCombined?.targetValue ?: 30) }
    var targetMins by remember(activeGoals) { mutableIntStateOf(timeCombined?.targetValue ?: 30) }
    var reelsEnabled by remember(activeGoals) { mutableStateOf(countCombined?.isEnabled ?: true) }
    var minsEnabled by remember(activeGoals) { mutableStateOf(timeCombined?.isEnabled ?: true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MintBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Configure Daily Goals",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Set your personal boundary limits for mindful scrolling. Reaching these boundaries is an intentional reminder, not a penalty.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // 1. Reel Count Limit Card
            GoalSettingItem(
                title = "Maximum Daily Reels",
                subtitle = "Combined Instagram Reels + YouTube Shorts",
                value = targetReels,
                unit = "reels",
                step = 5,
                min = 5,
                max = 150,
                isEnabled = reelsEnabled,
                onToggle = { reelsEnabled = it },
                onValueChange = { targetReels = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Viewing Time Limit Card
            GoalSettingItem(
                title = "Maximum Viewing Time",
                subtitle = "Total observed duration in feed",
                value = targetMins,
                unit = "mins",
                step = 5,
                min = 5,
                max = 180,
                isEnabled = minsEnabled,
                onToggle = { minsEnabled = it },
                onValueChange = { targetMins = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Save Goals",
                    onClick = {
                        onSaveGoal(GoalType.REEL_COUNT, GoalScope.COMBINED, targetReels, reelsEnabled)
                        onSaveGoal(GoalType.VIEWING_TIME_MINUTES, GoalScope.COMBINED, targetMins, minsEnabled)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GoalSettingItem(
    title: String,
    subtitle: String,
    value: Int,
    unit: String,
    step: Int,
    min: Int,
    max: Int,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(!isEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) TextPrimary else TextSecondary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MintPrimary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD1D5DB)
                    )
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MintSurface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onValueChange((value - step).coerceAtLeast(min)) },
                        enabled = value > min
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = if (value > min) MintPrimaryDark else Color.LightGray
                        )
                    }

                    Text(
                        text = "$value $unit",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimaryDark
                    )

                    IconButton(
                        onClick = { onValueChange((value + step).coerceAtMost(max)) },
                        enabled = value < max
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (value < max) MintPrimaryDark else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}
