package com.example.appfirewall

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var blocked: Boolean
)

class AppAdapter(
    private val items: MutableList<AppItem>,
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val name: TextView = view.findViewById(R.id.txtName)
        val pkg: TextView = view.findViewById(R.id.txtPkg)
        val toggle: Switch = view.findViewById(R.id.swBlock)
    }

    fun setItems(newItems: List<AppItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.label
        holder.pkg.text = item.packageName

        // حذف listener قبلی قبل از تنظیم مقدار، تا با recycle شدن ردیف‌ها مشکلی پیش نیاید
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = item.blocked
        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            item.blocked = isChecked
            onToggle(item.packageName, isChecked)
        }
    }
}
