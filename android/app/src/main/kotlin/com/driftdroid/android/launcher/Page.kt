package com.driftdroid.android.launcher

import android.content.Context
import android.view.View
import com.driftdroid.android.ModePickerActivity
import com.driftdroid.android.ui.Icon
import com.driftdroid.android.ui.Lx

/** The launcher's top-level pages, in navigation-rail order. */
internal enum class Tab(val label: String, val icon: Icon) {
    HOME("Home", Icon.HOME),
    MODS("Mods", Icon.EXTENSION),
    ROOMS("Rooms", Icon.PUBLIC),
    RANKS("Ranks", Icon.TROPHY),
    PROFILE("Profile", Icon.FACE),
    SETTINGS("Settings", Icon.SETTINGS),
}

/**
 * One screen of the launcher. The top-level pages sit behind the navigation rail; detail pages
 * (a mod, a room, a player, the Mii editor) are pushed on top of them with a back arrow. The shell
 * ([ModePickerActivity]) owns the header, the back stack, toasts and the task bar.
 */
internal abstract class Page(val host: ModePickerActivity) {
    enum class Data { MODS, MIIS, SAVES, ROOMS, INSTALL }

    val context: Context get() = host

    abstract val title: String
    open val subtitle: String? get() = null

    /** A picture (drawable id) the header shows instead of the title and subtitle - Home's wordmark. */
    open val headerArt: Int? get() = null

    private var built: View? = null
    val view: View get() = built ?: build().also { built = it }

    protected abstract fun build(): View

    /** Buttons for the header's right-hand side. Built once per page. */
    open fun actions(): List<View> = emptyList()

    open fun onShow() {}

    open fun onHide() {}

    /** The page was popped off the stack for good. */
    open fun onClose() {}

    /** Something this page may show changed (a mod installed, a Mii saved, rooms refreshed...). */
    open fun onDataChanged(what: Data) {}

    /** Back was pressed while this page is on top. Return true to keep the page open. */
    open fun onBack(): Boolean = false

    protected fun dp(value: Float): Int = Lx.dp(context, value)

    protected fun refreshHeader() = host.refreshHeader(this)
}
