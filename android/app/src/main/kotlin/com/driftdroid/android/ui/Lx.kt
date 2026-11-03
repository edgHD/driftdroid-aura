package com.driftdroid.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.driftdroid.android.T
import java.text.NumberFormat
import kotlin.math.max

/**
 * The launcher's look: colours, type and small view builders shared by every page. Built on the
 * same palette as [DriftUi] (and so the native in-game settings drawer), so the launcher, its
 * sheets and the in-game menus read as one app.
 */
internal object Lx {
    const val BG = 0xFF0A0F22.toInt()
    const val RAIL = 0xFF0D1330.toInt()
    const val SURFACE = 0xFF141B39.toInt()
    const val SURFACE_HI = 0xFF1C2550.toInt()
    const val SURFACE_LO = 0xFF0F1530.toInt()
    const val LINE = 0xFF252F5E.toInt()
    const val TEXT = DriftUi.TEXT
    const val MUTED = DriftUi.MUTED
    const val FAINT = 0xFF67719A.toInt()
    const val ACCENT = DriftUi.ACCENT
    const val ACCENT_TEXT = DriftUi.ACCENT_TEXT
    const val CYAN = DriftUi.FOCUS
    const val GOLD = 0xFFFFC94A.toInt()
    const val SILVER = 0xFFC7D0E6.toInt()
    const val BRONZE = 0xFFE29A5C.toInt()
    const val DANGER = DriftUi.DANGER
    const val VIOLET = 0xFF9B8CFF.toInt()

    val HEAVY: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BOLD: Typeface = Typeface.DEFAULT_BOLD

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun alpha(color: Int, fraction: Float): Int = (color and 0x00FFFFFF) or ((fraction.coerceIn(0f, 1f) * 255).toInt() shl 24)

    // --- Shapes ---

    fun rounded(context: Context, color: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != 0) setStroke(dp(context, strokeDp).coerceAtLeast(1), strokeColor)
        }

    /** A rounded fill that ripples when pressed. */
    fun pressable(context: Context, color: Int, radiusDp: Float = 14f, strokeColor: Int = 0): Drawable {
        val shape = rounded(context, color, radiusDp, strokeColor)
        val mask = rounded(context, Color.WHITE, radiusDp)
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask)
    }

    fun card(context: Context, padDp: Float = 16f, color: Int = SURFACE): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(context, color, 18f, LINE)
            val p = dp(context, padDp)
            setPadding(p, p, p, p)
        }

    /** Clips a view (and its children, such as a banner image) to rounded corners. */
    fun clipRounded(view: View, radiusDp: Float) {
        val radius = dp(view.context, radiusDp).toFloat()
        view.clipToOutline = true
        view.outlineProvider =
            object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, radius)
                }
            }
    }

    // --- Text ---

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float,
        color: Int = TEXT,
        face: Typeface = Typeface.DEFAULT,
        maxLines: Int = 0,
    ): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            typeface = face
            includeFontPadding = false
            if (maxLines > 0) {
                this.maxLines = maxLines
                ellipsize = TextUtils.TruncateAt.END
            }
        }

    fun pageTitle(context: Context, value: String) = text(context, T(value), 22f, TEXT, HEAVY, 1)

    fun heading(context: Context, value: String, maxLines: Int = 1) = text(context, value, 16f, TEXT, BOLD, maxLines)

    fun body(context: Context, value: CharSequence, color: Int = MUTED) =
        text(context, value, 14f, color).apply { setLineSpacing(0f, 1.2f) }

    fun caption(context: Context, value: CharSequence, color: Int = MUTED, maxLines: Int = 1) = text(context, value, 12.5f, color, Typeface.DEFAULT, maxLines)

    /** Small all-caps label over a group. */
    fun overline(context: Context, value: String, color: Int = CYAN) =
        text(context, T(value).uppercase(), 11f, color, BOLD).apply { letterSpacing = 0.1f }

    /** A big number with a small label under it. */
    fun stat(context: Context, value: String, label: String, color: Int = TEXT, sizeSp: Float = 22f): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(context, value, sizeSp, color, HEAVY, 1))
            addView(caption(context, T(label)).apply { setPadding(0, dp(context, 3f), 0, 0) })
        }

    // --- Controls ---

    enum class Tone { PRIMARY, SECONDARY, GHOST, DANGER, INFO }

    private fun toneColors(tone: Tone): Pair<Int, Int> =
        when (tone) {
            Tone.PRIMARY -> ACCENT to ACCENT_TEXT
            Tone.SECONDARY -> SURFACE_HI to TEXT
            Tone.GHOST -> Color.TRANSPARENT to MUTED
            Tone.DANGER -> alpha(DANGER, 0.16f) to DANGER
            Tone.INFO -> alpha(CYAN, 0.16f) to CYAN
        }

    /** A pill button with an optional leading icon. */
    fun button(
        context: Context,
        label: String,
        tone: Tone = Tone.SECONDARY,
        icon: Icon? = null,
        compact: Boolean = false,
        onClick: () -> Unit,
    ): LinearLayout {
        val (fill, ink) = toneColors(tone)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(context, if (compact) 36f else 44f)
            val h = dp(context, if (compact) 12f else 18f)
            setPadding(h, 0, h, 0)
            background = pressable(context, fill, 22f, if (tone == Tone.GHOST) LINE else 0)
            isClickable = true
            isFocusable = true
            if (icon != null) {
                addView(
                    ImageView(context).apply { setImageDrawable(IconDrawable(icon, ink)) },
                    LinearLayout.LayoutParams(dp(context, 18f), dp(context, 18f)).apply { marginEnd = dp(context, 8f) },
                )
            }
            addView(text(context, T(label), if (compact) 13f else 14.5f, ink, BOLD, 1))
            setOnClickListener { onClick() }
            contentDescription = T(label)
        }
    }

    fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.4f
    }

    /** A round icon-only button. */
    fun iconButton(
        context: Context,
        icon: Icon,
        description: String,
        tint: Int = TEXT,
        fill: Int = SURFACE_HI,
        sizeDp: Float = 40f,
        onClick: () -> Unit,
    ): ImageView =
        ImageView(context).apply {
            setImageDrawable(IconDrawable(icon, tint))
            val p = dp(context, sizeDp * 0.25f)
            setPadding(p, p, p, p)
            background = pressable(context, fill, sizeDp / 2f)
            contentDescription = T(description)
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
            setOnClickListener { onClick() }
        }

    fun iconView(context: Context, icon: Icon, tint: Int, sizeDp: Float): ImageView =
        ImageView(context).apply {
            setImageDrawable(IconDrawable(icon, tint))
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    /** An icon on a soft round tint of the same colour, for list rows and tiles. */
    fun iconBadge(context: Context, icon: Icon, color: Int, sizeDp: Float = 40f): ImageView =
        ImageView(context).apply {
            setImageDrawable(IconDrawable(icon, color))
            val p = dp(context, sizeDp * 0.24f)
            setPadding(p, p, p, p)
            background = rounded(context, alpha(color, 0.15f), sizeDp / 2f)
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    fun chip(context: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        text(context, T(label), 13.5f, TEXT, MEDIUM, 1).apply {
            gravity = Gravity.CENTER
            minHeight = dp(context, 34f)
            setPadding(dp(context, 14f), 0, dp(context, 14f), 0)
            paintChip(this, selected)
            setOnClickListener { onClick() }
        }

    fun paintChip(chip: TextView, selected: Boolean) {
        val context = chip.context
        chip.background = pressable(context, if (selected) alpha(ACCENT, 0.18f) else SURFACE, 17f, if (selected) ACCENT else LINE)
        chip.setTextColor(if (selected) ACCENT else MUTED)
    }

    fun badge(context: Context, label: String, color: Int): TextView =
        text(context, T(label), 11f, color, BOLD, 1).apply {
            setPadding(dp(context, 8f), dp(context, 3f), dp(context, 8f), dp(context, 3f))
            background = rounded(context, alpha(color, 0.16f), 10f)
        }

    fun divider(context: Context): View =
        View(context).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1f))
        }

    fun spinner(context: Context, sizeDp: Float = 32f): ProgressBar =
        ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    fun progressBar(context: Context): ProgressBar =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(LINE)
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
        }

    /** A rounded search box. [onSearch] runs on the keyboard's search key; [onChange] on every edit. */
    fun searchField(
        context: Context,
        hint: String,
        onSearch: (String) -> Unit,
        onChange: ((String) -> Unit)? = null,
    ): Pair<LinearLayout, EditText> {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = rounded(context, SURFACE_LO, 20f, LINE)
        box.setPadding(dp(context, 12f), 0, dp(context, 6f), 0)
        box.addView(iconView(context, Icon.SEARCH, FAINT, 18f))
        val field = EditText(context)
        field.hint = T(hint)
        field.setHintTextColor(FAINT)
        field.setTextColor(TEXT)
        field.textSize = 14f
        field.background = null
        field.isSingleLine = true
        field.inputType = InputType.TYPE_CLASS_TEXT
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        field.setPadding(dp(context, 8f), dp(context, 8f), dp(context, 4f), dp(context, 8f))
        field.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v)
                onSearch(field.text.toString())
                true
            } else {
                false
            }
        }
        if (onChange != null) {
            field.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
                },
            )
        }
        box.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val clear =
            iconButton(context, Icon.CLOSE, "Clear", FAINT, Color.TRANSPARENT, 30f) {
                field.setText("")
                onSearch("")
            }
        box.addView(clear)
        return box to field
    }

    fun hideKeyboard(view: View) {
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    fun textField(context: Context, hint: String, initial: String = "", maxChars: Int = 0): EditText =
        EditText(context).apply {
            this.hint = T(hint)
            setText(initial)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
            if (maxChars > 0) filters = arrayOf(android.text.InputFilter.LengthFilter(maxChars))
            DriftUi.styleField(this)
        }

    /** A centred icon, title and message, with an optional button: empty lists, errors, offline. */
    fun emptyState(
        context: Context,
        icon: Icon,
        title: String,
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = dp(context, 24f)
            setPadding(p, p, p, p)
            addView(iconBadge(context, icon, CYAN, 56f))
            addView(
                heading(context, T(title), 2).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 14f), 0, dp(context, 6f))
                },
            )
            addView(body(context, T(message)).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(context, 360f), ViewGroup.LayoutParams.WRAP_CONTENT))
            if (actionLabel != null && action != null) {
                addView(
                    button(context, actionLabel, Tone.PRIMARY, onClick = action),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 16f) },
                )
            }
        }

    /** A centred spinner with a line under it. */
    fun loading(context: Context, message: String = "Loading..."): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(spinner(context, 36f))
            addView(caption(context, T(message)).apply { setPadding(0, dp(context, 12f), 0, 0) })
        }

    /** A row of the settings kind: icon, title, detail, then [trailing] or a chevron. */
    fun settingsRow(
        context: Context,
        icon: Icon,
        title: String,
        detail: String?,
        color: Int = CYAN,
        trailing: View? = null,
        onClick: (() -> Unit)?,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 60f)
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 8f))
            background = pressable(context, Color.TRANSPARENT, 14f)
            addView(iconBadge(context, icon, color, 38f))
            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL
            column.setPadding(dp(context, 14f), 0, dp(context, 8f), 0)
            column.addView(text(context, T(title), 15f, TEXT, MEDIUM, 1))
            if (!detail.isNullOrEmpty()) column.addView(caption(context, T(detail), MUTED, 2).apply { setPadding(0, dp(context, 3f), 0, 0) })
            addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (trailing != null) {
                addView(trailing)
            } else if (onClick != null) {
                addView(iconView(context, Icon.BACK, FAINT, 18f).apply { rotation = 180f })
            }
            if (onClick != null) setOnClickListener { onClick() } else isClickable = false
        }

    /** A labelled slider over [min]..[max] showing [format] of its value. */
    fun slider(
        context: Context,
        label: String,
        min: Int,
        max: Int,
        value: Int,
        format: (Int) -> String = { it.toString() },
        onChange: (Int) -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 6f), 0, dp(context, 6f))
            val top = LinearLayout(context)
            top.orientation = LinearLayout.HORIZONTAL
            top.addView(text(context, T(label), 14f, TEXT, MEDIUM, 1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = text(context, format(value), 14f, ACCENT, BOLD, 1)
            top.addView(valueView)
            addView(top)
            val seek = SeekBar(context)
            seek.max = max - min
            seek.progress = (value - min).coerceIn(0, max - min)
            seek.progressTintList = ColorStateList.valueOf(ACCENT)
            seek.progressBackgroundTintList = ColorStateList.valueOf(LINE)
            seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
            seek.setPadding(dp(context, 4f), dp(context, 10f), dp(context, 4f), dp(context, 6f))
            seek.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        valueView.text = format(min + progress)
                        onChange(min + progress)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar) {}

                    override fun onStopTrackingTouch(bar: SeekBar) {}
                },
            )
            addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    /** A label with an on/off switch at the end; the whole row toggles. */
    fun toggleRow(context: Context, label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 52f)
            setPadding(0, dp(context, 4f), 0, dp(context, 4f))
            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL
            column.addView(text(context, T(label), 14f, TEXT, MEDIUM, 1))
            if (!detail.isNullOrEmpty()) column.addView(caption(context, T(detail), MUTED, 2).apply { setPadding(0, dp(context, 3f), 0, 0) })
            addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val pill = PillSwitch(context)
            pill.isOn = checked
            addView(pill, LinearLayout.LayoutParams(dp(context, 50f), dp(context, 28f)).apply { marginStart = dp(context, 12f) })
            background = pressable(context, Color.TRANSPARENT, 12f)
            setOnClickListener {
                pill.isOn = !pill.isOn
                onChange(pill.isOn)
            }
        }

    // --- Layout helpers ---

    fun hbox(context: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
        }

    fun vbox(context: Context): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun space(context: Context, widthDp: Float = 0f, heightDp: Float = 0f): View =
        View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp)) }

    /** A flexible space that pushes what follows to the end of a row. */
    fun spring(context: Context): View = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    fun lp(
        context: Context,
        widthDp: Float = -1f,
        heightDp: Float = -2f,
        weight: Float = 0f,
        start: Float = 0f,
        top: Float = 0f,
        end: Float = 0f,
        bottom: Float = 0f,
    ): LinearLayout.LayoutParams {
        fun size(v: Float) =
            when (v) {
                -1f -> ViewGroup.LayoutParams.MATCH_PARENT
                -2f -> ViewGroup.LayoutParams.WRAP_CONTENT
                else -> dp(context, v)
            }
        return LinearLayout.LayoutParams(size(widthDp), size(heightDp), weight).apply {
            marginStart = dp(context, start)
            topMargin = dp(context, top)
            marginEnd = dp(context, end)
            bottomMargin = dp(context, bottom)
        }
    }

    // --- Formatting ---

    fun number(value: Long): String = NumberFormat.getIntegerInstance().format(value)

    fun number(value: Int): String = number(value.toLong())

    fun signed(value: Int): String = (if (value > 0) "+" else "") + number(value)

    fun fileSize(bytes: Long): String =
        when {
            bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toDouble())
            bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / (1L shl 10).toDouble())
            else -> "$bytes B"
        }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun ago(timeMs: Long?, now: Long = System.currentTimeMillis()): String {
        if (timeMs == null || timeMs <= 0) return ""
        val seconds = max(0L, (now - timeMs) / 1000)
        return when {
            seconds < 60 -> T("just now")
            seconds < 3600 -> T("%s min ago").format(seconds / 60)
            seconds < 86_400 -> T("%s h ago").format(seconds / 3600)
            seconds < 86_400 * 60 -> T("%s days ago").format(seconds / 86_400)
            else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(timeMs))
        }
    }
}

/** A ViewGroup that lays its children out left to right and wraps onto new lines. */
internal class FlowLayout(context: Context, private val gapPx: Int = Lx.dp(context, 8f)) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val unbounded = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (!unbounded && x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0
                y += lineHeight + gapPx
                lineHeight = 0
            }
            x += child.measuredWidth + gapPx
            widest = max(widest, x - gapPx)
            lineHeight = max(lineHeight, child.measuredHeight)
        }
        val width = if (unbounded) widest else maxWidth
        setMeasuredDimension(width + paddingLeft + paddingRight, resolveSize(y + lineHeight + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0
                y += lineHeight + gapPx
                lineHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + child.measuredWidth, paddingTop + y + child.measuredHeight)
            x += child.measuredWidth + gapPx
            lineHeight = max(lineHeight, child.measuredHeight)
        }
    }
}

/** An ImageView that keeps a fixed width:height ratio, sized from its width. */
internal class AspectImageView(context: Context, private val ratio: Float) : ImageView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width / ratio).toInt())
    }
}

/** A FrameLayout that is always as tall as it is wide times [ratio]. */
internal class AspectFrame(context: Context, private val ratio: Float) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((width / ratio).toInt(), MeasureSpec.EXACTLY),
        )
    }
}

/**
 * The launcher's backdrop: a deep navy gradient with two soft glows in the accent colours, like
 * stadium lights over a night track. Static, so it costs nothing while the launcher sits open.
 */
internal class LauncherBackground : Drawable() {
    private val base = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow2 = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        base.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0C1230.toInt(), 0xFF070A18.toInt(), Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(w * 0.92f, h * 0.02f, max(w, h) * 0.55f, Lx.alpha(Lx.CYAN, 0.16f), 0, Shader.TileMode.CLAMP)
        glow2.shader = RadialGradient(w * 0.15f, h * 1.05f, max(w, h) * 0.5f, Lx.alpha(Lx.ACCENT, 0.10f), 0, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, base)
        canvas.drawRect(bounds, glow)
        canvas.drawRect(bounds, glow2)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
