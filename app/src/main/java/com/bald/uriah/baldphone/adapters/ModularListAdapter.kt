package com.bald.uriah.baldphone.adapters

import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import com.bald.uriah.baldphone.views.ModularRecyclerView

/**
 * A ListAdapter implementation that extends [ModularAdapter] to be compatible with ModularRecyclerView.
 */
abstract class ModularListAdapter<T, VH : RecyclerView.ViewHolder>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ModularRecyclerView.ModularAdapter<VH>() {
    private val mDiffer = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<T>?, commitCallback: Runnable? = null) {
        mDiffer.submitList(list, commitCallback)
    }

    protected fun getItem(position: Int): T = mDiffer.currentList[position]

    override fun getItemCount(): Int = mDiffer.currentList.size

    fun getCurrentList(): List<T> = mDiffer.currentList
}
