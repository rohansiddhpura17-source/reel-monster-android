package com.reelmonster.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelmonster.app.core.design.MintCardBorder
import com.reelmonster.app.core.design.MintPrimary
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary

/**
 * OverlayPermissionDialog
 *
 * Explains to the user why the "Display over other apps" permission is required
 * to show the Munchie floating companion, emphasizing 100% offline privacy,
 * and launches Android settings when confirmed.
 */
@Composable
fun OverlayPermissionDialog(
    onDismissRequest: () -> Unit,
    onGrantPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MintSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MintPrimaryDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Floating Companion",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Munchie floats as a cute, draggable companion over Instagram Reels and YouTube Shorts so you can track your feed time with a single tap.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = MintSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MintPrimaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "100% Offline & Private: Reel Monster never reads video content and has zero internet access.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MintPrimaryDark,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Android requires you to enable 'Display over other apps' in Settings to show Munchie above other apps.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Open Settings",
                onClick = onGrantPermission
            )
        },
        dismissButton = {
            SecondaryButton(
                text = "Not Now",
                onClick = onDismissRequest
            )
        }
    )
}
