package com.sk8erdudex.texticle.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EditorComposable(
    state: EditorState,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    bottomOverscrollFraction: Float = 0.5f // 0.5 = half a screen
) {
    val selectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    )
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            color = LocalContentColor.current,
            fontFamily = FontFamily.Monospace
        )
    )

    val screenDp = LocalConfiguration.current.screenHeightDp.dp
    val extraBottom: Dp = screenDp * bottomOverscrollFraction
    val scroll = rememberScrollState()

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            // Make the field expand to the content height (so the Column actually scrolls)
            BasicTextField(
                value = state.text,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
            // The extra blank space that lets you scroll past the last line
            Spacer(Modifier.height(extraBottom))
        }
    }
}
