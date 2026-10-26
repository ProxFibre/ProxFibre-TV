package com.proxfibre.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Armadillo (VPN) and the AirPlay Server ship inside this APK and are installed on first run.
 *
 * Android asks for one confirmation per app, and (Android 8+) needs "install unknown apps" for ProxFibre.
 * The flow resumes by itself: when the user comes back from the installer or from that setting, the
 * next missing app is offered. Nothing is downloaded.
 */
final class Bundled {
    static final String ARMADILLO = "dev.kazuryy.armadillo";
    static final String AIRPLAY = "io.github.jqssun.airplay";

    /** package, asset, cache file, label. */
    private static final String[][] APPS = {
            {ARMADILLO, "armadillo/armadillo.apk", "armadillo.apk", "Armadillo"},
            {AIRPLAY, "airplay/airplay.apk", "airplay.apk", "AirPlay Server"},
    };

    private Bundled() {
    }

    static final int ARMADILLO_INDEX = 0;
    static final int AIRPLAY_INDEX = 1;

    /** Settings row label, e.g. "Armadillo". */
    static String label(int i) {
        return APPS[i][3];
    }

    /** Settings row value: "Installed" or what the row will do. */
    static String status(Context c, int i) {
        return installed(c, APPS[i][0]) ? "Installed" : "Install\u2026";
    }

    /** Settings row action: install (or reinstall) one of the bundled apps now. */
    static void installNow(Activity a, int i) {
        prefs(a).edit().putInt("pending", i).putBoolean("waiting_perm", false).apply();
        if (!canInstall(a)) {
            askPermission(a, i);
            return;
        }
        extract(a, i);
    }

    static File apkFile(Context c, String name) {
        File dir = new File(c.getCacheDir(), "bundled");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    static Uri apkUri(Context c, String name) {
        return new Uri.Builder().scheme("content").authority(ApkStore.AUTHORITY)
                .appendPath(name).build();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("bundled", Context.MODE_PRIVATE);
    }

    static boolean installed(Context c, String pkg) {
        return Suggestions.installed(c, pkg);
    }

    private static int nextMissing(Context c, int from) {
        for (int i = Math.max(0, from); i < APPS.length; i++) {
            if (!installed(c, APPS[i][0])) return i;
        }
        return -1;
    }

    static boolean anyMissing(Context c) {
        return nextMissing(c, 0) >= 0;
    }

    /** First run: offers Armadillo and AirPlay, once. */
    static void offer(final Activity a) {
        SharedPreferences p = prefs(a);
        if (!anyMissing(a)) {
            p.edit().putBoolean("offered", true).apply();
            return;
        }
        boolean snoozed = System.currentTimeMillis() - p.getLong("snooze", 0L) < 3L * 24 * 60 * 60 * 1000;
        if (p.getBoolean("offered", false) || snoozed) return;
        p.edit().putBoolean("offered", true).apply();
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Lang.t("Install Armadillo and AirPlay?"))
                .setMessage(Lang.s("ProxFibre can install two apps it ships with: Armadillo, a VPN client, and AirPlay Server, so iPhones and Macs can cast to this TV. Android asks for one confirmation per app.",
                        "ProxFibre peut installer deux applis qu'elle embarque : Armadillo, un client VPN, et AirPlay Server, pour diffuser depuis un iPhone ou un Mac vers cette TV. Android demande une confirmation par appli.",
                        "ProxFibre puede instalar dos apps incluidas: Armadillo, un cliente VPN, y AirPlay Server, para enviar contenido desde un iPhone o Mac a este TV. Android pide una confirmación por app."))
                .setPositiveButton(Lang.t("Install"), (d, w) -> installNext(a, 0))
                .setNegativeButton(Lang.t("Later"), (d, w) -> prefs(a).edit()
                        .putBoolean("offered", false)
                        .putLong("snooze", System.currentTimeMillis()).apply())
                .show();
    }

    /** Called on Home resume: continues the chain, or opens the missing permission. */
    static void resume(Activity a) {
        int pending = prefs(a).getInt("pending", -1);
        if (pending < 0) return;
        if (installed(a, APPS[pending][0])) {
            prefs(a).edit().putInt("pending", -1).putBoolean("waiting_perm", false).apply();
            installNext(a, pending + 1);
        } else if (canInstall(a)) {
            // The "install unknown apps" switch was just turned on: carry on by itself.
            if (prefs(a).getBoolean("waiting_perm", false)) {
                prefs(a).edit().putBoolean("waiting_perm", false).apply();
                extract(a, pending);
            }
        } else {
            askPermission(a, pending);
        }
    }

    static void installNext(Activity a, int from) {
        int i = nextMissing(a, from);
        if (i < 0) {
            prefs(a).edit().putInt("pending", -1).apply();
            return;
        }
        if (!canInstall(a)) {
            askPermission(a, i);
            return;
        }
        prefs(a).edit().putInt("pending", i).apply();
        extract(a, i);
    }

    private static void extract(final Activity a, final int i) {
        if (!canInstall(a)) {
            askPermission(a, i);
            return;
        }
        final String asset = APPS[i][1];
        final String name = APPS[i][2];
        Toast.makeText(a, Lang.t("Preparing ") + APPS[i][3] + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                InputStream in = a.getAssets().open(asset);
                FileOutputStream out = new FileOutputStream(apkFile(a, name));
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                a.runOnUiThread(() -> openInstaller(a, name));
            } catch (Throwable t) {
                a.runOnUiThread(() -> Toast.makeText(a, APPS[i][3] + " could not be prepared",
                        Toast.LENGTH_SHORT).show());
            }
        }, "hume-bundled").start();
    }

    private static void openInstaller(Activity a, String name) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(apkUri(a, name), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(a, Lang.t("No installer available on this TV"), Toast.LENGTH_SHORT).show();
        }
    }

    static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            android.app.AppOpsManager aom = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            return aom.checkOpNoThrow("android:request_install_packages",
                    android.os.Process.myUid(), c.getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return c.getPackageManager().canRequestPackageInstalls();
        }
    }

    private static void askPermission(final Activity a, final int i) {
        prefs(a).edit().putInt("pending", i).putBoolean("waiting_perm", true).apply();
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Lang.t("Allow installs"))
                .setMessage(Lang.s("Android must allow ProxFibre to install apps. Turn on \"Install unknown apps\" for ProxFibre, then come back here: the install resumes on its own.",
                        "Android doit autoriser ProxFibre à installer des applis. Activez « Installer des applis inconnues » pour ProxFibre, puis revenez ici : l'installation reprend toute seule.",
                        "Android debe permitir a ProxFibre instalar apps. Activa «Instalar apps desconocidas» para ProxFibre y vuelve aquí: la instalación continúa sola."))
                .setPositiveButton(Lang.t("Open settings"), (d, w) -> {
                    try {
                        a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + a.getPackageName()))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) {
                        try {
                            a.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Throwable ignored2) {
                        }
                    }
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
    }
}
