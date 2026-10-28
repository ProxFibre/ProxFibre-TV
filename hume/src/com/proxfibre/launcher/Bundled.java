package com.proxfibre.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;

/**
 * Armadillo (VPN) and the AirPlay Server: offered once on first run, one after another. Fetched
 * from their own GitHub releases (see AppCatalog.java, which does the actual download, permission
 * check and install) — nothing is bundled inside the ProxFibre APK.
 *
 * Android asks for one confirmation per app, and (Android 8+) needs "install unknown apps" for
 * ProxFibre. The flow resumes by itself: when the user comes back from the installer or from that
 * setting, the next missing app is offered.
 */
final class Bundled {
    static final String ARMADILLO = "dev.kazuryy.armadillo";
    static final String AIRPLAY = "io.github.jqssun.airplay";

    /** catalog id, package. */
    private static final String[][] APPS = {
            {"armadillo", ARMADILLO},
            {"airplay", AIRPLAY},
    };

    static final int ARMADILLO_INDEX = 0;
    static final int AIRPLAY_INDEX = 1;

    private Bundled() {
    }

    /** Settings row value: "Installed" or what the row will do. */
    static String status(Context c, int i) {
        return installed(c, APPS[i][1]) ? "Installed" : "Install…";
    }

    /** Settings row action: install (or reinstall) one of these apps now. */
    static void installNow(Activity a, int i) {
        prefs(a).edit().putInt("pending", i).apply();
        AppCatalog.Entry e = AppCatalog.findBuiltin(APPS[i][0]);
        if (e != null) AppCatalog.install(a, e);
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
            if (!installed(c, APPS[i][1])) return i;
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
                .setMessage(Lang.s("ProxFibre can install two apps: Armadillo, a VPN client, and AirPlay Server, so iPhones and Macs can cast to this TV. Android asks for one confirmation per app.",
                        "ProxFibre peut installer deux applis : Armadillo, un client VPN, et AirPlay Server, pour diffuser depuis un iPhone ou un Mac vers cette TV. Android demande une confirmation par appli.",
                        "ProxFibre puede instalar dos apps: Armadillo, un cliente VPN, y AirPlay Server, para enviar contenido desde un iPhone o Mac a este TV. Android pide una confirmación por app."))
                .setPositiveButton(Lang.t("Install"), (d, w) -> installNext(a, 0))
                .setNegativeButton(Lang.t("Later"), (d, w) -> prefs(a).edit()
                        .putBoolean("offered", false)
                        .putLong("snooze", System.currentTimeMillis()).apply())
                .show();
    }

    /** Called on resume: moves the chain to the next app once the current one is installed. */
    static void resume(Activity a) {
        int pending = prefs(a).getInt("pending", -1);
        if (pending < 0) return;
        if (installed(a, APPS[pending][1])) {
            prefs(a).edit().putInt("pending", -1).apply();
            installNext(a, pending + 1);
        }
        // Still installing (AppCatalog.install() goes straight to the system installer now, so
        // there's no pending permission step to pick back up here).
    }

    static void installNext(Activity a, int from) {
        int i = nextMissing(a, from);
        if (i < 0) {
            prefs(a).edit().putInt("pending", -1).apply();
            return;
        }
        prefs(a).edit().putInt("pending", i).apply();
        AppCatalog.Entry e = AppCatalog.findBuiltin(APPS[i][0]);
        if (e != null) AppCatalog.install(a, e);
    }
}
