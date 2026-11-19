package com.ridu.plan.data;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅读计划排期算法。
 * 书单可以只给页数，由这里按阅读强度自动计算每本书的天数；
 * 也可以在 JSON 的 algorithm 节点里指定参数，APK 读取后重新规划。
 */
public final class PlanScheduler {
    public static class Config {
        public boolean replan;
        public int totalDays;
        public int dailyPages = 40;
        public int maxDailyPages = 65;
        public int minDays = 2;
        public int intensivePages = 25;
        public int normalPages = 40;
        public int selectivePages = 50;
        public double intensiveWeight = 1.5;
        public double normalWeight = 1.0;
        public double selectiveWeight = 0.7;

        static Config from(JSONObject o) {
            Config c = new Config();
            if (o == null) return c;
            c.replan = o.optBoolean("replan", false);
            c.totalDays = Math.max(0, o.optInt("totalDays", 0));
            c.dailyPages = Math.max(5, o.optInt("dailyPages", 40));
            c.maxDailyPages = Math.max(c.dailyPages, o.optInt("maxDailyPages", 65));
            c.minDays = Math.max(1, o.optInt("minDaysPerBook", 2));

            JSONObject pages = o.optJSONObject("modePages");
            if (pages != null) {
                c.intensivePages = Math.max(5, pages.optInt("精读", c.intensivePages));
                c.normalPages = Math.max(5, pages.optInt("通读", c.normalPages));
                c.selectivePages = Math.max(5, pages.optInt("选读", c.selectivePages));
            }
            JSONObject weights = o.optJSONObject("modeWeights");
            if (weights != null) {
                c.intensiveWeight = Math.max(0.1, weights.optDouble("精读", c.intensiveWeight));
                c.normalWeight = Math.max(0.1, weights.optDouble("通读", c.normalWeight));
                c.selectiveWeight = Math.max(0.1, weights.optDouble("选读", c.selectiveWeight));
            }
            return c;
        }

        int pagesFor(String mode) {
            if (mode != null && mode.contains("精")) return intensivePages;
            if (mode != null && mode.contains("选")) return selectivePages;
            return normalPages;
        }

        double weightFor(String mode) {
            if (mode != null && mode.contains("精")) return intensiveWeight;
            if (mode != null && mode.contains("选")) return selectiveWeight;
            return normalWeight;
        }
    }

    private PlanScheduler() {
    }

    public static void apply(Plan plan, JSONObject algorithm) {
        Config cfg = Config.from(algorithm);
        if (cfg.replan || cfg.totalDays > 0) {
            allocateTotal(plan, cfg);
        } else {
            fillMissing(plan, cfg);
        }
        assignDays(plan);
    }

    private static void fillMissing(Plan plan, Config cfg) {
        for (Book b : plan.books) {
            int days = b.days;
            if (days <= 0) {
                int target = b.targetDailyPages > 0 ? b.targetDailyPages : cfg.pagesFor(b.mode);
                days = (int) Math.ceil(b.pages / (double) target);
            }
            days = Math.max(days, cfg.minDays);
            days = Math.max(days, (int) Math.ceil(b.pages / (double) cfg.maxDailyPages));
            b.days = Math.max(1, days);
        }
    }

    private static void allocateTotal(Plan plan, Config cfg) {
        int total = cfg.totalDays;
        if (total < plan.books.size()) total = plan.books.size();
        List<Double> weights = new ArrayList<>();
        double sum = 0;
        for (Book b : plan.books) {
            double w = b.weight > 0 ? b.weight : b.pages * cfg.weightFor(b.mode);
            w = Math.max(1, w);
            weights.add(w);
            sum += w;
        }

        int assigned = 0;
        double[] remainders = new double[plan.books.size()];
        int[] days = new int[plan.books.size()];
        for (int i = 0; i < plan.books.size(); i++) {
            double exact = total * weights.get(i) / sum;
            days[i] = Math.max(1, (int) Math.floor(exact));
            remainders[i] = exact - Math.floor(exact);
            assigned += days[i];
        }
        while (assigned < total) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[best]) best = i;
            }
            days[best]++;
            remainders[best] = -1;
            assigned++;
        }
        while (assigned > total) {
            int best = -1;
            for (int i = 0; i < days.length; i++) {
                if (days[i] <= 1) continue;
                if (best < 0 || days[i] > days[best]) best = i;
            }
            if (best < 0) break;
            days[best]--;
            assigned--;
        }

        for (int i = 0; i < plan.books.size(); i++) {
            Book b = plan.books.get(i);
            int d = days[i];
            d = Math.max(d, (int) Math.ceil(b.pages / (double) cfg.maxDailyPages));
            b.days = Math.max(1, d);
        }
    }

    private static void assignDays(Plan plan) {
        int cursor = 0;
        for (Book b : plan.books) {
            b.startDay = cursor;
            cursor += Math.max(1, b.days);
            b.endDay = cursor;
        }
        plan.totalDays = cursor;
    }
}
