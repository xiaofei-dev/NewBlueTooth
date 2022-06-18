package com.example.bluetooth.utils;

import android.content.Context;
import android.widget.Toast;

/**
 * @author xiaofei_dev
 * @date 2022/5/11
 */
public class ToastUtils {
    public static void showToast(Context context, String content) {
        Toast.makeText(context.getApplicationContext(),
                content,
                Toast.LENGTH_SHORT)
                .show();
    }

    public static void showToast(Context context, int content) {
        Toast.makeText(context.getApplicationContext(),
                content,
                Toast.LENGTH_SHORT)
                .show();
    }
}
