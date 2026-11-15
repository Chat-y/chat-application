package com.example.chat.ui.core.components.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** Android's documented no-personalized-learning flag. IMEs still own their implementation. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AuthInputPrivacy(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = PlatformTextInputInterceptor { request, next ->
        next.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection {
                val connection = request.createInputConnection(outAttrs)
                outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                return connection
            }
        })
    }, content = content)
}
