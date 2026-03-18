package com.sk8erdudex.texticle.plugins

import org.mozilla.javascript.Context as RhinoCtx
import org.mozilla.javascript.Scriptable

class RhinoEngine {
    fun <T> withContext(block: (RhinoCtx, Scriptable) -> T): T {
        val cx = RhinoCtx.enter()
        cx.optimizationLevel = -1 // required on Android
        val scope = cx.initStandardObjects()
        try { return block(cx, scope) } finally { RhinoCtx.exit() }
    }
}