package com.github.canny1913.settings

import android.view.View
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.settings.delegate
import com.aliucord.views.Button

class BetterEmojiSettings(
    private val settingsAPI: SettingsAPI
) : SettingsPage() {
    var SettingsAPI.savedPositions: ArrayList<String> by settingsAPI.delegate(arrayListOf())


    override fun onViewBound(view: View) {
        super.onViewBound(view)

        setActionBarTitle("BetterEmoji Settings")
        val ctx = requireContext()

        val saveButton = Button(ctx).apply {
            text = "Reset category order"
            setOnClickListener {
                settingsAPI.savedPositions = arrayListOf()
                Utils.showToast("Success!")
                Utils.promptRestart()
            }
        }
        addView(saveButton)
    }
}
