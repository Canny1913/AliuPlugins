package com.github.canny1913

import android.content.Context
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.settings.delegate
import com.discord.models.domain.emoji.EmojiSet
import com.discord.stores.StoreEmoji
import com.discord.widgets.chat.input.emoji.EmojiCategoryAdapter
import com.discord.widgets.chat.input.emoji.EmojiCategoryItem
import com.discord.widgets.chat.input.emoji.EmojiPickerViewModel
import com.discord.widgets.chat.input.emoji.WidgetEmojiPicker
import com.discord.widgets.chat.input.expression.WidgetExpressionTray
import com.github.canny1913.settings.BetterEmojiSettings
import java.lang.reflect.Field

@Suppress(
    "unused",
    "UNCHECKED_CAST",
    "MISSING_DEPENDENCY_SUPERCLASS",
    "MISSING_DEPENDENCY_SUPERCLASS_WARNING"
)
@AliucordPlugin(
    requiresRestart = true // or just exit main activity in some way
)
class BetterEmojis : Plugin() {
    var SettingsAPI.savedPositions: MutableList<String> by settings.delegate(mutableListOf())


    val EmojiCategoryAdapter.items: List<EmojiCategoryItem>
        get() = EmojiCategoryAdapter.`access$getItems$p`(this) as List<EmojiCategoryItem>

    init {
        settingsTab = SettingsTab(
            BetterEmojiSettings::class.java,
            SettingsTab.Type.PAGE
        ).withArgs(settings)
    }
    val categoryAdapterField: Field = WidgetEmojiPicker::class.java.getDeclaredField("categoryAdapter")
        .apply { isAccessible = true }
    val emojiFragmentField: Field = WidgetExpressionTray::class.java.getDeclaredField("emojiPickerFragment")
        .apply { isAccessible = true }
    val isSelectedField: Field = EmojiCategoryItem::class.java.getDeclaredField("isSelected")
        .apply { isAccessible = true }

    override fun start(context: Context) {
        patcher.after<WidgetEmojiPicker>("setUpCategoryRecycler") { param ->
            val binding = WidgetEmojiPicker.`access$getBinding$p`(this)
            val recyclerView = binding.i
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@after
            // enable horizontal scroll
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL

            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val adapter = recyclerView.adapter as EmojiCategoryAdapter
                    val items = adapter.items.toMutableList()
                    val item = items.removeAt(viewHolder.bindingAdapterPosition)
                    items.add(target.bindingAdapterPosition, item)
                    // no need for async updater here
                    EmojiCategoryAdapter.`access$setItems$p`(adapter, items)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun isLongPressDragEnabled(): Boolean = true
            }

            // register listener to emoji view
            ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
        }

        patcher.before<EmojiPickerViewModel.ViewState.Results>(String::class.java, List::class.java, List::class.java) { param ->
            // build new category list from settings
            val items = param.args[2] as ArrayList<EmojiCategoryItem>
            val newList = buildNewItemList(items)
            param.args[2] = newList
        }

        patcher.after<WidgetExpressionTray>("isShown", Boolean::class.javaPrimitiveType!!) { param ->
            val isShown = param.args[0] as Boolean
            val fragment = emojiFragmentField[this] as WidgetEmojiPicker
            val adapter = categoryAdapterField[fragment] as EmojiCategoryAdapter
            if (!isShown) {
                val items = adapter.items
                // so we don't accidentally overwrite settings with empty list
                if (items.isEmpty()) return@after
                settings.savedPositions = items.map(EmojiCategoryItem::getKey).toMutableList()
            }
        }

        /// prevent automatic selection of FAVOURITES emoji category
        patcher.before<EmojiPickerViewModel.StoreState.Emoji>(
            EmojiSet::class.java,
            StoreEmoji.EmojiContext::class.java,
            LinkedHashMap::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Set::class.java
        ) { param ->
            param.args[5] = 0L // FIXME: Maybe support saving scroll position?
        }
    }


    fun buildNewItemList(items: ArrayList<EmojiCategoryItem>): List<EmojiCategoryItem> {
        val positions = settings.savedPositions.takeIf { it.isNotEmpty() } ?: return items
        val newList = arrayListOf<EmojiCategoryItem>()
        positions.forEach { categoryId ->
            // come on even rust did better
            val itemIndex = items.indexOfFirst { it.key == categoryId }.takeIf { it != -1 } ?: return@forEach
            val item = items.removeAt(itemIndex)
            newList.add(item)
        }
        newList.addAll(items) // if there are new categories in existing list just append them
        // select the new first item
        isSelectedField[newList.firstOrNull()] = true
        return newList
    }
    override fun stop(context: Context) = patcher.unpatchAll()
}
