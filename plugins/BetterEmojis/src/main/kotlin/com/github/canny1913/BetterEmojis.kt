package com.github.canny1913

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.widgets.chat.input.emoji.WidgetEmojiPicker

@Suppress("unused")
@AliucordPlugin(
    requiresRestart = false
)
class BetterEmojis : Plugin() {
    override fun start(context: Context) {
        patcher.after<WidgetEmojiPicker>("setUpCategoryRecycler") { param ->
            val binding = WidgetEmojiPicker.`access$getBinding$p`(this)
            val layoutManager = binding.i.layoutManager as? LinearLayoutManager ?: return@after
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
