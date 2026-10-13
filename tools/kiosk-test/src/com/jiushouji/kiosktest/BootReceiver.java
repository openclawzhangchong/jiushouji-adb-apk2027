package com.jiushouji.kiosktest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启接收器。
 * 用于验证：应用安装并启动过一次后，能否在开机时自动拉起。
 * 注意：framework 软重启后 BOOT_COMPLETED 会再次广播，此接收器也会被再次触发。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(MainActivity.TAG, "BootReceiver onReceive action=" + action
                + " at " + new java.util.Date());

        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(i);
            Log.i(MainActivity.TAG, "BootReceiver 已拉起 MainActivity");
        } catch (Throwable e) {
            Log.e(MainActivity.TAG, "BootReceiver 拉起失败: " + e);
        }
    }
}
