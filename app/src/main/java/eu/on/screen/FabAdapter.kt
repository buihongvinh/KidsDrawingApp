package eu.on.screen

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FabAdapter(
    private val context: Context,
    private val items: MutableList<FabItem>,
    private val onItemClick: (FabItem) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<FabAdapter.FabViewHolder>() {

    private var fabSizePreference: Int = 1

    init {
        setHasStableIds(true)
    }

    inner class FabViewHolder(val fab: FloatingActionButton) : RecyclerView.ViewHolder(fab)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FabViewHolder {
        val fab = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_overlay_fab, parent, false) as FloatingActionButton
        return FabViewHolder(fab)
    }

    override fun onBindViewHolder(holder: FabViewHolder, position: Int) {
        val item = items[position]
        holder.fab.setImageResource(item.iconRes)
        holder.fab.contentDescription = context.getString(item.contentDescRes)
        holder.fab.backgroundTintList = ContextCompat.getColorStateList(
            context,
            if (item.isActive) R.color.home_primary else R.color.home_panel_background
        )
        holder.fab.imageTintList = ContextCompat.getColorStateList(
            context,
            if (item.isActive) R.color.white else R.color.home_primary
        )
        applyFabSize(holder.fab)
        holder.fab.setOnClickListener { onItemClick(item) }
        holder.fab.setOnLongClickListener {
            onStartDrag(holder)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) {
            return
        }
        val movedItem = items.removeAt(from)
        items.add(to, movedItem)
        notifyItemMoved(from, to)
    }

    fun getItems(): List<FabItem> = items

    fun updateFabSize(size: Int) {
        fabSizePreference = size
        notifyDataSetChanged()
    }

    fun notifyItemChangedById(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    private fun applyFabSize(fab: FloatingActionButton) {
        when (fabSizePreference) {
            0 -> {
                fab.size = FloatingActionButton.SIZE_MINI
                fab.customSize = 0
            }
            2 -> {
                fab.size = FloatingActionButton.SIZE_NORMAL
                fab.customSize = context.dpToPx(64)
            }
            else -> {
                fab.size = FloatingActionButton.SIZE_NORMAL
                fab.customSize = 0
            }
        }
    }
}

private fun Context.dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()
