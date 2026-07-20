package com.github.canny1913.settings

import android.app.AlertDialog
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.settings.SettingsDelegate
import com.aliucord.settings.delegate
import com.aliucord.utils.ViewUtils.addTo
import com.discord.utilities.dimen.DimenUtils
import com.discord.views.CheckedSetting
import com.lytefast.flexinput.R

class GhostedSettings(
    settingsAPI: SettingsAPI
): SettingsPage() {
    private val ghostedTimeIndexDelegate = settingsAPI.delegate("ghostedTime", GhostedTime.None.ordinal)
    var ghostedTimeIndex by ghostedTimeIndexDelegate
    val ghostedTime
        get() = GhostedTime.entries[ghostedTimeIndex]
    private val ignoreGroupDmsDelegate = settingsAPI.delegate("ignoreGroupDms", false)
    private val ignoreMutedDmsDelegate = settingsAPI.delegate("ignoreMutedDms", false)
    private val ignoreBotsDelegate = settingsAPI.delegate("ignoreBots", false)

    override fun onViewBound(view: View?) {
        super.onViewBound(view)
        val ctx = requireContext()
        setActionBarTitle("Ghosted Settings")
        createSetting(
            "Ignore Group DMs",
            "Exempts group dms from ghost check",
            ignoreGroupDmsDelegate
        ).apply(::addView)
        createSetting(
            "Ignore Bots",
            "Exempts bots from ghost check",
            ignoreBotsDelegate
        ).apply(::addView)
        createSetting(
            "Ignore Muted DMs",
            "Exempts muted dms from ghost check",
            ignoreMutedDmsDelegate
        ).apply(::addView)
        LinearLayout(
            ctx,
            null,
            0,
            R.i.UiKit_ViewGroup_LinearLayout_Horizontal
        ).apply layout@{ // todo: proper highlighter for this
            TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Compound_Left).apply {
                this.id = View.generateViewId()
                this.text = "Only ghost DMs active within this timeframe"
                this.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
                this@layout.addView(this)
            }
            val valueTextView = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Compound_Right).apply text@{
                this.id = View.generateViewId()
                this.text = ghostedTime.asString()
                this.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)

                this@layout.addView(this)
            }
            setOnClickListener {
                val items = GhostedTime.entries.map { it.asString() }.toTypedArray()
                val themeId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    android.R.style.Theme_DeviceDefault_Dialog_Alert
                } else {
                    android.R.style.Theme_DeviceDefault_Dialog
                }
                val builder = AlertDialog.Builder(ctx, themeId).setTitle("Select duration")
                    .setSingleChoiceItems(items, ghostedTime.ordinal) { dialog, selected ->
                        ghostedTimeIndex = selected
                        valueTextView.text = ghostedTime.asString()
                        dialog.dismiss()
                    }.setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                builder.create().show()
            }
            setPadding(paddingLeft, paddingTop, DimenUtils.dpToPixels(8), paddingBottom)

        }.addTo(linearLayout)
    }

    private fun createSetting(
        title: String?,
        description: String?,
        delegate: SettingsDelegate<Boolean>
    ): CheckedSetting {
        return Utils.createCheckedSetting(
            requireContext(), CheckedSetting.ViewType.SWITCH, title, description
        ).apply {
            var setting by delegate
            isChecked = setting
            setOnCheckedListener {
                setting = !setting
            }
        }
    }
}

enum class GhostedTime {
    None,
    Hour,
    Day,
    Week,
    Month;

    fun toMs() = when (this) {
        None -> Long.MAX_VALUE
        Hour -> 1L*60L*60L*1000L
        Day -> 1L*24L*60L*60L*1000L
        Week -> 1L*7L*24L*60L*60L*1000L
        Month -> 1L*30L*7L*24L*60L*60L*1000L
    }
    fun asString() = when (this) {
        None -> "None"
        Hour -> "1 hour"
        Day -> "1 day"
        Week -> "1 week"
        Month -> "1 month"
    }
}
