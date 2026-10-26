package com.proxfibre.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Updates from GitHub Releases (ProxFibre/ProxFibre-TV): asks the public API for the newest
 * release, compares its tag with this build's versionName, and installs the .apk asset through the
 * same installer flow the bundled apps use. Nothing is downloaded until the user asks.
 *
 * A quiet check runs at most once a day when Home starts, so Settings can show "1.0.1 available"
 * without the TV doing work in the background.
 */
final class Update {
    static final String REPO = "ProxFibre/ProxFibre-TV";
    private static final String PREFS = "update";
    private static final String K_TAG = "tag", K_URL = "url", K_PAGE = "page",
            K_NOTES = "notes", K_AT = "checked_at", K_ERROR = "error";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private Update() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** This build's version, e.g. "1.0.0". */
    static String current(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    /** "v1.2.3" / "1.2.3" -> {1,2,3}; anything unparsable counts as 0. */
    static int[] parts(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.indexOf('-');
        if (cut > 0) s = s.substring(0, cut);          // 1.2.3-beta1 -> 1.2.3
        String[] p = s.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < out.length && i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    static boolean isNewer(String tag, String current) {
        int[] a = parts(tag), b = parts(current);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    /** The newest published release, or null. Blocking: call off the UI thread. */
    static JSONObject latest(Context c) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(
                "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "ProxFibre/" + current(c));
        try {
            int code = con.getResponseCode();
            if (code == 404) throw new IllegalStateException("no release");
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            return new JSONObject(new String(Suggestions.readAll(con.getInputStream()),
                    StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }

    /** Checks now and remembers the answer in prefs. Returns true when a newer build exists. */
    static boolean check(Context c) {
        SharedPreferences p = prefs(c);
        try {
            JSONObject rel = latest(c);
            String tag = rel.optString("tag_name", "");
            String page = rel.optString("html_url", "");
            String apk = "";
            JSONArray assets = rel.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (name.endsWith(".apk")) {
                    apk = a.optString("browser_download_url", "");
                    break;
                }
            }
            p.edit().putString(K_TAG, tag).putString(K_URL, apk).putString(K_PAGE, page)
                    .putString(K_NOTES, rel.optString("body", "").trim())
                    .putLong(K_AT, System.currentTimeMillis()).remove(K_ERROR).apply();
            return isNewer(tag, current(c));
        } catch (Exception e) {
            p.edit().putLong(K_AT, System.currentTimeMillis())
                    .putString(K_ERROR, String.valueOf(e.getMessage())).apply();
            return false;
        }
    }

    /** Called when Home starts: at most one request a day, and never on the UI thread. */
    static void checkQuiet(Context c) {
        long at = prefs(c).getLong(K_AT, 0);
        if (System.currentTimeMillis() - at < DAY) return;
        check(c);
    }

    /** Settings row value. */
    static String status(Context c) {
        SharedPreferences p = prefs(c);
        String tag = p.getString(K_TAG, "");
        String err = p.getString(K_ERROR, null);
        if (p.getLong(K_AT, 0) == 0) return "Not checked";
        if (err != null && !err.isEmpty()) return "Can't check";
        if (tag.isEmpty()) return "No releases yet";
        return isNewer(tag, current(c)) ? tag + " available" : "Up to date";
    }

    static boolean available(Context c) {
        String tag = prefs(c).getString(K_TAG, "");
        return !tag.isEmpty() && isNewer(tag, current(c));
    }

    /** The .apk asset of the newest release (empty when it has none). */
    static String apkUrl(Context c) {
        return prefs(c).getString(K_URL, "");
    }

    /** Settings action: check, then offer the download when there is one. */
    static void checkNow(final Activity a) {
        Toast.makeText(a, Lang.s("Checking for updates…", "Recherche de mise à jour…",
                "Buscando actualización…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean newer = check(a);
            final String tag = prefs(a).getString(K_TAG, "");
            final String apk = prefs(a).getString(K_URL, "");
            final String err = prefs(a).getString(K_ERROR, "");
            a.runOnUiThread(() -> {
                if (newer && !apk.isEmpty()) {
                    new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(Lang.s("Update " + tag, "Mise à jour " + tag, "Actualización " + tag))
                            .setMessage(Lang.s("Version " + current(a) + " is installed. Install " + tag + " now?",
                                    "La version " + current(a) + " est installée. Installer " + tag + " maintenant ?",
                                    "La versión " + current(a) + " está instalada. ¿Instalar " + tag + " ahora?"))
                            .setPositiveButton(Lang.t("Install"), (d, w) -> install(a, apk))
                            .setNegativeButton(Lang.t("Later"), null)
                            .show();
                } else if (newer) {
                    Toast.makeText(a, Lang.s("A release exists but has no APK attached",
                            "Une version existe mais sans APK joint",
                            "Hay una versión pero sin APK adjunto"), Toast.LENGTH_LONG).show();
                } else if (err != null && !err.isEmpty()) {
                    Toast.makeText(a, Lang.s("Can't check for updates right now",
                            "Impossible de vérifier les mises à jour",
                            "No se pueden comprobar las actualizaciones"), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(a, Lang.s("ProxFibre is up to date", "ProxFibre est à jour",
                            "ProxFibre está actualizado"), Toast.LENGTH_SHORT).show();
                }
            });
        }, "hume-update-check").start();
    }

    /** Downloads the release APK, then hands it to the system installer. */
    static void install(final Activity a, final String url) {
        if (!Bundled.canInstall(a)) {
            Toast.makeText(a, Lang.s("Allow ProxFibre to install apps first (Settings → Apps)",
                    "Autorisez d'abord ProxFibre à installer des applis (Réglages → Applis)",
                    "Permite primero a ProxFibre instalar apps (Ajustes → Apps)"), Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // no such screen on this TV: the user can find it in the TV settings
            }
            return;
        }
        Toast.makeText(a, Lang.s("Downloading the update…", "Téléchargement de la mise à jour…",
                "Descargando la actualización…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String name = "ProxFibre-update.apk";
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(120000);
                con.setRequestProperty("User-Agent", "ProxFibre/" + current(a));
                con.setRequestProperty("Accept", "application/octet-stream");
                if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
                File f = Bundled.apkFile(a, name);
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                con.disconnect();
                a.runOnUiThread(() -> openInstaller(a, name));
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, Lang.s("The update couldn't be downloaded",
                        "La mise à jour n'a pas pu être téléchargée",
                        "No se pudo descargar la actualización"), Toast.LENGTH_LONG).show());
            }
        }, "hume-update-download").start();
    }

    private static void openInstaller(Activity a, String name) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Bundled.apkUri(a, name), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(a, Lang.t("No installer available on this TV"), Toast.LENGTH_SHORT).show();
        }
    }
}
