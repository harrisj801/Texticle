package com.sk8erdudex.texticle.plugins

import android.content.Context as AndroidContext
import android.widget.Toast
import com.sk8erdudex.texticle.commands.CommandRegistry
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

class PluginHost(
private val ctx: AndroidContext,
private val commands: CommandRegistry,
private val getText: () -> String,
private val setText: (String) -> Unit
) {
    private val engine = RhinoEngine()
    private val pluginDir: File = File(ctx.filesDir, "plugins")

    fun ensureSamplePluginInstalled() {
        if (!pluginDir.exists()) pluginDir.mkdirs()
        val sample = File(pluginDir, "hello-world.js")
        if (!sample.exists()) {
            ctx.assets.open("plugins/hello-world/plugin.js").use { input ->
                sample.outputStream().use { out -> input.copyTo(out) }
            }
        }
    }

    fun loadAll() {
        val files: Array<File> = pluginDir.listFiles { f -> f.extension == "js" } ?: emptyArray()
        for (file in files) {
            runCatching { loadScript(file.readText()) }
                .onFailure { t ->
                    Toast.makeText(ctx, "Plugin load failed: ${file.name}: ${t.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadScript(code: String) {
        engine.withContext { cx: RhinoContext, scope: Scriptable ->
            // Expose editor API
            ScriptableObject.putProperty(
                scope, "editor",
                RhinoContext.javaToJS(EditorApi(getText, setText), scope)
            )

            // registerCommand(id, title, handler)
            val registerCommandFn: BaseFunction = object : BaseFunction() {
                override fun call(
                    cx: RhinoContext,
                    scope: Scriptable,
                    thisObj: Scriptable,
                    args: Array<Any?>
                ): Any? {
                    val id: String = args.getOrNull(0)?.toString() ?: return null
                    val title: String = args.getOrNull(1)?.toString() ?: id
                    val fn: Function = (args.getOrNull(2) as? Function) ?: return null

                    // Explicit type on the action parameter avoids the recursive inference issue
                    commands.register(id, title, action = { _: AndroidContext ->
                        engine.withContext { ecx: RhinoContext, escope: Scriptable ->
                            ScriptableObject.putProperty(
                                escope, "editor",
                                RhinoContext.javaToJS(EditorApi(getText, setText), escope)
                            )
                            fn.call(ecx, escope, escope, emptyArray<Any?>())
                        }
                    })
                    return null
                }
            }
            ScriptableObject.putProperty(scope, "registerCommand", registerCommandFn)

            // toast(message)
            val toastFn: BaseFunction = object : BaseFunction() {
                override fun call(
                    cx: RhinoContext,
                    scope: Scriptable,
                    thisObj: Scriptable,
                    args: Array<Any?>
                ): Any? {
                    val msg: String = args.getOrNull(0)?.toString() ?: return null
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    return null
                }
            }
            ScriptableObject.putProperty(scope, "toast", toastFn)

            // Evaluate plugin script
            cx.evaluateString(scope, code, "plugin.js", 1, null)
        }
    }
}

class EditorApi(
    private val read: () -> String,
    private val write: (String) -> Unit
) {
    fun getText(): String = read()
    fun setText(s: String) = write(s)
    fun replaceAll(find: String, replace: String) {
        write(read().replace(find, replace))
    }
}
