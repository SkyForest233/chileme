package com.agon.app.ui.components

// Official Miuix Glass dialog adapter used by the app's Miuix branches.
//
// All Miuix dialogs and Glass dropdowns render in the outermost Miuix Scaffold popup host. The app's
// Material3 shell is wrapped by that host only in MIUIX mode; page Miuix Scaffolds deliberately use
// `popupHost = {}` so overlays are not captured into a page backdrop. Dialogs use the distinct root
// backdrop (page + app bars, excluding the popup host and scrim). A null backdrop gives GlassDialog
// its opaque container fallback on API 26–32 or whenever blur is disabled.

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agon.app.ui.components.app.LocalMiuixBlurEnabled
import com.agon.app.ui.components.app.LocalMiuixRootBackdrop
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix (HyperOS) Glass dialog.
 *
 * The root Miuix Scaffold in [com.agon.app.MainApp] owns the popup host, even when this dialog is
 * called from a page-level Miuix Scaffold or the app-level batch-move overlay. Keep the call
 * unconditional and control visibility with [show], so Glass can retain its close animation.
 *
 * The Glass surface samples only the root app backdrop. When the backdrop is null (API 26–32,
 * shader support unavailable, or the user's blur preference is off), the library paints its opaque
 * container color instead. Keep default window/IME insets enabled; the M3 dialog branches have their
 * separate `DialogProperties(decorFitsSystemWindows = false)` and `stickyImePadding()` safeguards.
 *
 * **content must have one root node.** Overlay dialog content is placed inside the host's own
 * vertical layout; sibling roots have no guaranteed spacing. Prefer a single `Column` with
 * `Arrangement.spacedBy(...)`, leaving an explicit gap before the action row. The static guard is
 * `MiuixDialogContentTest`.
 */
@Composable
fun MiuixDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    summary: String = "",
    content: @Composable () -> Unit,
) {
    GlassDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        summary = summary,
        backdrop = LocalMiuixRootBackdrop.current.takeIf { LocalMiuixBlurEnabled.current },
        defaultWindowInsetsPadding = true,
        renderInRootScaffold = true,
        content = content,
    )
}

/** Glass dialog action with the official tonal role and Miuix button typography. */
@Composable
internal fun MiuixGlassDialogButton(
    text: String,
    onClick: () -> Unit,
    role: GlassDialogButtonRole,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassDialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        role = role,
    ) {
        MiuixText(text = text, style = MiuixTheme.textStyles.button)
    }
}
