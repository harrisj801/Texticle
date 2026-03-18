package com.sk8erdudex.texticle.ui.model

import android.net.Uri
import com.sk8erdudex.texticle.editor.EditorState

/** Represents one open document/tab. */
data class EditorDoc(
    val uri: Uri?,
    val title: String,
    val state: EditorState,
    val baseline: String
)
