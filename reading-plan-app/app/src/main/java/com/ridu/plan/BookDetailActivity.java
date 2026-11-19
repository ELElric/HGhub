package com.ridu.plan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;

public class BookDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        int index = getIntent().getIntExtra("book_index", -1);
        Plan plan = PlanStore.getPlan(this);
        Book book = plan.bookByIndex(index);
        if (book == null) {
            finish();
            return;
        }

        TextView mode = findViewById(R.id.tv_detail_mode);
        TextView title = findViewById(R.id.tv_detail_title);
        TextView author = findViewById(R.id.tv_detail_author);
        TextView meta = findViewById(R.id.tv_detail_meta);
        TextView background = findViewById(R.id.tv_detail_background);
        TextView prerequisite = findViewById(R.id.tv_detail_prerequisite);
        TextView schedule = findViewById(R.id.tv_detail_schedule);

        mode.setText(book.mode);
        title.setText(book.title);
        author.setText(book.author);
        meta.setText("第 " + book.index + " 本 · " + book.part);
        background.setText(book.background);
        prerequisite.setText(book.prerequisite);

        LocalDate start = PlanStore.getStartDate(this);
        schedule.setText(
                "日期：" + DateUtils.range(start, book.startDay, book.days) + "\n"
                        + "安排：" + book.days + " 天 · 约 " + book.dailyPages() + " 页/天 · " + book.mode + "\n"
                        + "位置：" + book.stage + " · " + book.slot
        );

        MaterialButton today = findViewById(R.id.btn_detail_today);
        today.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("open_today", true);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }
}
