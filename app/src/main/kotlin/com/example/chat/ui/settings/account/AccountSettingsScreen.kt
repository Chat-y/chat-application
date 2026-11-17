package com.example.chat.ui.settings.account

import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.WorkspacePremium
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.ChatyAsyncImage
import com.example.chat.ui.theme.LocalAppColors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/** Current-user snapshot for the account screen (ChatyDataStore.currentUser
 *  equivalent; the host maps ApiUserProfile / its user store onto this). */
data class AccountUserInfo(
    val id: String,
    val displayName: String,
    val username: String,
    val about: String,
    val avatarInitials: String,
    val avatarUrl: String?,
)


/** Port of lib/features/settings/account/account_settings_screen.dart.
 *
 *  All backend effects are injected (the Dart screen reads them from the
 *  locator): the host wires ApiBackendService + the account delete/logout
 *  orchestration. [onOpenSubscription] replaces the PlanSubscriptionScreen
 *  push with the host's route change. The QR tile is a no-op in the Dart
 *  source (onTap: {}); kept as a no-op for parity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    user: AccountUserInfo,
    organization: () -> Pair<String, String>,
    onBack: () -> Unit,
    onOpenSubscription: (organizationId: String, organizationName: String) -> Unit,
    checkOrganization: suspend (workspace: String) -> Map<String, Any?>,
    updateRegistrationSupport: suspend (organizationIdOrTenantId: String, allow: Boolean) -> Boolean,
    updateProfile: suspend (displayName: String, about: String, username: String, avatarUrl: String?) -> Unit,
    uploadProfilePhoto: suspend (filename: String, bytes: ByteArray) -> String,
    deleteAccountData: suspend (userId: String, password: String) -> Unit,
    recoverAccountDeletion: suspend () -> String = { "Deletion recovery is unavailable." },
    logout: suspend () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // AC1-01: the background colour was ignored; keep it so success and failure look different.
    var snackColor by remember { mutableStateOf<Color?>(null) }
    fun friendlyError(e: Exception): String =
        (e.message ?: "Something went wrong").removePrefix("Exception: ")
    fun snack(message: String, background: Color? = null) {
        scope.launch {
            snackColor = background
            snackbarHostState.showSnackbar(message)
        }
    }

    var deletionPassword by remember { mutableStateOf("") }
    var recoveryVisible by remember { mutableStateOf(false) }
    var recoveryResult by remember { mutableStateOf("Recovery checks only the saved deletion receipt. Local data is kept unless the server confirms deletion.") }
    var recovering by remember { mutableStateOf(false) }
    if (recoveryVisible) {
        AlertDialog(onDismissRequest = { if (!recovering) recoveryVisible = false },
            title = { Text("Account deletion recovery") },
            text = { Text(recoveryResult) },
            confirmButton = { TextButton(enabled = !recovering, onClick = {
                recovering = true
                scope.launch {
                    try { recoveryResult = recoverAccountDeletion() }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { recoveryResult = "Outcome indeterminate. Local data is retained. Retry recovery later." }
                    finally { recovering = false }
                }
            }) { Text(if (recovering) "Checking..." else "Check saved receipt") } },
            dismissButton = { TextButton(onClick = { recoveryVisible = false }, enabled = !recovering) { Text("Close") } })
    }
    // Dart _confirmDeleteAccount: confirm dialog -> deleteUser + local purge
    // -> confirmChatyLogout. The logout confirm is a second dialog, exactly
    // like the source.
    if (showDeleteDialog) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showDeleteDialog = false; deletionPassword = "" },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_delete_title)) },
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_delete_body))
                    OutlinedTextField(value = deletionPassword, onValueChange = { deletionPassword = it },
                        label = { Text("Current password") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    TextButton(onClick = { showDeleteDialog = false; deletionPassword = ""; recoveryVisible = true }) { Text("Recover a previous deletion") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val password = deletionPassword
                        deletionPassword = ""
                        showDeleteDialog = false
                        isDeleting = true
                        scope.launch {
                            try {
                                deleteAccountData(user.id, password)
                                // AC1-10 / AC2-13: end in one signed-out state; do not chain a second "log out" dialog.
                                // (previously: isDeleting = false; showLogoutDialog = true)
                                try {
                                    logout()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (le: Exception) {
                                    snack("Account deleted, but sign-out failed: ${friendlyError(le)}", colors.error)
                                }
                                isDeleting = false
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) {
                                isDeleting = false
                                snack("Failed to delete account: ${friendlyError(e)}", colors.error)
                            } finally { isDeleting = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error), enabled = deletionPassword.isNotBlank(),
                ) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deletionPassword = "" }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_cancel)) }
            },
        )
    }

    // Dart confirmChatyLogout (profile_actions.dart).
    if (showLogoutDialog) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_logout_title)) },
            text = {
                Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_logout_body))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            try {
                                logout()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                                snack("Could not log out: ${friendlyError(e)}", colors.error)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                ) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_logout_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_cancel)) }
            },
        )
    }

    if (showEditSheet) {
        EditProfileSheet(
            user = user,
            onDismiss = { showEditSheet = false },
            uploadProfilePhoto = uploadProfilePhoto,
            updateProfile = updateProfile,
            snack = { msg, bg -> snack(msg, bg) },
        )
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val bg = snackColor
                if (bg != null) {
                    Snackbar(
                        snackbarData = data,
                        containerColor = bg,
                        contentColor = com.example.chat.ui.kit.chatyReadableOn(bg, Color.White, Color.Black),
                    )
                } else {
                    Snackbar(snackbarData = data)
                }
            }
        },
    ) { padding ->
        // AC5-34: pane title for screen readers.
        Box(Modifier.fillMaxSize().padding(padding).semantics { paneTitle = "Account and profile" }) {
            ChatySettingsPage(
                title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_settings_title),
                subtitle = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_settings_subtitle),
                onBack = onBack,
            ) {
                // 1. PROFILE DETAILS
                ChatySettingsSection(title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_section_profile)) {
                    ChatySettingsTile(
                        leading = { AccountAvatar(user, colors.primary, 42) },
                        title = user.displayName.ifEmpty { "Display Name" },
                        subtitle = "@${user.username}",
                        trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = null, modifier = Modifier.size(20.dp)) },
                        onTap = { showEditSheet = true },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Info,
                        iconColor = colors.primary,
                        title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_tile_about),
                        subtitle = if (user.about.isNotEmpty()) user.about else "No bio set (Tap to edit)",
                        onTap = { showEditSheet = true },
                    )
                }

                // 2. WORKSPACE & SUBSCRIPTION
                // Subscription & Plans tile hidden: there is no order backend
                // yet (the create-order route does not exist), so the plan
                // flow is unreachable until payments are built.
                // Member Self-Registration tile hidden (cross-check 2026-11-08):
                // the backend has no org-check PATCH route and no admin role
                // model, so the switch could never succeed. The org-admin
                // plane (check + registration toggle + admin authz) is queued
                // with the later backend pieces.
                if (false) ChatySettingsSection(title = "Workspace & Registration") {
                    WorkspaceRegistrationToggle(
                        organization = organization,
                        checkOrganization = checkOrganization,
                        updateRegistrationSupport = updateRegistrationSupport,
                        snack = { msg, bg -> snack(msg, bg) },
                        accentColor = colors.accent,
                    )
                }

                // 3. ACCOUNT ACTIONS
                ChatySettingsSection(title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_section_actions)) {
                    // AC1-04: commented out until the QR screen exists; the tile did nothing.
                    // ChatySettingsTile(
                    //     icon = Icons.Rounded.QrCode,
                    //     iconColor = colors.primary,
                    //     title = "My QR Code Identity",
                    //     subtitle = "Share your public contact card",
                    //     onTap = { /* no-op in the Dart source (onTap: {}) */ },
                    // )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Logout,
                        iconColor = colors.error,
                        title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_tile_logout),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_tile_logout_sub),
                        onTap = { showLogoutDialog = true },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.DeleteForever,
                        iconColor = colors.error,
                        title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_tile_delete),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_tile_delete_sub),
                        enabled = !isDeleting,
                        onTap = { showDeleteDialog = true },
                    )
                }
            }
            // AC1-40: block taps and show progress while the irreversible delete runs.
            if (isDeleting) {
                Box(
                    Modifier.fillMaxSize()
                        // Previous fixed scrim: .background(Color.Black.copy(alpha = 0.4f))
                        .background(colors.foreground.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun AccountAvatar(user: AccountUserInfo, color: Color, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (!user.avatarUrl.isNullOrBlank()) {
            ChatyAsyncImage(
                url = user.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = { AvatarInitials(user.avatarInitials, sizeDp, color) },
                error = { AvatarInitials(user.avatarInitials, sizeDp, color) },
            )
        } else {
            AvatarInitials(user.avatarInitials, sizeDp, color)
        }
    }
}

@Composable
private fun AvatarInitials(initials: String, sizeDp: Int, bg: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            initials,
            color = com.example.chat.ui.kit.chatyReadableOn(bg, Color.White, Color.Black),
            fontSize = (sizeDp * 0.4).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Dart _WorkspaceRegistrationToggle. */
@Composable
private fun WorkspaceRegistrationToggle(
    organization: () -> Pair<String, String>,
    checkOrganization: suspend (workspace: String) -> Map<String, Any?>,
    updateRegistrationSupport: suspend (organizationIdOrTenantId: String, allow: Boolean) -> Boolean,
    snack: (String, Color?) -> Unit,
    accentColor: Color,
) {
    val colors = LocalAppColors.current
    var loading by remember { mutableStateOf(false) }
    var allowRegistration by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val (orgId) = organization()
        if (orgId.isEmpty()) return@LaunchedEffect
        try {
            val data = checkOrganization(orgId)
            val flag = data["allow_user_registration"] ?: data["user_registration_enabled"]
            allowRegistration = when (flag) {
                is Boolean -> flag
                null -> true
                else -> flag.toString().lowercase() != "false"
            }
        } catch (_: Exception) {
        }
    }

    ChatySwitchTile(
        icon = Icons.Rounded.PersonAdd,
        // Previous fixed tint: iconColor = Color(0xFF6366F1),
        iconColor = colors.primary,
        title = "Member Self-Registration",
        subtitle = when {
            loading -> "Updating workspace setting..."
            allowRegistration -> "New users can self-register using workspace ID"
            else -> "Self-registration disabled (invite-only)"
        },
        value = allowRegistration,
        enabled = !loading,
        onChanged = { value ->
            val (orgId) = organization()
            loading = true
            scope.launch {
                try {
                    val success = updateRegistrationSupport(orgId, value)
                    if (success) {
                        allowRegistration = value
                        loading = false
                        snack(
                            if (value) "User self-registration enabled for workspace."
                            else "User self-registration disabled for workspace.",
                            accentColor,
                        )
                    } else {
                        loading = false
                        snack("Failed to update registration settings. Admin rights required.", colors.error)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                    loading = false
                    snack("Error: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", colors.error)
                }
            }
        },
    )
}

/** Dart _showEditProfileSheet. Photo pick + resize mirrors image_picker
 *  (gallery, max 1024px, JPEG quality 85). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    user: AccountUserInfo,
    onDismiss: () -> Unit,
    uploadProfilePhoto: suspend (filename: String, bytes: ByteArray) -> String,
    updateProfile: suspend (displayName: String, about: String, username: String, avatarUrl: String?) -> Unit,
    snack: (String, Color?) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(user.displayName) }
    var about by remember { mutableStateOf(user.about) }
    var avatarUrl by remember { mutableStateOf(user.avatarUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        // AC1-17: system photo picker (was GetContent("image/*")).
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || isSaving || isUploadingPhoto) return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        scope.launch {
            try {
                val (filename, bytes) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = readDownscaledJpeg(context.contentResolver, uri, 1024, 85)
                    val name = queryDisplayName(context.contentResolver, uri) ?: "profile_photo.jpg"
                    name to bytes
                }
                val newUrl = uploadProfilePhoto(filename, bytes)
                // Dart: dataStore.updateProfile(currentUser.copyWith(avatarUrl:
                // avatarUrl)) - the stored profile fields, not the edit fields.
                updateProfile(user.displayName, user.about, user.username, newUrl)
                avatarUrl = newUrl
                snack("Profile photo updated!", colors.accent)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                snack("Failed to upload photo: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", colors.error)
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = com.example.chat.ui.kit.ChatyModalShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_edit_profile),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.foreground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))

            // Avatar with camera badge.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(80.dp)
                    .clickable(enabled = !isUploadingPhoto && !isSaving) { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                AccountAvatar(user.copy(avatarUrl = avatarUrl), colors.primary, 80)
                if (isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            // Previous fixed overlay: .background(Color(0x73000000)),
                            .background(colors.foreground.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            // Previous fixed foreground: color = Color.White,
                            color = colors.onPrimary,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .border(2.dp, colors.surface, CircleShape)
                            .padding(6.dp),
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera,
                            semanticLabel = null,
                            // Previous fixed foreground: color = Color.White,
                            color = colors.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isUploadingPhoto && !isSaving,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_change_photo),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_label_display_name)) },
                leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_label_about)) },
                leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.info, semanticLabel = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 2,
            )
            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    if (isSaving || isUploadingPhoto) return@Button
                    isSaving = true
                    scope.launch {
                        try {
                            // Dart: dataStore.updateProfile(copyWith(displayName, about))
                            updateProfile(name.trim(), about.trim(), user.username, avatarUrl)
                            onDismiss()
                            snack("Profile updated successfully!", colors.accent)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                            snack("Failed to update: ${(e.message ?: e.toString()).removePrefix("Exception: ")}", colors.error)
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && !isUploadingPhoto,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        // Previous fixed foreground: color = Color.White,
                        color = colors.onPrimary,
                    )
                } else {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.accounts_save_profile), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** image_picker equivalent: gallery image, downscaled so the long edge is at
 *  most [maxEdge] px, re-encoded as JPEG at [quality]. */
private fun readDownscaledJpeg(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    maxEdge: Int,
    quality: Int,
): ByteArray {
    val source = com.example.chat.ui.core.system.SampledBitmapDecoder.decode(
        { resolver.openInputStream(uri) },
        maxEdge * 2,
    ) ?: throw Exception("Could not read the selected image")
    var scaledOwned: Bitmap? = null
    var orientedOwned: Bitmap? = null
    try {
    val longEdge = maxOf(source.width, source.height)
    val scaled = if (longEdge > maxEdge) {
        val scale = maxEdge.toFloat() / longEdge
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }
    scaledOwned = scaled
    // AC1-18: apply the EXIF orientation so portrait photos are not uploaded sideways.
    val orientation = try {
        resolver.openInputStream(uri)?.use {
            androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = android.graphics.Matrix()
    when (orientation) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    val oriented = if (matrix.isIdentity) scaled else Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    orientedOwned = oriented
    val out = ByteArrayOutputStream()
    check(oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Could not prepare the selected image" }
    return out.toByteArray()
    } finally {
        listOfNotNull(orientedOwned, scaledOwned, source).distinct().forEach { if (!it.isRecycled) it.recycle() }
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? = try {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}

// Superseded account-screen source retained inactive:
//                                 } catch (le: Exception) {
//                             } catch (e: Exception) {
//                             }
//                             } catch (e: Exception) {
//                         .background(Color.Black.copy(alpha = 0.4f))
//         iconColor = Color(0xFF6366F1),
//                 } catch (e: Exception) {
//         if (uri == null) return@rememberLauncherForActivityResult
//             } catch (e: Exception) {
//                     .clickable(enabled = !isUploadingPhoto) { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
//                             .background(Color(0x73000000)),
//                             color = Color.White,
//                             color = Color.White,
//                 enabled = !isUploadingPhoto,
//                         } catch (e: Exception) {
//                 enabled = !isSaving,
//                         color = Color.White,
//     oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
