package com.driftdroid.android.launcher

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.T
import com.driftdroid.android.mii.Mii
import com.driftdroid.android.mii.MiiDatabase
import com.driftdroid.android.mii.MiiImages
import com.driftdroid.android.mii.MiiStudio
import com.driftdroid.android.profile.SaveFile
import com.driftdroid.android.ui.DriftSheet
import com.driftdroid.android.ui.FlowLayout
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.Lx
import java.io.IOException
import kotlin.random.Random

/**
 * Edits one Mii, or makes a new one: every part of the Wii Mii format, grouped like the Mii
 * Channel's own tabs, with a live picture. Saving writes that one slot of the Mii file back (with
 * a backup), and renames any license that races as this Mii.
 */
internal class MiiEditorPage(
    host: ModePickerActivity,
    private val slot: Int?,
    private val original: Mii?,
) : Page(host) {
    private val mii: Mii = original?.copy() ?: Mii.blank()
    private var dirty = false
    private var saving = false
    private val handler = Handler(Looper.getMainLooper())
    private val repaint = Runnable { paintPreview() }

    override val title: String get() = if (original == null) T("New Mii") else T("Edit %s").format(original.displayName)
    override val subtitle: String
        get() = when {
            saving -> T("Saving...")
            dirty -> T("Unsaved changes")
            slot != null -> T("Slot %s of 100").format(slot + 1)
            else -> T("Not saved yet")
        }

    private lateinit var preview: ImageView
    private lateinit var nameView: TextView
    private lateinit var controls: LinearLayout
    private val tabChips = ArrayList<TextView>()
    private var tab = 0

    private val tabs: List<Pair<String, () -> Unit>> =
        listOf(
            "Info" to ::infoTab,
            "Body" to ::bodyTab,
            "Face" to ::faceTab,
            "Hair" to ::hairTab,
            "Eyebrows" to ::browTab,
            "Eyes" to ::eyeTab,
            "Nose" to ::noseTab,
            "Mouth" to ::mouthTab,
            "Glasses" to ::glassesTab,
            "Facial hair" to ::facialHairTab,
            "Mole" to ::moleTab,
        )

    override fun build(): View {
        val row = Lx.hbox(context, Gravity.TOP)
        row.setPadding(dp(20f), dp(4f), dp(20f), 0)

        val left = Lx.card(context, 16f)
        left.gravity = Gravity.CENTER_HORIZONTAL
        preview = ImageView(context)
        preview.background = Lx.rounded(context, Lx.SURFACE_LO, 90f)
        left.addView(preview, Lx.lp(context, 168f, 168f))
        nameView = Lx.text(context, "", 18f, Lx.TEXT, Lx.HEAVY, 1)
        left.addView(nameView, Lx.lp(context, -2f, -2f, top = 12f))
        left.addView(Lx.caption(context, T("Changes show here as you make them"), Lx.FAINT, 2).apply { gravity = Gravity.CENTER }, Lx.lp(context, -2f, -2f, top = 4f))
        left.addView(Lx.button(context, "Save", Lx.Tone.PRIMARY, Icon.SAVE) { save() }, Lx.lp(context, -1f, -2f, top = 16f))
        left.addView(Lx.button(context, "Discard", Lx.Tone.GHOST, Icon.CLOSE) { leave() }, Lx.lp(context, -1f, -2f, top = 8f))
        val leftScroll = ScrollView(context).apply { addView(left) }
        row.addView(leftScroll, Lx.lp(context, 230f, -1f, end = 16f))

        val right = Lx.vbox(context)
        val chips = Lx.hbox(context)
        tabs.forEachIndexed { i, (label, _) ->
            val chip = Lx.chip(context, label, i == 0) { selectTab(i) }
            tabChips += chip
            chips.addView(chip, Lx.lp(context, -2f, -2f, end = 8f))
        }
        right.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })
        controls = Lx.vbox(context)
        controls.setPadding(dp(4f), dp(8f), dp(4f), dp(28f))
        val scroll = ScrollView(context)
        scroll.addView(controls, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(scroll, Lx.lp(context, -1f, 0f, 1f, top = 8f))
        row.addView(right, Lx.lp(context, 0f, -1f, 1f))

        selectTab(0)
        paintPreview()
        return row
    }

    override fun onBack(): Boolean {
        if (!dirty) return false
        leave()
        return true
    }

    override fun onClose() = handler.removeCallbacks(repaint)

    private fun leave() {
        if (!dirty) {
            host.back(force = true)
            return
        }
        DriftSheet.confirm(host, "Discard your changes?", "Your changes to this Mii haven't been saved.", "Discard", danger = true, cancelLabel = "Keep editing") {
            dirty = false
            host.back(force = true)
        }
    }

    private fun selectTab(index: Int) {
        tab = index
        tabChips.forEachIndexed { i, chip -> Lx.paintChip(chip, i == index) }
        controls.removeAllViews()
        tabs[index].second()
    }

    private fun changed() {
        dirty = true
        nameView.text = mii.displayName
        refreshHeader()
        handler.removeCallbacks(repaint)
        handler.postDelayed(repaint, 300)
    }

    private fun paintPreview() {
        nameView.text = mii.displayName
        MiiImages.bind(preview, mii.copy(), MiiStudio.Size.LARGE)
    }

    // --- Control builders ---

    private fun section(label: String) {
        controls.addView(Lx.overline(context, label), Lx.lp(context, top = if (controls.childCount == 0) 4f else 18f, bottom = 8f))
    }

    /** Numbered tiles for a part's shape; [noneFirst] labels tile 0 "None". */
    private fun types(label: String, count: Int, get: () -> Int, set: (Int) -> Unit, noneFirst: Boolean = false) {
        section(label)
        val flow = FlowLayout(context, dp(6f))
        val tiles = ArrayList<TextView>()
        fun paint() {
            val current = get()
            tiles.forEachIndexed { i, t ->
                val on = i == current
                t.background = Lx.pressable(context, if (on) Lx.ACCENT else Lx.SURFACE, 12f, if (on) Lx.ACCENT else Lx.LINE)
                t.setTextColor(if (on) Lx.ACCENT_TEXT else Lx.TEXT)
            }
        }
        for (i in 0 until count) {
            val text = if (noneFirst && i == 0) T("None") else (if (noneFirst) i else i + 1).toString()
            val tile = Lx.text(context, text, 13f, Lx.TEXT, Lx.BOLD, 1)
            tile.gravity = Gravity.CENTER
            tile.minWidth = dp(46f)
            tile.minHeight = dp(42f)
            tile.setPadding(dp(8f), 0, dp(8f), 0)
            tile.setOnClickListener {
                set(i)
                paint()
                changed()
            }
            tiles += tile
            flow.addView(tile)
        }
        paint()
        controls.addView(flow)
    }

    private fun colors(label: String, palette: IntArray, get: () -> Int, set: (Int) -> Unit, names: List<String>? = null) {
        section(label)
        val flow = FlowLayout(context, dp(10f))
        val swatches = ArrayList<View>()
        fun paint() {
            val current = get()
            swatches.forEachIndexed { i, s ->
                s.background = Lx.rounded(context, palette[i], 21f, if (i == current) Lx.TEXT else Lx.LINE, if (i == current) 3f else 1f)
            }
        }
        palette.forEachIndexed { i, _ ->
            val swatch = View(context)
            swatch.layoutParams = ViewGroup.LayoutParams(dp(42f), dp(42f))
            swatch.contentDescription = names?.getOrNull(i)?.let { T(it) } ?: T("Colour %s").format(i + 1)
            swatch.setOnClickListener {
                set(i)
                paint()
                changed()
            }
            swatches += swatch
            flow.addView(swatch)
        }
        paint()
        controls.addView(flow)
    }

    private fun slider(label: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit, format: (Int) -> String = { it.toString() }) {
        controls.addView(
            Lx.slider(context, label, min, max, get(), format) {
                set(it)
                changed()
            },
        )
    }

    private fun toggle(label: String, detail: String?, get: () -> Boolean, set: (Boolean) -> Unit) {
        controls.addView(
            Lx.toggleRow(context, label, detail, get()) {
                set(it)
                changed()
            },
        )
    }

    private fun choice(label: String, options: List<String>, get: () -> Int, set: (Int) -> Unit) {
        section(label)
        val row = Lx.hbox(context)
        val chips = ArrayList<TextView>()
        options.forEachIndexed { i, option ->
            val chip = Lx.chip(context, option, get() == i) {
                set(i)
                chips.forEachIndexed { j, c -> Lx.paintChip(c, j == i) }
                changed()
            }
            chips += chip
            row.addView(chip, Lx.lp(context, -2f, -2f, end = 8f))
        }
        controls.addView(row)
    }

    private fun field(label: String, hint: String, get: () -> String, set: (String) -> Unit) {
        section(label)
        val input = Lx.textField(context, hint, get(), Mii.NAME_CHARS)
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString().orEmpty()
                    if (text != get()) {
                        set(text)
                        changed()
                    }
                }
            },
        )
        controls.addView(input)
    }

    private val position: (Int) -> String = { it.toString() }

    // --- Tabs ---

    private fun infoTab() {
        field("Name", "Up to 10 characters", { mii.name }, { mii.name = it })
        field("Made by", "Creator's name", { mii.creatorName }, { mii.creatorName = it })
        choice("Gender", listOf("Boy", "Girl"), { if (mii.isGirl) 1 else 0 }, { mii.isGirl = it == 1 })
        colors("Favorite colour", Mii.FAVORITE_COLORS, { mii.favoriteColor }, { mii.favoriteColor = it }, Mii.FAVORITE_COLOR_NAMES)
        section("Birthday")
        slider("Month", 0, 12, { mii.birthMonth }, { mii.birthMonth = it }) { if (it == 0) T("Not set") else it.toString() }
        slider("Day", 0, 31, { mii.birthDay }, { mii.birthDay = it }) { if (it == 0) T("Not set") else it.toString() }
        section("More")
        toggle("Favorite", "Marked as a favorite in the Mii Channel", { mii.isFavorite }, { mii.isFavorite = it })
        toggle("Allow copying and mingling", "Lets this Mii travel to other consoles", { !mii.mingleOff }, { mii.mingleOff = !it })
    }

    private fun bodyTab() {
        section("Build")
        slider("Height", 0, 127, { mii.height }, { mii.height = it })
        slider("Weight", 0, 127, { mii.weight }, { mii.weight = it })
    }

    private fun faceTab() {
        types("Face shape", 8, { mii.faceShape }, { mii.faceShape = it })
        colors("Skin tone", Mii.SKIN_COLORS, { mii.skinColor }, { mii.skinColor = it })
        types("Features and makeup", 12, { mii.facialFeature }, { mii.facialFeature = it }, noneFirst = true)
    }

    private fun hairTab() {
        types("Hairstyle", 72, { mii.hairType }, { mii.hairType = it })
        colors("Hair colour", Mii.HAIR_COLORS, { mii.hairColor }, { mii.hairColor = it })
        section("Parting")
        toggle("Flip hairstyle", "Mirrors the parting", { mii.hairFlipped }, { mii.hairFlipped = it })
    }

    private fun browTab() {
        types("Eyebrows", 24, { mii.browType }, { mii.browType = it })
        colors("Colour", Mii.HAIR_COLORS, { mii.browColor }, { mii.browColor = it })
        section("Placement")
        slider("Size", 0, 8, { mii.browSize }, { mii.browSize = it })
        slider("Angle", 0, 11, { mii.browRotation }, { mii.browRotation = it })
        slider("Height", 3, 18, { mii.browVertical }, { mii.browVertical = it }, position)
        slider("Spacing", 0, 12, { mii.browSpacing }, { mii.browSpacing = it })
    }

    private fun eyeTab() {
        types("Eyes", 48, { mii.eyeType }, { mii.eyeType = it })
        colors("Colour", Mii.EYE_COLORS, { mii.eyeColor }, { mii.eyeColor = it })
        section("Placement")
        slider("Size", 0, 7, { mii.eyeSize }, { mii.eyeSize = it })
        slider("Angle", 0, 7, { mii.eyeRotation }, { mii.eyeRotation = it })
        slider("Height", 0, 18, { mii.eyeVertical }, { mii.eyeVertical = it }, position)
        slider("Spacing", 0, 12, { mii.eyeSpacing }, { mii.eyeSpacing = it })
    }

    private fun noseTab() {
        types("Nose", 12, { mii.noseType }, { mii.noseType = it })
        section("Placement")
        slider("Size", 0, 8, { mii.noseSize }, { mii.noseSize = it })
        slider("Height", 0, 18, { mii.noseVertical }, { mii.noseVertical = it }, position)
    }

    private fun mouthTab() {
        types("Mouth", 24, { mii.lipType }, { mii.lipType = it })
        colors("Colour", Mii.LIP_COLORS, { mii.lipColor }, { mii.lipColor = it })
        section("Placement")
        slider("Size", 0, 8, { mii.lipSize }, { mii.lipSize = it })
        slider("Height", 0, 18, { mii.lipVertical }, { mii.lipVertical = it }, position)
    }

    private fun glassesTab() {
        types("Glasses", 9, { mii.glassesType }, { mii.glassesType = it }, noneFirst = true)
        colors("Colour", Mii.GLASSES_COLORS, { mii.glassesColor }, { mii.glassesColor = it })
        section("Placement")
        slider("Size", 0, 7, { mii.glassesSize }, { mii.glassesSize = it })
        slider("Height", 0, 20, { mii.glassesVertical }, { mii.glassesVertical = it }, position)
    }

    private fun facialHairTab() {
        types("Mustache", 4, { mii.mustacheType }, { mii.mustacheType = it }, noneFirst = true)
        types("Beard", 4, { mii.beardType }, { mii.beardType = it }, noneFirst = true)
        colors("Colour", Mii.HAIR_COLORS, { mii.facialHairColor }, { mii.facialHairColor = it })
        section("Mustache placement")
        slider("Size", 0, 8, { mii.mustacheSize }, { mii.mustacheSize = it })
        slider("Height", 0, 16, { mii.mustacheVertical }, { mii.mustacheVertical = it }, position)
    }

    private fun moleTab() {
        section("Mole")
        toggle("Show a mole", null, { mii.moleOn }, { mii.moleOn = it })
        slider("Size", 0, 8, { mii.moleSize }, { mii.moleSize = it })
        slider("Height", 0, 30, { mii.moleVertical }, { mii.moleVertical = it }, position)
        slider("Across", 0, 16, { mii.moleHorizontal }, { mii.moleHorizontal = it }, position)
    }

    // --- Saving ---

    private fun save() {
        if (saving) return
        if (mii.name.isBlank()) {
            host.toast(T("Give the Mii a name first."), Lx.Tone.INFO)
            selectTab(0)
            return
        }
        host.guardGameRunning {
            saving = true
            refreshHeader()
            val result = mii.copy()
            host.work({ write(result) }) { outcome ->
                saving = false
                outcome.fold(
                    onSuccess = {
                        dirty = false
                        host.toast(T("%s saved.").format(result.displayName), Lx.Tone.PRIMARY)
                        host.broadcast(Data.MIIS)
                        host.broadcast(Data.SAVES)
                        host.back(force = true)
                    },
                    onFailure = {
                        refreshHeader()
                        host.toast(T("Couldn't save: %s").format(it.message ?: "unknown error"), Lx.Tone.DANGER)
                    },
                )
            }
        }
    }

    /** Runs off the main thread. */
    private fun write(result: Mii) {
        val snapshot = MiiDatabase.load(context) ?: throw IOException("there's no Mii file yet - start Mario Kart Wii once first")
        if (slot == null || original == null) {
            result.miiId = Mii.newMiiId(snapshot.slots.map { it.mii.miiId }.toSet())
            result.systemId = snapshot.systemId ?: (Random.nextLong(1, 0xFFFFFFFFL))
            val free = MiiDatabase.firstFreeSlot(snapshot.bytes)
            if (free < 0) throw IOException("all 100 Mii slots are in use - delete one first")
            MiiDatabase.save(context, MiiDatabase.withSlot(snapshot.bytes, free, result))
            return
        }
        val current = snapshot.slots.firstOrNull { it.index == slot }
        if (current == null || current.mii.miiId != original.miiId) {
            throw IOException("the Mii file changed while you were editing - reopen this Mii and try again")
        }
        MiiDatabase.save(context, MiiDatabase.withSlot(snapshot.bytes, slot, result))
        // Licenses keep their own copy of the name; keep them matching the Mii.
        if (result.name != original.name) {
            for ((game, license) in SaveFile.licensesUsing(context, result.miiId)) {
                runCatching { SaveFile.setLicenseMii(context, game, license.index, result) }
            }
        }
    }
}
