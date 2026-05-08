package app.baldphone.neo.permissions.ui

import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.permissions.model.AppPermission

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.adapters.ModularListAdapter
import com.bald.uriah.baldphone.databinding.ItemPermissionBinding

class PermissionListAdapter(
    private val onAllowClicked: (AppPermission) -> Unit
) : ModularListAdapter<PermissionUiModel, PermissionListAdapter.PermissionViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val binding = ItemPermissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PermissionViewHolder(binding, onAllowClicked)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.bind(getItem(position))
    }

    class PermissionViewHolder(
        private val binding: ItemPermissionBinding,
        private val onAllowClicked: (AppPermission) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uiModel: PermissionUiModel) {
            val permission = uiModel.permission
            val isMandatory = uiModel.isMandatory

            binding.apply {
                icon.setImageResource(permission.iconRes)
                title.setText(permission.titleRes)
                explanation.setText(permission.messageRes)
                requiredLabel.isVisible = isMandatory

                val backgroundColor =
                    if (isMandatory) {
                        ContextCompat.getColor(root.context, R.color.color_3)
                    } else {
                        android.graphics.Color.TRANSPARENT
                    }
                root.setBackgroundColor(backgroundColor)

                allow.setOnClickListener { onAllowClicked(permission) }
            }
        }
    }

    companion object {
        private val DiffCallback =
            object : DiffUtil.ItemCallback<PermissionUiModel>() {
                override fun areItemsTheSame(oldItem: PermissionUiModel, newItem: PermissionUiModel): Boolean =
                    oldItem.permission::class == newItem.permission::class

                override fun areContentsTheSame(oldItem: PermissionUiModel, newItem: PermissionUiModel): Boolean =
                    oldItem == newItem
            }
    }
}
