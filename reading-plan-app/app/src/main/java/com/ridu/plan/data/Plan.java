package com.ridu.plan.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Plan {
    public String planName = "阅读计划";
    public String source = "";
    public LocalDate startDate = LocalDate.now();
    public int totalDays;
    public final List<Book> books = new ArrayList<>();

    public static Plan parse(String json) throws JSONException {
        json = json.replace("\uFEFF", "").trim();
        JSONObject root = new JSONObject(json);
        Plan plan = new Plan();
        plan.planName = root.optString("planName", "阅读计划").trim();
        plan.source = root.optString("source", "").trim();
        String date = root.optString("startDate", "");
        if (!date.isEmpty()) {
            try {
                plan.startDate = LocalDate.parse(date);
            } catch (Exception ignored) {
                plan.startDate = LocalDate.now();
            }
        }

        JSONArray array = root.optJSONArray("books");
        if (array == null || array.length() == 0) {
            throw new JSONException("books 为空");
        }
        int cursor = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            Book b = new Book();
            b.index = o.optInt("index", plan.books.size() + 1);
            b.title = o.optString("title", "").trim();
            if (b.title.isEmpty()) {
                throw new JSONException("第 " + (i + 1) + " 本书缺少 title");
            }
            b.author = o.optString("author", "").trim();
            b.part = o.optString("part", "").trim();
            b.stage = o.optString("stage", "").trim();
            b.slot = o.optString("slot", "").trim();
            b.mode = o.optString("mode", "通读").trim();
            b.days = Math.max(1, o.optInt("days", 7));
            b.pages = Math.max(1, o.optInt("pages", 300));
            b.background = o.optString("background", "暂无写作背景。").trim();
            b.prerequisite = o.optString("prerequisite", "暂无前置知识。").trim();
            b.startDay = cursor;
            cursor += b.days;
            b.endDay = cursor;
            plan.books.add(b);
        }
        if (plan.books.isEmpty()) {
            throw new JSONException("没有有效的书");
        }
        plan.totalDays = Math.max(cursor, root.optInt("totalDays", cursor));
        if (plan.totalDays > cursor) {
            // 如果 totalDays 大于实际天数，把差额留给最后一本书之前的空档没有意义，
            // 这里以实际书单天数为主，避免“永远读不完”。
            plan.totalDays = cursor;
        }
        return plan;
    }

    public Book bookForDay(int dayIndex) {
        if (books.isEmpty()) return null;
        if (dayIndex < 0) return books.get(0);
        for (Book b : books) {
            if (dayIndex >= b.startDay && dayIndex < b.endDay) return b;
        }
        return books.get(books.size() - 1);
    }

    public Book bookByIndex(int index) {
        for (Book b : books) {
            if (b.index == index) return b;
        }
        return null;
    }

    public int completedBooks(int dayIndex) {
        int n = 0;
        for (Book b : books) {
            if (dayIndex >= b.endDay) n++;
        }
        return n;
    }
}
