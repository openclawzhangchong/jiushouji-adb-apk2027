package com.jiushouji.kiosktest;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 设备管理员接收器。
 * 存在这个组件，才能用 `dpm set-device-owner` 把应用设为 Device Owner，
 * 进而使用 LockTask（锁定任务）实现真正的 Kiosk 模式。
 */
public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(MainActivity.TAG, "AdminReceiver onEnabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(MainActivity.TAG, "AdminReceiver onDisabled");
    }
}
