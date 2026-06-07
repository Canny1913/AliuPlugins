package com.github.canny1913


import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.ChannelUtils
import com.aliucord.utils.accessField
import com.aliucord.wrappers.ChannelWrapper.Companion.id
import com.aliucord.wrappers.GuildRoleWrapper.Companion.name
import com.discord.widgets.chat.input.autocomplete.ApplicationCommandAutocompletable
import com.discord.widgets.chat.input.autocomplete.ApplicationCommandChoiceAutocompletable
import com.discord.widgets.chat.input.autocomplete.ApplicationCommandLoadingPlaceholder
import com.discord.widgets.chat.input.autocomplete.ApplicationPlaceholder
import com.discord.widgets.chat.input.autocomplete.Autocompletable
import com.discord.widgets.chat.input.autocomplete.AutocompletableKt
import com.discord.widgets.chat.input.autocomplete.ChannelAutocompletable
import com.discord.widgets.chat.input.autocomplete.`ChatInputAutocompletables$observeChannelAutocompletables$1$1`
import com.discord.widgets.chat.input.autocomplete.EmojiAutocompletable
import com.discord.widgets.chat.input.autocomplete.EmojiUpsellPlaceholder
import com.discord.widgets.chat.input.autocomplete.GlobalRoleAutocompletable
import com.discord.widgets.chat.input.autocomplete.RoleAutocompletable
import com.discord.widgets.chat.input.autocomplete.UserAutocompletable
import java.util.TreeMap
import java.util.TreeSet
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@AliucordPlugin(
    requiresRestart = true
)
class AutocompleteFix: Plugin() {

    private var TreeSet<*>.map by accessField<TreeMap<*, *>>("m")
    private var TreeMap<*, *>.comparator by accessField<Comparator<*>>()

    override fun start(context: Context) {
        patcher.after<`ChatInputAutocompletables$observeChannelAutocompletables$1$1`<*,*,*,*,*>>("call",
            Map::class.java,
            Map::class.java,
            Map::class.java,
            Map::class.java
        ) { param ->
            val result = param.result as Map<*, TreeSet<Autocompletable>>
            result.keys.forEach {
                val treeSet = result[it] ?: return@forEach
                treeSet.map.comparator = AutocompletableComparator()
            }
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}

/**
 * Replaces Discord's stock autocomplete comparator to fix bugs such as some entries not appearing. For instance
 * user suggestion entries with the same nickname/display name will only appear once.
 */
@Suppress("unused")
class AutocompletableComparator : Comparator<Autocompletable> {
    override fun compare(a: Autocompletable, b: Autocompletable): Int {
        if (a::class != b::class) {
            return AutocompletableKt.getSortIndex(a).compareTo(AutocompletableKt.getSortIndex(b))
        }

        return when {
            check<ApplicationCommandChoiceAutocompletable>(a, b) -> {
                compareValuesBy(a, b) { it.choice.a().lowercase()} // CommandChoice::getName()
            }

            // *New*: Compare by name first, then compare by app id, then compare by app name
            // Originally it only compares by name if id matches, then by application name if it doesn't
            check<ApplicationCommandAutocompletable>(a, b) -> {
                compareValuesBy(
                    a, b,
                    { it.command.name },
                    { it.application?.id },
                )
            }

            check<ApplicationPlaceholder>(a, b) -> {
                compareValuesBy(a, b) { it.application.name.lowercase() }
            }

            check<ChannelAutocompletable>(a, b) -> {
                compareValuesBy(
                    a, b,
                    { ChannelUtils.getDisplayName(it.channel).lowercase() },
                    { it.channel.id },
                )
            }

            check<EmojiAutocompletable>(a, b) -> {
                compareValuesBy(a, b) { it.emoji.firstName } // TODO: Check if .lower() should be used
            }

            check<GlobalRoleAutocompletable>(a, b) -> {
                compareValuesBy(a, b) { it.text.lowercase() }
            }

            // *New*: replace default name comparison
            check<RoleAutocompletable>(a, b) -> {
                compareValuesBy(
                    a, b,
                    { it.role.name.lowercase() },
                    { it.role.id } // *New*: additionally compare by id
                )
            }

            // *New*: replace default username#discrim comparison
            check<UserAutocompletable>(a, b) -> {
                compareValuesBy(
                    a, b,
                    { (it.nickname ?: it.user.username).lowercase() }, // Compare by nickname/display name first
                    { it.user.username.lowercase() }, // Then compare by username
                    { it.user.discriminator }, // Then compare by discrim
                )
            }

            check<ApplicationCommandLoadingPlaceholder>(a, b) -> 0
            check<EmojiUpsellPlaceholder>(a, b) -> 0

            else -> throw NoWhenBranchMatchedException()
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <reified T : Autocompletable> check(a: Autocompletable, b: Autocompletable): Boolean {
        contract {
            returns(true) implies (a is T)
            returns(true) implies (b is T)
        }
        return a is T
    }
}
