package com.github.canny1913


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.GatewayAPI
import com.aliucord.entities.Plugin
import com.aliucord.fragments.ConfirmDialog
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.patcher.component1
import com.aliucord.patcher.component2
import com.aliucord.patcher.component3
import com.aliucord.settings.delegate
import com.aliucord.utils.ChannelUtils
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.utils.GsonUtils
import com.aliucord.utils.RxUtils
import com.aliucord.utils.RxUtils.subscribe
import com.aliucord.utils.SerializedName
import com.aliucord.utils.ViewUtils.addTo
import com.aliucord.utils.ViewUtils.findViewById
import com.aliucord.utils.ViewUtils.setPadding
import com.aliucord.utils.accessField
import com.aliucord.utils.accessGetter
import com.aliucord.wrappers.ChannelWrapper.Companion.id
import com.discord.api.message.Message
import com.discord.app.AppActivity
import com.discord.databinding.WidgetChannelsListItemActionsBinding
import com.discord.databinding.WidgetChannelsListItemChannelPrivateBinding
import com.discord.models.domain.ModelMessageDelete
import com.discord.stores.StoreStream
import com.discord.utilities.dimen.DimenUtils
import com.discord.utilities.lifecycle.ActivityProvider
import com.discord.utilities.rx.ObservableExtensionsKt
import com.discord.widgets.channels.list.WidgetChannelsList
import com.discord.widgets.channels.list.WidgetChannelsListAdapter
import com.discord.widgets.channels.list.WidgetChannelsListItemChannelActions
import com.discord.widgets.channels.list.items.ChannelListItem
import com.discord.widgets.channels.list.items.ChannelListItemPrivate
import com.github.canny1913.settings.GhostedSettings
import com.github.canny1913.settings.GhostedTime
import com.google.gson.reflect.TypeToken
import rx.Observable
import rx.Subscription
import java.lang.ref.WeakReference

@AliucordPlugin(
    requiresRestart = true
)
class Ghosted : Plugin() {
    var WidgetChannelsListAdapter.ItemChannelPrivate.binding by accessField<WidgetChannelsListItemChannelPrivateBinding>()
    val WidgetChannelsListItemChannelActions.binding by accessGetter<WidgetChannelsListItemActionsBinding>()
    val ghostIconSize = DimenUtils.dpToPixels(16)
    val ghostIcon: Drawable by lazy {
        resources!!.getDrawable(getPluginRes("ghost", "drawable"), getActivity().theme).apply { mutate() }
            .apply { setBounds(0, 0, ghostIconSize, ghostIconSize) }
    }
    val ghostAllClearIcon: Drawable by lazy {
        resources!!.getDrawable(getPluginRes("ghost_x", "drawable"), getActivity().theme)
    }
    val ghostClearIconSize = DimenUtils.dpToPixels(24)
    val ghostClearIcon: Drawable by lazy {
        resources!!.getDrawable(getPluginRes("ghost_x", "drawable"), getActivity().theme).apply { mutate() }
            .apply { setBounds(0, 0, ghostClearIconSize, ghostClearIconSize) }
    }
    var msgCache = mutableMapOf<Long, MessageItem>()
    var mutedDmsCache = mutableSetOf<Long>()
    var adapterRef: WeakReference<WidgetChannelsListAdapter> = WeakReference(null)
    var me: Long = StoreStream.Companion!!.users.me.id
    val subscriptions = mutableListOf<Subscription>()


    // Aliucord settings implementation is horrible so this contains a lot of hacky fixes
    val ignoreGroupDms by settings.delegate("ignoreGroupDms", false)
    val ignoreBots by settings.delegate("ignoreBots", false)
    val ignoreMutedDms by settings.delegate("ignoreMutedDms", false)
    var clearedIds by settings.delegate("clearMarkerIds", mutableMapOf<String, String>())
    var clearMarkerTimestamps: MutableMap<Long, Long> =
        clearedIds.mapValues { it.value.toLong() }.mapKeys { it.key.toLong() }.toMutableMap()
        set(value) {
            clearedIds = value.mapValues { it.value.toString() }.mapKeys { it.key.toString() }.toMutableMap()
            field = value
        }

    // Enums are also borked idk why
    private val ghostedTimeIndex by settings.delegate("ghostedTime", GhostedTime.None.ordinal)
    val ghostedTime
        get() = GhostedTime.entries[ghostedTimeIndex]

    init {
        settingsTab = SettingsTab(
            GhostedSettings::class.java, SettingsTab.Type.PAGE
        ).withArgs(settings)
    }

    override fun start(context: Context) {
        observeApi()
        patchWidgets()
        patchStores()
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        subscriptions.forEach(Subscription::unsubscribe)
    }

    fun observeApi() {
        StoreStream.getUsers().observeMeId().withComputation().trackerSubscribe { me = it }
        StoreStream.Companion!!.userGuildSettings.observeGuildSettings(0).withComputation()
            .trackerSubscribe { settings ->
                settings.channelOverrides.map { it.channelId }.toHashSet()
            }
        GatewayAPI.onEvent<Any>("READY") { refreshAll() }
        GatewayAPI.onEvent<Any>("RESUMED") { refreshAll() }
    }

    fun patchWidgets() {
        patcher.after<WidgetChannelsListAdapter.ItemChannelPrivate>(
            "onConfigure",
            Int::class.java,
            ChannelListItem::class.java,
        ) { (_, _: Int, item: ChannelListItemPrivate) ->
            msgCache[item.channel.id]?.let { message ->
                val textView = binding.f
                val isGroup = item.channel.D() == 3
                val isCurrentUser = message.author == me
                val clearedStamp = clearMarkerTimestamps[item.channel.id] ?: 0
                val skipCheck =
                    ((message.bot == true && ignoreBots) || (isGroup && ignoreGroupDms) || (item.channel.id in mutedDmsCache && ignoreMutedDms) || message.timestamp <= clearedStamp || isCurrentUser)

                val isInactive = (System.currentTimeMillis() - message.timestamp) > ghostedTime.toMs() && !skipCheck
                if (isInactive) {
                    textView.compoundDrawablePadding = 8.dp
                    textView.setCompoundDrawables(null, null, ghostIcon, null)
                } else {
                    textView.compoundDrawablePadding = 0
                    textView.setCompoundDrawables(null, null, null, null)
                }
            }
        }
        patcher.before<WidgetChannelsList>(
            "onViewBound", View::class.java
        ) { (_, view: View) ->
            val inviteButton = view.findViewById<AppCompatImageView>("channels_list_start_group")
            val constraintLayout = inviteButton.parent as ConstraintLayout
            inviteButton.post {
                val index = constraintLayout.indexOfChild(inviteButton)
                val ghostButton = AppCompatImageView(view.context).apply {
                    setImageDrawable(ghostAllClearIcon)

                    layoutParams = ConstraintLayout.LayoutParams(
                        inviteButton.width, inviteButton.height
                    ).apply {
                        rightToLeft = inviteButton.id
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        bottomToBottom = inviteButton.id
                    }
                    setPadding(11.dp)
                    scaleType = inviteButton.scaleType
                    setOnClickListener { onClickClearAll() }
                }
                constraintLayout.addView(ghostButton, index - 1)
            }
        }
        patcher.after<WidgetChannelsListItemChannelActions>(
            "configureUI", WidgetChannelsListItemChannelActions.Model::class.java
        ) { (_, model: WidgetChannelsListItemChannelActions.Model) ->
            if (ChannelUtils.getRecipients(model.channel).isNullOrEmpty()) return@after
            val layout = binding.h.parent as LinearLayout
            val index = layout.indexOfChild(binding.h)
            TextView(binding.h.context, null, 0, com.lytefast.flexinput.R.i.UiKit_Settings_Item_Icon).addTo(layout, index) {
                text = "Clear Ghost"
                setCompoundDrawablesRelative(ghostClearIcon, null, null, null)
                setOnClickListener {
                    onClickClear(model.channel.id)
                }

                // todo: bad fix until logo is fixed
                compoundDrawablePadding = 29.dp
                setPadding(19.5f.dp, paddingTop, paddingRight, paddingBottom)
            }
        }
        patcher.after<WidgetChannelsListAdapter>(
            RecyclerView::class.java,
            FragmentManager::class.java,
        ) {
            adapterRef = WeakReference(this)
        }
    }

    fun patchStores() {
        patcher.before<StoreStream>(
            "handleMessageCreate", Message::class.java
        ) { (_, msg: Message) ->
            handleMessageUpdate(msg)
        }

        patcher.before<StoreStream>(
            "handleMessageUpdate", Message::class.java
        ) { (_, msg: Message) ->
            handleMessageUpdate(msg)
        }

        patcher.before<StoreStream>(
            "handleMessageDelete", ModelMessageDelete::class.java
        ) { (_, deleteModel: ModelMessageDelete) ->
            handleMessageDelete(deleteModel)
        }
    }

    fun onClickClearAll() {
        val dialog =
            ConfirmDialog().setTitle("Clear Ghost").setDescription("Are you sure you want to clear all current ghosts?")

        dialog.setOnOkListener {
            clearMarkerTimestamps = msgCache.mapValues { it.value.timestamp }.toMutableMap()
            Utils.showToast("Cleared all ghost markers")
            @SuppressLint("NotifyDataSetChanged") adapterRef.get()?.notifyDataSetChanged()
            dialog.dismiss()
        }.show(getActivity().supportFragmentManager, "Clear Ghost")
    }

    fun onClickClear(id: Long) {
        val dialog = ConfirmDialog().setTitle("Clear Ghost")
            .setDescription("Are you sure you want to clear ghost for this channel?")

        dialog.setOnOkListener {
            clearMarkerTimestamps = clearMarkerTimestamps.apply { this[id] = msgCache[id]?.timestamp ?: 0 }
            Utils.showToast("Cleared ghost")
            rerender(id)
            dialog.dismiss()
        }.show(getActivity().supportFragmentManager, "Clear Ghost")
    }

    private fun handleMessageUpdate(msg: Message) {
        val gid = msg.m()
        if (gid == null) {
            val channelId = msg.g()

            val oldMsgId = msgCache[channelId]?.id ?: 0
            if (msg.o() > oldMsgId) {
                msgCache[channelId] = msg.toItem()
                rerender(channelId)
            }
        }
    }

    private fun handleMessageDelete(deleteModel: ModelMessageDelete) {
        msgCache[deleteModel.channelId]?.let { msg ->
            if (msg.id in deleteModel.messageIds) {
                msgCache.remove(deleteModel.channelId)
                rerender(deleteModel.channelId)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun refreshAll() {
        val channels = StoreStream.getChannels().getChannelsForGuild(0)
            .filterValues { it.D() == 1 || it.D() == 3 } // type == Type.DM && type == Type.GROUP_DM
            .keys.take(100)
        Utils.threadPool.execute {
            val res = Http.Request.newDiscordRNRequest("/channels/preload-messages", "POST")
                .executeWithJson(ChannelIdsPayload(channels)).json<List<Message>>(GsonUtils.gsonRestApi, responseType)
            msgCache = mutableMapOf(*res.map { it.g() to it.toItem() }.toTypedArray())

            Utils.mainThread.post {
                @SuppressLint("NotifyDataSetChanged") adapterRef.get()?.notifyDataSetChanged()
            }
        }
    }

    private fun rerender(id: Long) {
        val adapter = adapterRef.get() ?: return
        val idx = adapter.internalData.indexOfFirst { it.key == "3$id" }
        if (idx != -1) {
            Utils.mainThread.post {
                adapter.notifyItemChanged(idx)
            }
        }
    }

    private val responseType = TypeToken.getParameterized(List::class.java, Message::class.java).type

    data class ChannelIdsPayload(
        @SerializedName("channel_ids") val channelIds: List<Long>,
    )

    data class MessageItem(
        val id: Long, val timestamp: Long, val author: Long, val bot: Boolean?
    )

    fun Message.toItem() = MessageItem(
        id = this.o(), author = this.e().id, timestamp = this.D().g(), bot = this.e().e()
    )

    fun <T> Observable<T>.trackerSubscribe(
        onError: (Throwable) -> Unit = {}, onCompleted: () -> Unit = {}, onNext: (T) -> Unit
    ) = RxUtils.createActionSubscriber(
        onNext, onError = {
            logger.error(it)
            onError(it)
        }, onCompleted
    ).apply {
        subscriptions.add(this)
        subscribe(this)
    }

    fun <T> Observable<T>.withComputation(): Observable<T> = ObservableExtensionsKt.computationLatest<T>(this)

    fun getActivity() = ActivityProvider.`access$getINSTANCE$cp`().currentActivity as AppActivity
    fun getPluginRes(name: String, type: String) = resources!!.getIdentifier(name, type, "com.github.canny1913")
}