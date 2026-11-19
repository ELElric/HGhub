package com.ridu.plan.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ridu.plan.MainActivity;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;

public class TodayFragment extends Fragment implements Refreshable {
    private TextView tvDate;
    private TextView tvMode;
    private TextView tvTitle;
    private TextView tvAuthor;
    private TextView tvMeta;
    private TextView tvBackground;
    private TextView tvPrerequisite;
    private TextView tvBookProgress;
    private TextView tvOverall;
    private ProgressBar pbBook;
    private MaterialButton btnCheckin;
    private MaterialButton btnDetail;

    private Plan plan;
    private Book book;
    private LocalDate today;
    private int dayIndex;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvDate = view.findViewById(R.id.tv_date);
        tvMode = view.findViewById(R.id.tv_mode);
        tvTitle = view.findViewById(R.id.tv_book_title);
        tvAuthor = view.findViewById(R.id.tv_book_author);
        tvMeta = view.findViewById(R.id.tv_book_meta);
        tvBackground = view.findViewById(R.id.tv_background);
        tvPrerequisite = view.findViewById(R.id.tv_prerequisite);
        tvBookProgress = view.findViewById(R.id.tv_book_progress);
        tvOverall = view.findViewById(R.id.tv_overall);
        pbBook = view.findViewById(R.id.pb_book);
        btnCheckin = view.findViewById(R.id.btn_checkin);
        btnDetail = view.findViewById(R.id.btn_detail);

        btnCheckin.setOnClickListener(v -> {
            boolean checked = PlanStore.toggleCheckIn(requireContext(), LocalDate.now());
            updateCheckinButton(checked);
            if (checked) {
                android.widget.Toast.makeText(requireContext(), "已打卡，明天继续", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        btnDetail.setOnClickListener(v -> {
            if (book != null && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showBookDetail(book);
            }
        });
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void refresh() {
        render();
    }

    private void render() {
        if (tvDate == null) return;
        plan = PlanStore.getPlan(requireContext());
        today = LocalDate.now();
        dayIndex = DateUtils.dayIndex(PlanStore.getStartDate(requireContext()), today);
        book = plan.bookForDay(dayIndex);
        if (book == null) return;

        tvDate.setText(DateUtils.pretty(today));
        tvTitle.setText(book.title);
        tvAuthor.setText(book.author.isEmpty() ? "佚名" : book.author);
        tvMode.setText(book.mode);
        tvBackground.setText(book.background);
        tvPrerequisite.setText(book.prerequisite);

        int inBook = dayIndex - book.startDay + 1;
        if (dayIndex < 0) {
            tvMeta.setText("计划从 " + DateUtils.shortDate(PlanStore.getStartDate(requireContext()))
                    + " 开始 · 还有 " + (-dayIndex) + " 天");
            pbBook.setProgress(0);
        } else if (dayIndex >= plan.totalDays) {
            tvMeta.setText("计划已完成 · 这是最后一本");
            pbBook.setProgress(100);
        } else {
            tvMeta.setText("第 " + inBook + "/" + book.days + " 天 · 约 " + book.dailyPages() + " 页/天");
            int p = (int) Math.max(0, Math.min(100, inBook * 100f / Math.max(1, book.days)));
            pbBook.setProgress(p);
        }

        tvBookProgress.setText("本书进度 " + pbBook.getProgress() + "% · 第 " + book.index
                + " 本 / 共 " + plan.books.size() + " 本");

        int shown = Math.max(0, Math.min(plan.totalDays, dayIndex + 1));
        tvOverall.setText("整体进度：第 " + shown + " 天 / 共 " + plan.totalDays
                + " 天 · 已完成 " + plan.completedBooks(dayIndex) + " 本");

        updateCheckinButton(PlanStore.isCheckedIn(requireContext(), today));
    }

    private void updateCheckinButton(boolean checked) {
        if (btnCheckin == null) return;
        if (checked) {
            btnCheckin.setText("今天已打卡 ✓");
            btnCheckin.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.success)));
        } else {
            btnCheckin.setText("今天读完了");
            btnCheckin.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)));
        }
    }
}
