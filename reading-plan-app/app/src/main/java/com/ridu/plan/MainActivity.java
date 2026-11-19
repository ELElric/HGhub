package com.ridu.plan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.notify.ReminderScheduler;
import com.ridu.plan.ui.PlanFragment;
import com.ridu.plan.ui.Refreshable;
import com.ridu.plan.ui.SettingsFragment;
import com.ridu.plan.ui.TodayFragment;
import com.ridu.plan.update.UpdateChecker;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_NOTIFICATION = 12;
    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.bottom_nav);

        nav.setSelectedItemId(R.id.nav_today);
        nav.setOnItemSelectedListener(this::onNavItem);
        select(R.id.nav_today);

        PlanStore.getPlan(this);
        ReminderScheduler.schedule(this);
        requestNotificationPermission();
        maybeCheckUpdate();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private boolean onNavItem(@NonNull MenuItem item) {
        select(item.getItemId());
        return true;
    }

    private void select(int id) {
        Fragment fragment;
        if (id == R.id.nav_plan) {
            fragment = new PlanFragment();
        } else if (id == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else {
            fragment = new TodayFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_today", false)) {
            nav.setSelectedItemId(R.id.nav_today);
        }
    }

    public void showBookDetail(Book book) {
        Intent i = new Intent(this, BookDetailActivity.class);
        i.putExtra("book_index", book.index);
        startActivity(i);
    }

    public void refreshCurrent() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
        if (f instanceof Refreshable) ((Refreshable) f).refresh();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    private void maybeCheckUpdate() {
        if (!PlanStore.isAutoUpdate(this)) return;
        String url = PlanStore.getAppUpdateUrl(this);
        if (url.isEmpty()) return;
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> UpdateChecker.checkForUpdate(this, url, true), 1500);
    }
}
