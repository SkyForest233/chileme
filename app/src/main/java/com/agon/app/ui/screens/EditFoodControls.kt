package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The Miuix-side selected container used by the editor's compact choice chips. */
internal enum class EditFoodChipTone { Default, Primary, Tertiary }

/** Shared section heading: the existing MD3 typography, mapped to Miuix's subtitle style. */
@Composable
internal fun EditFoodSectionTitle(text: String) {
    AppText(text, AppTextScale.Heading, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
}

/**
 * Editor text input. The Material3 path retains its OutlinedTextField configuration; Miuix uses
 * the pinned GlassTextField API, with placeholder/supporting copy rendered as a small helper below.
 */
@Suppress("DEPRECATION")
@Composable
internal fun EditFoodTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        Column(modifier = modifier) {
            GlassTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                colors = if (isError) {
                    GlassTextFieldDefaults.textFieldColors(
                        labelColor = MiuixTheme.colorScheme.error,
                        borderColor = MiuixTheme.colorScheme.error,
                    )
                } else {
                    GlassTextFieldDefaults.textFieldColors()
                },
                keyboardOptions = keyboardOptions,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            )
            val helper = if (isError) {
                errorText
            } else {
                listOfNotNull(placeholder, supportingText).joinToString(" · ").ifBlank { null }
            }
            if (helper != null) {
                if (isError) {
                    AppText(
                        helper,
                        AppTextScale.Hint,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        color = MiuixTheme.colorScheme.error,
                    )
                } else {
                    AppMutedText(
                        helper,
                        AppTextScale.Hint,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = isError,
            supportingText = (if (isError) errorText else supportingText)?.let { { Text(it) } },
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

/**
 * Choice chip for category/unit/location/shelf-life values. MD3 keeps FilterChip's own defaults
 * except where the old editor explicitly overrode its selected container color.
 */
@Composable
internal fun EditFoodFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    tone: EditFoodChipTone = EditFoodChipTone.Default,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val (container, content) = when (tone) {
            // The Miuix onSecondaryContainer role is low-contrast; match the root bridge's mapping.
            EditFoodChipTone.Default ->
                MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onTertiaryContainer
            EditFoodChipTone.Primary ->
                MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
            EditFoodChipTone.Tertiary ->
                MiuixTheme.colorScheme.tertiaryContainer to MiuixTheme.colorScheme.onTertiaryContainer
        }
        MiuixSurface(
            onClick = onClick,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
            shape = RoundedCornerShape(50),
            color = if (selected) container else MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) content else MiuixTheme.colorScheme.onSurface,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(label, style = MiuixTheme.textStyles.body2)
            }
        }
    } else {
        when (tone) {
            EditFoodChipTone.Default -> FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(),
            )
            EditFoodChipTone.Primary -> FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            EditFoodChipTone.Tertiary -> FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            )
        }
    }
}

/** Suggestion affordance for history matches; keeps AssistChip unchanged in the MD3 path. */
@Composable
internal fun EditFoodSuggestionChip(label: String, onClick: () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            onClick = onClick,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(label, style = MiuixTheme.textStyles.body2)
            }
        }
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(label) },
            shape = RoundedCornerShape(50),
        )
    }
}

/** Theme-native outlined action used by the camera, gallery, and production-date controls. */
@Composable
internal fun EditFoodOutlinedActionButton(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    miuixIcon: ImageVector = icon,
    iconSize: Dp = 18.dp,
    iconSpacing: Dp = 6.dp,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    miuixIcon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                SpacerForEditorAction(iconSpacing)
                MiuixText(label, style = MiuixTheme.textStyles.button)
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(50),
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
            SpacerForEditorAction(iconSpacing)
            Text(label)
        }
    }
}

@Composable
private fun SpacerForEditorAction(width: Dp) {
    Spacer(Modifier.width(width))
}
