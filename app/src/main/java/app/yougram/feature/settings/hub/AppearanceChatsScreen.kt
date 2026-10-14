package app.yougram.feature.settings.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.SettingsScreen
import app.yougram.feature.settings.component.LocalEmbeddedPage
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.general.ChatSettingsScreen
import app.yougram.feature.settings.general.FoldersScreen

/** Одна страница «Внешний вид и чаты»: оформление, настройки чатов и папки с чатами подряд. */
@Composable
fun AppearanceChatsScreen(
    settings: SettingsRepository,
    plates: PlateRepository,
    details: SettingsDetailsViewModel,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    SettingsPageColumn(contentPadding) {
        CompositionLocalProvider(LocalEmbeddedPage provides true) {
            SettingsScreen(settings, plates, PaddingValues())
            ChatSettingsScreen(settings, PaddingValues(), onNavigate)
            FoldersScreen(details, PaddingValues())
        }
    }
}
