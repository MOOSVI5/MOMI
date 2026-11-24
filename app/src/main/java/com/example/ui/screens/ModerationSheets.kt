package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContentReport
import com.example.data.model.ModerationScopes
import com.example.data.model.MomiRoles
import com.example.data.model.UserProfile
import com.example.ui.components.LumaAvatar

data class ReportDraftTarget(
    val targetType: String,
    val targetId: String,
    val targetAuthorUid: String,
    val targetAuthorUsername: String,
    val previewText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContentSheet(
    target: ReportDraftTarget,
    onDismiss: () -> Unit,
    onSubmitReport: (category: String, reasonDetail: String) -> Unit
) {
    val defaultCategory = if (target.targetType == "PRODUCT") {
        ModerationScopes.MARKETPLACE_SAFETY
    } else {
        ModerationScopes.VIOLENCE_GRAPHIC
    }
    var selectedCategory by remember(target.targetId) { mutableStateOf(defaultCategory) }
    var detailNote by remember(target.targetId) { mutableStateOf("") }

    val availableCategories = remember(target.targetType) {
        if (target.targetType == "PRODUCT") {
            listOf(
                ModerationScopes.MARKETPLACE_SAFETY,
                ModerationScopes.VIOLENCE_GRAPHIC,
                ModerationScopes.SEXUAL_NUDITY,
                ModerationScopes.HARASSMENT_SPAM
            )
        } else {
            listOf(
                ModerationScopes.VIOLENCE_GRAPHIC,
                ModerationScopes.SEXUAL_NUDITY,
                ModerationScopes.HARASSMENT_SPAM
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Report ${target.targetType.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Reports go directly to Momi's Safety Team enrolled for that category.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            availableCategories.forEach { category ->
                val selected = selectedCategory == category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("report_category_$category"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { selectedCategory = category }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ModerationScopes.labelOf(category),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ModerationScopes.descriptionOf(category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = detailNote,
                onValueChange = { detailNote = it },
                label = { Text("Details (optional)") },
                placeholder = { Text("Describe what violates community safety...") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_detail_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        onSubmitReport(
                            selectedCategory,
                            detailNote.trim().ifBlank { "Flagged as ${ModerationScopes.labelOf(selectedCategory)}" }
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("submit_report_button")
                ) {
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamRolesManagementSheet(
    currentUserId: String,
    allUsers: List<UserProfile>,
    onAssignRoleAndScopes: (targetUid: String, role: String, scopes: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }

    val members = remember(allUsers, currentUserId, searchQuery) {
        val q = searchQuery.trim()
        allUsers
            .filter { it.userId != currentUserId }
            .filter {
                q.isEmpty() ||
                    it.username.contains(q, ignoreCase = true) ||
                    it.displayName.contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<UserProfile> { it.role != MomiRoles.USER }
                    .thenBy { it.username }
            )
    }

    editingUser?.let { target ->
        EditMemberRoleSheet(
            member = target,
            onDismiss = { editingUser = null },
            onSave = { nextRole, nextScopes ->
                onAssignRoleAndScopes(target.userId, nextRole, nextScopes)
                editingUser = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Team & Scoped Roles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Appoint signed-up users as Moderators or Editors and enroll them into specific safety work only (never full Admin authority).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search signed-up users...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("team_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (members.isEmpty()) {
                Text(
                    text = "No matching signed-up users found yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(members, key = { "team_${it.userId}" }) { member ->
                        val isStaff = member.role in setOf(MomiRoles.MODERATOR, MomiRoles.EDITOR)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LumaAvatar(
                                    username = member.username,
                                    avatarKey = member.avatarKey,
                                    avatarData = member.avatarData,
                                    avatarUrl = member.avatarUrl,
                                    size = 42.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${member.username} · ${MomiRoles.labelOf(member.role)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isStaff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isStaff) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (member.moderationScopes.isNotEmpty()) {
                                        Text(
                                            text = member.moderationScopes.joinToString(" · ") { ModerationScopes.labelOf(it) },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { editingUser = member },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("manage_role_${member.userId}")
                                ) {
                                    Text(
                                        text = if (isStaff) "Edit Role" else "Assign",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMemberRoleSheet(
    member: UserProfile,
    onDismiss: () -> Unit,
    onSave: (role: String, scopes: List<String>) -> Unit
) {
    var selectedRole by remember(member.userId) {
        mutableStateOf(
            member.role.uppercase().takeIf {
                it in setOf(MomiRoles.USER, MomiRoles.MODERATOR, MomiRoles.EDITOR)
            } ?: MomiRoles.USER
        )
    }
    var selectedScopes by remember(member.userId) {
        mutableStateOf(member.moderationScopes.toSet())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Assign Role: @${member.username}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose their role and enroll them into exact surveillance or editorial scopes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    MomiRoles.USER to "Member",
                    MomiRoles.MODERATOR to "Moderator",
                    MomiRoles.EDITOR to "Editor"
                ).forEach { (roleKey, label) ->
                    val active = selectedRole == roleKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedRole = roleKey
                                if (roleKey == MomiRoles.USER) {
                                    selectedScopes = emptySet()
                                } else if (roleKey == MomiRoles.EDITOR && selectedScopes.isEmpty()) {
                                    selectedScopes = setOf(ModerationScopes.EDITORIAL_CURATION)
                                } else if (roleKey == MomiRoles.MODERATOR && selectedScopes.isEmpty()) {
                                    selectedScopes = setOf(ModerationScopes.VIOLENCE_GRAPHIC)
                                }
                            }
                            .padding(vertical = 12.dp)
                            .testTag("pick_role_$roleKey"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (selectedRole != MomiRoles.USER) {
                Text(
                    text = "Enrolled Work & Surveillance Scopes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val scopeChoices = if (selectedRole == MomiRoles.EDITOR) {
                    ModerationScopes.ALL_SCOPES
                } else {
                    ModerationScopes.SURVEILLANCE_SCOPES
                }
                scopeChoices.forEach { scope ->
                    val checked = selectedScopes.contains(scope)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable {
                                selectedScopes = if (checked) selectedScopes - scope else selectedScopes + scope
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("toggle_scope_$scope"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { next ->
                                selectedScopes = if (next) selectedScopes + scope else selectedScopes - scope
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ModerationScopes.labelOf(scope),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ModerationScopes.descriptionOf(scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Member has standard Momi permissions with no moderation or editorial access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val canSave = selectedRole == MomiRoles.USER || selectedScopes.isNotEmpty()
            Button(
                enabled = canSave,
                onClick = { onSave(selectedRole, selectedScopes.toList()) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_member_role_button")
            ) {
                Text(
                    text = if (canSave) "Save Role & Scopes" else "Select at least 1 duty",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationQueueSheet(
    isAdminModeActive: Boolean,
    currentRole: String,
    enrolledScopes: Set<String>,
    reports: List<ContentReport>,
    onResolveReport: (report: ContentReport, removeContent: Boolean, resolutionNote: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showResolved by remember { mutableStateOf(false) }

    val visibleReports = remember(reports, showResolved, enrolledScopes, isAdminModeActive) {
        reports.filter { report ->
            val scopeMatch = isAdminModeActive || enrolledScopes.contains(report.category)
            val statusMatch = if (showResolved) report.status != "OPEN" else report.status == "OPEN"
            scopeMatch && statusMatch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isAdminModeActive) Icons.Filled.AdminPanelSettings else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Safety Surveillance Queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAdminModeActive) {
                            "Admin · Full surveillance across all categories"
                        } else {
                            "${MomiRoles.labelOf(currentRole)} · Enrolled: ${
                                enrolledScopes.filter { it in ModerationScopes.SURVEILLANCE_SCOPES }
                                    .joinToString(", ") { ModerationScopes.labelOf(it) }
                            }"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(false to "Open Reports", true to "Resolved").forEach { (resolvedTab, label) ->
                    val active = showResolved == resolvedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { showResolved = resolvedTab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (visibleReports.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (showResolved) "No resolved reports yet" else "All clear in your enrolled queue",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reported violent, sexual, graphic, or unsafe items in your scope appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleReports, key = { "rep_${it.id}" }) { report ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = ModerationScopes.labelOf(report.category),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Text(
                                        text = "${report.targetType} · ${report.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = "Target: @${report.targetAuthorUsername.ifBlank { "creator" }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (report.targetPreviewText.isNotBlank()) {
                                    Text(
                                        text = "\"${report.targetPreviewText}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Reported by @${report.reporterUsername}: ${report.reasonDetail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (report.status == "OPEN") {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onResolveReport(
                                                    report,
                                                    false,
                                                    "Cleared by ${MomiRoles.labelOf(currentRole)}"
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("dismiss_report_${report.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        val canDeleteTarget = isAdminModeActive ||
                                            currentRole == MomiRoles.MODERATOR ||
                                            (currentRole == MomiRoles.EDITOR &&
                                                report.targetType == "PRODUCT" &&
                                                enrolledScopes.contains(ModerationScopes.MARKETPLACE_SAFETY))
                                        if (canDeleteTarget) {
                                            Button(
                                                onClick = {
                                                    onResolveReport(
                                                        report,
                                                        true,
                                                        "Removed violating ${report.targetType.lowercase()} (${ModerationScopes.labelOf(report.category)})"
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(44.dp)
                                                    .testTag("remove_reported_${report.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteForever,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remove Content", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    onResolveReport(
                                                        report,
                                                        false,
                                                        "Escalated & closed by Editor"
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Gavel,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mark Reviewed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else if (!report.resolutionNote.isNullOrBlank()) {
                                    Text(
                                        text = "Action by @${report.reviewedByUsername ?: "staff"} (${report.reviewedByRole ?: ""}): ${report.resolutionNote}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
