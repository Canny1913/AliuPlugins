package com.github.canny1913

import android.content.Context
import com.aliucord.BuildConfig
import com.aliucord.Constants
import com.aliucord.PluginManager
import com.aliucord.Utils

import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.updater.ManagerBuild
import com.aliucord.utils.SemVer
import com.aliucord.wrappers.ChannelWrapper.Companion.id
import com.discord.api.channel.Channel
import com.discord.widgets.guilds.list.`WidgetGuildsListViewModel$createDirectMessageItems$1`
import java.io.File

@AliucordPlugin()
class MutedDirectMessagesFix : Plugin() {
    override fun start(context: Context) {
        val aliucordSemver = SemVer.parse(BuildConfig.VERSION)
        if (aliucordSemver >= SemVer.parse("2.9.0")) {
            Utils.showToast("MutedDirectMessagesFix has been merged to core. so i automatically deleted it for you.")
            File("${Constants.PLUGINS_PATH}/MutedDirectMessagesFix.zip").takeIf { it.exists() }?.let {
                if (it.delete())
                    Utils.showToast("MutedDirectMessagesFix has been merged to Aliucord. so i automatically deleted it for you.")
                else
                    Utils.showToast("MutedDirectMessagesFix has been merged into Aliucord. Please delete the plugin.", true)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}