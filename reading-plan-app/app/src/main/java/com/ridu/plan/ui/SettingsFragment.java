package com.ridu.plan.ui;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.ridu.plan.BuildConfig;
import com.ridu.plan.MainActivity;
import com.ridu.plan.R;
import com.ridu.plan.data.Plan;
import com.ridu.plan.data.PlanStore;
import com.ridu.plan.imports.PlanImporter;
import com.ridu.plan.notify.ReminderScheduler;
import com.ridu.plan.update.UpdateChecker;
import com.ridu.plan.util.DateUtils;

import java.time.LocalDate;
import java.util.function.Consumer;

public class SettingsFragment extends Fragment implements Refreshable {
    private MaterialSwitch swReminder;
    private MaterialSwitch swAutoUpdate;
    private TextView tvReminderTime;
    private TextView tvStartDate;
    private TextView tvPlanUrl;
    private TextView tvUpdateUrl;
    private TextView tvToken;
    private TextView tvVersion;
    private TextView tvAboutPlan;
    private TextView tvNoticePermission;
    private MaterialButton btnNotifPermission;
    private ActivityResultLauncher<String[]> openPlan;
    private ActivityResultLauncher<String[]> openApk;
    private boolean binding = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        swReminder = v.findViewById(R.id.sw_reminder);
        swAutoUpdate = v.findViewById(R.id.sw_auto_update);
        tvReminderTime = v.findViewById(R.id.tv_reminder_time);
        tvStartDate = v.findViewById(R.id.tv_start_date);
        tvPlanUrl = v.findViewById(R.id.tv_plan_url);
        tvUpdateUrl = v.findViewById(R.id.tv_update_url);
        tvToken = v.findViewById(R.id.tv_token);
        tvVersion = v.findViewById(R.id.tv_version);
        tvAboutPlan = v.findViewById(R.id.tv_about_plan);
        tvNoticePermission = v.findViewById(R.id.tv_notice_permission);
        btnNotifPermission = v.findViewById(R.id.btn_notif_permission);

        openPlan = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importPlanUri(uri);
                });
        openApk = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) UpdateChecker.installLocalApk(requireActivity(), uri);
                });

        swReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding) return;
            PlanStore.setReminderEnabled(requireContext(), isChecked);
            if (isChecked) ReminderScheduler.schedule(requireContext());
            else ReminderScheduler.cancel(requireContext());
            updatePermissionRow();
        });

        swAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding) return;
            PlanStore.setAutoUpdate(requireContext(), isChecked);
        });

        v.findViewById(R.id.btn_time).setOnClickListener(x -> pickTime());
        v.findViewById(R.id.btn_restart_today).setOnClickListener(x -> confirmRestart());
        v.findViewById(R.id.btn_import_file).setOnClickListener(x ->
                openPlan.launch(new String[]{"application/json", "text/plain", "*/*"}));
        v.findViewById(R.id.btn_import_clipboard).setOnClickListener(x -> importClipboard());
        v.findViewById(R.id.btn_plan_url).setOnClickListener(x -> editPlanUrl());
        v.findViewById(R.id.btn_update_url).setOnClickListener(x -> editAppUrl());
        v.findViewById(R.id.btn_token).setOnClickListener(x -> editToken());
        v.findViewById(R.id.btn_check_update).setOnClickListener(x ->
                UpdateChecker.checkForUpdate(requireActivity(), PlanStore.getAppUpdateUrl(requireContext()), false));
        v.findViewById(R.id.btn_install_local).setOnClickListener(x ->
                openApk.launch(new String[]{"application/vnd.android.package-archive", "application/octet-stream", "*/*"}));
        btnNotifPermission.setOnClickListener(x -> requestNotificationPermission());

        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (tvReminderTime == null) return;
        binding = true;
        swReminder.setChecked(PlanStore.isReminderEnabled(requireContext()));
        swAutoUpdate.setChecked(PlanStore.isAutoUpdate(requireContext()));
        binding = false;

        int h = PlanStore.getReminderHour(requireContext());
        int m = PlanStore.getReminderMinute(requireContext());
        tvReminderTime.setText(String.format("提醒时间：%02d:%02d", h, m));

        LocalDate start = PlanStore.getStartDate(requireContext());
        tvStartDate.setText("开始日期：" + start.format(DateUtils.ISO)
                + " · 今天是第 " + (DateUtils.dayIndex(start, LocalDate.now()) + 1) + " 天");

        String planUrl = PlanStore.getPlanUpdateUrl(requireContext());
        tvPlanUrl.setText(planUrl.isEmpty() ? "书单更新地址：未设置（可手动导入）" : "书单更新地址：" + planUrl);
        String appUrl = PlanStore.getAppUpdateUrl(requireContext());
        tvUpdateUrl.setText(appUrl.isEmpty() ? "应用更新地址：未设置（可安装本地 APK）" : "应用更新地址：" + appUrl);
        String token = PlanStore.getGithubToken(requireContext());
        tvToken.setText(token.isEmpty() ? "GitHub 只读 Token：未设置"
                : "GitHub 只读 Token：已设置（" + mask(token) + "）");

        tvVersion.setText("当前版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        Plan plan = PlanStore.getPlan(requireContext());
        tvAboutPlan.setText("当前计划：" + plan.planName + " · " + plan.books.size() + " 本 · "
                + plan.totalDays + " 天");

        TextView source = requireView().findViewById(R.id.tv_source);
        source.setText(plan.source.isEmpty() ? "本地导入" : plan.source);
        source.setMovementMethod(LinkMovementMethod.getInstance());

        updatePermissionRow();
    }

    private void updatePermissionRow() {
        if (btnNotifPermission == null) return;
        boolean need = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        btnNotifPermission.setVisibility(need ? View.VISIBLE : View.GONE);
        tvNoticePermission.setVisibility(need ? View.VISIBLE : View.GONE);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 13);
        }
    }

    private void pickTime() {
        int h = PlanStore.getReminderHour(requireContext());
        int m = PlanStore.getReminderMinute(requireContext());
        new TimePickerDialog(requireContext(), (view, hour, minute) -> {
            PlanStore.setReminderTime(requireContext(), hour, minute);
            ReminderScheduler.schedule(requireContext());
            refresh();
        }, h, m, true).show();
    }

    private void confirmRestart() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("从今天重新开始？")
                .setMessage("开始日期会改为今天，书单本身不变。")
                .setPositiveButton("确定", (d, w) -> {
                    PlanStore.setStartDate(requireContext(), LocalDate.now());
                    ReminderScheduler.schedule(requireContext());
                    Toast.makeText(requireContext(), "已从今天重新开始", Toast.LENGTH_SHORT).show();
                    refresh();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshCurrent();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importClipboard() {
        ClipboardManager cm = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) {
            Toast.makeText(requireContext(), "剪贴板是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip.getItemCount() == 0) return;
        String text = clip.getItemAt(0).coerceToText(requireContext()).toString();
        PlanImporter.importJson(requireActivity(), text, this::afterImport);
    }

    private void importPlanUri(Uri uri) {
        new Thread(() -> {
            try {
                String json = PlanImporter.readUri(requireContext(), uri);
                Activity a = getActivity();
                if (a == null) return;
                a.runOnUiThread(() -> PlanImporter.importJson(a, json, this::afterImport));
            } catch (Exception e) {
                Activity a = getActivity();
                if (a != null) {
                    a.runOnUiThread(() -> Toast.makeText(requireContext(),
                            "读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void afterImport() {
        refresh();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshCurrent();
        }
    }

    private void editPlanUrl() {
        inputDialog("书单 JSON 地址", PlanStore.getPlanUpdateUrl(requireContext()), value -> {
            PlanStore.setPlanUpdateUrl(requireContext(), value);
            refresh();
            if (!value.isEmpty()) {
                PlanImporter.fetchAndImport(requireActivity(), value, this::afterImport);
            }
        });
    }

    private void editAppUrl() {
        inputDialog("应用更新地址（version.json 或 GitHub manifest 接口）",
                PlanStore.getAppUpdateUrl(requireContext()), value -> {
            PlanStore.setAppUpdateUrl(requireContext(), value);
            refresh();
        });
    }

    private void editToken() {
        inputDialog("GitHub 只读 Token", PlanStore.getGithubToken(requireContext()), value -> {
            PlanStore.setGithubToken(requireContext(), value);
            refresh();
            Toast.makeText(requireContext(), value.isEmpty() ? "已清除 Token" : "已保存 Token",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private static String mask(String token) {
        if (token.length() <= 4) return "已隐藏";
        return "…" + token.substring(token.length() - 4);
    }

    private void inputDialog(String title, String value, Consumer<String> onOk) {
        EditText et = new EditText(requireContext());
        et.setText(value);
        et.setSelection(et.getText().length());
        et.setSingleLine(false);
        et.setHint("https://…");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad / 2, pad, pad / 2);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(et)
                .setPositiveButton("保存", (d, w) -> onOk.accept(et.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }
}
