package com.driftdroid.android.launcher

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.driftdroid.android.ModManager
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.T
import com.driftdroid.android.mods.GameBanana
import com.driftdroid.android.net.Http
import com.driftdroid.android.ui.AspectFrame
import com.driftdroid.android.ui.DriftSheet
import com.driftdroid.android.ui.DriftUi
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.IconDrawable
import com.driftdroid.android.ui.Lx
import com.driftdroid.android.ui.PillSwitch
import com.driftdroid.android.ui.RemoteImages

/**
 * Mods: what's installed (on/off, priority by drag, delete, import a file you have) and a browser
 * for GameBanana's Mario Kart Wii mods that installs with one tap - the mod browser and "My Stuff"
 * manager WheelWizard has on PC, for the phone.
 */
internal class ModsPage(host: ModePickerActivity) : Page(host) {
    override val title: String get() = T("Mods")
    override val subtitle: String
        get() = if (browsing) T("Mario Kart Wii mods on GameBanana") else T("Turned-on mods load the next time you start the game")

    private var browsing = false
    private lateinit var installedTab: TextView
    private lateinit var browseTab: TextView
    private lateinit var importButton: View
    private lateinit var searchBox: View
    private lateinit var searchField: EditText
    private lateinit var categoryRow: View
    private val categoryChips = ArrayList<TextView>()
    private lateinit var content: FrameLayout

    // Installed
    private lateinit var installedList: RecyclerView
    private val installedAdapter = InstalledAdapter()
    private lateinit var installedEmpty: View

    // Browse
    private lateinit var browseList: RecyclerView
    private val browseAdapter = BrowseAdapter()
    private lateinit var browseState: FrameLayout
    private var category = 0
    private var query = ""
    private var page = 0
    private var complete = false
    private var loading = false
    private var generation = 0
    private var installedIds: Set<Int> = emptySet()

    override fun build(): View {
        val root = Lx.vbox(context)
        root.setPadding(dp(20f), dp(2f), dp(20f), 0)

        val bar = Lx.hbox(context)
        installedTab = Lx.chip(context, "Installed", true) { showTab(false) }
        browseTab = Lx.chip(context, "Browse GameBanana", false) { showTab(true) }
        bar.addView(installedTab)
        bar.addView(browseTab, Lx.lp(context, -2f, -2f, start = 8f))
        bar.addView(Lx.spring(context))
        importButton = Lx.button(context, "Import a file", Lx.Tone.SECONDARY, Icon.UPLOAD, compact = true) { importFile() }
        bar.addView(importButton)
        val (box, field) = Lx.searchField(context, "Search mods", { startQuery(it) })
        searchBox = box
        searchField = field
        bar.addView(box, Lx.lp(context, 260f, 40f))
        root.addView(bar)

        val chips = Lx.hbox(context)
        GameBanana.CATEGORIES.forEachIndexed { index, cat ->
            val chip = Lx.chip(context, cat.label, index == 0) { selectCategory(index) }
            categoryChips += chip
            chips.addView(chip, Lx.lp(context, -2f, -2f, end = 8f))
        }
        val chipScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        categoryRow = chipScroll
        root.addView(chipScroll, Lx.lp(context, top = 12f))

        content = FrameLayout(context)
        root.addView(content, Lx.lp(context, -1f, 0f, 1f, top = 12f))

        // Installed
        installedList = RecyclerView(context)
        installedList.layoutManager = LinearLayoutManager(context)
        installedList.adapter = installedAdapter
        installedList.clipToPadding = false
        installedList.setPadding(0, 0, 0, dp(20f))
        val touch = ItemTouchHelper(DragCallback())
        touch.attachToRecyclerView(installedList)
        installedAdapter.dragStarter = { touch.startDrag(it) }
        installedEmpty =
            Lx.emptyState(
                context, Icon.EXTENSION, "No mods yet",
                "Browse GameBanana and install one with a tap, or import a .zip, .7z or .rar you already have.",
                "Browse mods",
            ) { showTab(true) }
        content.addView(installedList, match())
        content.addView(installedEmpty, match())

        // Browse
        browseList = RecyclerView(context)
        val grid = GridLayoutManager(context, 3)
        grid.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (browseAdapter.getItemViewType(position) == FOOTER) grid.spanCount else 1
            }
        browseList.layoutManager = grid
        browseList.adapter = browseAdapter
        browseList.clipToPadding = false
        browseList.setPadding(0, 0, 0, dp(20f))
        browseList.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val spans = ((right - left) / dp(190f)).coerceIn(2, 6)
                if (grid.spanCount != spans) v.post { grid.spanCount = spans }
            }
        }
        browseList.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0 && grid.findLastVisibleItemPosition() >= browseAdapter.itemCount - 6) loadMore()
                }
            },
        )
        browseState = FrameLayout(context)
        content.addView(browseList, match())
        content.addView(browseState, match())

        showTab(false)
        return root
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    override fun onShow() {
        refreshInstalled()
    }

    override fun onDataChanged(what: Data) {
        if (what == Data.MODS) refreshInstalled()
    }

    fun showTab(browse: Boolean) {
        browsing = browse
        Lx.paintChip(installedTab, !browse)
        Lx.paintChip(browseTab, browse)
        importButton.visibility = if (browse) View.GONE else View.VISIBLE
        searchBox.visibility = if (browse) View.VISIBLE else View.GONE
        categoryRow.visibility = if (browse) View.VISIBLE else View.GONE
        installedList.visibility = View.GONE
        installedEmpty.visibility = View.GONE
        browseList.visibility = if (browse) View.VISIBLE else View.GONE
        browseState.visibility = if (browse) View.VISIBLE else View.GONE
        if (browse) {
            if (browseAdapter.mods.isEmpty() && !loading) startQuery(query)
        } else {
            refreshInstalled()
        }
        refreshHeader()
    }

    // --- Installed ---

    private fun refreshInstalled() {
        val packs = ModManager.installedPacks(context)
        installedIds = packs.mapNotNull { it.meta?.gameBananaId }.toSet()
        installedTab.text = if (packs.isEmpty()) T("Installed") else T("Installed (%s)").format(packs.size)
        installedAdapter.items = packs.toMutableList()
        installedAdapter.notifyDataSetChanged()
        browseAdapter.notifyDataSetChanged()
        if (!browsing) {
            installedList.visibility = if (packs.isEmpty()) View.GONE else View.VISIBLE
            installedEmpty.visibility = if (packs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun importFile() {
        host.openDocument { uri ->
            if (uri == null) return@openDocument
            val fileName = ModManager.displayName(context, uri) ?: "mod"
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in GameBanana.ARCHIVE_EXTENSIONS) {
                host.toast(T("\"%s\" isn't a .zip, .7z or .rar file.").format(fileName), Lx.Tone.DANGER)
                return@openDocument
            }
            val sheet = DriftSheet(host, T("Name this mod"), compact = true)
            val field = sheet.textField("Mod name", fileName.substringBeforeLast('.'))
            sheet.button("Cancel") { sheet.dismiss() }
            sheet.button("Install", DriftUi.Style.PRIMARY) {
                val name = field.text.toString()
                sheet.dismiss()
                host.task(T("Installing \"%s\"...").format(name), { progress ->
                    ModManager.importFile(context, uri, name) { count -> progress(T("%s files unpacked").format(count)) }
                }) { result ->
                    result.fold(
                        onSuccess = {
                            host.toast(T("Installed \"%s\". It loads the next time you start the game.").format(it.title), Lx.Tone.PRIMARY)
                            host.broadcast(Data.MODS)
                        },
                        onFailure = { host.toast(T("Couldn't install it: %s").format(it.message ?: "unknown error"), Lx.Tone.DANGER) },
                    )
                }
            }
            sheet.show()
        }
    }

    private fun showPackMenu(pack: ModManager.Pack) {
        val sheet = DriftSheet(host, pack.title, pack.meta?.author?.let { T("by %s").format(it) })
        val id = pack.meta?.gameBananaId
        if (id != null) {
            sheet.action("Open on GameBanana", "See its page, comments and updates") {
                sheet.dismiss()
                runCatching { host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gamebanana.com/mods/$id"))) }
            }
        }
        sheet.action("Delete", "Removes the mod's files from the app") {
            sheet.dismiss()
            DriftSheet.confirm(host, T("Delete \"%s\"?").format(pack.title), "The mod's files are removed from the app.", "Delete", danger = true) {
                ModManager.delete(context, pack)
                host.broadcast(Data.MODS)
            }
        }
        sheet.show()
    }

    private inner class InstalledAdapter : RecyclerView.Adapter<InstalledAdapter.Holder>() {
        var items: MutableList<ModManager.Pack> = mutableListOf()
        var dragStarter: ((RecyclerView.ViewHolder) -> Unit)? = null

        inner class Holder(
            val row: LinearLayout,
            val handle: ImageView,
            val thumb: ImageView,
            val name: TextView,
            val detail: TextView,
            val toggle: PillSwitch,
            val more: ImageView,
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = Lx.hbox(context)
            row.background = Lx.pressable(context, Lx.SURFACE, 16f, Lx.LINE)
            row.setPadding(dp(6f), dp(8f), dp(10f), dp(8f))
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8f) }
            val handle = Lx.iconView(context, Icon.DRAG, Lx.FAINT, 22f)
            handle.setPadding(dp(4f), 0, dp(4f), 0)
            handle.layoutParams = LinearLayout.LayoutParams(dp(34f), dp(40f))
            row.addView(handle)
            val thumb = ImageView(context)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.background = Lx.rounded(context, Lx.SURFACE_HI, 10f)
            Lx.clipRounded(thumb, 10f)
            row.addView(thumb, Lx.lp(context, 72f, 44f, start = 4f))
            val column = Lx.vbox(context)
            val name = Lx.text(context, "", 15f, Lx.TEXT, Lx.MEDIUM, 1)
            val detail = Lx.caption(context, "", Lx.MUTED, 1)
            column.addView(name)
            column.addView(detail, Lx.lp(context, top = 4f))
            row.addView(column, Lx.lp(context, 0f, -2f, 1f, start = 14f, end = 10f))
            val toggle = PillSwitch(context)
            row.addView(toggle, Lx.lp(context, 50f, 28f))
            val more = Lx.iconButton(context, Icon.MORE_VERT, "More", Lx.MUTED, android.graphics.Color.TRANSPARENT, 40f) {}
            row.addView(more, Lx.lp(context, 40f, 40f, start = 6f))
            return Holder(row, handle, thumb, name, detail, toggle, more)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val pack = items[position]
            holder.name.text = pack.title
            val meta = pack.meta
            holder.detail.text =
                when {
                    meta?.gameBananaId != null -> listOfNotNull(meta.author?.let { T("by %s").format(it) }, "GameBanana", Lx.ago(meta.installedAt)).joinToString(" · ")
                    meta != null -> listOfNotNull(T("Imported file"), meta.fileName, Lx.ago(meta.installedAt)).joinToString(" · ")
                    else -> T("Imported mod")
                }
            holder.toggle.isOn = pack.enabled
            holder.row.alpha = if (pack.enabled) 1f else 0.65f
            RemoteImages.bind(holder.thumb, meta?.thumbnailUrl, dp(80f), IconDrawable(Icon.EXTENSION, Lx.FAINT))
            holder.thumb.scaleType = if (meta?.thumbnailUrl != null) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.CENTER_INSIDE
            if (meta?.thumbnailUrl == null) holder.thumb.setPadding(dp(22f), dp(8f), dp(22f), dp(8f)) else holder.thumb.setPadding(0, 0, 0, 0)
            holder.row.setOnClickListener {
                val current = items.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
                val on = !holder.toggle.isOn
                holder.toggle.isOn = on
                holder.row.alpha = if (on) 1f else 0.65f
                ModManager.setEnabled(current, on)
                items[holder.bindingAdapterPosition] = current.copy(enabled = on)
            }
            holder.more.setOnClickListener { items.getOrNull(holder.bindingAdapterPosition)?.let { showPackMenu(it) } }
            holder.handle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) dragStarter?.invoke(holder)
                false
            }
        }

        fun move(from: Int, to: Int) {
            val moved = items.removeAt(from)
            items.add(to, moved)
            notifyItemMoved(from, to)
        }
    }

    // Drag to reorder by the handle only, so a long press on the switch doesn't start a drag. The
    // order is saved the moment a drag ends.
    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            installedAdapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            ModManager.setOrder(context, installedAdapter.items.map { it.name })
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    // --- Browse ---

    private fun selectCategory(index: Int) {
        category = index
        categoryChips.forEachIndexed { i, chip -> Lx.paintChip(chip, i == index) }
        startQuery(query)
    }

    private fun startQuery(text: String) {
        query = text.trim()
        generation++
        page = 0
        complete = false
        loading = false
        browseAdapter.mods.clear()
        browseAdapter.footer = Footer.NONE
        browseAdapter.notifyDataSetChanged()
        showBrowseState(Lx.loading(context, "Loading mods..."))
        loadMore()
    }

    private fun loadMore() {
        if (loading || complete) return
        loading = true
        val wanted = page + 1
        val token = generation
        val cat = GameBanana.CATEGORIES[category]
        val text = query
        if (browseAdapter.mods.isNotEmpty()) {
            browseAdapter.footer = Footer.LOADING
            browseAdapter.notifyItemChanged(browseAdapter.itemCount - 1)
        }
        host.work({
            if (text.isEmpty()) GameBanana.list(cat.id, wanted) else GameBanana.search(text, wanted, cat)
        }) { result ->
            if (token != generation) return@work
            loading = false
            result.fold(
                onSuccess = { list ->
                    page = wanted
                    complete = list.complete || list.mods.isEmpty() && text.isEmpty()
                    val known = browseAdapter.mods.map { it.id }.toHashSet()
                    val fresh = list.mods.filter { known.add(it.id) }
                    browseAdapter.mods.addAll(fresh)
                    browseAdapter.footer = if (complete) Footer.END else Footer.NONE
                    browseAdapter.notifyDataSetChanged()
                    if (browseAdapter.mods.isEmpty() && (complete || list.mods.isEmpty())) {
                        complete = true
                        showBrowseState(
                            Lx.emptyState(
                                context, Icon.SEARCH, "Nothing found",
                                if (text.isEmpty()) "There are no mods in this category yet." else T("No mods match \"%s\".").format(text),
                            ),
                        )
                    } else {
                        showBrowseState(null)
                        // A search filtered down to a few results may not fill the screen: keep going.
                        if (fresh.size < 6 && !complete) browseList.post { loadMore() }
                    }
                },
                onFailure = { error ->
                    if (browseAdapter.mods.isEmpty()) {
                        showBrowseState(
                            Lx.emptyState(context, Icon.WARNING, "GameBanana didn't answer", T("Check your connection and try again (%s).").format(Http.describe(error)), "Try again") {
                                startQuery(query)
                            },
                        )
                    } else {
                        browseAdapter.footer = Footer.ERROR
                        browseAdapter.notifyItemChanged(browseAdapter.itemCount - 1)
                    }
                },
            )
        }
    }

    private fun showBrowseState(view: View?) {
        browseState.removeAllViews()
        if (view != null) browseState.addView(view, match())
        browseState.visibility = if (view != null && browsing) View.VISIBLE else View.GONE
    }

    private inner class BrowseAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val mods = ArrayList<GameBanana.ModSummary>()
        var footer = Footer.NONE

        inner class Card(
            val root: LinearLayout,
            val image: ImageView,
            val badge: TextView,
            val name: TextView,
            val author: TextView,
            val likes: TextView,
            val views: TextView,
        ) : RecyclerView.ViewHolder(root)

        inner class FooterHolder(val root: FrameLayout) : RecyclerView.ViewHolder(root)

        override fun getItemViewType(position: Int) = if (position >= mods.size) FOOTER else CARD

        override fun getItemCount() = mods.size + if (footer == Footer.NONE) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == FOOTER) {
                val frame = FrameLayout(context)
                frame.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f))
                return FooterHolder(frame)
            }
            val root = Lx.vbox(context)
            root.background = Lx.pressable(context, Lx.SURFACE, 16f, Lx.LINE)
            Lx.clipRounded(root, 16f)
            root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(5f), dp(5f), dp(5f), dp(5f))
            }
            val frame = AspectFrame(context, 16f / 10f)
            val image = ImageView(context)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setBackgroundColor(Lx.SURFACE_HI)
            frame.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val badge = Lx.badge(context, "Installed", Lx.ACCENT)
            badge.background = Lx.rounded(context, 0xE6142010.toInt(), 10f, Lx.ACCENT)
            frame.addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(8f), dp(8f), 0, 0)
            })
            root.addView(frame)
            val body = Lx.vbox(context)
            body.setPadding(dp(12f), dp(10f), dp(12f), dp(12f))
            val name = Lx.text(context, "", 14f, Lx.TEXT, Lx.BOLD, 2)
            name.minLines = 2
            body.addView(name)
            val author = Lx.caption(context, "", Lx.MUTED, 1)
            body.addView(author, Lx.lp(context, top = 4f))
            val stats = Lx.hbox(context)
            stats.addView(Lx.iconView(context, Icon.THUMB_UP, Lx.FAINT, 13f))
            val likes = Lx.caption(context, "", Lx.FAINT)
            stats.addView(likes, Lx.lp(context, -2f, -2f, start = 5f, end = 12f))
            stats.addView(Lx.iconView(context, Icon.VISIBILITY, Lx.FAINT, 13f))
            val views = Lx.caption(context, "", Lx.FAINT)
            stats.addView(views, Lx.lp(context, -2f, -2f, start = 5f))
            body.addView(stats, Lx.lp(context, top = 8f))
            root.addView(body)
            return Card(root, image, badge, name, author, likes, views)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is FooterHolder) {
                holder.root.removeAllViews()
                val view: View =
                    when (footer) {
                        Footer.LOADING, Footer.NONE -> Lx.spinner(context, 28f)
                        Footer.END -> Lx.caption(context, T("That's everything"), Lx.FAINT)
                        Footer.ERROR -> Lx.button(context, "Couldn't load more - try again", Lx.Tone.GHOST, Icon.REFRESH, compact = true) { loadMore() }
                    }
                holder.root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                return
            }
            val card = holder as Card
            val mod = mods[position]
            card.name.text = mod.name
            card.author.text = listOf(mod.author, mod.category).filter { it.isNotBlank() }.joinToString(" · ")
            card.likes.text = Lx.number(mod.likes)
            card.views.text = Lx.number(mod.views)
            card.badge.visibility = if (mod.id in installedIds) View.VISIBLE else View.GONE
            RemoteImages.bind(card.image, mod.previewUrl ?: mod.thumbnailUrl, dp(240f), null)
            card.root.setOnClickListener { host.open(ModDetailPage(host, mod)) }
        }
    }

    private enum class Footer { NONE, LOADING, END, ERROR }

    private companion object {
        const val CARD = 0
        const val FOOTER = 1
    }
}
