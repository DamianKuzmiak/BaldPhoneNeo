package app.baldphone.neo.views.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bald.uriah.baldphone.R;

import java.util.ArrayList;
import java.util.List;

class ActionPopupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnItemClick {
        void onItemClicked(@NonNull Object row, int adapterPosition);
    }

    private static final int TYPE_ITEM = 1;
    private static final int TYPE_DIVIDER = 2;

    // Rows can be SimpleAction, ToggleAction, or Divider (singleton)
    public static final class Divider {
        public static final Divider INSTANCE = new Divider();

        private Divider() {}
    }

    private final List<Object> rows = new ArrayList<>();
    private OnItemClick onItemClick;

    protected ActionPopupAdapter() {}

    void setOnItemClick(OnItemClick listener) {
        this.onItemClick = listener;
    }

    public void submitRows(List<Object> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    void submitRows(List<Object> newRows, OnItemClick clickListener) {
        rows.clear();
        rows.addAll(newRows);
        this.onItemClick = clickListener;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object row = rows.get(position);
        return (row instanceof Divider) ? TYPE_DIVIDER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DIVIDER) {
            View v = inf.inflate(R.layout.item_action_divider, parent, false);
            return new DividerVH(v);
        } else {
            View v = inf.inflate(R.layout.item_action_popup, parent, false);
            return new ItemVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof ItemVH vh) {
            if (row instanceof SimpleAction a) {
                vh.icon.setImageResource(a.iconRes());
                vh.label.setText(a.labelRes());
            } else if (row instanceof ToggleAction t) {
                vh.icon.setImageResource(t.currentIconRes());
                vh.label.setText(t.currentLabelRes());
            }
            vh.itemView.setOnClickListener(
                    v -> {
                        if (onItemClick != null)
                            onItemClick.onItemClicked(row, holder.getBindingAdapterPosition());
                    });
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    void updateToggle(ActionId id, boolean checked) {
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof ToggleAction) {
                ToggleAction ta = (ToggleAction) row;
                if (ta.id() == id) {
                    ta.setChecked(checked);
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void setItemEnabled(ActionId id, boolean enabled) {
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof SimpleAction && ((SimpleAction) row).id() == id) {
                ((SimpleAction) row).setEnabled(enabled);
                notifyItemChanged(i);
                break;
            } else if (row instanceof ToggleAction && ((ToggleAction) row).id() == id) {
                ((ToggleAction) row).setEnabled(enabled);
                notifyItemChanged(i);
                break;
            }
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        ItemVH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
        }
    }

    static final class DividerVH extends RecyclerView.ViewHolder {
        DividerVH(@NonNull View itemView) {
            super(itemView);
        }
    }
}
