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
import com.ridu.plan.MainActivity;
import com.ridu.plan.R;
import com.ridu.plan.data.Book;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.imports.PlanImporter;
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
