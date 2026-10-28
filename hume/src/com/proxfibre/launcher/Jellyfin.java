package com.proxfibre.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Jellyfin / Emby library on the top shelf: what you were watching (with real progress) and what
 * was added recently, straight from the server you own. No SDK: the few endpoints used here are
 * plain JSON over HTTP, so the box stays light.
 *
 * Jellyfin clients don't take deep links, so a card opens the client app (Moonfin first, then the
 * official app, Streamyfin, Findroid) on the server you configured.
 */
final class Jellyfin {
    static final String PREFS = "jellyfin";
    static final String K_SERVER = "server", K_TOKEN = "token", K_USERID = "user_id",
            K_USER = "user", K_DEVICE = "device_id", K_ENABLED = "enabled", K_SHELFMODE = "shelf_mode";

    /** Suggestion items coming from the library carry this provider id instead of a service id. */
    static final int ID = -3;

    /** What Jellyfin's own top shelf shows, when its dock app is focused. */
    static final String MODE_BOTH = "both", MODE_RESUME = "resume", MODE_LATEST = "latest";

    private Jellyfin() {
    }

    /** Android TV Jellyfin clients, best first: package, display name. */
    static final String[][] CLIENTS = {
            {"org.moonfin.androidtv", "Moonfin"},
            {"org.jellyfin.androidtv", "Jellyfin"},
            {"com.fredrikburmester.streamyfin", "Streamyfin"},
            {"dev.jdtech.jellyfin", "Findroid"},
            {"com.github.damontecres.wholphin", "Wholphin"},
            {"org.jellyfin.mobile", "Jellyfin"},
    };

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        String s = prefs(c).getString(K_SERVER, "").trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static String token(Context c) {
        return prefs(c).getString(K_TOKEN, "").trim();
    }

    static String userId(Context c) {
        return prefs(c).getString(K_USERID, "").trim();
    }

    static boolean configured(Context c) {
        return !server(c).isEmpty() && !token(c).isEmpty() && !userId(c).isEmpty();
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, true);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_ENABLED, on).apply();
        Suggestions.changed(c);
    }

    /** "Moonfin", "Jellyfin"… the client a card will open; the first installed one wins. */
    static String clientName(Context c) {
        for (String[] cl : CLIENTS) if (Suggestions.installed(c, cl[0])) return cl[1];
        return CLIENTS[0][1];
    }

    static String clientPackage(Context c) {
        for (String[] cl : CLIENTS) if (Suggestions.installed(c, cl[0])) return cl[0];
        return CLIENTS[0][0];
    }

    static boolean clientInstalled(Context c) {
        for (String[] cl : CLIENTS) if (Suggestions.installed(c, cl[0])) return true;
        return false;
    }

    /** Whether pkg is one of the Jellyfin/Emby clients (for the per-app top shelf, dock focus). */
    static boolean isClient(String pkg) {
        for (String[] cl : CLIENTS) if (cl[0].equals(pkg)) return true;
        return false;
    }

    /** What its own top shelf shows: Continue Watching, Recently added, or both (default). */
    static String shelfMode(Context c) {
        return prefs(c).getString(K_SHELFMODE, MODE_BOTH);
    }

    static void setShelfMode(Context c, String mode) {
        prefs(c).edit().putString(K_SHELFMODE, mode).apply();
        Suggestions.changed(c);
    }

    /** One line for Settings: the server, or how to set it up. */
    static String status(Context c) {
        if (!configured(c)) return "Not connected";
        try {
            return Uri.parse(server(c)).getHost();
        } catch (Exception e) {
            return server(c);
        }
    }

    /** A stable device id, so the server can list this TV under its devices. */
    static String deviceId(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString(K_DEVICE, "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            p.edit().putString(K_DEVICE, id).apply();
        }
        return id;
    }

    static void disconnect(Context c) {
        prefs(c).edit().remove(K_TOKEN).remove(K_USERID).remove(K_USER).remove(K_SERVER).apply();
        Suggestions.changed(c);
    }

    // ------------------------------------------------------------------ API

    /** Normalises what people type: "192.168.1.20:8096" becomes an http URL. */
    static String normalize(String url) {
        String s = url.trim();
        if (s.isEmpty()) return s;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String authHeader(Context c) {
        return "MediaBrowser Client=\"ProxFibre\", Device=\"Android TV\", DeviceId=\"" + deviceId(c)
                + "\", Version=\"" + version(c) + "\"";
    }

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    /**
     * Signs in with a username and password and keeps the token. Returns null on success, or a
     * short message to show when it fails (blocking: call off the UI thread).
     */
    static String login(Context c, String url, String user, String pass) {
        String base = normalize(url);
        if (base.isEmpty()) return "Server address is empty";
        if (user.trim().isEmpty()) return "Username is empty";
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(base + "/Users/AuthenticateByName").openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("X-Emby-Authorization", authHeader(c));
            con.setRequestProperty("Accept", "application/json");
            JSONObject body = new JSONObject().put("Username", user.trim()).put("Pw", pass);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) {
                return code == 401 ? "Wrong username or password" : "Server answered HTTP " + code;
            }
            JSONObject res = new JSONObject(new String(Suggestions.readAll(con.getInputStream()),
                    StandardCharsets.UTF_8));
            String token = res.optString("AccessToken");
            String uid = res.optJSONObject("User") != null ? res.getJSONObject("User").optString("Id") : "";
            if (token.isEmpty() || uid.isEmpty()) return "Unexpected reply from the server";
            prefs(c).edit().putString(K_SERVER, base).putString(K_TOKEN, token)
                    .putString(K_USERID, uid).putString(K_USER, user.trim()).apply();
            Suggestions.changed(c);
            return null;
        } catch (Exception e) {
            return "Can't reach " + base;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static JSONObject get(Context c, String path) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(server(c) + path).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("X-Emby-Authorization", authHeader(c));
        con.setRequestProperty("X-Emby-Token", token(c));
        try {
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            return new JSONObject(new String(Suggestions.readAll(con.getInputStream()), StandardCharsets.UTF_8));
        } finally {
            con.disconnect();
        }
    }

    static final String FIELDS = "Overview,ProductionYear,Genres,CommunityRating,RunTimeTicks,"
            + "SeriesName,ParentBackdropItemId,BackdropImageTags,ImageTags,UserData";

    /** Continue watching and recently added, as carousel items (blocking). */
    static List<Suggestions.Item> items(Context c) throws Exception {
        return items(c, MODE_BOTH);
    }

    /** Same, limited to one half when mode asks for it (the app's own dedicated top shelf). */
    static List<Suggestions.Item> items(Context c, String mode) throws Exception {
        if (!configured(c)) return new ArrayList<>();
        List<Suggestions.Item> out = new ArrayList<>();
        String uid = URLEncoder.encode(userId(c), "UTF-8");
        if (!mode.equals(MODE_LATEST)) {
            try {
                JSONObject res = get(c, "/Users/" + uid + "/Items/Resume?Limit=10&MediaTypes=Video"
                        + "&Fields=" + FIELDS + "&ImageTypeLimit=1&EnableImageTypes=Backdrop");
                addAll(c, res.optJSONArray("Items"), out);
            } catch (Exception e) {
                android.util.Log.w("TvHome", "Jellyfin resume: " + e);
            }
        }
        if (!mode.equals(MODE_RESUME)) {
            try {
                JSONObject res = get(c, "/Users/" + uid + "/Items/Latest?Limit=6&Fields=" + FIELDS
                        + "&ImageTypeLimit=1&EnableImageTypes=Backdrop");
                JSONArray latest = res.optJSONArray("Items");
                List<Suggestions.Item> added = new ArrayList<>();
                addAll(c, latest, added);
                for (Suggestions.Item it : added) {
                    boolean seen = false;
                    for (Suggestions.Item have : out) if (have.link.equals(it.link)) seen = true;
                    if (!seen) out.add(it);
                }
            } catch (Exception e) {
                android.util.Log.w("TvHome", "Jellyfin latest: " + e);
            }
        }
        return out;
    }

    static java.io.File shelfCacheFile(Context c) {
        return new java.io.File(c.getFilesDir(), "jellyfin_shelf.json");
    }

    /** Cached dedicated shelf (null when missing/stale). */
    static List<Suggestions.Item> shelfCached(Context c, boolean allowStale) {
        java.io.File f = shelfCacheFile(c);
        if (!f.exists()) return null;
        if (!allowStale && System.currentTimeMillis() - f.lastModified() > Suggestions.MAX_AGE_MS) return null;
        try {
            JSONArray a = new JSONArray(new String(Suggestions.readAll(new java.io.FileInputStream(f)),
                    StandardCharsets.UTF_8));
            List<Suggestions.Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(Suggestions.Item.fromJson(a.getJSONObject(i)));
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    static void writeShelfCache(Context c, List<Suggestions.Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Suggestions.Item it : items) a.put(it.toJson());
            try (OutputStream os = new java.io.FileOutputStream(shelfCacheFile(c))) {
                os.write(a.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            android.util.Log.w("TvHome", "jellyfin shelf cache: " + e);
        }
    }

    private static void addAll(Context c, JSONArray arr, List<Suggestions.Item> out) {
        if (arr == null) return;
        String pkg = clientPackage(c);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String type = o.optString("Type");
            if (type.equals("Series") || type.equals("Season") || type.equals("BoxSet")) continue;
            String id = o.optString("Id");
            String name = o.optString("Name");
            if (id.isEmpty() || name.isEmpty()) continue;
            JSONObject ud = o.optJSONObject("UserData");
            if (ud != null && ud.optBoolean("Played", false)) continue;

            Suggestions.Item it = new Suggestions.Item();
            it.id = Math.abs(id.hashCode());
            it.providerId = ID;
            it.type = type.equals("Movie") ? "movie" : "tv";
            String series = o.optString("SeriesName");
            it.title = series.isEmpty() ? name
                    : series + "  \u2013  S" + o.optInt("ParentIndexNumber") + "E" + o.optInt("IndexNumber");
            if (series.isEmpty()) {
                int y = o.optInt("ProductionYear", 0);
                it.year = y > 0 ? String.valueOf(y) : "";
            } else {
                it.year = "";
            }
            it.genres = genres(o.optJSONArray("Genres"));
            it.overview = o.optString("Overview");
            it.backdrop = backdrop(c, o);
            it.logo = "";
            it.rating = o.optDouble("CommunityRating", 0);
            it.pkg = pkg;
            it.link = id;
            if (ud != null) {
                double pct = ud.optDouble("PlayedPercentage", -1);
                if (pct > 0) it.progress = (float) Math.min(1, pct / 100.0);
                long pos = ud.optLong("PlaybackPositionTicks", 0);
                long run = o.optLong("RunTimeTicks", 0);
                if (run > 0 && pos > 0 && pos < run) {
                    it.remainingMin = (int) Math.max(1, (run - pos) / 600000000L);
                    if (it.progress < 0) it.progress = (float) Math.min(1, pos / (double) run);
                }
            }
            out.add(it);
        }
    }

    /** Backdrop (or a parent's) as an absolute, cached-friendly URL. */
    private static String backdrop(Context c, JSONObject o) {
        String base = server(c);
        String itemId = o.optString("Id");
        String backdropId = itemId;
        String tag = "";
        JSONArray tags = o.optJSONArray("BackdropImageTags");
        if (tags != null && tags.length() > 0) {
            tag = tags.optString(0);
        } else {
            String parent = o.optString("ParentBackdropItemId");
            if (!parent.isEmpty()) backdropId = parent;
            else return "";
        }
        return base + "/Items/" + backdropId + "/Images/Backdrop/0?maxWidth=1280&quality=84"
                + (tag.isEmpty() ? "" : "&tag=" + Uri.encode(tag));
    }

    private static String genres(JSONArray g) {
        if (g == null) return "";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < g.length() && out.size() < 2; i++) {
            String s = g.optString(i);
            if (!s.isEmpty()) out.add(s);
        }
        return String.join(" \u00b7 ", out);
    }

    /** Opens the library in its client app (or the Play Store page when none is installed). */
    static void open(Context c, Suggestions.Item it) {
        String pkg = it.pkg == null || it.pkg.isEmpty() ? clientPackage(c) : it.pkg;
        if (!Suggestions.installed(c, pkg)) {
            Suggestions.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
            return;
        }
        android.content.pm.PackageManager pm = c.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) Suggestions.start(c, launch);
    }
}
