package app.baldphone.neo.features.media.ui.viewer

import android.net.Uri
import android.util.Log
import android.widget.VideoView

/**
 * Encapsulates the [android.widget.VideoView] playback state machine: preparing, playing, pausing, and stopping.
 */
class VideoPlaybackController {
    var isPrepared: Boolean = false
        private set

    interface Callback {
        /** Called when the video is prepared and starts playing. */
        fun onPlaybackStarted()

        /** Called when an error occurs during playback. */
        fun onPlaybackError()

        /** Called when the video finishes playing. */
        fun onPlaybackCompleted()
    }

    /**
     * Prepares and starts playback of the given [uri] on the [videoView].
     */
    fun play(videoView: VideoView, uri: Uri, callback: Callback) {
        if (isPrepared && videoView.isPlaying) return

        stop(videoView)

        videoView.setOnPreparedListener {
            isPrepared = true
            videoView.start()
            callback.onPlaybackStarted()
        }

        videoView.setOnCompletionListener {
            isPrepared = false
            callback.onPlaybackCompleted()
        }

        videoView.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "VideoView playback error: what=$what, extra=$extra")
            isPrepared = false
            callback.onPlaybackError()
            true
        }

        videoView.setVideoURI(uri)
    }

    /**
     * Pauses the currently playing video.
     */
    fun pause(videoView: VideoView) {
        if (isPrepared && videoView.isPlaying) {
            videoView.pause()
        }
    }

    /**
     * Returns whether the [videoView] is currently playing.
     */
    fun isPlaying(videoView: VideoView): Boolean = isPrepared && videoView.isPlaying

    /**
     * Stops playback and releases all listeners. Resets the prepared state.
     */
    fun stop(videoView: VideoView) {
        if (isPrepared) {
            videoView.stopPlayback()
        }
        isPrepared = false
        videoView.setOnPreparedListener(null)
        videoView.setOnCompletionListener(null)
        videoView.setOnErrorListener(null)
    }

    companion object {
        private const val TAG = "VideoPlaybackController"
    }
}
