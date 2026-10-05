package app.baldphone.neo.settings.help

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

import app.baldphone.neo.R
import app.baldphone.neo.settings.BaseSettingsFragment

class CreditsFragment : BaseSettingsFragment(R.layout.fragment_credits) {
    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view as RecyclerView
        recyclerView.adapter = CreditsAdapter(CREDITS)

        val divider = DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
        AppCompatResources.getDrawable(requireContext(), R.drawable.ll_divider)?.let {
            divider.setDrawable(it)
        }
        recyclerView.addItemDecoration(divider)
    }

    private class CreditsAdapter(
        private val credits: List<Credit>
    ) : RecyclerView.Adapter<CreditsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_credit, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = credits.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val credit = credits[position]
            holder.name.text = holder.name.context.getString(credit.nameRes)
            holder.task.text = holder.task.context.getString(credit.taskRes)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.name)
            val task: TextView = itemView.findViewById(R.id.task)
        }
    }
}
