// Copyright (c) 2026 Damian Kuźmiak
// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.features.media

/**
 * Constants defining the media filtering modes.
 *
 * Values:
 *  0 = PHOTOS_AND_VIDEOS (default)
 *  1 = PHOTOS
 *  2 = VIDEOS
 */
object MediaMode {
    const val EXTRA = "media_mode"

    const val PHOTOS_AND_VIDEOS = 0
    const val PHOTOS = 1
    const val VIDEOS = 2
}
