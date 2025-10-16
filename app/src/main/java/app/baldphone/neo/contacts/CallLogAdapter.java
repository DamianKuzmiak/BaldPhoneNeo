package app.baldphone.neo.contacts;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import app.baldphone.neo.calls.CallLogItemType;
import app.baldphone.neo.utils.DateTimeFormatter;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.databases.calls.Call;
import com.bald.uriah.baldphone.views.ModularRecyclerView;

import java.text.DateFormat;
import java.util.List;

public class CallLogAdapter extends ModularRecyclerView.ModularAdapter<CallLogAdapter.ViewHolder> {
    private final List<Call> callList;

    public CallLogAdapter(List<Call> callList) {
        this.callList = callList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.contact_call_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Call call = callList.get(position);
        Call previousCall = (position > 0) ? callList.get(position - 1) : null;
        holder.bind(call, previousCall);
    }

    @Override
    public int getItemCount() {
        return callList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView callTypeIcon;
        final ImageView durationIcon;
        final TextView callTimeLabel;
        final TextView callTypeLabel;
        final TextView dateHeader;
        final TextView durationLabel;

        public ViewHolder(View itemView) {
            super(itemView);
            callTimeLabel = itemView.findViewById(R.id.tv_time);
            callTypeIcon = itemView.findViewById(R.id.iv_call_type);
            callTypeLabel = itemView.findViewById(R.id.tv_call_type);
            dateHeader = itemView.findViewById(R.id.day);
            durationIcon = itemView.findViewById(R.id.iv_calltime);
            durationLabel = itemView.findViewById(R.id.tv_duration);
        }

        public void bind(final Call call, @Nullable Call previousCall) {

            final CallLogItemType logType = CallLogItemType.fromSystemType(call.callType);
            setCallType(logType);

            final boolean shouldShowHeader =
                    previousCall == null || !DateTimeFormatter.isSameDay(previousCall.dateTime, call.dateTime);

            if (shouldShowHeader) {
                dateHeader.setVisibility(View.VISIBLE);
                dateHeader.setText(DateTimeFormatter.toRelativeDateString(call.dateTime));
            } else {
                dateHeader.setVisibility(View.GONE);
            }

            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(itemView.getContext());
            callTimeLabel.setText(timeFormat.format(call.dateTime));

            setCallDuration(logType, call.duration);
        }

        public void setCallType(@NonNull CallLogItemType type) {
            callTypeLabel.setText(type.stringRes);
            callTypeIcon.setImageResource(type.drawableRes);
            callTypeLabel.setTextColor(
                    ContextCompat.getColor(itemView.getContext(), type.colorRes));
        }

        private void setCallDuration(CallLogItemType logType, int duration) {
            boolean isVoiceCall =
                    logType == CallLogItemType.INCOMING
                            || logType == CallLogItemType.OUTGOING
                            || logType == CallLogItemType.VOICEMAIL;

            if (isVoiceCall && duration > 0) {
                durationIcon.setVisibility(View.VISIBLE);
                durationLabel.setText(DateUtils.formatElapsedTime(duration));
            } else {
                durationIcon.setVisibility(View.GONE);
                durationLabel.setText(isVoiceCall ? "---" : "");
            }
        }
    }
}
