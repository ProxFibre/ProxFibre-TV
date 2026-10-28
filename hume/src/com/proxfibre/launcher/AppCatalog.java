package com.proxfibre.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Custom apps, on top of what Armadillo/AirPlay already do (Bundled.java): a small built-in
 * catalog (also Armadillo and AirPlay, fetched live from their GitHub releases, nothing bundled
 * in the APK) plus apps the user adds on the TV itself, by a GitHub repo ("owner/name") or a
 * direct .apk link. Installing reuses the download → ApkStore → system-installer path Update.java
 * uses for app updates.
 */
final class AppCatalog {
    private AppCatalog() {
    }

    static final class Entry {
        final String id, name, pkg, repo, url;
        final boolean custom;

        Entry(String id, String name, String pkg, String repo, String url, boolean custom) {
            this.id = id;
            this.name = name;
            this.pkg = pkg;
            this.repo = repo;
            this.url = url;
            this.custom = custom;
        }
    }

    private static final Entry[] BUILTIN = {
            new Entry("armadillo", "Armadillo (VPN)", Bundled.ARMADILLO, "Kazuryy/armadillo-android", null, false),
            new Entry("airplay", "AirPlay Server", Bundled.AIRPLAY, "jqssun/android-airplay-server", null, false),
    };

    private static final String PREFS = "app_catalog";
    private static final String K_CUSTOM = "custom";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : BUILTIN) out.add(e);
        out.addAll(custom(c));
        return out;
    }

    static List<Entry> custom(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_CUSTOM, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.getString("id"), o.getString("name"),
                        o.optString("pkg", null), o.optString("repo", null), o.optString("url", null), true));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static Entry find(Context c, String id) {
        for (Entry e : all(c)) if (e.id.equals(id)) return e;
        return null;
    }

    /** A built-in entry (Armadillo, AirPlay) by id, with no Context needed. */
    static Entry findBuiltin(String id) {
        for (Entry e : BUILTIN) if (e.id.equals(id)) return e;
        return null;
    }

    /** Adds an app from either a GitHub "owner/repo" or a direct https://…/something.apk link. */
    static void addCustom(Context c, String name, String source) {
        String src = source.trim();
        boolean isUrl = src.startsWith("http://") || src.startsWith("https://");
        String id = "custom-" + System.currentTimeMillis();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_CUSTOM, "[]"));
            JSONObject o = new JSONObject();
            o.put("id", id).put("name", name.trim());
            if (isUrl) o.put("url", src);
            else o.put("repo", src.replaceAll("^https?://github\\.com/", "").replaceAll("/+$", ""));
            arr.put(o);
            prefs(c).edit().putString(K_CUSTOM, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void removeCustom(Context c, String id) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(K_CUSTOM, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!arr.getJSONObject(i).getString("id").equals(id)) out.put(arr.getJSONObject(i));
            }
            prefs(c).edit().putString(K_CUSTOM, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Settings row value. */
    static String status(Context c, Entry e) {
        return e.pkg != null && Suggestions.installed(c, e.pkg) ? "Installed" : "Install…";
    }

    /**
     * Settings row action: resolve the APK (GitHub release or direct link) and install it. Goes
     * straight to the system installer rather than pre-checking canRequestPackageInstalls(): on at
     * least one real TV (a TCL box) that check, and its own "Install unknown apps" screen, stayed
     * stuck on "not allowed" after the toggle was switched on — a bug in that screen, not in the
     * actual permission. The system installer's own prompt grants it correctly when it's missing.
     */
    static void install(final Activity a, final Entry e) {
        Toast.makeText(a, Lang.t("Preparing ") + e.name + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String apk = resolveApkUrl(e);
                if (apk == null || apk.isEmpty()) throw new IllegalStateException("no .apk asset");
                HttpURLConnection con = (HttpURLConnection) new URL(apk).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(120000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", "ProxFibre/" + Update.current(a));
                con.setRequestProperty("Accept", "application/octet-stream");
                if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
                String name = e.id + ".apk";
                File f = Bundled.apkFile(a, name);
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                con.disconnect();
                a.runOnUiThread(() -> openInstaller(a, name));
            } catch (Exception ex) {
                a.runOnUiThread(() -> Toast.makeText(a, e.name + " " + Lang.t("could not be downloaded"),
                        Toast.LENGTH_LONG).show());
            }
        }, "hume-appcatalog").start();
    }

    /** The newest .apk asset for a GitHub repo, or the entry's direct link. Blocking. */
    private static String resolveApkUrl(Entry e) throws Exception {
        if (e.url != null && !e.url.isEmpty()) return e.url;
        HttpURLConnection con = (HttpURLConnection) new URL(
                "https://api.github.com/repos/" + e.repo + "/releases/latest").openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "ProxFibre");
        try {
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            JSONObject rel = new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8));
            JSONArray assets = rel.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject as = assets.optJSONObject(i);
                if (as != null && as.optString("name", "").endsWith(".apk")) {
                    return as.optString("browser_download_url", "");
                }
            }
            return null;
        } finally {
            con.disconnect();
        }
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
