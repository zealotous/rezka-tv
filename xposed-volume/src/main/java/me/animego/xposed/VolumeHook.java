package me.animego.xposed;

import android.app.Dialog;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Moves the MiTV volume dialog from the center of the screen to the right
 * edge. The dialog is created by com.xiaomi.mitv.systemui.volume.b with the
 * window title "VolumeUI" and a hard-coded gravity of CENTER.
 *
 * Hooked at runtime, so MiTVSystemUI.apk keeps its original platform
 * signature and android.uid.system shared UID.
 */
public class VolumeHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.xiaomi.mitv.systemui";
    private static final String WINDOW_TITLE = "VolumeUI";
    private static final float EDGE_MARGIN_DP = 20f;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Dialog",
                    lpparam.classLoader,
                    "show",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Dialog dialog = (Dialog) param.thisObject;
                                Window window = dialog.getWindow();
                                if (window == null) {
                                    return;
                                }

                                WindowManager.LayoutParams lp = window.getAttributes();
                                CharSequence title = lp.getTitle();
                                if (title == null || !WINDOW_TITLE.contentEquals(title)) {
                                    return;
                                }

                                float density = dialog.getContext()
                                        .getResources().getDisplayMetrics().density;
                                lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                                lp.x = -Math.round(EDGE_MARGIN_DP * density);
                                window.setAttributes(lp);

                                XposedBridge.log("VolumeHook: VolumeUI moved to the right edge");
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    });
            XposedBridge.log("VolumeHook: installed in " + TARGET_PACKAGE);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
