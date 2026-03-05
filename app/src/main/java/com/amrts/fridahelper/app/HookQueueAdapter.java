package com.amrts.fridahelper.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.recyclerview.widget.DiffUtil;

import com.amrts.fridahelper.core.model.HookRequest;
import com.amrts.fridahelper.core.model.NativeSymbol;
import com.amrts.fridahelper.core.model.SmaliMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying the hook composition queue.
 * Each item shows hook type, concise summary, and a delete button.
 */
public final class HookQueueAdapter extends RecyclerView.Adapter<HookQueueAdapter.ViewHolder> {

    /** Callback for delete button clicks. */
    public interface OnDeleteListener {
        void onDelete(int position);
    }

    private List<HookRequest> items = new ArrayList<>();
    private OnDeleteListener deleteListener;

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.deleteListener = listener;
    }

    /**
     * Replaces the entire dataset and refreshes the list.
     */
    public void submitList(List<HookRequest> newItems) {
        List<HookRequest> oldItems = this.items;
        List<HookRequest> updated = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldItems.size(); }
            @Override public int getNewListSize() { return updated.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos) == updated.get(newPos);
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).equals(updated.get(newPos));
            }
        });
        this.items = updated;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hook_queue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HookRequest request = items.get(position);
        holder.textIndex.setText(String.valueOf(position + 1));
        holder.textType.setText(request.getType() == HookRequest.Type.JAVA ? "JAVA" : "NATIVE");
        holder.textSummary.setText(formatSummary(request));
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    deleteListener.onDelete(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatSummary(HookRequest request) {
        if (request.getType() == HookRequest.Type.JAVA) {
            SmaliMethod m = request.getSmaliMethod();
            return m.getClassName() + "." + m.getMethodName()
                    + " (" + m.getParamTypes().size() + " params)";
        } else {
            NativeSymbol s = request.getNativeSymbol();
            if (s.getTargetMode() == NativeSymbol.TargetMode.ADDRESS) {
                return "ptr(" + s.getAddress() + ") (" + s.getArgCount() + " args)";
            }
            String lib = s.getLibName() != null ? s.getLibName() : "null";
            String suffix = s.isWaitForLoad() ? " [wait]" : "";
            return lib + " -> " + s.getExportName()
                    + " (" + s.getArgCount() + " args)" + suffix;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textIndex;
        final TextView textType;
        final TextView textSummary;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textIndex = itemView.findViewById(R.id.text_queue_index);
            textType = itemView.findViewById(R.id.text_queue_type);
            textSummary = itemView.findViewById(R.id.text_queue_summary);
            btnDelete = itemView.findViewById(R.id.btn_queue_delete);
        }
    }
}
