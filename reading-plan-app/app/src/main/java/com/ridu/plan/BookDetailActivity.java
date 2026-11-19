package com.ridu.plan;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.reader.ExternalReader;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;

public class BookDetailActivity extends AppCompatActivity {
    private Book book;
    private MaterialButton btnOpen;

    private final ActivityResultLauncher<String[]> pickBook =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || book == null) return;
                ExternalReader.bind(this, ExternalReader.key(book), uri);
                updateReaderButton();
                openReader();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        int index = getIntent().getIntExtra("book_index", -1);
        Plan plan = PlanStore.getPlan(this);
        book = plan.bookByIndex(index);
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

        btnOpen = findViewById(R.id.btn_detail_open);
        btnOpen.setOnClickListener(v -> openReader());
        btnOpen.setOnLongClickListener(v -> {
            ExternalReader.unbind(this, ExternalReader.key(book));
            updateReaderButton();
            Toast.makeText(this, "已解除文件关联，点击可重新选择", Toast.LENGTH_SHORT).show();
            return true;
        });
        updateReaderButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateReaderButton();
    }

    private void openReader() {
        Uri uri = ExternalReader.boundUri(this, ExternalReader.key(book));
        if (uri == null) {
            pickBook.launch(new String[]{"*/*"});
            return;
        }
        if (!ExternalReader.open(this, uri)) {
            Toast.makeText(this, "没有找到可以打开该文件的应用", Toast.LENGTH_LONG).show();
        }
    }

    private void updateReaderButton() {
        if (btnOpen == null || book == null) return;
        String app = ExternalReader.moonPackage(this) != null ? "静读天下" : "阅读应用";
        boolean bound = ExternalReader.boundUri(this, ExternalReader.key(book)) != null;
        btnOpen.setText(bound ? "继续阅读 · " + app : "打开电子书 · " + app);
    }
}
