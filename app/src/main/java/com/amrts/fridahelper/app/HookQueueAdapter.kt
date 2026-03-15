package com.amrts.fridahelper.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.amrts.fridahelper.core.model.HookRequest
import com.amrts.fridahelper.core.model.NativeSymbol

/**
 * RecyclerView adapter for displaying the hook composition queue.
 * Each item shows hook type, concise summary, and a delete button.
 */
class HookQueueAdapter : RecyclerView.Adapter<HookQueueAdapter.ViewHolder>() {

    fun interface OnDeleteListener {
        fun onDelete(position: Int)
    }

    private var items: List<HookRequest> = emptyList()
    var onDeleteListener: OnDeleteListener? = null

    fun submitList(newItems: List<HookRequest>?) {
        val oldItems = items
        val updated = newItems?.toList() ?: emptyList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = updated.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                oldItems[oldPos] === updated[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                oldItems[oldPos] == updated[newPos]
        })
        items = updated
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hook_queue, parent, false)
        val holder = ViewHolder(view)
        holder.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDeleteListener?.onDelete(pos)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = items[position]
        holder.textIndex.text = (position + 1).toString()
        holder.textType.text = if (request.type == HookRequest.Type.JAVA) "JAVA" else "NATIVE"
        holder.textSummary.text = formatSummary(request)
    }

    override fun getItemCount(): Int = items.size

    private fun formatSummary(request: HookRequest): String {
        if (request.type == HookRequest.Type.JAVA) {
            val m = request.smaliMethod
            return "${m.className}.${m.methodName} (${m.paramTypes.size} params)"
        }
        val s = request.nativeSymbol
        if (s.targetMode == NativeSymbol.TargetMode.ADDRESS) {
            return "ptr(${s.address}) (${s.argCount} args)"
        }
        val lib = s.libName ?: "null"
        val suffix = if (s.isWaitForLoad) " [wait]" else ""
        return "$lib -> ${s.exportName} (${s.argCount} args)$suffix"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textIndex: TextView = itemView.findViewById(R.id.text_queue_index)
        val textType: TextView = itemView.findViewById(R.id.text_queue_type)
        val textSummary: TextView = itemView.findViewById(R.id.text_queue_summary)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_queue_delete)
    }
}
