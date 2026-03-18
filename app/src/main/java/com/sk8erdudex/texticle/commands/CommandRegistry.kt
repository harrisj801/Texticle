package com.sk8erdudex.texticle.commands

import android.content.Context

data class Command(val id: String, val title: String, val action: (Context) -> Unit)

class CommandRegistry {
    private val map = LinkedHashMap<String, Command>()

    fun register(id: String, title: String, action: (Context) -> Unit) {
        map[id] = Command(id, title, action)
    }

    fun list(): List<Command> = map.values.toList()

    fun run(ctx: Context, id: String) { map[id]?.action?.invoke(ctx) }
}