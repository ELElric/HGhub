package com.ridu.plan.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtils {
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateUtils() {
    }

    public static String pretty(LocalDate d) {
        return d.getMonthValue() + "月" + d.getDayOfMonth() + "日 · " + WEEK[d.getDayOfWeek().getValue() - 1];
    }

    public static String shortDate(LocalDate d) {
        return d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    public static int dayIndex(LocalDate start, LocalDate today) {
        return (int) ChronoUnit.DAYS.between(start, today);
    }

    public static String range(LocalDate start, int startDay, int days) {
        LocalDate a = start.plusDays(startDay);
        LocalDate b = a.plusDays(Math.max(0, days - 1));
        if (a.equals(b)) return shortDate(a);
        return shortDate(a) + " — " + shortDate(b);
    }
}
