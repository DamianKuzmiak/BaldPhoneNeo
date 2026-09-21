package app.baldphone.neo.features.contacts.ui.details

import android.content.Context

import app.baldphone.neo.R
import app.baldphone.neo.features.calls.model.Call
import app.baldphone.neo.features.calls.model.CallLogItemType
import app.baldphone.neo.utils.formatAsElapsedDuration
import app.baldphone.neo.utils.formatTime
import app.baldphone.neo.utils.isSameDayAs
import app.baldphone.neo.utils.toRelativeDateString

/**
 * Formats a contact's call history into display-ready rows.
 *
 * The list is expected to be ordered as returned by the call log provider; day headers are derived
 * by comparing each call with its predecessor.
 */
internal fun List<Call>.toCallUiModels(context: Context): List<CallUiModel> =
    mapIndexed { index, call ->
        val previousCall = getOrNull(index - 1)
        val logType = CallLogItemType.fromSystemType(call.callType)
        val isFirstInDay = (previousCall == null) || !call.dateTime.isSameDayAs(previousCall.dateTime)

        val isVoiceCall = logType.isVoiceCall
        val isDurationVisible = isVoiceCall && call.duration > 0

        CallUiModel(
            callTypeStringRes = logType.stringRes,
            callTypeDrawableRes = logType.drawableRes,
            callTypeColorRes = logType.colorRes,
            isFirstInDay = isFirstInDay,
            relativeDate = if (isFirstInDay) call.dateTime.toRelativeDateString() else null,
            timeString = call.dateTime.formatTime(context),
            isDurationVisible = isDurationVisible,
            durationText =
                when {
                    isDurationVisible -> call.duration.toLong().formatAsElapsedDuration()
                    isVoiceCall -> context.getString(R.string.not_available_duration)
                    else -> ""
                }
        )
    }
