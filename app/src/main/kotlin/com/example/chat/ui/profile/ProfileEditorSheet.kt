package com.example.chat.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.data.services.ProfileService
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/**
 * Port of lib/features/profile/profile_actions.dart showChatyProfileEditor
 * (api @ 3bee780d): the shared modal profile editor used by BOTH the
 * Profile root screen and Settings. Same fields, same live username
 * availability contract, same save flow and same snackbar copy.
 *
 * The photo row uploads immediately on pick (matching the Dart behavior
 * that media changes never roll back with cancelled text edits).
 * checkUsernameAvailable is nullable: the custom backend has no
 * availability endpoint on the api branch, so when null the sheet skips
 * the live check and a taken username surfaces as the server's save error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    user: UserProfile,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    /** Immediate photo upload; returns the new public URL or null when the
     *  user cancelled picking. */
    onUploadAvatar: suspend (ProfileMediaSource) -> String?,
    /** Persisted once on Save; throws with a user-readable message on
     *  failure. avatarUrl carries any immediately-uploaded photo URL. */
    onSave: suspend (displayName: String, username: String, about: String, avatarUrl: String?) -> Unit,
    checkUsernameAvailable: (suspend (String) -> Boolean)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(user.displayName) }
    var username by remember { mutableStateOf(user.username) }
    var about by remember { mutableStateOf(user.about) }
    var saving by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var usernameTaken by remember { mutableStateOf(false) }
    var uploadedAvatarUrl by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (saving || photoBusy) return
        ChatyValidators.validateDisplayName(displayName)?.let {
            scope.launch { snackbar.showSnackbar(ProfileValidationCopy.localize(context, it)) }
            return
        }
        ChatyValidators.validateUsername(username)?.let {
            scope.launch { snackbar.showSnackbar(ProfileValidationCopy.localize(context, it)) }
            return
        }
        ChatyValidators.validateBio(about)?.let {
            scope.launch { snackbar.showSnackbar(ProfileValidationCopy.localize(context, it)) }
            return
        }
        val normalized = ChatyValidators.normalizeUsername(username)
        val unchanged = normalized == ChatyValidators.normalizeUsername(user.username)
        if (!unchanged && usernameTaken) {
            scope.launch { snackbar.showSnackbar(context.getString(com.example.chat.R.string.profile_sheet_username_available)) }
            return
        }
        saving = true // Synchronous reservation blocks a second tap before coroutine dispatch.
        scope.launch {
            try {
                if (!unchanged && checkUsernameAvailable != null &&
                    checkUsernameAvailable(normalized) == false
                ) {
                    usernameTaken = true
                    saving = false
                    return@launch
                }
                onSave(displayName.trim(), normalized, about.trim(), uploadedAvatarUrl)
                onDismiss()
                snackbar.showSnackbar(context.getString(com.example.chat.R.string.profile_sheet_saved))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                snackbar.showSnackbar(context.getString(com.example.chat.R.string.profile_sheet_save_failure, ProfileErrorMessages.friendly(context, error, context.getString(com.example.chat.R.string.profile_retry), isSave = true)))
            } finally { saving = false }
        }
    }

    fun uploadPhoto(source: ProfileMediaSource) {
        if (saving || photoBusy) return
        photoBusy = true // Reserve before dispatch, including accessibility duplicate activation.
        scope.launch {
            try {
                val url = onUploadAvatar(source)
                if (url != null) {
                    uploadedAvatarUrl = url
                    snackbar.showSnackbar(context.getString(com.example.chat.R.string.profile_sheet_photo_saved))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error.message?.contains("cancelled") != true) {
                    snackbar.showSnackbar(context.getString(com.example.chat.R.string.profile_sheet_photo_failure, ProfileErrorMessages.friendly(context, error, context.getString(com.example.chat.R.string.profile_retry))))
                }
            } finally {
                photoBusy = false
            }
        }
    }

    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp, containerColor = com.example.chat.ui.theme.LocalAppColors.current.surfaceElevated, shape = com.example.chat.ui.kit.ChatyModalShape, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // _ProfilePhotoRow: avatar + Camera/Gallery text buttons.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatyAvatar(
                    initials = ProfileService.initials(
                        displayName.ifEmpty { user.displayName },
                    ),
                    colorHex = user.avatarColorHex,
                    url = uploadedAvatarUrl ?: user.avatarUrl,
                    size = 68,
                    shape = "circle",
                )
                Spacer(Modifier.width(16.dp))
                Row {
                    TextButton(
                        onClick = { uploadPhoto(ProfileMediaSource.camera) },
                        enabled = !photoBusy,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera, semanticLabel = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(context.getString(com.example.chat.R.string.profile_sheet_camera))
                    }
                    TextButton(
                        onClick = { uploadPhoto(ProfileMediaSource.gallery) },
                        enabled = !photoBusy,
                    ) {

//Icon(Icons.Rounded.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.gallery, Icons.Rounded.Photo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(context.getString(com.example.chat.R.string.profile_sheet_gallery))
                    }
                    if (photoBusy) {
                        CircularProgressIndicator(
                            Modifier
                                .size(16.dp)
                                .semantics {
                                    contentDescription = context.getString(com.example.chat.R.string.profile_sheet_uploading)
                                    liveRegion = LiveRegionMode.Polite
                                },
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(context.getString(com.example.chat.R.string.profile_sheet_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                context.getString(if (checkUsernameAvailable == null) com.example.chat.R.string.profile_sheet_description_unchecked else com.example.chat.R.string.profile_sheet_description),
                fontSize = 12.sp,
                color = Color(theme.secondaryTextColor),
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                enabled = !saving,
                label = { Text(context.getString(com.example.chat.R.string.profile_sheet_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    usernameTaken = false
                },
                enabled = !saving,
                label = { Text(context.getString(com.example.chat.R.string.profile_sheet_username)) },
                isError = usernameTaken,
                supportingText = {
                    if (usernameTaken) Text(context.getString(com.example.chat.R.string.profile_sheet_username_taken))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                enabled = !saving,
                label = { Text(context.getString(com.example.chat.R.string.profile_sheet_about)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { save() },
                enabled = !saving && !photoBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) context.getString(com.example.chat.R.string.profile_sheet_saving) else context.getString(com.example.chat.R.string.profile_sheet_save))
            }
        }
    }
}

// Previous localization paths retained:
//             scope.launch { snackbar.showSnackbar("Choose an available username before saving.") }
//                 snackbar.showSnackbar("Profile updated.")
//                 snackbar.showSnackbar("Could not update profile. " + ProfileErrorMessages.friendly(error, "Please try again.", isSave = true))
//                     snackbar.showSnackbar("Profile photo updated.")
//                     snackbar.showSnackbar("Could not update photo. " + ProfileErrorMessages.friendly(error, "Please try again."))
//                         Text("Camera")
//                         Text("Gallery")
//                                     contentDescription = "Uploading photo"
//             Text("Edit profile", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
//                 "Update your profile. Usernames are checked live so you never submit a name that is already taken.",
//                 label = { Text("Display name") },
//                 label = { Text("Username") },
//                     if (usernameTaken) Text("This username is already taken")
//                 label = { Text("About") },
//                 Text(if (saving) "Saving…" else "Save")

// Previous profile copy retained:
//             scope.launch { snackbar.showSnackbar(it) }
//             scope.launch { snackbar.showSnackbar(it) }
//             scope.launch { snackbar.showSnackbar(it) }
//                 context.getString(com.example.chat.R.string.profile_sheet_description),

// Previous legacy-sheet dispatch/error paths retained:
//         if (saving) return
//             saving = true
//             } catch (error: Throwable) {
//                 saving = false
//             }
//             photoBusy = true
//             } catch (error: Throwable) {
