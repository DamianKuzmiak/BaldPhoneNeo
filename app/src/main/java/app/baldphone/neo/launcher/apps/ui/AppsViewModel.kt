package app.baldphone.neo.launcher.apps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

import app.baldphone.neo.launcher.apps.data.AppsRepository
import app.baldphone.neo.launcher.apps.data.PredefinedApps
import app.baldphone.neo.launcher.apps.data.db.AppEntry

/**
 * ViewModel for [AppsActivity].
 */
class AppsViewModel : ViewModel() {
    private val showHeaders = MutableStateFlow(true)
    private val isChooseMode = MutableStateFlow(false)

    fun setShowHeaders(show: Boolean) {
        showHeaders.value = show
    }

    fun setIsChooseMode(isChooseMode: Boolean) {
        this.isChooseMode.value = isChooseMode
    }

    val appItems: StateFlow<List<AppListItem>> =
        combine(
            AppsRepository.allAppsFlow,
            showHeaders,
            isChooseMode
        ) { apps, showHeaders, chooseMode ->
            apps
                .asSequence()
                .filter { app ->
                    // If in choose mode, show everything. Otherwise, hide predefined/internal apps.
                    chooseMode || !PredefinedApps.isAppsActivity(app.componentName)
                }
//                .sortedBy { it.label.lowercase() } // Explicitly ensure sorting
                .toList()
                .toAppListItems(showHeaders)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private fun List<AppEntry>.toAppListItems(showHeaders: Boolean): List<AppListItem> {
        if (isEmpty()) return emptyList()
        if (!showHeaders) return map { AppListItem.App(it) }

        val result = ArrayList<AppListItem>(size + 16)
        var lastLetter: Char? = null

        for (app in this) {
            val rawChar = app.label.firstOrNull()?.uppercaseChar() ?: '#'
            val firstChar = if (rawChar.isLetter()) rawChar else '#'

            if (firstChar != lastLetter) {
                result.add(AppListItem.Header(firstChar.toString()))
                lastLetter = firstChar
            }
            result.add(AppListItem.App(app))
        }
        return result
    }
}

sealed class AppListItem {
    data class Header(val letter: String) : AppListItem()

    data class App(val entry: AppEntry) : AppListItem()
}
