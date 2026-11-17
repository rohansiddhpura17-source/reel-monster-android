package com.reelmonster.app.presentation.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reelmonster.app.core.design.MintPrimaryDark
import com.reelmonster.app.core.design.MintSurface
import com.reelmonster.app.core.design.TextMuted
import com.reelmonster.app.core.design.TextPrimary
import com.reelmonster.app.core.design.TextSecondary
import com.reelmonster.app.core.design.YouTubeRed
import com.reelmonster.app.data.backup.BackupExporter
import com.reelmonster.app.data.backup.BackupImporter
import com.reelmonster.app.data.backup.ImportResult
import com.reelmonster.app.data.backup.SqliteBackupTableStore
import com.reelmonster.app.data.repository.ReelRepository
import com.reelmonster.app.domain.model.backup.BackupPayload
import com.reelmonster.app.domain.model.backup.BackupSchema
import com.reelmonster.app.domain.model.backup.BackupValidation
import com.reelmonster.app.domain.model.backup.ConflictPolicy
import com.reelmonster.app.domain.model.backup.RestoreJournal
import com.reelmonster.app.domain.model.backup.RestoreMode
import com.reelmonster.app.domain.model.backup.RestorePlan
import com.reelmonster.app.domain.model.backup.RestorePlanner
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 15 — Backup & restore section for Settings (proposal §8).
 *
 * Offline only: the user picks a file through the Android Storage
 * Access Framework. Export writes one JSON backup (manifest +
 * SHA-256). Import validates the file fully before any database
 * change. Merge is the default and conflicts are resolved explicitly
 * (keep mine / use backup / cancel). Full restore first creates and
 * verifies a backup of the current data, then replaces it in one
 * transaction. An interrupted restore is reported when Settings opens.
 */
@Composable
fun BackupRestoreSection(
    repository: ReelRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SqliteBackupTableStore(repository.dbHelper) }
    val journal = remember {
        RestoreJournal(File(context.filesDir, "phase15_restore_journal.json"))
    }
    val importer = remember { BackupImporter(store, journal) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var pendingMerge by remember { mutableStateOf<Pair<BackupPayload, RestorePlan>?>(null) }
    var pendingImportMode by remember { mutableStateOf<RestoreMode?>(null) }
    var showFullRestoreConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val interrupted = withContext(Dispatchers.IO) { journal.recoverOnStartup() }
        if (interrupted != null) {
            statusMessage = "A previous restore was interrupted before it finished. " +
                "Your data was left unchanged."
        }
    }

    fun reportResult(result: ImportResult) {
        statusMessage = when (result) {
            is ImportResult.Success -> when (result.mode) {
                RestoreMode.MERGE -> "Restore complete: ${result.inserted} added, " +
                    "${result.updated} updated, ${result.alreadyPresent} already present."
                RestoreMode.FULL -> "Full restore complete: ${result.inserted} records restored, " +
                    "${result.deleted} old records replaced."
            }
            is ImportResult.Failure -> "Restore failed: ${result.error}"
        }
    }

    fun applyMerge(payload: BackupPayload, plan: RestorePlan) {
        scope.launch {
            isBusy = true
            val result = withContext(Dispatchers.IO) { importer.importMerge(payload, plan) }
            isBusy = false
            reportResult(result)
        }
    }

    fun applyFull(payload: BackupPayload, plan: RestorePlan) {
        scope.launch {
            isBusy = true
            val result = withContext(Dispatchers.IO) {
                val preBackup = BackupExporter(store).export()
                importer.importFull(payload, plan, preBackup)
            }
            isBusy = false
            reportResult(result)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupExporter(store).export()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open the chosen file.")
                    json.length
                }
            }
            isBusy = false
            statusMessage = outcome.fold(
                onSuccess = { bytes -> "Backup saved ($bytes bytes). Keep the file somewhere safe." },
                onFailure = { e -> "Backup failed: ${e.message ?: "unknown error"}" }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val mode = pendingImportMode ?: RestoreMode.MERGE
        pendingImportMode = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("Could not read the chosen file.")
                    importer.validateAndPlan(json, mode, ConflictPolicy.KEEP_EXISTING)
                }
            }
            isBusy = false
            outcome.fold(
                onSuccess = { (validation, plan) ->
                    if (validation !is BackupValidation.Valid || plan == null) {
                        val errors = (validation as? BackupValidation.Invalid)?.errors
                            ?: listOf("Backup file could not be validated.")
                        statusMessage = "This backup cannot be restored:\n" + errors.joinToString("\n")
                    } else if (mode == RestoreMode.FULL) {
                        applyFull(validation.payload, plan)
                    } else if (plan.totalConflicts > 0) {
                        pendingMerge = validation.payload to plan
                    } else {
                        applyMerge(validation.payload, plan)
                    }
                },
                onFailure = { e ->
                    statusMessage = "Restore failed: ${e.message ?: "unknown error"}"
                }
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Backup, contentDescription = null, tint = MintPrimaryDark)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Offline JSON backup of all your tracking, goals, focus, and progression data. " +
                    "Backups are validated before anything is restored, and a restore never silently overwrites your data.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    exportLauncher.launch("reel-monster-backup-${LocalDate.now()}.json")
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintSurface,
                    contentColor = MintPrimaryDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Create Backup", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    pendingImportMode = RestoreMode.MERGE
                    importLauncher.launch(arrayOf("application/json"))
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Restore From Backup (Merge)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showFullRestoreConfirm = true },
                enabled = !isBusy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = YouTubeRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Restore (Replace All Data)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            statusMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, fontSize = 12.sp, color = TextMuted)
            }
        }
    }

    // Conflict resolution: explicit choice, never silent.
    pendingMerge?.let { (payload, plan) ->
        Dialog(onDismissRequest = { pendingMerge = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${plan.totalConflicts} conflicting record(s)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "These records exist both in the backup and on this device, with different content. " +
                            "Choose how to resolve them. New records (${plan.totalToInsert}) will be added either way.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            pendingMerge = null
                            scope.launch {
                                val useBackupPlan = withContext(Dispatchers.IO) {
                                    val existing = BackupSchema.TABLES.associate { table ->
                                        table.name to store.existingRows(table)
                                    }
                                    RestorePlanner.plan(
                                        payload, existing, RestoreMode.MERGE, ConflictPolicy.USE_BACKUP
                                    )
                                }
                                applyMerge(payload, useBackupPlan)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Use Backup (overwrite conflicts)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            pendingMerge = null
                            applyMerge(payload, plan)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Keep Mine (skip conflicts)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pendingMerge = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Cancel Restore")
                    }
                }
            }
        }
    }

    // Full restore confirmation: a verified pre-restore backup is made first.
    if (showFullRestoreConfirm) {
        com.reelmonster.app.presentation.components.ConfirmationDialog(
            title = "Replace all data?",
            message = "This deletes all current tracking, goals, focus, and progression data and replaces it with the backup you choose. " +
                "A backup of your current data is created and verified first, so you can recover.",
            confirmText = "Choose Backup File",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showFullRestoreConfirm = false
                pendingImportMode = RestoreMode.FULL
                importLauncher.launch(arrayOf("application/json"))
            },
            onDismiss = { showFullRestoreConfirm = false }
        )
    }
}
