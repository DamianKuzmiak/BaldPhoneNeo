package app.baldphone.neo.launcher.apps

import android.widget.ImageView

import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder

import app.baldphone.neo.launcher.apps.data.PredefinedApps
import app.baldphone.neo.launcher.apps.data.db.AppEntry
import app.baldphone.neo.launcher.apps.sync.AppIconStorage

import com.bald.uriah.baldphone.R

/**
 * Handles binding app icons to ImageViews using Coil.
 */
object AppIconBinder {
    @JvmStatic
    fun loadPic(app: AppEntry?, imageView: ImageView) {
        val icon: Any? =
            when {
                app == null -> {
                    null
                }

                app.isPredefined -> {
                    PredefinedApps.getIconResId(app.componentName)
                }

                else -> {
                    AppIconStorage.getCachedIconFile(
                        imageView.context.applicationContext,
                        app.componentName,
                        app.userId
                    )
                }
            }

        imageView.load(icon) {
            placeholder(R.drawable.ic_default_app_icon)
            error(android.R.drawable.sym_def_app_icon)
            fallback(R.drawable.ic_default_app_icon)
            crossfade(false)
        }
    }
}
