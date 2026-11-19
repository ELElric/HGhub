package com.ridu.plan.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ridu.plan.MainActivity;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.imports.PlanImporter;
import com.ridu.plan.notify.ReminderScheduler;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PlanFragment extends Fragment implements Refreshable {
    private TextView tvPlanName;
    private TextView tvProgress;
    private ProgressBar pbOverall;
    private RecyclerView recycler;
    private PlanAdapter adapter;
    private ActivityResultLauncher<String[]> openDocument;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvPlanName = view.findViewById(R.id.tv_plan_name);
        tvProgress = view.findViewById(R.id.tv_plan_progress);
        pbOverall = view.findViewById(R.id.pb_overall);
        recycler = view.findViewById(R.id.rv_plan);
        MaterialButton importBtn = view.findViewById(R.id.btn_import);
        MaterialButton plansBtn = view.findViewById(R.id.btn_plans);

        adapter = new PlanAdapter(book -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showBookDetail(book);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        openDocument = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importUri(uri);
                });

        importBtn.setOnClickListener(v -> openDocument.launch(
                new String[]{"application/json", "text/plain", "*/*"}));
        plansBtn.setOnClickListener(v -> showPlanLibrary());
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        if (tvPlanName == null) return;
        Plan plan = PlanStore.getPlan(requireContext());
        LocalDate start = PlanStore.getStartDate(requireContext());
        int todayIndex = DateUtils.dayIndex(start, LocalDate.now());

        tvPlanName.setText(plan.planName + " · 共 " + plan.books.size() + " 本");
        int shown = Math.max(0, Math.min(plan.totalDays, todayIndex + 1));
        int percent = plan.totalDays <= 0 ? 0 : shown * 100 / plan.totalDays;
        pbOverall.setProgress(percent);
        tvProgress.setText("第 " + shown + " 天 / 共 " + plan.totalDays + " 天 · 已完成 "
                + plan.completedBooks(todayIndex) + " 本 · " + percent + "%");

        List<Object> rows = new ArrayList<>();
        String lastSlot = null;
        for (Book b : plan.books) {
            String slot = (b.stage + " · " + b.slot).trim();
            if (!slot.equals(lastSlot)) {
                rows.add(slot);
                lastSlot = slot;
            }
            rows.add(b);
        }
        adapter.setData(rows, start, todayIndex);
    }

    private void showPlanLibrary() {
        List<PlanStore.PlanRecord> records = PlanStore.listArchived(requireContext());
        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "计划库还是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            PlanStore.PlanRecord r = records.get(i);
            items[i] = (r.active ? "● " : "") + r.name + " · " + r.books + " 本 / "
                    + r.days + " 天" + (r.active ? "（当前）" : "");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("计划库")
                .setItems(items, (d, which) -> {
                    PlanStore.PlanRecord r = records.get(which);
                    if (r.active) return;
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("切换到《" + r.name + "》？")
                            .setMessage("当前计划会存入计划库，打卡记录保留；切换后可随时切回。")
                            .setPositiveButton("切换", (x, y) -> switchTo(r))
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void switchTo(PlanStore.PlanRecord record) {
        try {
            PlanStore.activateArchived(requireContext(), record.id);
            ReminderScheduler.schedule(requireContext());
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshCurrent();
            }
            load();
            Toast.makeText(requireContext(), "已切换到《" + record.name + "》", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "切换失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importUri(Uri uri) {
        new Thread(() -> {
            try {
                String json = PlanImporter.readUri(requireContext(), uri);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> PlanImporter.importJson(
                        requireActivity(), json,
                        () -> {
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).refreshCurrent();
                            }
                        }));
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(
                            requireContext(), "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }
}
