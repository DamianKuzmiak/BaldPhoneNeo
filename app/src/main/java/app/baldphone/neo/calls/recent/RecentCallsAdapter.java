package app.baldphone.neo.calls.recent;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import app.baldphone.neo.calls.CallLogItemType;
import app.baldphone.neo.utils.DateTimeFormatter;
import com.bald.uriah.baldphone.R;
import com.bumptech.glide.Glide;

public class RecentCallsAdapter extends ListAdapter<CallListEntry, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private OnCallClickListener listener;

    // Cache for drawables
    private static final LruCache<Integer, Drawable> drawableCache = new LruCache<>(2);

    public interface OnCallClickListener {
        void onCallClick(@NonNull CallItem item);
    }

    public RecentCallsAdapter() {
        super(DIFF_CALLBACK);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    public void setOnCallClickListener(OnCallClickListener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<CallListEntry> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull CallListEntry oldItem, @NonNull CallListEntry newItem) {
                    if (oldItem.getClass() != newItem.getClass()) return false;
                    if (oldItem instanceof CallHeader och && newItem instanceof CallHeader nch) {
                        return och.text().equals(nch.text());
                    }
                    if (oldItem instanceof CallItem oci && newItem instanceof CallItem nci) {
                        return oci.id() == nci.id();
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull CallListEntry oldItem, @NonNull CallListEntry newItem) {
                    return oldItem.equals(newItem);
                }
            };

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvHeader;

        HeaderVH(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_header);
        }
    }

    class ItemVH extends RecyclerView.ViewHolder {
        final ImageView profilePictureIV;
        final TextView nameTV;
        final TextView callTypeTV;
        final ImageView callTypeIV;
        final TextView callTimeTV;

        private CallItem boundItem;

        ItemVH(@NonNull View v) {
            super(v);
            profilePictureIV = v.findViewById(R.id.profile_pic);
            nameTV = v.findViewById(R.id.contact_name);
            callTypeTV = v.findViewById(R.id.tv_call_type);
            callTimeTV = v.findViewById(R.id.tv_call_time);
            callTypeIV = v.findViewById(R.id.iv_call_type);

            View contactLayout = v.findViewById(R.id.ll_contact_only);
            contactLayout.setOnClickListener(
                    view -> {
                        if (listener != null && boundItem != null) {
                            listener.onCallClick(boundItem);
                        }
                    });
        }

        void bind(CallItem item) {
            boundItem = item;

            nameTV.setText(getContactDisplayName(item));
            callTimeTV.setText(DateTimeFormatter.formatTimeWithRelativeOffset(item.date()));

            formatCallType(item.type());
            loadAvatar(item);

            int typefaceStyle = item.isNew() ? Typeface.BOLD : Typeface.NORMAL;
            nameTV.setTypeface(null, typefaceStyle);
            callTypeTV.setTypeface(null, typefaceStyle);
        }

        private CharSequence getContactDisplayName(CallItem item) {
            if (item.name() != null && !item.name().isEmpty()) return item.name();
            if (item.cachedFormattedNumber() != null && !item.cachedFormattedNumber().isEmpty())
                return item.cachedFormattedNumber();
            if (item.number() != null && !item.number().isEmpty()) return item.number();
            return itemView.getContext().getString(R.string.private_number);
        }

        private void loadAvatar(CallItem item) {
            Uri uri = (item.cachedPhotoUri() != null) ? Uri.parse(item.cachedPhotoUri()) : null;
            Drawable placeholder = getDefaultAvatar(itemView.getContext(), item.number());

            Glide.with(profilePictureIV.getContext())
                    .load(uri)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .dontAnimate()
                    .into(profilePictureIV);
        }

        private Drawable getDefaultAvatar(Context ctx, String number) {
            final int drawableRes =
                    (number == null || number.trim().isEmpty())
                            ? R.drawable.private_face_in_recent_calls
                            : R.drawable.face_in_recent_calls;

            Drawable drawable = drawableCache.get(drawableRes);
            if (drawable == null) {
                drawable = AppCompatResources.getDrawable(ctx, drawableRes);
                if (drawable != null) {
                    drawableCache.put(drawableRes, drawable);
                }
            }
            return drawable;
        }

        private void formatCallType(int type) {
            CallLogItemType displayType = CallLogItemType.fromSystemType(type);
            callTypeTV.setText(displayType.stringRes);
            callTypeIV.setImageResource(displayType.drawableRes);
            itemView.setBackgroundTintList(
                    ContextCompat.getColorStateList(itemView.getContext(), displayType.colorRes));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (getItem(position) instanceof CallHeader) ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inflater.inflate(R.layout.call_log_header, parent, false));
        } else {
            return new ItemVH(inflater.inflate(R.layout.call_log_item, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CallListEntry entry = getItem(position);
        if (holder instanceof HeaderVH hvh) {
            hvh.tvHeader.setText(((CallHeader) entry).text());
        } else if (holder instanceof ItemVH ivh) {
            ivh.bind((CallItem) entry);
        }
    }
}
