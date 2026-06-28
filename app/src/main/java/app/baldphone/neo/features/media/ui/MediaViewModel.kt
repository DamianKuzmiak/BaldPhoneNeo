package app.baldphone.neo.features.media.ui

import android.app.Application
import android.net.Uri

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import app.baldphone.neo.features.media.MediaItem
import app.baldphone.neo.features.media.MediaMode
import app.baldphone.neo.features.media.data.MediaRepository

/**
 * Activity-scoped ViewModel shared between [MediaBrowserFragment] and [viewer.MediaViewerFragment].
 *
 * Holds the single source of truth for the media item list and the currently
 * selected position, enabling seamless state synchronization between the
 * gallery grid and the full-screen viewer.
 *
 * Observes the repository's media flow reactively.
 */
class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application.contentResolver)

    private val currentMode = MutableStateFlow(MediaMode.PHOTOS_AND_VIDEOS)
    private val externalItem = MutableStateFlow<MediaItem?>(null)

    val items: StateFlow<List<MediaItem>> =
        combine(
            currentMode.flatMapLatest { mode -> repository.getMediaFlow(mode) },
            externalItem
        ) { mediaList, externalItem ->
            if (externalItem != null && mediaList.none { it.uri == externalItem.uri }) {
                listOf(externalItem) + mediaList
            } else {
                mediaList
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedPosition = MutableStateFlow(0)
    val selectedPosition: StateFlow<Int> = _selectedPosition

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent

    private val _moreMenuClickEvent = MutableSharedFlow<Unit>()
    val moreMenuClickEvent: SharedFlow<Unit> = _moreMenuClickEvent

    /**
     * Loads media items from the MediaStore based on the provided [mode].
     */
    fun loadMedia(mode: Int) {
        externalItem.value = null
        currentMode.value = mode
    }

    /** Updates the currently selected position (e.g., when swiping in the viewer). */
    fun setCurrentPosition(position: Int) {
        _selectedPosition.value = position
    }

    /** Selects the item at [position] and emits a navigation event to open the viewer. */
    fun openViewer(position: Int) {
        setCurrentPosition(position)
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.OpenViewer)
        }
    }

    /**
     * Loads media and opens the viewer for the given [uri].
     * Ensures the URI exists in the list even if not found in MediaStore results.
     * Used for deep-linking or external entry points.
     */
    fun openViewerByUri(uri: Uri, mode: Int) {
        val item =
            if (repository.isVideo(uri)) {
                MediaItem.Video(-1L, uri)
            } else {
                MediaItem.Photo(-1L, uri)
            }
        externalItem.value = item
        currentMode.value = mode

        viewModelScope.launch {
            items
                .first { list ->
                    list.any { it.uri == uri }
                }.let { list ->
                    val index = list.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
                    openViewer(index)
                }
        }
    }

    /** Propagates More menu button clicks from Activity to the active Fragment. */
    fun onMoreMenuClicked() {
        viewModelScope.launch {
            _moreMenuClickEvent.emit(Unit)
        }
    }

    sealed interface NavigationEvent {
        data object OpenViewer : NavigationEvent

        data object CloseViewer : NavigationEvent
    }
}
