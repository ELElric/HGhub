package com.ridu.plan.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BOOK = 1;

    public interface OnBookClick {
        void onBook(Book book);
    }

    private final List<Object> rows = new ArrayList<>();
    private LocalDate startDate = LocalDate.now();
    private int todayIndex = 0;
    private final OnBookClick listener;

    public PlanAdapter(OnBookClick listener) {
        this.listener = listener;
    }

    public void setData(List<Object> data, LocalDate start, int today) {
        rows.clear();
        rows.addAll(data);
        startDate = start;
        todayIndex = today;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_BOOK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_header, parent, false));
        }
        return new BookHolder(inflater.inflate(R.layout.item_book, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder && row instanceof String) {
            ((HeaderHolder) holder).text.setText((String) row);
        } else if (holder instanceof BookHolder && row instanceof Book) {
            ((BookHolder) holder).bind((Book) row);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_header);
        }
    }

    private class BookHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView index;
        final TextView title;
        final TextView author;
        final TextView meta;
        final TextView mode;

        BookHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            index = itemView.findViewById(R.id.tv_index);
            title = itemView.findViewById(R.id.tv_title);
            author = itemView.findViewById(R.id.tv_author);
            meta = itemView.findViewById(R.id.tv_meta);
            mode = itemView.findViewById(R.id.tv_mode);
        }

        void bind(Book b) {
            index.setText(String.format("%02d", b.index));
            title.setText(b.title);
            author.setText(b.author);
            meta.setText(DateUtils.range(startDate, b.startDay, b.days)
                    + " · " + b.days + " 天 · 约 " + b.dailyPages() + " 页/天");
            mode.setText(b.mode);

            boolean current = todayIndex >= b.startDay && todayIndex < b.endDay;
            card.setStrokeColor(ContextCompat.getColor(itemView.getContext(),
                    current ? R.color.primary : R.color.outline));
            card.setStrokeWidth(current ? 2 : 1);
            card.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(),
                    current ? R.color.surface_variant : R.color.surface));
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onBook(b);
            });
        }
    }
}
