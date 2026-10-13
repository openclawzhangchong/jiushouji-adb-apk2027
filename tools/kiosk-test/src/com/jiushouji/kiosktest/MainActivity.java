package com.jiushouji.kiosktest;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Kiosk 测试主界面。
 * 同时声明为 HOME，用于验证「接管桌面」能力。
 * 刻意使用 API 16 可用的 API，Android 5.1 特有 API 通过反射调用。
 */
public class MainActivity extends Activity {

    public static final String TAG = "KioskTest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 常亮 + 尝试解除锁屏（无密码锁屏时有效）
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        String owner = isDeviceOwner();
        String lockTask = isLockTaskPermitted();
        Log.i(TAG, "MainActivity onCreate, isDeviceOwner=" + owner
                + ", isLockTaskPermitted=" + lockTask);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B3D2E"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);

        root.addView(line("KIOSK TEST APP", 28, "#7CFC00"));
        root.addView(line("已接管桌面（HOME）", 18, "#FFFFFF"));
        root.addView(line("com.jiushouji.kiosktest", 14, "#AAAAAA"));
        root.addView(line("", 10, "#FFFFFF"));
        root.addView(line("Device Owner: " + owner, 16,
                "YES".equals(owner) ? "#7CFC00" : "#FF6B6B"));
        root.addView(line("LockTask 可用: " + lockTask, 16,
                "YES".equals(lockTask) ? "#7CFC00" : "#FF6B6B"));
        root.addView(line("", 10, "#FFFFFF"));
        root.addView(line("启动 " + new SimpleDateFormat("HH:mm:ss").format(new Date())
                + "   pid " + android.os.Process.myPid(), 12, "#888888"));
        root.addView(line("", 10, "#FFFFFF"));

        root.addView(btn("1. SET LOCKTASK PACKAGES", new Runnable() {
            public void run() { setLockTaskPackages(); }
        }));
        root.addView(btn("2. START LOCK TASK", new Runnable() {
            public void run() { startLock(); }
        }));
        root.addView(btn("3. STOP LOCK TASK", new Runnable() {
            public void run() { stopLock(); }
        }));
        root.addView(btn("9. WIPE DATA (factory reset)", new Runnable() {
            public void run() { wipe(); }
        }));

        setContentView(root);
    }

    private TextView line(String text, int size, String color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button btn(String label, final Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        return b;
    }

    private ComponentName admin() {
        return new ComponentName(this, AdminReceiver.class);
    }

    /** 反射：DevicePolicyManager.setLockTaskPackages（API 21+） */
    private void setLockTaskPackages() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            Method m = DevicePolicyManager.class.getMethod(
                    "setLockTaskPackages", ComponentName.class, String[].class);
            m.invoke(dpm, admin(), new String[]{ getPackageName() });
            Log.i(TAG, "setLockTaskPackages OK");
        } catch (Throwable e) {
            Log.e(TAG, "setLockTaskPackages 失败: " + e);
        }
    }

    /** 反射：Activity.startLockTask（API 21+） */
    private void startLock() {
        try {
            Method m = Activity.class.getMethod("startLockTask");
            m.invoke(this);
            Log.i(TAG, "startLockTask OK");
        } catch (Throwable e) {
            Log.e(TAG, "startLockTask 失败: " + e);
        }
    }

    /** 反射：Activity.stopLockTask（API 21+） */
    private void stopLock() {
        try {
            Method m = Activity.class.getMethod("stopLockTask");
            m.invoke(this);
            Log.i(TAG, "stopLockTask OK");
        } catch (Throwable e) {
            Log.e(TAG, "stopLockTask 失败: " + e);
        }
    }

    /** 反射：DevicePolicyManager.wipeData（Device Owner 专用） */
    private void wipe() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            Method m = DevicePolicyManager.class.getMethod("wipeData", int.class);
            m.invoke(dpm, 0);
            Log.i(TAG, "wipeData 已调用");
        } catch (Throwable e) {
            Log.e(TAG, "wipeData 失败: " + e);
        }
    }

    /** 反射查询是否已是 Device Owner（isDeviceOwnerApp 为 API 18+） */
    private String isDeviceOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            Method m = DevicePolicyManager.class.getMethod("isDeviceOwnerApp", String.class);
            Object r = m.invoke(dpm, getPackageName());
            return Boolean.TRUE.equals(r) ? "YES" : "NO";
        } catch (Throwable e) {
            return "查询失败";
        }
    }

    /** 反射查询 LockTask 是否对本包开放（API 21+） */
    private String isLockTaskPermitted() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager)
                    getSystemService(Context.DEVICE_POLICY_SERVICE);
            Method m = DevicePolicyManager.class.getMethod("isLockTaskPermitted", String.class);
            Object r = m.invoke(dpm, getPackageName());
            return Boolean.TRUE.equals(r) ? "YES" : "NO";
        } catch (Throwable e) {
            return "不可用";
        }
    }
}
