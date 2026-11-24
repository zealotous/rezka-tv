# Volume OSD Right — Xposed module

Moves the MiTV volume OSD from the centre of the screen to the **right edge**
on the Xiaomi Mijia Youth projector (`MiProjLED1 / conan`, Android 6.0.1).

## Why an Xposed hook instead of patching MiTVSystemUI.apk

The volume dialog is created in `com.xiaomi.mitv.systemui`:

- `res/layout/volume_dialog.xml` — a centred `CircleSeekBar` + label.
- `com.xiaomi.mitv.systemui.volume.b` — sets
  `WindowManager.LayoutParams.gravity = 17` (CENTER).

Editing `MiTVSystemUI.apk` directly is not safe:

- it is signed with Xiaomi’s platform key, and
- it declares `android:sharedUserId="android.uid.system"`.

Re-signing it with another key breaks the system UID and can take down the
system UI. This ROM also has **no runtime resource overlay (`overlay`)
service**, so an RRO cannot be used.

The hook changes only the live window attributes:

```java
lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
lp.x = -20dp; // small margin from the edge
window.setAttributes(lp);
```

It targets only the dialog whose window title is `VolumeUI`, so other
MiTV dialogs are untouched.

## Requirements

- Xposed framework v89 (SDK 23 / ARM), installed via Magisk.
- Xposed Installer 3.1.4 Magisk (`XposedInstaller_3.1.4_Magisk.apk`).
- This module APK installed as a normal user app.

## Build

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew :xposed-volume:assembleDebug
# APK: xposed-volume/build/outputs/apk/debug/xposed-volume-debug.apk
```

`libs/xposed-api.jar` is the compile-time API extracted from the framework’s
`XposedBridge.jar` with dex2jar. It is `compileOnly`, so the Xposed classes
are not duplicated inside the module APK.

## Install

```bash
adb install -r xposed-volume/build/outputs/apk/debug/xposed-volume-debug.apk
adb shell su -c \
  'echo /data/app/me.animego.xposed.volumeright-1/base.apk > \
   /data/data/de.robv.android.xposed.installer/conf/modules.list'
adb shell su -c reboot
```

Alternatively enable **Volume OSD Right** in Xposed Installer → Modules.
The module intentionally has **no user interface**; tapping it only shows
“this module doesn’t provide user interface”. Use the checkbox, then reboot.

## Verify

Xposed log:

```bash
adb shell su -c \
  'grep -i VolumeHook /data/data/de.robv.android.xposed.installer/log/error.log'
```

Expected lines include:

```
Loading modules from /data/app/me.animego.xposed.volumeright-1/base.apk
Loading class me.animego.xposed.VolumeHook
VolumeHook: installed in com.xiaomi.mitv.systemui
VolumeHook: VolumeUI moved to the right edge
```

Then press a volume key and check the OSD position:

```bash
adb shell input keyevent 24
```

## Disable / recover

Disable the module in Xposed Installer and reboot, or remove its line from
`modules.list`. If the system UI is affected, delete the line via TWRP or ADB
root and reboot.
