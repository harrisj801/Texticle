package com.sk8erdudex.texticle.editor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.sk8erdudex.texticle.R
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme

// TextMate APIs
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

data class SoraConfig(val languageId: String) // "json" or "xml"

@Composable
fun SoraEditor(
    text: String,
    onTextChange: (String) -> Unit,
    config: SoraConfig,
    modifier: Modifier = Modifier
) {
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // Load TextMate once, then flip a state flag → triggers recompose
    var tmReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ensureTextMateLoaded(appCtx.assets)   // synchronous load
        tmReady = true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                setText(text)
                isLineNumberEnabled = true
                setWordwrap(false)
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                isFocusable = true
                isFocusableInTouchMode = true
                setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        if (!v.hasFocus()) v.requestFocus()
                        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                    false
                }
            }
        },
        update = { editor ->
            // ---- Only set language AFTER grammars are registered
            if (tmReady) {
                val scope = when (config.languageId.lowercase()) {
                    "json" -> "source.json"
                    "xml"  -> "text.xml"
                    else   -> "text.log"
                }
                ThemeRegistry.getInstance().setTheme("dark_vs")


                // Read the last applied scope from a view tag
                val lastScope = editor.getTag(R.id.text1) as? String
                if (lastScope != scope) {
                    runCatching {
                        editor.setEditorLanguage(
                            io.github.rosemoe.sora.langs.textmate.TextMateLanguage.create(scope, /* enableCompletion = */ true)
                        )
                        // Cache the scope we just applied
                        editor.setTag(R.id.text1, scope)
                    }
                }
            }
            // ---- caret restore
            val oldLine = editor.cursor.leftLine
            val oldCol  = editor.cursor.leftColumn
            if (editor.text.toString() != text) {
                editor.setText(text)
                val last = (editor.text.lineCount - 1).coerceAtLeast(0)
                val line = oldLine.coerceIn(0, last)
                val col  = oldCol.coerceIn(0, editor.text.getColumnCount(line))
                editor.setSelection(line, col, true)
            }
            // ---- subscribe once
            if (editor.getTag(R.id.edit) != true) {
                val recv = EventReceiver<ContentChangeEvent> { _, _ ->
                    onTextChange(editor.text.toString())
                }
                editor.setTag(R.id.contentupdated, recv)
                editor.subscribeEvent(ContentChangeEvent::class.java, recv)
                editor.setTag(R.id.edit, true)
            }
        }
    )
}
@Volatile private var tmReady = false

private fun ensureTextMateLoaded(assets: android.content.res.AssetManager) {
    if (tmReady) return

    // Resolve files from /assets
    FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

    // Load a theme into the registry
    val themePath = "textmate/dark_vs.json"
    val themeSrc: IThemeSource = IThemeSource.fromInputStream(
        FileProviderRegistry.getInstance().tryGetInputStream(themePath),
        themePath,
        null
    )
    val model = ThemeModel(themeSrc, "dark_vs")
    model.isDark = true
    ThemeRegistry.getInstance().loadTheme(model)

    ThemeRegistry.getInstance().setTheme("dark_vs")


    // Load grammars listed in languages.json
    GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    val reg = GrammarRegistry.getInstance()
    check(reg.findGrammar("source.json") != null) { "JSON grammar not registered" }
    check(reg.findGrammar("text.xml")   != null) { "XML grammar not registered" }
    check(reg.findGrammar("text.log")   != null) { "default grammar not registered" }


    tmReady = true
}
