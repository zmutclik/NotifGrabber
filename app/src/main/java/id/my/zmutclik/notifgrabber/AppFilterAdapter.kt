package id.my.zmutclik.notifgrabber

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Model sederhana untuk satu item aplikasi.
 */
data class AppItem(
    val appName: String,
    val packageName: String,
    val icon: Drawable?
)

/**
 * Adapter RecyclerView untuk daftar aplikasi dengan checkbox.
 */
class AppFilterAdapter(
    private val selectedPackages: MutableSet<String>,
    private val onSelectionChanged: (count: Int) -> Unit
) : RecyclerView.Adapter<AppFilterAdapter.ViewHolder>() {

    private var allApps: List<AppItem> = emptyList()
    private var filteredApps: List<AppItem> = emptyList()

    fun submitList(apps: List<AppItem>) {
        allApps = apps
        filteredApps = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.lowercase()
            allApps.filter {
                it.appName.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    fun selectAll() {
        filteredApps.forEach { selectedPackages.add(it.packageName) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPackages.size)
    }

    fun deselectAll() {
        filteredApps.forEach { selectedPackages.remove(it.packageName) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPackages.size)
    }

    override fun getItemCount() = filteredApps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_filter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredApps[position]
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.appCheckbox)
        private val icon: ImageView    = itemView.findViewById(R.id.appIcon)
        private val name: TextView     = itemView.findViewById(R.id.appName)
        private val pkg: TextView      = itemView.findViewById(R.id.appPackage)

        fun bind(item: AppItem) {
            name.text = item.appName
            pkg.text  = item.packageName
            icon.setImageDrawable(item.icon)

            // Hapus listener sementara supaya setChecked tidak trigger callback
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = selectedPackages.contains(item.packageName)

            // Klik di seluruh baris toggle checkbox
            val toggle = View.OnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                if (checkbox.isChecked) {
                    selectedPackages.add(item.packageName)
                } else {
                    selectedPackages.remove(item.packageName)
                }
                onSelectionChanged(selectedPackages.size)
            }
            itemView.setOnClickListener(toggle)
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedPackages.add(item.packageName)
                else           selectedPackages.remove(item.packageName)
                onSelectionChanged(selectedPackages.size)
            }
        }
    }
}
