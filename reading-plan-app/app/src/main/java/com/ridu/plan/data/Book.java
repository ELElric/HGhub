package com.ridu.plan.data;

public class Book {
    public int index;
    public String title = "";
    public String author = "";
    public String part = "";
    public String stage = "";
    public String slot = "";
    public String mode = "通读";
    public int days;
    public int pages = 300;
    public double weight;
    public int targetDailyPages;
    public String background = "";
    public String prerequisite = "";

    /** 0-based, inclusive */
    public int startDay;
    /** 0-based, exclusive */
    public int endDay;

    public int dailyPages() {
        if (days <= 0) return 0;
        return Math.max(1, Math.round((float) pages / days));
    }
}
