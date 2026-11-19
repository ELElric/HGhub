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

public class CheckInReceiver extends BroadcastReceiver {
    public static final String ACTION_CHECK_IN = "com.ridu.plan.ACTION_CHECK_IN";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!ACTION_CHECK_IN.equals(intent.getAction())) return;
        LocalDate today = LocalDate.now();
        PlanStore.setCheckedIn(c, today, true);
        NotificationManagerCompat.from(c).cancel(ReminderReceiver.NOTIFICATION_ID);

        Plan plan = PlanStore.getPlan(c);
        int dayIndex = DateUtils.dayIndex(PlanStore.getStartDate(c), today);
        Book book = plan.bookForDay(dayIndex);
        String text = book == null ? "今天已打卡" : "已读《" + book.title + "》";

        Intent open = new Intent(c, MainActivity.class);
        open.putExtra("open_today", true);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(c, 2003, open, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c, App.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_today)
                .setContentTitle("已打卡")
                .setContentText(text)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setTimeoutAfter(4000)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManagerCompat.from(c).notify(2002, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
