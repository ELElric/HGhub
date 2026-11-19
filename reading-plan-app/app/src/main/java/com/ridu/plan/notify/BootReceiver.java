package com.ridu.plan.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ridu.plan.data.PlanStore;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (PlanStore.isReminderEnabled(context)) {
            ReminderScheduler.schedule(context);
        }
    }
}
