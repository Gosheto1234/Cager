package com.example.cager

import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val apps: List<ApplicationInfo>,
    private val selectedUids: MutableSet<String>,
    private val onSelectionChanged: (MutableSet<String>) -> Unit
) : RecyclerView.Adapter<AppListAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        // Label
        holder.checkedText.text = app.loadLabel(holder.itemView.context.packageManager)
        // Checked state
        val uidStr = app.uid.toString()
        holder.checkedText.isChecked = selectedUids.contains(uidStr)

        // Toggle selection on click
        holder.checkedText.setOnClickListener {
            val nowChecked = !holder.checkedText.isChecked
            holder.checkedText.isChecked = nowChecked

            if (nowChecked) selectedUids.add(uidStr)
            else selectedUids.remove(uidStr)

            onSelectionChanged(selectedUids)
        }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkedText: CheckedTextView =
            itemView.findViewById(android.R.id.text1)
    }
}
