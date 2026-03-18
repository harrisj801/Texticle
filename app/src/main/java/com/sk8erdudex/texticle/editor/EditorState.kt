package com.sk8erdudex.texticle.editor

import androidx.compose.ui.text.input.TextFieldValue

data class EditorState(
    val text: TextFieldValue = TextFieldValue("") // default so callers can just EditorState()
)
