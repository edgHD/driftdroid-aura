package com.driftdroid.android.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.T
import com.driftdroid.android.mii.Mii
import com.driftdroid.android.mii.MiiImages
import com.driftdroid.android.mii.MiiStudio
import com.driftdroid.android.net.Http
import com.driftdroid.android.online.RetroWfc
import com.driftdroid.android.profile.FriendCode
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.Lx

/** Retro WFC's top 100 by VR, with a podium for the top three. */
internal class LeaderboardPage(host: ModePickerActivity) : Page(host) {
    override val title: String get() = T("Leaderboard")
    override val subtitle: String get() = T("Top 100 on Retro WFC by VR")

    private lateinit var frame: FrameLayout
    private val adapter = Adapter()
    private var players: List<RetroWfc.Ranked> = emptyList()
    private var loaded = false

    override fun actions(): List<View> {
        val (box, _) =
            Lx.searchField(context, "Look up a friend code", { text ->
                if (text.isBlank()) return@searchField
                val code = FriendCode.normalize(text)
                if (code == null) host.toast(T("That isn't a valid friend code."), Lx.Tone.DANGER) else host.open(PlayerPage(host, code, null, null))
            })
        box.layoutParams = LinearLayout.LayoutParams(dp(240f), dp(40f)).apply { marginEnd = dp(8f) }
        return listOf(box, Lx.iconButton(context, Icon.REFRESH, "Refresh") { load(true) })
    }

    override fun build(): View {
        frame = FrameLayout(context)
        return frame
    }

    override fun onShow() {
        if (!loaded) load(false)
    }

    private fun load(force: Boolean) {
        frame.removeAllViews()
        frame.addView(Lx.loading(context, "Loading the leaderboard..."), match())
        host.work({ RetroWfc.fetchTop(100, force) }) { result ->
            frame.removeAllViews()
            result.fold(
                onSuccess = {
                    loaded = true
                    players = it.sortedBy { p -> p.rank ?: Int.MAX_VALUE }
                    val list = RecyclerView(context)
                    list.layoutManager = LinearLayoutManager(context)
                    list.adapter = adapter
                    list.clipToPadding = false
                    list.setPadding(dp(20f), dp(4f), dp(20f), dp(20f))
                    adapter.notifyDataSetChanged()
                    frame.addView(list, match())
                },
                onFailure = {
                    frame.addView(
                        Lx.emptyState(context, Icon.WARNING, "Retro WFC didn't answer", T("Check your connection and try again (%s).").format(Http.describe(it)), "Try again") { load(true) },
                        match(),
                    )
                },
            )
        }
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun medal(rank: Int?): Int =
        when (rank) {
            1 -> Lx.GOLD
            2 -> Lx.SILVER
            3 -> Lx.BRONZE
            else -> Lx.MUTED
        }

    private fun openPlayer(p: RetroWfc.Ranked) = host.open(PlayerPage(host, p.friendCode, p.displayName, p.mii))

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val podium get() = players.size >= 3

        override fun getItemCount() = if (players.isEmpty()) 0 else if (podium) players.size - 2 else players.size

        override fun getItemViewType(position: Int) = if (podium && position == 0) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == 0) {
                object : RecyclerView.ViewHolder(Lx.hbox(context, Gravity.BOTTOM).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14f) }
                }) {}
            } else {
                val row = PlayerRow(context)
                val rank = Lx.text(context, "", 15f, Lx.MUTED, Lx.HEAVY, 1)
                rank.gravity = Gravity.CENTER
                row.lead.addView(rank, Lx.lp(context, 40f, -2f, end = 6f))
                object : RecyclerView.ViewHolder(row) {}
            }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == 0) {
                val box = holder.itemView as LinearLayout
                box.removeAllViews()
                for (index in intArrayOf(1, 0, 2)) {
                    box.addView(podiumCard(players[index], index == 0), Lx.lp(context, 0f, -2f, 1f, start = 5f, end = 5f))
                }
                return
            }
            val player = players[if (podium) position + 2 else position]
            val row = holder.itemView as PlayerRow
            ((row.lead.getChildAt(0)) as android.widget.TextView).apply {
                text = (player.rank ?: (position + 1)).toString()
                setTextColor(medal(player.rank))
            }
            val seen = player.lastSeen?.let { T("seen %s").format(Lx.ago(it)) }
            row.bind(player.mii, player.displayName, listOfNotNull(player.friendCode.ifBlank { null }, seen).joinToString(" · "))
            row.setValue(T("%s VR").format(Lx.number(player.vr)), Lx.GOLD, if (player.vr24h != 0) T("%s today").format(Lx.signed(player.vr24h)) else null)
            row.setOnClickListener { openPlayer(player) }
        }
    }

    private fun podiumCard(player: RetroWfc.Ranked, first: Boolean): View {
        val color = medal(player.rank)
        val card = Lx.vbox(context)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = Lx.pressable(context, Lx.SURFACE, 20f, Lx.alpha(color, 0.55f))
        card.setPadding(dp(12f), dp(if (first) 20f else 14f), dp(12f), dp(14f))
        card.addView(Lx.badge(context, "#${player.rank ?: "?"}", color), Lx.lp(context, -2f, -2f))
        val mii = ImageView(context)
        MiiImages.bind(mii, player.mii, MiiStudio.Size.LARGE)
        val size = if (first) 84f else 68f
        card.addView(mii, Lx.lp(context, size, size, top = 8f))
        card.addView(Lx.text(context, player.displayName, 15f, Lx.TEXT, Lx.BOLD, 1).apply { gravity = Gravity.CENTER }, Lx.lp(context, -2f, -2f, top = 8f))
        card.addView(Lx.text(context, T("%s VR").format(Lx.number(player.vr)), if (first) 20f else 17f, Lx.GOLD, Lx.HEAVY, 1), Lx.lp(context, -2f, -2f, top = 4f))
        if (player.vrWeek != 0) card.addView(Lx.caption(context, T("%s this week").format(Lx.signed(player.vrWeek)), Lx.MUTED), Lx.lp(context, -2f, -2f, top = 3f))
        card.setOnClickListener { openPlayer(player) }
        return card
    }
}

/** One player's Retro WFC profile: rank, VR and how it moved over the last week. */
internal class PlayerPage(
    host: ModePickerActivity,
    private val friendCode: String,
    private val knownName: String?,
    private val knownMii: Mii?,
) : Page(host) {
    override val title: String get() = profile?.displayName ?: knownName ?: friendCode
    override val subtitle: String get() = friendCode

    private var profile: RetroWfc.Ranked? = null
    private lateinit var frame: FrameLayout

    override fun actions(): List<View> =
        listOf(
            Lx.iconButton(context, Icon.OPEN_IN_NEW, "Open on rwfc.net") {
                runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rwfc.net/player/${friendCode.filter { it.isDigit() }}"))) }
            },
        )

    override fun build(): View {
        frame = FrameLayout(context)
        load()
        return frame
    }

    private fun load() {
        frame.removeAllViews()
        frame.addView(Lx.loading(context, "Loading the profile..."), match())
        host.work({
            val p = RetroWfc.fetchPlayer(friendCode)
            val history = runCatching { RetroWfc.fetchHistory(friendCode, 7) }.getOrDefault(emptyList())
            p to history
        }) { result ->
            frame.removeAllViews()
            result.fold(
                onSuccess = { (p, history) ->
                    profile = p
                    refreshHeader()
                    frame.addView(content(p, history), match())
                },
                onFailure = { error ->
                    val notFound = (error as? Http.HttpException)?.code == 404
                    frame.addView(
                        if (notFound) {
                            Lx.emptyState(context, Icon.PERSON, "Not on the leaderboard", "This friend code hasn't raced on Retro WFC yet, or not recently.")
                        } else {
                            Lx.emptyState(context, Icon.WARNING, "Couldn't load this player", T("Retro WFC didn't answer (%s).").format(Http.describe(error)), "Try again") { load() }
                        },
                        match(),
                    )
                },
            )
        }
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun content(p: RetroWfc.Ranked, history: List<RetroWfc.VrPoint>): View {
        val row = Lx.hbox(context, Gravity.TOP)
        row.setPadding(dp(20f), dp(4f), dp(20f), dp(24f))

        val left = Lx.card(context, 18f)
        left.gravity = Gravity.CENTER_HORIZONTAL
        val mii = ImageView(context)
        MiiImages.bind(mii, p.mii ?: knownMii, MiiStudio.Size.LARGE)
        left.addView(mii, Lx.lp(context, 120f, 120f))
        left.addView(Lx.text(context, p.displayName, 19f, Lx.TEXT, Lx.HEAVY, 1).apply { gravity = Gravity.CENTER }, Lx.lp(context, -2f, -2f, top = 10f))
        left.addView(Lx.caption(context, p.friendCode), Lx.lp(context, -2f, -2f, top = 4f))
        val badges = Lx.hbox(context)
        p.rank?.let { badges.addView(Lx.badge(context, T("Rank #%s").format(it), if (it <= 3) Lx.GOLD else Lx.CYAN), Lx.lp(context, -2f, -2f, end = 6f)) }
        p.vehiclePreference?.let { badges.addView(Lx.badge(context, if (it.equals("bike", true)) T("Bikes") else if (it.equals("kart", true)) T("Karts") else it, Lx.VIOLET)) }
        left.addView(badges, Lx.lp(context, -2f, -2f, top = 10f))
        p.lastSeen?.let { left.addView(Lx.caption(context, T("Last seen %s").format(Lx.ago(it)), Lx.FAINT), Lx.lp(context, -2f, -2f, top = 10f)) }
        left.addView(
            Lx.button(context, "Add as a friend", Lx.Tone.PRIMARY, Icon.PERSON_ADD) { PlayerActions.addFriend(host, p.friendCode, p.mii ?: knownMii, p.vr, null) },
            Lx.lp(context, -1f, -2f, top = 16f),
        )
        left.addView(Lx.button(context, "Copy friend code", Lx.Tone.SECONDARY, Icon.COPY) { PlayerActions.copy(host, p.friendCode) }, Lx.lp(context, -1f, -2f, top = 8f))
        row.addView(left, Lx.lp(context, 250f, -2f, end = 16f))

        val right = Lx.vbox(context)
        val stats = Lx.hbox(context)
        fun tile(value: String, label: String, color: Int, end: Float) {
            val c = Lx.card(context, 14f)
            c.addView(Lx.stat(context, value, label, color, 20f))
            stats.addView(c, Lx.lp(context, 0f, -1f, 1f, end = end))
        }
        tile(Lx.number(p.vr), "VR", Lx.GOLD, 8f)
        tile(Lx.signed(p.vr24h), "last 24 hours", Lx.TEXT, 8f)
        tile(Lx.signed(p.vrWeek), "last 7 days", Lx.TEXT, 8f)
        tile(Lx.signed(p.vrMonth), "last 30 days", Lx.TEXT, 0f)
        right.addView(stats)

        val chartCard = Lx.card(context, 16f)
        chartCard.addView(Lx.heading(context, T("VR, last 7 days")))
        if (history.size >= 2) {
            chartCard.addView(Lx.caption(context, T("Touch the line to read any point"), Lx.FAINT), Lx.lp(context, top = 3f))
            chartCard.addView(VrChart(context, history), Lx.lp(context, -1f, 190f, top = 12f))
        } else {
            chartCard.addView(Lx.body(context, T("No VR changes in the last 7 days.")), Lx.lp(context, top = 10f))
        }
        right.addView(chartCard, Lx.lp(context, top = 12f))
        row.addView(right, Lx.lp(context, 0f, -2f, 1f))

        val scroll = ScrollView(context)
        scroll.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }
}

/**
 * One player's VR over time: a single 2dp line on one axis with recessive gridlines, the latest
 * value marked and labelled at the end, and touch-to-read for any other point. The line colour is
 * a deeper step of the app green, checked for lightness and contrast against the card surface.
 */
@SuppressLint("ViewConstructor")
internal class VrChart(context: Context, private val points: List<RetroWfc.VrPoint>) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = Lx.dp(context, 2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val grid = Paint().apply {
        color = Lx.alpha(Lx.LINE, 0.8f)
        strokeWidth = Lx.dp(context, 1f).toFloat()
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Lx.dp(context, 11f).toFloat()
        color = Lx.FAINT
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Lx.dp(context, 12f).toFloat()
        color = Lx.TEXT
        typeface = Lx.BOLD
    }
    private val path = Path()
    private val box = RectF()
    private var touched = -1

    private val minVr = points.minOf { it.totalVr }
    private val maxVr = points.maxOf { it.totalVr }
    private val t0 = points.first().time
    private val t1 = points.last().time

    init {
        contentDescription =
            T("VR went from %s to %s over the last 7 days").format(Lx.number(points.first().totalVr), Lx.number(points.last().totalVr))
    }

    private val left get() = Lx.dp(context, 52f).toFloat()
    private val right get() = width - Lx.dp(context, 64f).toFloat()
    private val top get() = Lx.dp(context, 10f).toFloat()
    private val bottom get() = height - Lx.dp(context, 22f).toFloat()

    private fun x(time: Long) = if (t1 == t0) (left + right) / 2 else left + (right - left) * ((time - t0).toFloat() / (t1 - t0))

    private fun y(vr: Int): Float {
        val span = (maxVr - minVr).coerceAtLeast(1)
        // A little headroom so the line never sits on the frame.
        val pad = span * 0.1f
        return bottom - (bottom - top) * ((vr - minVr + pad) / (span + 2 * pad))
    }

    override fun onDraw(canvas: Canvas) {
        // Three recessive gridlines with their values on the one axis.
        for (i in 0..2) {
            val vr = minVr + (maxVr - minVr) * i / 2
            val gy = y(vr)
            canvas.drawLine(left, gy, right, gy, grid)
            val text = Lx.number(vr)
            canvas.drawText(text, left - Lx.dp(context, 8f) - label.measureText(text), gy + label.textSize / 3, label)
        }
        val format = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
        canvas.drawText(format.format(java.util.Date(t0)), left, height.toFloat() - Lx.dp(context, 4f), label)
        val endDate = format.format(java.util.Date(t1))
        canvas.drawText(endDate, right - label.measureText(endDate), height.toFloat() - Lx.dp(context, 4f), label)

        path.reset()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.time), y(p.totalVr)) else path.lineTo(x(p.time), y(p.totalVr))
        }
        canvas.drawPath(path, line)

        val last = points.last()
        drawMarker(canvas, x(last.time), y(last.totalVr))
        canvas.drawText(Lx.number(last.totalVr), x(last.time) + Lx.dp(context, 10f), y(last.totalVr) + value.textSize / 3, value)

        if (touched in points.indices) {
            val p = points[touched]
            val px = x(p.time)
            val py = y(p.totalVr)
            grid.color = Lx.MUTED
            canvas.drawLine(px, top, px, bottom, grid)
            grid.color = Lx.alpha(Lx.LINE, 0.8f)
            drawMarker(canvas, px, py)
            val time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(p.time))
            val text = "$time · ${Lx.number(p.totalVr)} VR"
            val w = value.measureText(text) + Lx.dp(context, 16f)
            val h = Lx.dp(context, 26f).toFloat()
            val bx = (px - w / 2).coerceIn(0f, width - w)
            val by = (py - h - Lx.dp(context, 12f)).coerceAtLeast(0f)
            box.set(bx, by, bx + w, by + h)
            dot.color = Lx.SURFACE_HI
            canvas.drawRoundRect(box, h / 2, h / 2, dot)
            canvas.drawText(text, bx + Lx.dp(context, 8f), by + h / 2 + value.textSize / 3, value)
        }
    }

    /** An 8dp dot in the line colour, ringed with the surface so it reads over the line. */
    private fun drawMarker(canvas: Canvas, cx: Float, cy: Float) {
        dot.color = Lx.SURFACE
        canvas.drawCircle(cx, cy, Lx.dp(context, 6f).toFloat(), dot)
        dot.color = LINE_COLOR
        canvas.drawCircle(cx, cy, Lx.dp(context, 4f).toFloat(), dot)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touched = points.indices.minByOrNull { kotlin.math.abs(x(points[it].time) - event.x) } ?: -1
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touched = -1
                invalidate()
            }
        }
        return true
    }

    companion object {
        /** Validated with the dataviz palette checker against the dark card surface. */
        const val LINE_COLOR = 0xFF4BA63A.toInt()
    }
}
