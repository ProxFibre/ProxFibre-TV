package com.proxfibre.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Locale;

/**
 * Live state of the bundled AirPlay receiver (io.github.jqssun.airplay), so Home can say whether an
 * iPhone or Mac is actually casting instead of guessing.
 *
 * The receiver is a third-party app: it exposes no API we may call, so the state is assembled from
 * what Android lets any app observe:
 *  - installed       : PackageManager.
 *  - running         : its RTSP port answers on the loopback (only visible to us, no permission).
 *  - receiving       : inferred from two independent signals, either of which is enough —
 *                      1. its media session, active while a sender plays video or audio;
 *                      2. its ongoing notification, which switches to the track/video once audio plays.
 *                     Mirroring alone publishes neither (the receiver keeps its idle notification and
 *                     parks its session), so a pure screen mirror is reported as a ready receiver.
 */
final class AirPlay {
    static final String PKG = "io.github.jqssun.airplay";

    /** not installed · installed but stopped · listening for senders · a sender is connected */
    static final int MISSING = 0, OFF = 1, READY = 2, RECEIVING = 3;

    private static final String PREFS = "airplay";
    private static final String K_STATE = "state", K_DETAIL = "detail", K_TEXT = "text",
            K_TEXT_AT = "text_at", K_AT = "checked";
    /** Notification text older than this is ignored: the receiver may have been killed. */
    private static final long TEXT_STALE_MS = 10L * 60 * 1000;
    /** Ports the UxPlay-based receiver listens on (RTSP first). */
    private static final int[] PORTS = {7000, 5000, 7100};

    private AirPlay() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean installed(Context c) {
        return Suggestions.installed(c, PKG);
    }

    static int state(Context c) {
        if (!installed(c)) return MISSING;
        return prefs(c).getInt(K_STATE, OFF);
    }

    static boolean active(Context c) {
        return state(c) == RECEIVING;
    }

    /** What is playing (or the notification line), empty when idle. */
    static String detail(Context c) {
        return prefs(c).getString(K_DETAIL, "");
    }

    /** Called by the notification listener: the receiver re-posts its notification as it changes. */
    static void noteNotification(Context c, String title, String text) {
        String line = (title == null ? "" : title).trim();
        String body = (text == null ? "" : text).trim();
        if (!body.isEmpty()) line = line.isEmpty() ? body : line + " \u2014 " + body;
        prefs(c).edit().putString(K_TEXT, line).putLong(K_TEXT_AT, System.currentTimeMillis()).apply();
    }

    static void clearNotification(Context c, String title, String text) {
        prefs(c).edit().remove(K_TEXT).remove(K_TEXT_AT).apply();
    }

    /**
     * Recomputes the state and stores it. Blocking but short: one loopback connect and a few reads,
     * so call it off the main thread.
     */
    static void refresh(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        boolean receiving = false;
        String detail = "";

        // 1. Media session: the receiver keeps one active while a sender plays video or audio.
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(c, MediaWatchService.class);
            List<MediaController> list = msm == null ? null : msm.getActiveSessions(me);
            for (int i = 0; list != null && i < list.size(); i++) {
                MediaController mc = list.get(i);
                if (!PKG.equals(mc.getPackageName())) continue;
                PlaybackState st = mc.getPlaybackState();
                int state = st == null ? PlaybackState.STATE_NONE : st.getState();
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                        || state == PlaybackState.STATE_PAUSED) {
                    receiving = true;
                }
                MediaMetadata md = mc.getMetadata();
                if (md != null) {
                    String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    if (title == null) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                    if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                    if (title != null && !title.isEmpty()) detail = title;
                    if (artist != null && !artist.isEmpty()) {
                        detail = detail.isEmpty() ? artist : artist + " \u00b7 " + detail;
                    }
                }
            }
        } catch (Exception e) {
            // No notification-listener access: the notification text below still works.
        }

        // 2. Notification: idle wording while nothing plays, the media line once audio starts.
        String note = p.getString(K_TEXT, "");
        long noteAt = p.getLong(K_TEXT_AT, 0);
        if (!note.isEmpty() && now - noteAt < TEXT_STALE_MS && !idleText(note)) {
            receiving = true;
            if (detail.isEmpty()) detail = note;
        }

        boolean running = receiving || listening(PORTS);

        int state = !installed(c) ? MISSING : receiving ? RECEIVING : running ? READY : OFF;
        p.edit().putInt(K_STATE, state)
                .putString(K_DETAIL, receiving ? detail : "")
                .putLong(K_AT, now).apply();
    }

    /** True when something accepts a TCP connection on one of the receiver's ports. */
    static boolean listening(int[] ports) {
        for (int port : ports) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress("127.0.0.1", port), 220);
                return true;
            } catch (Exception e) {
                // no listener on this port
            } finally {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /** The receiver's own "nothing connected" wording, in the languages it ships. */
    static boolean idleText(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        return t.contains("accepting") || t.contains("waiting") || t.contains("en attente")
                || t.contains("server running") || t.contains("disconnected") || t.contains("deconnect")
                || t.contains("esperando") || t.contains("apagado");
    }

    /** One line for Settings and Control Center: what the receiver is doing right now. */
    static String status(Context c) {
        switch (state(c)) {
            case MISSING:
                return Lang.s("Not installed", "Non install\u00e9e", "No instalada");
            case OFF:
                return Lang.s("Off", "Arr\u00eat\u00e9e", "Detenida");
            case RECEIVING: {
                String d = detail(c);
                return d.isEmpty()
                        ? Lang.s("Receiving", "En r\u00e9ception", "Recibiendo")
                        : Lang.s("Receiving \u00b7 " + d, "En r\u00e9ception \u00b7 " + d,
                        "Recibiendo \u00b7 " + d);
            }
            default:
                return Lang.s("Ready \u00b7 no device", "Pr\u00eate \u00b7 aucun appareil",
                        "Lista \u00b7 ning\u00fan dispositivo");
        }
    }

    static Intent launch(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(PKG);
        if (i == null) i = pm.getLaunchIntentForPackage(PKG);
        return i;
    }

    /**
     * Starts the receiver without showing anything: its own boot receiver brings the server up
     * (and announces it over mDNS) exactly as it does at power-on, when "start on boot" is on.
     */
    static void start(Context c) {
        c.sendBroadcast(new Intent("android.intent.action.QUICKBOOT_POWERON")
                .setClassName(PKG, PKG + ".BootReceiver")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND));
    }
}
