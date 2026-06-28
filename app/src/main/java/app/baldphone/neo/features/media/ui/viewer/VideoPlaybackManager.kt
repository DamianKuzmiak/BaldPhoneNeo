package app.baldphone.neo.features.media.ui.viewer

import android.net.Uri
import android.view.View

/**
 * Manages video playback state, coordination, and UI updates for the Media Viewer.
 * Encapsulates the low-level [VideoPlaybackController].
 */
class VideoPlaybackManager {
    private val controller = VideoPlaybackController()
    private var activeHolder: MediaViewerAdapter.VideoViewHolder? = null

    private val playbackCallback =
        object : VideoPlaybackController.Callback {
            override fun onPlaybackStarted() {
                activeHolder?.updateUI(isPlaying = true)
            }

            override fun onPlaybackError() {
                activeHolder?.updateUI(isPlaying = false)
            }

            override fun onPlaybackCompleted() {
                stop()
            }
        }

    /**
     * Starts playing a video for the specified view holder.
     */
    fun play(holder: MediaViewerAdapter.VideoViewHolder, uri: Uri) {
        stop()
        activeHolder = holder

        val videoView = holder.videoView
        videoView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    videoView.removeOnAttachStateChangeListener(this)
                    if (activeHolder === holder) {
                        controller.stop(videoView)
                        activeHolder = null
                    }
                }
            }
        )

        controller.play(videoView, uri, playbackCallback)
    }

    /**
     * Stops the current video playback and resets the active holder.
     */
    fun stop() {
        val holder = activeHolder ?: return
        controller.stop(holder.videoView)
        holder.updateUI(isPlaying = false)
        activeHolder = null
    }

    /**
     * Pauses the current video playback.
     */
    fun pause() {
        val holder = activeHolder ?: return
        if (controller.isPlaying(holder.videoView)) {
            controller.pause(holder.videoView)
            holder.updateUI(isPlaying = false)
        }
    }

    /**
     * Handles the play/pause toggle button click.
     */
    fun togglePlayPause(holder: MediaViewerAdapter.VideoViewHolder, uri: Uri) {
        if (activeHolder === holder) {
            if (controller.isPrepared) {
                if (controller.isPlaying(holder.videoView)) {
                    controller.pause(holder.videoView)
                    holder.updateUI(isPlaying = false)
                } else {
                    holder.videoView.start()
                    holder.updateUI(isPlaying = true)
                }
            } else {
                play(holder, uri)
            }
        } else {
            play(holder, uri)
        }
    }

    /**
     * Toggles the visibility of the user interface overlay, such as playback controls,
     */
    fun toggleUIOverlay(holder: MediaViewerAdapter.VideoViewHolder) {
        if (controller.isPlaying(holder.videoView)) {
            holder.toggleButtonVisibility()
        }
    }
}
