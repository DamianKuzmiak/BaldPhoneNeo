package app.baldphone.neo.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.core.assisttouch.enableAssistTouchHierarchy
import app.baldphone.neo.extensions.setClickableAccessibilityRole

import com.bald.uriah.baldphone.R

class SettingsAdapter(
    private val items: List<Item>,
    private val onClick: (SettingId) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        private val icon: ImageView = view.findViewById(R.id.icon)

        fun bind(item: Item, onClick: (SettingId) -> Unit) {
            title.setText(item.titleRes)
            icon.setImageResource(item.iconRes ?: 0)
            itemView.contentDescription = itemView.context.getString(item.titleRes)
            itemView.setOnClickListener { onClick(item.id) }
            itemView.setClickableAccessibilityRole()
            itemView.enableAssistTouchHierarchy()
        }
    }
}
