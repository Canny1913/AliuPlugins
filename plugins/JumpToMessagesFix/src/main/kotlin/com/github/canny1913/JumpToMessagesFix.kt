package com.github.canny1913

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.before
import com.aliucord.patcher.instead
import com.aliucord.utils.RxUtils
import com.aliucord.utils.RxUtils.subscribe
import com.discord.api.channel.Channel
import com.discord.databinding.WidgetChatListBinding
import com.discord.models.message.Message
import com.discord.stores.StoreChat
import com.discord.stores.StoreMessagesLoader
import com.discord.stores.StoreStream
import com.discord.utilities.channel.ChannelSelector
import com.discord.utilities.rx.ObservableExtensionsKt
import com.discord.widgets.chat.list.WidgetChatList
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapter
import com.discord.widgets.chat.list.entries.MessageEntry
import com.discord.widgets.chat.list.entries.NewMessagesEntry
import com.discord.widgets.chat.list.model.WidgetChatListModel
import com.github.canny1913.jumptomessages.JumpToMessageSettings
import rx.Observable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.discord.stores.`StoreMessagesLoader$jumpToMessage$1$1` as SelectedChannelStateUpdater
import com.discord.stores.`StoreMessagesLoader$jumpToMessage$2$1` as ConnectionChannelUpdater

@Suppress("unused")
@AliucordPlugin(
    requiresRestart = false
)
class JumpToMessageFix : Plugin() {
    init {
        manifest.description = "Fixes message links not jumping to correct message."
        settingsTab = SettingsTab(JumpToMessageSettings.Sheet::class.java, SettingsTab.Type.BOTTOM_SHEET)
    }

    val bindingGetter: Method = WidgetChatList::class.java.getDeclaredMethod("getBinding").apply { isAccessible = true }
    val channelIdField: Field = WidgetChatListAdapter.HandlerOfUpdates::class.java.getDeclaredField("channelId").apply { isAccessible = true }
    val itemAnimatorField: Field = WidgetChatList::class.java.getDeclaredField("defaultItemAnimator").apply { isAccessible = true }
    val highlightedMessageView: AtomicReference<View?> = AtomicReference()

    override fun start(context: Context) {
        // Modified reimplementation of original function that concatenates
        // channel loading and message jumping Observables to avoid synchronization issues
        patcher.instead<WidgetChatList>("onViewBoundOrOnResume") {

            val adapter = WidgetChatList.`access$getAdapter$p`(this)
            val binding = bindingGetter(this) as WidgetChatListBinding
            itemAnimatorField[this] = binding.b.itemAnimator

            adapter.setHandlers()
            adapter.onResume()

            val channelObservable =
                ObservableExtensionsKt.ui(
                    ObservableExtensionsKt.computationLatest(WidgetChatListModel.Companion!!.get()),
                    this,
                    adapter
                )
            val channelSubscriber = RxUtils.createActionSubscriber<WidgetChatListModel>(
                onNext = { widgetModel ->
                    WidgetChatList.`access$configureUI`(this, widgetModel)
                },
                onError = ::observableError
            )
            channelObservable.subscribe(channelSubscriber)

            val scrollObservable =
                ObservableExtensionsKt.ui(StoreStream.Companion!!.messagesLoader.scrollTo, this, null)
            val scrollSubscriber = RxUtils.createActionSubscriber<Long>(
                onNext = { messageId ->
                    // Prevent auto scroller from getting executed *after* jumping action
                    // else it would instantly scroll back to bottom
                    val handler = WidgetChatListAdapter.`access$getHandlerOfUpdates$p`(adapter)
                    // id from chat list model doesn't seem reliable
                    channelIdField[handler] = StoreStream.Companion!!.channelsSelected.id
                    WidgetChatListAdapter.`access$setTouchedSinceLastJump$p`(adapter, true)
                    WidgetChatList.`access$scrollTo`(this, messageId)
                },
                onError = ::observableError
            )
            scrollObservable.subscribe(scrollSubscriber)

            Observable.m(channelObservable, scrollObservable) // Observable.concat()
        }
        // For auto-expanding blocked messages on jump
        patcher.before<WidgetChatListAdapter.ScrollToWithHighlight>("run") {
            if (JumpToMessageSettings.autoExpandBlockedMessages) {
                val storeChat = StoreStream.Companion!!.chat
                val dispatcher = StoreStream.getDispatcherYesThisIsIntentional()
                // the public getter method in StoreChat returns a snapshot list instead of live
                val expandedBlockedMessages = StoreChat.`access$getExpandedBlockedMessageGroups$p`(storeChat)
                if (messageId !in expandedBlockedMessages) {
                    expandedBlockedMessages.add(messageId)
                }
                dispatcher.schedule {
                    storeChat.markChanged()
                }
            }
        }
        // Custom message highlighting implementation. Message won't de-highlight until user taps on chat.
        patcher.instead<WidgetChatListAdapter.ScrollToWithHighlight>(
            "animateHighlight",
            View::class.java
        ) { param ->
            customAnimateHighlight(param.args[0] as View)
        }
        // De-highlight
        patcher.before<WidgetChatListAdapter.HandlerOfTouches>("onTouch", View::class.java, MotionEvent::class.java) {
            val messageView = highlightedMessageView.getAndSet(null) ?: return@before
            val transitionDrawable = messageView.background as? TransitionDrawable ?: return@before
            transitionDrawable.reverseTransition(500)
        }
        patcher.instead<WidgetChatListAdapter.ScrollToWithHighlight>("getNewMessageEntryIndex", List::class.java) { param ->
            val list = param.args[0] as List<*>
            var messageId = this.messageId

            if (messageId == StoreMessagesLoader.SCROLL_TO_LATEST) {
                return@instead 0
            }
            if (messageId == StoreMessagesLoader.SCROLL_TO_LAST_UNREAD) {
                messageId = this.adapter.data.newMessagesMarkerMessageId
                if (messageId <= 0L) {
                    return@instead 0
                }
            }
            // invalid id
            if (messageId <= 0L) {
                return@instead -1
            }

            val messageIndex = list.indexOfFirst { item -> (item is MessageEntry) && item.message.id == messageId }

            if (messageIndex == -1) {
                return@instead -1
            }
            val newMessageIndex = list.subList(0, messageIndex).indexOfLast { (it is NewMessagesEntry) && it.messageId == messageId }.takeIf { it != -1 }

            return@instead newMessageIndex ?: messageIndex
        }
        patcher.instead<StoreMessagesLoader>("jumpToMessage", Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!) { param ->
            val channelId = param.args[0] as Long
            val messageId = param.args[1] as Long
            this.customJumpToMessage(channelId, messageId)
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()

    fun StoreMessagesLoader.customJumpToMessage(channelId: Long, messageId: Long) {
        if (messageId <= 0) {
            return
        }

        val connectionSubscriber = RxUtils.createActionSubscriber<Channel>(
            onNext = { channel ->
                if (channel.k() == StoreMessagesLoader.`access$getSelectedChannelId$p`(this)) return@createActionSubscriber

                StoreMessagesLoader.`access$channelLoadedStateUpdate`(
                    this,
                    channelId,
                    ConnectionChannelUpdater.INSTANCE
                )
                val selector = ChannelSelector.Companion!!.instance
                selector.selectChannel(channel, null, null)
            },
            onError = ::observableError
        )

        val connectionObservable = StoreStream.Companion!!.connectionOpen.observeConnectionOpen(true).Z(1) // Observable.buffer
            .A { bool -> // Observable.flatMap
                val channelObserver = StoreStream.Companion!!.channels.observeChannel(channelId)
                ObservableExtensionsKt.takeSingleUntilTimeout(channelObserver, 5000L, true)
            }

        val latestConnectionObservable = ObservableExtensionsKt.computationLatest(connectionObservable)
            .apply { subscribe(connectionSubscriber) }

        val selectedSubscriber = RxUtils.createActionSubscriber<Message>(
            onNext = { message ->
                StoreMessagesLoader.`access$getDispatcher$p`(this@customJumpToMessage)
                    .schedule {
                        if (message != null) {
                            StoreMessagesLoader.`access$getScrollToSubject$p`(this).k.onNext(message.id)
                        } else {
                            StoreMessagesLoader.`access$channelLoadedStateUpdate`(this, channelId, SelectedChannelStateUpdater.INSTANCE)
                            StoreMessagesLoader.`tryLoadMessages$default`(this, 0L, true, false, false, channelId, messageId, 13, null)
                        }
                    }
            },
            // FIXME: This can potentially spam logs
            onError = ::observableError
        )

        // not sure what this is but oh well
        val transformer = b.a.d.o.c({ newId -> channelId == newId }, -1L, 5000L, TimeUnit.MILLISECONDS)
        val selectedChannelObserver = StoreStream.Companion!!.channelsSelected.observeId()
            .k(transformer) // Observable.compose
            .Y {
                StoreStream.Companion!!.messages.observeMessagesForChannel(channelId, messageId)
            } // Observable.flatMap?
        val latestSelectedObserver = ObservableExtensionsKt.takeSingleUntilTimeout(selectedChannelObserver, 5000L, true)
            .apply { subscribe(selectedSubscriber) }

        Observable.m(latestConnectionObservable, latestSelectedObserver) // Observable.concat
    }
    fun customAnimateHighlight(view: View) {

        val highlightDrawableId = Utils.getResId("drawable_bg_highlight", "drawable")
        val highlightDrawable = ContextCompat.getDrawable(Utils.appActivity, highlightDrawableId) as TransitionDrawable
        Thread.sleep(100) // bad workaround
        Utils.mainThread.post {
            view.background = highlightDrawable
            highlightDrawable.startTransition(500)
        }
        highlightedMessageView.set(view)
    }

    fun observableError(e: Throwable) = logger.error("Observable error:", e)
}
