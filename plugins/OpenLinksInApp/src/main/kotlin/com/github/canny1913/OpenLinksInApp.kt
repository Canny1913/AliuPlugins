package com.github.canny1913

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.discord.utilities.uri.UriHandler

private val DISCORD_DOMAIN_REGEX = Regex("""(?:^|^https\:\/\/)(?:www\.|)(?:discordapp|discord)\.(?:com|gift|new|gg)""")

@Suppress("unused")
@AliucordPlugin(requiresRestart = false)
class OpenLinksInApp : Plugin() {

    init {
        manifest.description = "Forces URL handler to open links in current app."
    }

    override fun start(context: Context) {
        patcher.before<UriHandler>("openUrl",
            Context::class.java,
            Uri::class.java,
            String::class.javaObjectType,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Function0::class.java
        ) { param ->
            val uri = param.args[1] as Uri
            val str = param.args[2] as String

            val isDiscordLink = DISCORD_DOMAIN_REGEX.containsMatchIn(str)
            if (isDiscordLink) {
                logger.debug("Launching in current activity.")
                Utils.appActivity.startActivity(
                    Intent().apply {
                        data = uri
                        `package` = Utils.appContext.packageName
                    }
                )
                param.result = null
            }
        }
    }
    override fun stop(context: Context) = patcher.unpatchAll()
}
