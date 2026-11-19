package com.ridu.plan.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

public class CheckInFragment extends Fragment implements Refreshable {
    private TextView tvStreak;
    private TextView tvMonthCount;
    private TextView tvTotalCount;
    private TextView tvTodayDate;
    private TextView tvTodayBook;
    private TextView tvTodayMeta;
    private TextView tvMonthLabel;
    private MaterialButton btnToday;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;
    private GridLayout weekdayRow;
    private GridLayout calendarGrid;

    private YearMonth displayed = YearMonth.now();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvStreak = view.findViewById(R.id.tv_streak);
        tvMonthCount = view.findViewById(R.id.tv_month_count);
        tvTotalCount = view.findViewById(R.id.tv_total_count);
        tvTodayDate = view.findViewById(R.id.tv_today_date);
        tvTodayBook = view.findViewById(R.id.tv_today_book);
        tvTodayMeta = view.findViewById(R.id.tv_today_meta);
        tvMonthLabel = view.findViewById(R.id.tv_month_label);
        btnToday = view.findViewById(R.id.btn_today_checkin);
        btnPrev = view.findViewById(R.id.btn_prev_month);
        btnNext = view.findViewById(R.id.btn_next_month);
        weekdayRow = view.findViewById(R.id.weekday_row);
        calendarGrid = view.findViewById(R.id.calendar_grid);

        buildWeekdayRow();

        btnPrev.setOnClickListener(v -> {
            displayed = displayed.minusMonths(1);
            render();
        });
        btnNext.setOnClickListener(v -> {
            if (displayed.isBefore(YearMonth.now())) {
                displayed = displayed.plusMonths(1);
                render();
            }
        });
        btnToday.setOnClickListener(v -> {
            boolean checked = PlanStore.toggleCheckIn(requireContext(), LocalDate.now());
            render();
            Toast.makeText(requireContext(),
                    checked ? "已打卡，今天读完了" : "已取消今天的打卡",
                    Toast.LENGTH_SHORT).show();
        });
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        displayed = YearMonth.now();
        render();
    }

    @Override
    public void refresh() {
        render();
    }

    private void render() {
        if (tvStreak == null) return;
        Set<LocalDate> dates = checkIns();
        LocalDate today = LocalDate.now();

        tvStreak.setText(String.valueOf(streak(dates, today)));
        tvMonthCount.setText(String.valueOf(monthCount(dates, YearMonth.now())));
        tvTotalCount.setText(String.valueOf(dates.size()));

        renderToday(today, dates);
        renderCalendar(today, dates);
    }

    private void renderToday(LocalDate today, Set<LocalDate> dates) {
        Context c = requireContext();
        tvTodayDate.setText(DateUtils.pretty(today));

        Plan plan = PlanStore.getPlan(c);
        LocalDate start = PlanStore.getStartDate(c);
        int dayIndex = DateUtils.dayIndex(start, today);
        boolean checked = dates.contains(today);

        if (dayIndex < 0) {
            tvTodayBook.setText("计划还没开始");
            tvTodayMeta.setText("从 " + DateUtils.shortDate(start) + " 开始");
            setTodayButton(false, false, "今天不在计划周期内");
            return;
        }
        if (dayIndex >= plan.totalDays) {
            tvTodayBook.setText("这份计划已经读完");
            tvTodayMeta.setText("共 " + plan.books.size() + " 本 · " + plan.totalDays + " 天");
            setTodayButton(false, false, "今天不在计划周期内");
            return;
        }

        Book book = plan.bookForDay(dayIndex);
        if (book == null) {
            tvTodayBook.setText("今天没有安排");
            tvTodayMeta.setText("");
            setTodayButton(false, false, "今天没有安排");
            return;
        }
        int inBook = dayIndex - book.startDay + 1;
        tvTodayBook.setText("《" + book.title + "》");
        tvTodayMeta.setText("第 " + inBook + "/" + book.days + " 天 · 约 "
                + book.dailyPages() + " 页 · " + book.mode);
        setTodayButton(true, checked, checked ? "今天已打卡 · 点击取消" : "今天读完了，打卡");
    }

    private void setTodayButton(boolean enabled, boolean checked, String text) {
        btnToday.setEnabled(enabled);
        btnToday.setText(text);
        int color = checked ? R.color.success : R.color.primary;
        btnToday.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), color)));
    }

    private void renderCalendar(LocalDate today, Set<LocalDate> dates) {
        tvMonthLabel.setText(displayed.getYear() + " 年 " + displayed.getMonthValue() + " 月");
        boolean canNext = displayed.isBefore(YearMonth.now());
        btnNext.setEnabled(canNext);
        btnNext.setAlpha(canNext ? 1f : 0.3f);

        calendarGrid.removeAllViews();
        int offset = displayed.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) {
            calendarGrid.addView(emptyCell(), cellParams(44));
        }
        int days = displayed.lengthOfMonth();
        for (int d = 1; d <= days; d++) {
            LocalDate date = displayed.atDay(d);
            boolean future = date.isAfter(today);
            boolean checked = dates.contains(date);
            boolean isToday = date.equals(today);
            calendarGrid.addView(dayCell(date, d, checked, future, isToday), cellParams(44));
        }
    }

    private void buildWeekdayRow() {
        String[] week = {"一", "二", "三", "四", "五", "六", "日"};
        for (String w : week) {
            TextView tv = new TextView(requireContext());
            tv.setText(w);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            tv.setTextSize(12);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(28);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            weekdayRow.addView(tv, lp);
        }
    }

    private View dayCell(LocalDate date, int day, boolean checked, boolean future, boolean today) {
        Context c = requireContext();
        FrameLayout cell = new FrameLayout(c);
        TextView tv = new TextView(c);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER);
        tv.setLayoutParams(tp);
        tv.setGravity(Gravity.CENTER);
        tv.setText(String.valueOf(day));
        tv.setTextSize(14);

        if (checked) {
            tv.setBackgroundResource(R.drawable.bg_checkin_done);
            tv.setTextColor(ContextCompat.getColor(c, R.color.text_on_primary));
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        } else if (today) {
            tv.setBackgroundResource(R.drawable.bg_checkin_today);
            tv.setTextColor(ContextCompat.getColor(c, R.color.primary));
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        } else {
            tv.setTextColor(ContextCompat.getColor(c,
                    future ? R.color.outline : R.color.ink));
        }
        cell.addView(tv);

        if (!future) {
            cell.setOnClickListener(v -> {
                boolean now = PlanStore.toggleCheckIn(requireContext(), date);
                render();
                Toast.makeText(requireContext(),
                        now ? "已打卡 " + DateUtils.shortDate(date)
                                : "已取消 " + DateUtils.shortDate(date),
                        Toast.LENGTH_SHORT).show();
            });
        }
        return cell;
    }

    private View emptyCell() {
        return new FrameLayout(requireContext());
    }

    private GridLayout.LayoutParams cellParams(int heightDp) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(heightDp);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        return lp;
    }

    private Set<LocalDate> checkIns() {
        Set<LocalDate> dates = new HashSet<>();
        for (String s : PlanStore.getCheckInDates(requireContext())) {
            try {
                dates.add(LocalDate.parse(s));
            } catch (Exception ignored) {
            }
        }
        return dates;
    }

    private int streak(Set<LocalDate> dates, LocalDate today) {
        LocalDate d = today;
        if (!dates.contains(d)) d = d.minusDays(1);
        int n = 0;
        while (dates.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    private int monthCount(Set<LocalDate> dates, YearMonth month) {
        int n = 0;
        for (LocalDate d : dates) {
            if (YearMonth.from(d).equals(month)) n++;
        }
        return n;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
