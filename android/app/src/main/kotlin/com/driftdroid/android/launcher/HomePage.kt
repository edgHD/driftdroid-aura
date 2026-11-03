package com.driftdroid.android.launcher

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.driftdroid.android.ModManager
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.R
import com.driftdroid.android.RetroRewindInstallStatus
import com.driftdroid.android.SaveData
import com.driftdroid.android.T
import com.driftdroid.android.mii.MiiImages
import com.driftdroid.android.online.RetroWfc
import com.driftdroid.android.profile.SaveFile
import com.driftdroid.android.ui.AspectImageView
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.Lx

/** The first page: the two games with their Play/Install buttons, and a glance at everything else. */
internal class HomePage(host: ModePickerActivity) : Page(host) {
    override val title: String get() = T("Ready to race")
    override val subtitle: String get() = T("Pick a game, or browse mods, rooms and rankings")
    override val headerArt: Int get() = R.drawable.driftdroid_logo

    private lateinit var baseCard: GameCard
    private lateinit var retroCard: GameCard
    private lateinit var onlineTile: Tile
    private lateinit var racerTile: Tile
    private lateinit var modsTile: Tile
    private var lastRoomsFetch = 0L

    override fun build(): View {
        val column = Lx.vbox(context)
        column.setPadding(dp(20f), dp(4f), dp(20f), dp(24f))

        val games = Lx.hbox(context, Gravity.TOP)
        baseCard = GameCard(R.drawable.channel_mkwii, T("Mario Kart Wii"))
        retroCard = GameCard(R.drawable.channel_retro_rewind, T("Retro Rewind"))
        games.addView(baseCard.root, Lx.lp(context, 0f, -2f, 1f, end = 8f))
        games.addView(retroCard.root, Lx.lp(context, 0f, -2f, 1f, start = 8f))
        column.addView(games)

        val tiles = Lx.hbox(context, Gravity.TOP)
        onlineTile = Tile(Icon.PUBLIC, Lx.CYAN, T("Online now")) { host.navigate(Tab.ROOMS) }
        racerTile = Tile(Icon.FACE, Lx.GOLD, T("Your racer")) { host.navigate(Tab.PROFILE) }
        modsTile = Tile(Icon.EXTENSION, Lx.VIOLET, T("Mods")) { host.navigate(Tab.MODS) }
        tiles.addView(onlineTile.root, Lx.lp(context, 0f, -1f, 1f, end = 8f))
        tiles.addView(racerTile.root, Lx.lp(context, 0f, -1f, 1f, start = 4f, end = 4f))
        tiles.addView(modsTile.root, Lx.lp(context, 0f, -1f, 1f, start = 8f))
        column.addView(tiles, Lx.lp(context, top = 16f))

        val scroll = ScrollView(context)
        scroll.isFillViewport = true
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    override fun onShow() {
        refresh()
        if (System.currentTimeMillis() - lastRoomsFetch > 60_000) fetchRooms()
    }

    override fun onDataChanged(what: Data) {
        if (what == Data.ROOMS) refreshOnline() else refresh()
    }

    private fun fetchRooms() {
        lastRoomsFetch = System.currentTimeMillis()
        host.work({ RetroWfc.fetchRooms() }) { result ->
            if (result.isSuccess) refreshOnline() else onlineTile.set("—", T("Retro WFC is unreachable right now"))
        }
    }

    fun refresh() {
        refreshGames()
        refreshOnline()
        refreshRacer()
        refreshMods()
    }

    private fun refreshGames() {
        val romReady = host.isRomImported()
        val busy = RetroRewindInstallStatus.running
        baseCard.set(
            locked = !romReady,
            status = if (romReady) T("Ready") else T("Needs your disc image (.iso or .wbfs)"),
            statusColor = if (romReady) Lx.ACCENT else Lx.MUTED,
            primaryLabel = if (romReady) "Play" else "Install with ISO/WBFS",
            primaryIcon = if (romReady) Icon.PLAY else Icon.FOLDER,
            primaryEnabled = !busy,
            onPrimary = { host.playBase() },
            menu = null,
        )

        val retroInstalled = host.isRetroRewindInstalled()
        val notInPack = host.packWithoutRetroRewind()
        val version = host.retroRewindVersion()
        val status =
            when {
                busy -> RetroRewindInstallStatus.label.ifBlank { T("Installing...") }
                notInPack -> T("Not in your game pack")
                retroInstalled -> if (version != null) T("Version %s installed").format(version) else T("Installed")
                else -> T("300+ tracks, free. About 1.9 GB")
            }
        retroCard.set(
            locked = !retroInstalled || notInPack,
            status = status,
            statusColor = if (retroInstalled && !notInPack && !busy) Lx.ACCENT else Lx.MUTED,
            primaryLabel = if (retroInstalled && !notInPack) "Play" else "Install",
            primaryIcon = if (retroInstalled && !notInPack) Icon.PLAY else Icon.DOWNLOAD,
            primaryEnabled = !busy,
            onPrimary = { host.playRetroRewind() },
            menu = if (retroInstalled && !busy) ({ host.showRetroRewindMenu() }) else null,
        )
    }

    private fun refreshOnline() {
        val rooms = RetroWfc.lastRooms
        if (rooms == null) {
            onlineTile.set("…", T("Checking Retro WFC"))
            return
        }
        val active = rooms.activeRooms.size
        onlineTile.set(Lx.number(rooms.totalPlayers), T("racers in %s rooms").format(active))
    }

    private fun refreshRacer() {
        host.work({
            val save = SaveFile.load(context, SaveData.Game.RetroRewind) ?: SaveFile.load(context, SaveData.Game.MarioKartWii)
            save?.licenses?.firstOrNull()
        }) { result ->
            val license = result.getOrNull()
            if (license == null) {
                racerTile.set(T("No license"), T("Play once to make one"))
                racerTile.showMii(null, false)
            } else {
                racerTile.set(license.displayName, if (license.isOnline) T("%s VR").format(Lx.number(license.vr)) else T("Offline license"))
                racerTile.showMii(license.mii, true)
            }
        }
    }

    private fun refreshMods() {
        val packs = ModManager.installedPacks(context)
        val on = packs.count { it.enabled }
        modsTile.set(
            if (packs.isEmpty()) "0" else Lx.number(on),
            if (packs.isEmpty()) T("Browse GameBanana") else T("on, of %s installed").format(packs.size),
        )
    }

    /** A game: its banner art, what state it's in, and the one thing to do next. */
    private inner class GameCard(art: Int, name: String) {
        val root: LinearLayout = Lx.vbox(context)
        private val banner = AspectImageView(context, 1120f / 256f)
        private val scrim = View(context)
        private val statusView: TextView
        private val actions = Lx.hbox(context)

        init {
            root.background = Lx.rounded(context, Lx.SURFACE, 20f, Lx.LINE)
            Lx.clipRounded(root, 20f)
            val art2 = FrameLayout(context)
            banner.scaleType = ImageView.ScaleType.FIT_XY
            banner.setImageResource(art)
            art2.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            scrim.setBackgroundColor(Color.argb(150, 0, 0, 0))
            art2.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addView(art2)

            val body = Lx.vbox(context)
            body.setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
            body.addView(Lx.heading(context, name))
            statusView = Lx.caption(context, "", Lx.MUTED, 2)
            body.addView(statusView, Lx.lp(context, top = 4f))
            body.addView(actions, Lx.lp(context, top = 14f))
            root.addView(body)
        }

        fun set(
            locked: Boolean,
            status: String,
            statusColor: Int,
            primaryLabel: String,
            primaryIcon: Icon,
            primaryEnabled: Boolean,
            onPrimary: () -> Unit,
            menu: (() -> Unit)?,
        ) {
            scrim.visibility = if (locked) View.VISIBLE else View.GONE
            statusView.text = status
            statusView.setTextColor(statusColor)
            actions.removeAllViews()
            val tone = if (primaryIcon == Icon.PLAY) Lx.Tone.PRIMARY else Lx.Tone.INFO
            val primary = Lx.button(context, primaryLabel, tone, primaryIcon) { onPrimary() }
            Lx.setEnabled(primary, primaryEnabled)
            actions.addView(primary, Lx.lp(context, 0f, -2f, 1f))
            if (menu != null) {
                actions.addView(Lx.iconButton(context, Icon.MORE_VERT, "More", Lx.TEXT, Lx.SURFACE_HI, 44f) { menu() }, Lx.lp(context, 44f, 44f, start = 8f))
            }
        }
    }

    /** A small card with a big value: tapping it goes to that page. */
    private inner class Tile(icon: Icon, color: Int, label: String, onClick: () -> Unit) {
        val root: LinearLayout = Lx.vbox(context)
        private val value = Lx.text(context, "", 22f, Lx.TEXT, Lx.HEAVY, 1)
        private val detail = Lx.caption(context, "", Lx.MUTED, 2)
        private val mii = ImageView(context)
        private val iconView = Lx.iconBadge(context, icon, color, 36f)

        init {
            root.background = Lx.pressable(context, Lx.SURFACE, 18f, Lx.LINE)
            root.setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            root.setOnClickListener { onClick() }
            val top = Lx.hbox(context)
            top.addView(iconView)
            mii.visibility = View.GONE
            top.addView(mii, Lx.lp(context, 36f, 36f))
            top.addView(Lx.overline(context, label, color), Lx.lp(context, 0f, -2f, 1f, start = 10f))
            top.addView(Lx.iconView(context, Icon.BACK, Lx.FAINT, 16f).apply { rotation = 180f })
            root.addView(top)
            root.addView(value, Lx.lp(context, top = 12f))
            root.addView(detail, Lx.lp(context, top = 4f))
        }

        fun set(main: String, line: String) {
            value.text = main
            detail.text = line
        }

        fun showMii(m: com.driftdroid.android.mii.Mii?, visible: Boolean) {
            if (visible && m != null) {
                iconView.visibility = View.GONE
                mii.visibility = View.VISIBLE
                MiiImages.bind(mii, m)
            } else {
                iconView.visibility = View.VISIBLE
                mii.visibility = View.GONE
            }
        }
    }
}
