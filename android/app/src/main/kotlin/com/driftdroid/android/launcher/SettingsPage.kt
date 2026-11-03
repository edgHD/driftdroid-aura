package com.driftdroid.android.launcher

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.driftdroid.android.GraphicsSettings
import com.driftdroid.android.IconSettings
import com.driftdroid.android.LanguageSettings
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.PrivateServerSettings
import com.driftdroid.android.R
import com.driftdroid.android.SaveData
import com.driftdroid.android.T
import com.driftdroid.android.mii.MiiImages
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.Lx
import com.driftdroid.android.ui.PillSwitch

/** Everything that used to be a stack of grey buttons on the old launcher, grouped. */
internal class SettingsPage(host: ModePickerActivity) : Page(host) {
    override val title: String get() = T("Settings")
    override val subtitle: String get() = "${host.getString(R.string.app_name)} ${host.appVersionName()}"

    override fun build(): View {
        val columns = Lx.hbox(context, Gravity.TOP)
        columns.setPadding(dp(20f), dp(4f), dp(20f), dp(24f))

        val left = Lx.vbox(context)
        left.addView(group("Game") {
            row(Icon.TUNE, "Graphics", "Renderer, vertical mode and GPU driver", Lx.CYAN) { GraphicsSettings.show(host) }
            row(Icon.TRANSLATE, "Language", "For these screens and the game's menus", Lx.CYAN) { LanguageSettings.show(host) }
            row(Icon.SAVE, "Save data", "Import or export your licenses, unlocks and ghosts", Lx.GOLD) { SaveData.showDialog(host) }
            row(Icon.FOLDER, "Import game pack", "Game code made by DriftDroid Builder on a PC", Lx.VIOLET) { host.importGamePack() }
            row(Icon.SERVER, "Experimental server", "Only for people who run their own server", Lx.FAINT) { PrivateServerSettings.show(host) }
        })
        columns.addView(left, Lx.lp(context, 0f, -2f, 1f, end = 8f))

        val right = Lx.vbox(context)
        right.addView(group("App") {
            row(Icon.IMAGE, "App icon", "Put a shortcut with your own picture on the home screen", Lx.CYAN) { IconSettings.show(host) }
            val pill = PillSwitch(context)
            pill.isOn = MiiImages.onlineEnabled(context)
            pill.layoutParams = LinearLayout.LayoutParams(dp(50f), dp(28f)).apply { marginStart = dp(12f) }
            addView(
                Lx.settingsRow(context, Icon.FACE, "Mii pictures", "Draw Miis with Nintendo's online Mii renderer. Off: simple offline drawings", Lx.GOLD, pill) {
                    pill.isOn = !pill.isOn
                    MiiImages.setOnlineEnabled(context, pill.isOn)
                    host.broadcast(Data.MIIS)
                },
            )
            row(Icon.BUG, "Export diagnostics", "Logs to attach to a bug report - review before sharing", Lx.DANGER) { host.exportDiagnostics() }
        })
        right.addView(group("About") {
            row(Icon.OPEN_IN_NEW, "DriftDroid Aura on GitHub", "This app's source code and bug reports", Lx.CYAN) { open("https://github.com/edgHD/driftdroid-aura") }
            row(Icon.OPEN_IN_NEW, "Based on DriftDroid", "The original project, and DriftDroid Builder", Lx.CYAN) { open("https://github.com/dorPXP/driftdroid") }
            row(Icon.PUBLIC, "Retro WFC", "The online service behind Rooms and the Leaderboard", Lx.CYAN) { open("https://rwfc.net") }
            row(Icon.EXTENSION, "GameBanana", "Where the mods in the Mods browser come from", Lx.CYAN) { open("https://gamebanana.com/games/5896") }
            addView(
                Lx.caption(
                    context,
                    T("Online pages use Retro WFC's and GameBanana's public APIs, the same ones the Wheel Wizard tool uses on PC. No Nintendo code or game data is included or downloaded."),
                    Lx.FAINT,
                    4,
                ),
                Lx.lp(context, start = 12f, top = 8f, end = 12f, bottom = 4f),
            )
        })
        columns.addView(right, Lx.lp(context, 0f, -2f, 1f, start = 8f))

        val scroll = ScrollView(context)
        scroll.addView(columns, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun open(url: String) {
        runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun group(label: String, fill: LinearLayout.() -> Unit): View {
        val box = Lx.vbox(context)
        box.addView(Lx.overline(context, label), Lx.lp(context, start = 4f, bottom = 8f))
        val card = Lx.card(context, 6f)
        card.fill()
        box.addView(card)
        box.layoutParams = Lx.lp(context, bottom = 18f)
        return box
    }

    private fun LinearLayout.row(icon: Icon, title: String, detail: String, color: Int, onClick: () -> Unit) {
        addView(Lx.settingsRow(context, icon, title, detail, color, null, onClick))
    }
}
