package app.baldphone.neo.features.share

import android.content.Context
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import coil3.load

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.views.ModularRecyclerView

class IntentAdapter(
    private val context: Context,
    private val resolveInfoList: List<ResolveInfo>,
    private val onResolveInfoClicked: (ResolveInfo, Context) -> Unit
) : ModularRecyclerView.ModularAdapter<IntentAdapter.ViewHolder>() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val packageManager = context.packageManager

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(layoutInflater.inflate(R.layout.settings_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(resolveInfoList[position])
    }

    override fun getItemCount(): Int = resolveInfoList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val iconView: ImageView = itemView.findViewById(R.id.setting_icon)
        private val nameView: TextView = itemView.findViewById(R.id.tv_setting_name)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(resolveInfo: ResolveInfo) {
            iconView.load(resolveInfo.loadIcon(packageManager))
            nameView.text = resolveInfo.loadLabel(packageManager)
        }

        override fun onClick(v: View) {
            val position = bindingAdapterPosition
            if (position != -1) {
                onResolveInfoClicked(resolveInfoList[position], context)
            }
        }
    }
}
