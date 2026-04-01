package com.github.canny1913

import android.content.Context

import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.wrappers.ChannelWrapper.Companion.id
import com.discord.api.channel.Channel
import com.discord.widgets.guilds.list.`WidgetGuildsListViewModel$createDirectMessageItems$1`

@AliucordPlugin(requiresRestart = false)
class MutedDirectMessagesFix : Plugin() {
    override fun start(context: Context) {
        patcher.after<`WidgetGuildsListViewModel$createDirectMessageItems$1`>("invoke", Channel::class.java) { (param, channel: Channel) ->
            val result = param.result as Boolean
            param.result = result || channel.id in this.`$mentionCounts`.keys
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}