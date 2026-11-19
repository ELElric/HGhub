package com.ridu.plan.notify;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ridu.plan.App;
import com.ridu.plan.MainActivity;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;

public class ReminderReceiver extends BroadcastReceiver {
    public static final int NOTIFICATION_ID = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        showToday(context);
        ReminderScheduler.schedule(context);
    }

    private void showToday(Context c) {
        Plan plan = PlanStore.getPlan(c);
        LocalDate start = PlanStore.getStartDate(c);
        int dayIndex = DateUtils.dayIndex(start, LocalDate.now());
        Book book = plan.bookForDay(dayIndex);

        String title;
        String text;
        if (dayIndex < 0) {
            title = "阅读计划还没开始";
            text = "距离开始还有 " + (-dayIndex) + " 天";
        } else if (dayIndex >= plan.totalDays) {
            title = "三年计划已完成";
            text = "今天可以自由重读一本喜欢的书";
        } else if (book != null) {
            int inBook = dayIndex - book.startDay + 1;
            title = "今天读《" + book.title + "》";
            text = "第 " + inBook + "/" + book.days + " 天 · 约 " + book.dailyPages() + " 页 · " + book.author;
        } else {
            title = "今天读点书";
            text = "打开日读看看今天的安排";
        }

        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("open_today", true);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(c, 2001, open, flags);

        Intent checkIn = new Intent(c, CheckInReceiver.class);
        checkIn.setAction(CheckInReceiver.ACTION_CHECK_IN);
        PendingIntent checkPi = PendingIntent.getBroadcast(c, 2002, checkIn, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c, App.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_today)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPi)
                .addAction(0, "打卡", checkPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
