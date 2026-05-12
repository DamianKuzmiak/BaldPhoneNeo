package app.baldphone.neo.features.contacts.ui

import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import coil3.load
import coil3.request.crossfade
import coil3.request.fallback
import coil3.request.transformations
import coil3.transform.CircleCropTransformation

import app.baldphone.neo.features.contacts.ContactItemType
import app.baldphone.neo.features.contacts.SimpleContact

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.adapters.ModularListAdapter
import com.bald.uriah.baldphone.databinding.ContactItemBinding
import com.bald.uriah.baldphone.databinding.ContactItemHeaderBinding

class ContactAdapter(
    private val showPhoneNumbers: Boolean = false,
    private val onContactClick: ((SimpleContact) -> Unit)? = null
) : ModularListAdapter<ContactItemType, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is ContactItemType.Header -> R.layout.contact_item_header
            is ContactItemType.ContactItem -> R.layout.contact_item
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.contact_item_header -> {
                val binding = ContactItemHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }

            R.layout.contact_item -> {
                val binding = ContactItemBinding.inflate(inflater, parent, false)
                ContactViewHolder(binding, showPhoneNumbers, onContactClick)
            }

            else -> {
                throw IllegalArgumentException("Unsupported layout ID: $viewType")
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as ContactItemType.Header)
            is ContactViewHolder -> holder.bind(item as ContactItemType.ContactItem)
        }
    }

    class HeaderViewHolder(
        private val binding: ContactItemHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ContactItemType.Header) {
            binding.letter.text = header.letter
        }
    }

    class ContactViewHolder(
        private val binding: ContactItemBinding,
        private val showPhoneNumbers: Boolean,
        private val onContactClick: ((SimpleContact) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contactItem: ContactItemType.ContactItem) {
            val contact = contactItem.contact

            binding.root.setOnClickListener { onContactClick?.invoke(contact) }
            binding.contactName.text = contact.name

            if (contact.isStarred) {
                binding.contactName.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_star_small,
                    0
                )
                binding.contactName.compoundDrawablePadding =
                    binding.root.context.resources
                        .getDimensionPixelSize(R.dimen.padding_small)
            } else {
                binding.contactName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            if (showPhoneNumbers) {
                binding.contactNumber.visibility = View.VISIBLE
                val context = binding.root.context
                val phoneNumber = contact.phoneNumber
                binding.contactNumber.text =
                    if (contact.phoneType == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                        phoneNumber
                    } else {
                        val typeLabel =
                            ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                contact.phoneType,
                                contact.phoneLabel
                            )
                        "$phoneNumber ($typeLabel)"
                    }
            } else {
                binding.contactNumber.visibility = View.GONE
            }

            binding.profilePic.load(contact.photoThumbnailUri ?: contact.photoUri) {
                crossfade(true)
                fallback(R.drawable.face)
                transformations(CircleCropTransformation())
            }
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactItemType>() {
        override fun areItemsTheSame(
            oldItem: ContactItemType,
            newItem: ContactItemType
        ): Boolean =
            when {
                oldItem is ContactItemType.Header && newItem is ContactItemType.Header -> {
                    oldItem.letter == newItem.letter
                }

                oldItem is ContactItemType.ContactItem && newItem is ContactItemType.ContactItem -> {
                    oldItem.contact.id == newItem.contact.id &&
                        oldItem.contact.phoneNumber == newItem.contact.phoneNumber
                }

                else -> {
                    false
                }
            }

        override fun areContentsTheSame(
            oldItem: ContactItemType,
            newItem: ContactItemType
        ): Boolean = oldItem == newItem
    }
}
