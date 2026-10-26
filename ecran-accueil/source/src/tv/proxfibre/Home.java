package tv.proxfibre;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * ProxFibre : ecran d'accueil minimaliste pour Android TV, facon Apple TV.
 * Photo plein ecran qui change + horloge + dock d'applis personnalisable.
 *
 * Personnalisation directement sur la TV : appui long sur OK (ou touche Menu) sur une appli du dock
 *   -> deplacer, remplacer, retirer, ajouter, disposition 4/6/8 applis, titre.
 *
 * En haut a gauche : etat du Wi-Fi et du VPN (pastille bleue quand un tunnel est actif).
 * Deux applis sont fournies dans l'APK : Armadillo (client VPN, assets/armadillo) et
 * AirPlay Server (reception iPhone/Mac, assets/airplay). ProxFibre propose de les installer
 * au premier demarrage, puis ouvre l'installateur du systeme pour chacune.
 *
 * Reglages envoyes par le kit (adb), pris en compte quand zenhome_rev change :
 *   settings put global zenhome_rev         <horodatage>
 *   settings put global zenhome_title_mode  device | none | custom
 *   settings put global zenhome_title_b64   <titre en base64 UTF-8>   (si custom)
 *   settings put global zenhome_layout      4 | 6 | 8
 *   settings put global zenhome_dock        com.netflix.ninja,com.google.android.youtube.tv,...
 *   settings put global zenhome_interval    60   (secondes entre deux photos)
 * Photos perso : fichiers .jpg/.png dans /sdcard/ZenHome/ (remplacent les photos fournies).
 */
public class Home extends Activity {

    /** Applis de streaming connues, par ordre de priorite pour le dock automatique. */
    static final String[] KNOWN_DOCK = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.canal.android.canal", "com.wbd.stream",
            "com.apple.atve.androidtv.appletv", "com.spotify.tv.android", "com.plexapp.android",
            "fr.francetv.pluzz", "fr.tf1.mytf1", "fr.m6.m6replay", "tv.arte.plus7", "tv.molotov.app",
            "com.cbs.ott", "com.dazn", "tv.twitch.android.app", "org.xbmc.kodi", "com.google.android.youtube.tvkids",
    };
    /** Applis "entrees TV / chaines" selon les marques. */
    static final String[][] KNOWN_INPUTS = {
            {"com.tcl.tv", "com.tcl.tv.TVActivity"},
            {"com.mediatek.wwtv.tvcenter", null},
            {"org.droidtv.playtv", null},
            {"com.sony.dtv.tvx", null},
            {"com.mitv.tvplayer", null},
            {"com.mitv.livetv", null},
            {"com.hisense.tv", null},
            {"com.google.android.tv", null},
            {"com.android.tv", null},
    };
    static final long FADE_MS = 2500L;
    static final int ACCENT = 0xFF7CC4FF;

    final Handler h = new Handler();
    SharedPreferences prefs;
    long rotationMs = 60_000L;
    FrameLayout root, wallLayer;
    ImageView wallA, wallB;
    boolean aOnTop = true, released = false;
    TextView clock, date, titleView, hintView;
    List<String> walls = new ArrayList<>();
    boolean wallsFromFiles = false;
    int wallIdx = 0;
    LinearLayout dock;
    final List<View> overlays = new ArrayList<>();   // menus / listes ouverts (pile)
    Typeface fReg, fMed, fLight;
    float density;
    boolean fr;

    // etat reseau et reglages, en haut a droite
    LinearLayout statusIcons;
    ImageView wifiView, vpnView, airplayView, settingsView;
    boolean wifiOn, vpnOn, airOn;
    int wifiLvl = -1;

    // Top Shelf : sections d'applis qui s'ouvrent sous le dock (fleche bas)
    FrameLayout shelf;
    boolean shelfOpen;
    final List<ShelfSection> sections = new ArrayList<>();
    final List<View> overlayReturn = new ArrayList<>();

    // Applis fournies dans l'APK, installees au premier demarrage
    static final class Bundled {
        final String pkg, asset, file, label;
        Bundled(String pkg, String asset, String file, String label) {
            this.pkg = pkg; this.asset = asset; this.file = file; this.label = label;
        }
    }
    static final Bundled ARMADILLO = new Bundled("dev.kazuryy.armadillo",
            "armadillo/armadillo.apk", "armadillo.apk", "Armadillo (VPN)");
    static final Bundled AIRPLAY = new Bundled("io.github.jqssun.airplay",
            "airplay/airplay.apk", "airplay.apk", "AirPlay");
    static final Bundled[] BUNDLED = {ARMADILLO, AIRPLAY};

    boolean bundledOffered = false;
    int pendingPermissionFor = -1;   // applis en attente du reglage « applis inconnues »
    int awaitingBundle = -1;         // appli dont l'installateur est ouvert

    // configuration du dock
    List<String> dockPkgs = new ArrayList<>();
    int layout = 4;               // 4, 6 ou 8 applis
    String titleMode = "device";  // device | none | custom
    String titleText = "";
    int moveIndex = -1;           // >= 0 : appli en cours de deplacement
    List<View> bigTiles = new ArrayList<>();

    int dp(float v) { return Math.round(v * density); }
    String t(String frTxt, String enTxt) { return fr ? frTxt : enTxt; }

    String setting(String key) {
        try { return Settings.Global.getString(getContentResolver(), key); } catch (Throwable e) { return null; }
    }

    // =====================================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        fr = "fr".equals(Locale.getDefault().getLanguage());
        prefs = getSharedPreferences("zenhome", MODE_PRIVATE);
        pendingPermissionFor = prefs.getInt("bundle_perm", -1);
        awaitingBundle = prefs.getInt("bundle_waiting", -1);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        density = Math.max(dm.widthPixels, dm.heightPixels) / 1280f; // maquette sur 1280 unites de large
        try {
            fReg = Typeface.createFromAsset(getAssets(), "Poppins-Regular.ttf");
            fMed = Typeface.createFromAsset(getAssets(), "Poppins-Medium.ttf");
            fLight = Typeface.createFromAsset(getAssets(), "Poppins-Light.ttf");
        } catch (Exception e) { fReg = fMed = fLight = Typeface.SANS_SERIF; }
        try {
            String iv = setting("zenhome_interval");
            if (iv != null) rotationMs = Math.max(10, Integer.parseInt(iv.trim())) * 1000L;
        } catch (Exception ignored) { }
        loadConfig();
        loadSections();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        wallA = newWall(); wallB = newWall();
        wallLayer = new FrameLayout(this);
        wallLayer.addView(wallB); wallLayer.addView(wallA);
        root.addView(wallLayer, new FrameLayout.LayoutParams(-1, -1));

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C000000, 0x00000000}));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(200), Gravity.TOP));
        View bot = new View(this);
        bot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        root.addView(bot, new FrameLayout.LayoutParams(-1, dp(340), Gravity.BOTTOM));

        // Horloge, date et nom de la TV, en haut a gauche
        clock = text("", 40, fLight, 0xFFFFFFFF);
        date = text("", 15, fReg, 0xE6FFFFFF);
        titleView = text("", 14, fReg, 0xD9FFFFFF);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxWidth(dp(620));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.START);
        head.setClipChildren(false); head.setClipToPadding(false);
        head.addView(clock);
        LinearLayout.LayoutParams lpDt = new LinearLayout.LayoutParams(-2, -2);
        lpDt.topMargin = dp(2);
        head.addView(date, lpDt);
        LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(-2, -2);
        lpN.topMargin = dp(4);
        head.addView(titleView, lpN);
        FrameLayout.LayoutParams lpHead = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lpHead.setMargins(dp(56), dp(26), 0, 0);
        root.addView(head, lpHead);
        applyTitle();

        // Etat Wi-Fi / VPN / AirPlay et acces aux reglages, en haut a droite
        statusIcons = new LinearLayout(this);
        statusIcons.setOrientation(LinearLayout.HORIZONTAL);
        statusIcons.setGravity(Gravity.CENTER_VERTICAL);
        wifiView = iconView(IC_WIFI);
        vpnView = iconView(IC_VPN);
        airplayView = iconView(IC_AIRPLAY);
        settingsView = iconView(IC_GEAR);
        statusIcons.addView(wifiView, iconLp(0));
        statusIcons.addView(vpnView, iconLp(4));
        statusIcons.addView(airplayView, iconLp(4));
        statusIcons.addView(settingsView, iconLp(4));
        wireIcon(settingsView, new Runnable() { public void run() { openSettings(); } });
        wireIcon(airplayView, new Runnable() { public void run() { openAirPlay(); } });
        FrameLayout.LayoutParams lpSt = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lpSt.setMargins(0, dp(20), dp(52), 0);
        root.addView(statusIcons, lpSt);

        // Indication pendant le deplacement
        hintView = text("", 13, fMed, 0xFFFFFFFF);
        GradientDrawable hb = new GradientDrawable(); hb.setCornerRadius(dp(16)); hb.setColor(0xB3101216);
        hintView.setBackground(hb);
        hintView.setPadding(dp(16), dp(8), dp(16), dp(8));
        hintView.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpH = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lpH.setMargins(0, 0, 0, dp(46 + dockHeight() + 10));
        root.addView(hintView, lpH);

        // Dock
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false); dock.setClipToPadding(false);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(28));
        glass.setColor(0x38FFFFFF);
        glass.setStroke(Math.max(1, dp(0.75f)), 0x40FFFFFF);
        dock.setBackground(glass);
        FrameLayout.LayoutParams lpD = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lpD.setMargins(0, 0, 0, dp(46));
        root.addView(dock, lpD);
        buildDock(0);

        // Top Shelf : rangees d'applis, masque jusqu'a la fleche bas depuis le dock
        shelf = new FrameLayout(this);
        shelf.setVisibility(View.GONE);
        shelf.setClipChildren(false); shelf.setClipToPadding(false);
        FrameLayout.LayoutParams lpSh = new FrameLayout.LayoutParams(-1, dp(SHELF_H), Gravity.BOTTOM);
        root.addView(shelf, lpSh);

        setContentView(root);
        loadWalls();
        showWall(true);
        updateStatus();
    }

    // =====================================================================
    // Etat du reseau : Wi-Fi / Ethernet connecte, tunnel VPN actif
    void updateStatus() {
        boolean net = false, vpn = false;
        int level = 3;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    NetworkCapabilities act = cm.getNetworkCapabilities(cm.getActiveNetwork());
                    if (act != null && (act.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || act.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))
                        net = act.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    for (Network n : cm.getAllNetworks()) {
                        NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpn = true; break; }
                    }
                } else {
                    NetworkInfo ni = cm.getActiveNetworkInfo();
                    net = ni != null && ni.isConnected()
                            && (ni.getType() == ConnectivityManager.TYPE_WIFI || ni.getType() == ConnectivityManager.TYPE_ETHERNET);
                    for (NetworkInfo ni2 : cm.getAllNetworkInfo())
                        if (ni2 != null && ni2.getType() == ConnectivityManager.TYPE_VPN && ni2.isConnected()) { vpn = true; break; }
                }
            }
            if (net) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wm != null) {
                    int rssi = wm.getConnectionInfo().getRssi();  // 0 : inconnu (Ethernet, permission)
                    if (rssi < 0) level = WifiManager.calculateSignalLevel(rssi, 3);
                }
            }
        } catch (Throwable ignored) { }
        int lvl = Math.max(1, Math.min(3, level));
        boolean air = installed(AIRPLAY);
        if (net != wifiOn || lvl != wifiLvl || wifiView.getDrawable() == null) {
            wifiOn = net; wifiLvl = lvl;
            wifiView.setImageBitmap(headIcon(IC_WIFI));
        }
        if (vpn != vpnOn || vpnView.getDrawable() == null) {
            vpnOn = vpn;
            vpnView.setImageBitmap(headIcon(IC_VPN));
        }
        if (air != airOn || airplayView.getDrawable() == null) {
            airOn = air;
            airplayView.setImageBitmap(headIcon(IC_AIRPLAY));
        }
    }

    final Runnable statusTick = new Runnable() {
        public void run() { updateStatus(); h.postDelayed(this, 5000); }
    };

    ImageView iconView(int kind) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(headIcon(kind));
        v.setScaleType(ImageView.ScaleType.CENTER);
        return v;
    }

    LinearLayout.LayoutParams iconLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.leftMargin = dp(leftMargin);
        return lp;
    }

    /** Menu d'une tuile du dock ou du Top Shelf (appui long ou rafale de OK). */
    void tileMenuFor(View f) {
        if (f == null) return;
        if (isInDock(f)) { showTileMenu(bigTiles.indexOf(f)); return; }
        String pkg = shelfPkg.get(f);
        if (pkg != null) shelfTileMenu(pkg, shelfSec.get(f));
    }

    /** Tuile du dock ou du Top Shelf : l'appui long y ouvre le menu. */
    boolean isLauncherTile(View v) {
        return v != null && (isInDock(v) || shelfPkg.containsKey(v));
    }

    /** Icone cliquable de l'en-tete : anneau au focus, sans l'animation des tuiles. */
    void wireIcon(final View v, final Runnable r) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
            if (moveIndex < 0) r.run(); } });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) {
                x.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                        .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                x.setBackground(null);
            }
        });
    }

    void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast(t("Réglages indisponibles", "Settings unavailable")); }
    }

    /** Ouvre AirPlay, ou propose de l'installer s'il n'est pas encore la. */
    void openAirPlay() {
        if (installed(AIRPLAY)) launch(AIRPLAY.pkg);
        else installBundled(AIRPLAY, bundleIndex(AIRPLAY));
    }

    int bundleIndex(Bundled b) {
        for (int i = 0; i < BUNDLED.length; i++) if (BUNDLED[i] == b) return i;
        return 0;
    }

    // =====================================================================
    // Applis fournies dans l'APK : Armadillo (VPN) et AirPlay Server
    boolean installed(Bundled b) {
        try { getPackageManager().getApplicationInfo(b.pkg, 0); return true; } catch (Exception e) { return false; }
    }

    void setPendingPermission(int i) {
        pendingPermissionFor = i;
        prefs.edit().putInt("bundle_perm", i).apply();
    }

    void setAwaitingBundle(int i) {
        awaitingBundle = i;
        prefs.edit().putInt("bundle_waiting", i).apply();
    }

    String missingNames() {
        StringBuilder sb = new StringBuilder();
        for (Bundled b : BUNDLED) {
            if (installed(b)) continue;
            if (sb.length() > 0) sb.append(fr ? " et " : " and ");
            sb.append(b.label);
        }
        return sb.toString();
    }

    /** Propose une fois d'installer les applis fournies ; elles s'enchainent ensuite. */
    void maybeOfferBundled() {
        if (bundledOffered) return;
        if (missingNames().isEmpty()) {
            prefs.edit().putBoolean("bundled_prompted", true).apply();
            return;
        }
        if (prefs.getBoolean("bundled_prompted", false)) return;
        bundledOffered = true;
        h.postDelayed(new Runnable() {
            public void run() {
                if (isFinishing() || missingNames().isEmpty()) return;
                final String names = missingNames();
                new AlertDialog.Builder(Home.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(t("Installer les applis fournies ?", "Install the bundled apps?"))
                        .setMessage(t("ProxFibre fournit " + names + ". Android demande une confirmation pour chaque appli ; AirPlay démarre ensuite à chaque allumage de la TV.",
                                "ProxFibre bundles " + names + ". Android asks for one confirmation per app; AirPlay then starts every time the TV boots."))
                        .setPositiveButton(t("Installer", "Install"), new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                prefs.edit().putBoolean("bundled_prompted", true).apply();
                                installNextBundled(-1); } })
                        .setNegativeButton(t("Plus tard", "Later"), new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                prefs.edit().putBoolean("bundled_prompted", true).apply(); } })
                        .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                            public void onDismiss(android.content.DialogInterface d) { focusDock(); } })
                        .show();
            }
        }, 4000);
    }

    /** Installe la premiere appli fournie encore absente apres l'index donne. */
    void installNextBundled(int after) {
        for (int i = after + 1; i < BUNDLED.length; i++) {
            if (!installed(BUNDLED[i])) { installBundled(BUNDLED[i], i); return; }
        }
        setAwaitingBundle(-1);
    }

    /** Android 8+ : « installer des applis inconnues » doit etre autorise pour ProxFibre. */
    boolean canInstallApks() {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            android.app.AppOpsManager aom = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return aom.checkOpNoThrow("android:request_install_packages",
                    android.os.Process.myUid(), getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) {
            return getPackageManager().canRequestPackageInstalls();
        }
    }

    /** Deja installee : l'ouvre. Sinon extrait l'APK fourni et lance l'installateur. */
    void installBundled(final Bundled b, final int index) {
        if (installed(b)) { launch(b.pkg); return; }
        setAwaitingBundle(index);
        if (!canInstallApks()) { askInstallPermission(index); return; }
        toast(t("Préparation de ", "Preparing ") + b.label + "…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream is = getAssets().open(b.asset);
                    FileOutputStream fos = new FileOutputStream(ApkProvider.apkFile(Home.this, b.file));
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.close(); is.close();
                    h.post(new Runnable() { public void run() { openInstaller(b); } });
                } catch (Throwable e) {
                    h.post(new Runnable() { public void run() {
                        toast(b.label + t(" n'a pas pu être préparé", " could not be prepared")); } });
                }
            }
        }).start();
    }

    /** Android 8+ : ProxFibre doit etre autorise a installer des applis. */
    void askInstallPermission(final int index) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(t("Autoriser l'installation", "Allow installs"))
                .setMessage(t("Android doit autoriser ProxFibre à installer des applis. Activez « Installer des applis inconnues » pour ProxFibre, puis revenez ici : l'installation reprendra toute seule.",
                        "Android needs to allow ProxFibre to install apps. Turn on \"Install unknown apps\" for ProxFibre, then come back here: the install resumes on its own."))
                .setPositiveButton(t("Ouvrir les réglages", "Open settings"), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        setPendingPermission(index);
                        openUnknownSourcesSettings(); } })
                .setNegativeButton(t("Annuler", "Cancel"), null)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    public void onDismiss(android.content.DialogInterface d) { focusDock(); } })
                .show();
    }

    void openUnknownSourcesSettings() {
        Uri me = Uri.parse("package:" + getPackageName());
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, me)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, me)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) { toast(t("Réglages indisponibles", "Settings unavailable")); }
        }
    }

    void openInstaller(Bundled b) {
        Uri apk = ApkProvider.uri(this, b.file);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(apk, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); return; } catch (Exception ignored) { }
        try {
            Intent a = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            a.setDataAndType(apk, "application/vnd.android.package-archive");
            a.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(a);
        } catch (Exception e) {
            toast(t("Aucun installateur d'APK sur cette TV", "No APK installer on this TV"));
        }
    }

    // =====================================================================
    // Configuration : preferences de la TV, remplacees par celles du kit si plus recentes
    void loadConfig() {
        String rev = setting("zenhome_rev");
        if (rev != null && !rev.equals(prefs.getString("rev", null))) {
            SharedPreferences.Editor e = prefs.edit().putString("rev", rev);
            String mode = setting("zenhome_title_mode");
            if (mode != null) e.putString("title_mode", mode.trim());
            String b64 = setting("zenhome_title_b64");
            if (b64 != null && b64.trim().length() > 0) {
                try { e.putString("title", new String(Base64.decode(b64.trim(), Base64.DEFAULT), "UTF-8").trim()); } catch (Exception ignored) { }
            }
            String lay = setting("zenhome_layout");
            if (lay != null) e.putString("layout", lay.trim());
            String d = setting("zenhome_dock");
            if (d != null) {
                // "auto" : le kit demande le choix automatique des applis
                if ("auto".equalsIgnoreCase(d.trim())) e.putString("dock", "").putBoolean("dock_user_set", false);
                else e.putString("dock", d.trim()).putBoolean("dock_user_set", true);
            }
            e.apply();
        }
        titleMode = prefs.getString("title_mode", "device");
        titleText = prefs.getString("title", "");
        try { layout = Integer.parseInt(prefs.getString("layout", "4")); } catch (Exception ex) { layout = 4; }
        if (layout != 6 && layout != 8) layout = 4;
        dockPkgs.clear();
        String csv = prefs.getString("dock", "");
        if (csv.length() > 0) {
            for (String p : csv.split("[,;\\s]+")) { p = p.trim(); if (p.length() > 0 && usable(p) && !dockPkgs.contains(p)) dockPkgs.add(p); }
        }
        if (dockPkgs.isEmpty() && !prefs.getBoolean("dock_user_set", false)) {
            for (String p : KNOWN_DOCK) { if (dockPkgs.size() >= layout) break; if (usable(p)) dockPkgs.add(p); }
        }
        while (dockPkgs.size() > layout) dockPkgs.remove(dockPkgs.size() - 1);
    }

    void saveConfig() {
        prefs.edit()
                .putString("dock", TextUtils.join(",", dockPkgs))
                .putBoolean("dock_user_set", true)
                .putString("layout", String.valueOf(layout))
                .putString("title_mode", titleMode)
                .putString("title", titleText)
                .apply();
    }

    String deviceName() {
        String n = setting("device_name");
        if (n == null || n.trim().isEmpty() || "null".equals(n)) n = Build.MODEL;
        return n == null ? "" : n.trim();
    }

    void applyTitle() {
        String s = "custom".equals(titleMode) ? titleText : ("none".equals(titleMode) ? "" : deviceName());
        titleView.setText(s);
        titleView.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    boolean usable(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return ai.enabled && launchIntent(pkg) != null;
        } catch (Exception e) { return false; }
    }

    // =====================================================================
    // Dock : une seule rangee de tuiles rectangulaires, applis puis raccourcis
    int dockTileW = 150, dockTileH = 84, dockInset = 56;

    int shortcutCount() {
        int n = 1;                                                          // Toutes les applis
        if (usable("com.android.vending")) n++;                              // Play Store
        if (findInput() != null || viewInputsTarget() != null) n++;          // Entrees TV
        return n;
    }

    int dockCount() {
        int n = Math.min(dockPkgs.size(), layout) + shortcutCount();
        if (dockPkgs.size() < layout) n++;                          // tuile "+"
        return Math.max(1, n);
    }

    int dockHeight() { return dockTileH + 36; }

    void buildDock(int focusIndex) {
        dock.removeAllViews();
        bigTiles.clear();
        int n = dockCount();
        int gap = n > 9 ? 8 : 12;
        dockTileW = Math.max(86, Math.min(152, (1160 - gap * n) / n));
        dockTileH = Math.round(dockTileW * 9f / 16f);
        dock.setPadding(dp(18), dp(18), dp(18), dp(18));
        // marge laterale du dock, reprise par le Top Shelf pour aligner les rangees
        dockInset = Math.max(28, (1280 - (36 + (n - 1) * (dockTileW + gap) + dockTileW)) / 2);

        for (int i = 0; i < dockPkgs.size() && i < layout; i++) {
            View v = appTile(dockPkgs.get(i), i, gap);
            if (v != null) { dock.addView(v); bigTiles.add(v); }
        }
        if (dockPkgs.size() < layout) dock.addView(addTile(gap));

        if (usable("com.android.vending"))
            dock.addView(shortcutTile(pkgIcon("com.android.vending"), "Play Store", gap,
                    new Runnable() { public void run() { launch("com.android.vending"); } }));
        final String[] input = findInput();
        final ComponentName viewInputs = viewInputsTarget();
        if (input != null)
            dock.addView(shortcutTile(pkgIcon(input[0]), t("Entrées TV", "TV inputs"), gap,
                    new Runnable() { public void run() { openInput(input); } }));
        else if (viewInputs != null)
            dock.addView(shortcutTile(pkgIcon(viewInputs.getPackageName()), t("Entrées TV", "TV inputs"), gap,
                    new Runnable() { public void run() { openInputs(); } }));
        dock.addView(shortcutTile(gridIconDrawable(), t("Toutes", "All apps"), 0,
                new Runnable() { public void run() { showApps(null); } }));

        // l'indication de deplacement se place juste au-dessus du dock
        FrameLayout.LayoutParams lpH = (FrameLayout.LayoutParams) hintView.getLayoutParams();
        if (lpH != null) { lpH.bottomMargin = dp(46 + dockHeight() + 10); hintView.setLayoutParams(lpH); }

        final View target = (focusIndex >= 0 && focusIndex < bigTiles.size()) ? bigTiles.get(focusIndex)
                : (bigTiles.isEmpty() ? (dock.getChildCount() > 0 ? dock.getChildAt(0) : null) : bigTiles.get(bigTiles.size() - 1));
        if (target != null) dock.post(new Runnable() { public void run() { target.requestFocus(); } });
    }

    View appTile(final String pkg, final int index, int gap) {
        PackageManager pm = getPackageManager();
        Drawable d = null;
        String label = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(ai).toString();
            d = pm.getApplicationBanner(ai);
            if (d == null) { Intent li = pm.getLeanbackLaunchIntentForPackage(pkg); if (li != null) d = pm.getActivityBanner(li); }
        } catch (Exception e) { return null; }
        FrameLayout f = roundedFrame(Math.max(8, dockTileH / 9), 0xFF1E1E1E);
        if (d != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.addView(tileFace(pkgIcon(pkg), label), new FrameLayout.LayoutParams(-1, -1));
        }
        return finishTile(f, label, gap, new Runnable() { public void run() { launch(pkg); } },
                new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { showTileMenu(index); return true; } });
    }

    View shortcutTile(Drawable icon, String label, int gap, Runnable r) {
        FrameLayout f = roundedFrame(Math.max(8, dockTileH / 9), 0xFF1E1E1E);
        f.addView(tileFace(icon, label), new FrameLayout.LayoutParams(-1, -1));
        return finishTile(f, label, gap, r, null);
    }

    View addTile(int gap) {
        FrameLayout f = roundedFrame(Math.max(8, dockTileH / 9), 0xFF1E1E1E);
        TextView plus = text("+", Math.max(20, dockTileH * 0.42f), fLight, 0x99FFFFFF);
        plus.setGravity(Gravity.CENTER);
        plus.setShadowLayer(0, 0, 0, 0);
        f.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        return finishTile(f, t("Ajouter une appli", "Add an app"), gap,
                new Runnable() { public void run() { pickAppToAdd(); } },
                new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { showTileMenu(-1); return true; } });
    }

    /** Visage d'une tuile sans banniere : icone a gauche, nom a droite. */
    View tileFace(Drawable icon, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.max(6, dockTileH / 6);
        l.setPadding(dp(pad), 0, dp(pad), 0);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon != null ? icon : gridIconDrawable());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int is = Math.round(dockTileH * 0.42f);
        l.addView(iv, new LinearLayout.LayoutParams(dp(is), dp(is)));
        TextView tv = text(label, dockTileW > 120 ? 11 : (dockTileW > 98 ? 10 : 8), fMed, 0xFFFFFFFF);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setShadowLayer(0, 0, 0, 0);
        tv.setPadding(dp(8), 0, 0, 0);
        l.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        return l;
    }

    View finishTile(FrameLayout f, String label, int gap, Runnable r, View.OnLongClickListener lc) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(dockTileW), dp(dockTileH));
        lp.rightMargin = dp(gap);
        f.setLayoutParams(lp);
        f.setContentDescription(label);
        f.setTag(Math.max(8, dockTileH / 9));
        wire(f, r, 1.12f, false);   // pas de cadre au focus : la tuile grandit, facon Apple TV
        if (lc != null) { f.setLongClickable(true); f.setOnLongClickListener(lc); }
        return f;
    }

    /** Icone "toutes les applis" : grille 3x3, dessinee dans un bitmap pour rester nette. */
    BitmapDrawable gridIconDrawable() {
        int px = Math.max(16, Math.round(Math.max(24, dockTileH * 0.42f) * density));
        Bitmap bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        float cell = px / 3f, sq = cell * 0.64f, r = sq * 0.3f;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float x = j * cell + (cell - sq) / 2f, y = i * cell + (cell - sq) / 2f;
            c.drawRoundRect(x, y, x + sq, y + sq, r, r, p);
        }
        return new BitmapDrawable(getResources(), bm);
    }

    FrameLayout roundedFrame(final int radius, int color) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(dp(radius)); bg.setColor(color);
        f.setBackground(bg);
        f.setClipToOutline(true);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(radius)); } });
        return f;
    }

    void wire(final View v, final Runnable r, final float scale) { wire(v, r, scale, true); }

    /** outline = false : pas d'anneau au focus, l'agrandissement suffit (tuiles sans fond). */
    void wire(final View v, final Runnable r, final float scale, final boolean outline) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) {
            if (moveIndex >= 0) return;
            if (isInDock(x) || shelfPkg.containsKey(x)) {
                // dock : on attend un peu, une rafale de OK (telecommandes Sony) vaut un appui long
                if (pendingClick != null) h.removeCallbacks(pendingClick);
                pendingClick = r;
                h.postDelayed(r, BURST_GAP_MS);
            } else r.run();
        } });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View x, boolean has) {
                x.animate().cancel();
                x.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                        .translationY(has ? -dp(5) : 0).translationZ(has ? dp(14) : 0)
                        .setDuration(has ? 230 : 170).setInterpolator(fluid()).start();
                if (has) centerInScroller(x);
                if (!outline && moveIndex < 0) { x.setForeground(null); return; }
                int r = x.getTag() instanceof Integer ? (Integer) x.getTag() : 14;
                x.setForeground(has ? ring(r, moveIndex >= 0 ? ACCENT : 0xF2FFFFFF) : null);
            }
        });
    }

    /** Courbe d'animation : sortie douce et longue, facon Apple. */
    Interpolator fluid() { return new PathInterpolator(0.22f, 1f, 0.36f, 1f); }

    /** Garde l'element selectionne au centre de sa rangee (Top Shelf) ou de sa grille (applis). */
    void centerInScroller(View v) {
        View p = v.getParent() instanceof View ? (View) v.getParent() : null;
        while (p != null && !(p instanceof ScrollView) && !(p instanceof HorizontalScrollView))
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        if (p instanceof HorizontalScrollView) {
            HorizontalScrollView hsv = (HorizontalScrollView) p;
            View content = hsv.getChildAt(0);
            if (content == null) return;
            int max = Math.max(0, content.getWidth() - hsv.getWidth());
            int x = v.getLeft() + v.getWidth() / 2 - hsv.getWidth() / 2;
            hsv.smoothScrollTo(Math.max(0, Math.min(max, x)), 0);
        } else if (p != null) {
            ScrollView sv = (ScrollView) p;
            View content = sv.getChildAt(0);
            if (content == null) return;
            int max = Math.max(0, content.getHeight() - sv.getHeight());
            int y = v.getTop() + v.getHeight() / 2 - sv.getHeight() / 2;
            sv.smoothScrollTo(0, Math.max(0, Math.min(max, y)));
        }
    }

    GradientDrawable ring(int r, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(r)); g.setColor(0x00000000); g.setStroke(dp(moveIndex >= 0 ? 3 : 2), color);
        return g;
    }

    Drawable pkgIcon(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    String appLabel(String pkg) {
        try { PackageManager pm = getPackageManager(); return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // =====================================================================
    // Mode edition (appui long sur OK, ou touche Menu)
    void showTileMenu(final int index) {
        if (moveIndex >= 0 || !overlays.isEmpty()) return;
        List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        String header;
        if (index >= 0 && index < dockPkgs.size()) {
            header = appLabel(dockPkgs.get(index));
            if (dockPkgs.size() > 1) { labels.add(t("Déplacer", "Move")); actions.add(new Runnable() { public void run() { startMove(index); } }); }
            labels.add(t("Remplacer par une autre appli", "Replace with another app")); actions.add(new Runnable() { public void run() { pickAppToReplace(index); } });
            labels.add(t("Retirer du dock", "Remove from dock")); actions.add(new Runnable() { public void run() {
                dockPkgs.remove(index); saveConfig(); buildDock(Math.max(0, index - 1)); } });
        } else {
            header = t("Écran d'accueil", "Home screen");
        }
        if (dockPkgs.size() < layout) { labels.add(t("Ajouter une appli", "Add an app")); actions.add(new Runnable() { public void run() { pickAppToAdd(); } }); }
        labels.add(t("Disposition : ", "Layout: ") + layout + t(" applis", " apps")); actions.add(new Runnable() { public void run() { showLayoutMenu(); } });
        labels.add(t("Modifier le titre", "Change the title")); actions.add(new Runnable() { public void run() { editTitle(); } });
        labels.add(t("Sections du Top Shelf", "Top Shelf sections")); actions.add(new Runnable() { public void run() { showSectionsMenu(); } });
        for (int i = 0; i < BUNDLED.length; i++) {
            final Bundled b = BUNDLED[i];
            final int idx = i;
            labels.add((installed(b) ? t("Ouvrir ", "Open ") : t("Installer ", "Install ")) + b.label);
            actions.add(new Runnable() { public void run() { installBundled(b, idx); } });
        }
        showMenu(header, labels, actions);
    }

    void showLayoutMenu() {
        final int[] opts = {4, 6, 8};
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String[] desc = {t("4 applis, grandes tuiles", "4 apps, large tiles"), t("6 applis sur une ligne", "6 apps in one row"), t("8 applis sur une ligne", "8 apps in one row")};
        for (int i = 0; i < opts.length; i++) {
            final int n = opts[i];
            labels.add((n == layout ? "✓  " : "     ") + desc[i]);
            actions.add(new Runnable() { public void run() {
                layout = n;
                while (dockPkgs.size() > layout) dockPkgs.remove(dockPkgs.size() - 1);
                if (dockPkgs.size() < layout && !prefs.getBoolean("dock_user_set", false)) {
                    for (String p : KNOWN_DOCK) { if (dockPkgs.size() >= layout) break; if (usable(p) && !dockPkgs.contains(p)) dockPkgs.add(p); }
                }
                saveConfig(); buildDock(0); } });
        }
        showMenu(t("Disposition", "Layout"), labels, actions);
    }

    // =====================================================================
    // Top Shelf : sections d'applis cachees sous le dock, ouvertes par la fleche bas
    static final class ShelfSection {
        String name;
        final List<String> pkgs = new ArrayList<>();
    }

    static final int SHELF_H = 272;   // hauteur de la zone sous le dock, en unites de maquette
    View firstShelfView;
    final List<HorizontalScrollView> shelfRows = new ArrayList<>();
    final java.util.HashMap<View, String> shelfPkg = new java.util.HashMap<>();
    final java.util.HashMap<View, ShelfSection> shelfSec = new java.util.HashMap<>();

    List<String> launchablePkgs() {
        final PackageManager pm = getPackageManager();
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN); q.addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (!map.containsKey(p) && !p.equals(getPackageName())) map.put(p, p);
            }
        }
        List<String> l = new ArrayList<>(map.keySet());
        Collections.sort(l, new java.util.Comparator<String>() {
            public int compare(String a, String b) { return appLabel(a).compareToIgnoreCase(appLabel(b)); } });
        return l;
    }

    String u8(byte[] b) { try { return new String(b, "UTF-8"); } catch (Exception e) { return ""; } }
    byte[] u8(String s) { try { return s.getBytes("UTF-8"); } catch (Exception e) { return new byte[0]; } }

    /** Sections enregistrees, sinon deux sections par defaut d'apres les applis installees. */
    void loadSections() {
        sections.clear();
        String raw = prefs.getString("shelf", "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(";")) {
                String[] kv = part.split("~", 2);
                if (kv.length != 2) continue;
                ShelfSection s = new ShelfSection();
                try { s.name = u8(Base64.decode(kv[0], Base64.NO_WRAP)); } catch (Exception e) { continue; }
                if (s.name.isEmpty()) continue;
                for (String p : kv[1].split(",")) if (!p.isEmpty() && usable(p) && !s.pkgs.contains(p)) s.pkgs.add(p);
                sections.add(s);
            }
            return;
        }
        ShelfSection stream = new ShelfSection();
        stream.name = t("Streaming", "Streaming");
        for (String p : KNOWN_DOCK) if (usable(p) && !stream.pkgs.contains(p)) stream.pkgs.add(p);
        if (!stream.pkgs.isEmpty()) sections.add(stream);
        ShelfSection all = new ShelfSection();
        all.name = t("Toutes les applis", "All apps");
        all.pkgs.addAll(launchablePkgs());
        if (!all.pkgs.isEmpty()) sections.add(all);
    }

    void saveSections() {
        StringBuilder sb = new StringBuilder();
        for (ShelfSection s : sections) {
            if (s.pkgs.isEmpty()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(Base64.encodeToString(u8(s.name), Base64.NO_WRAP));
            sb.append('~').append(TextUtils.join(",", s.pkgs));
        }
        prefs.edit().putString("shelf", sb.toString()).putBoolean("shelf_user_set", true).apply();
    }

    void buildShelf() {
        shelf.removeAllViews();
        firstShelfView = null;
        shelfRows.clear();
        shelfPkg.clear();
        shelfSec.clear();
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(dockInset), dp(10), dp(dockInset), dp(16));
        rows.setClipChildren(false); rows.setClipToPadding(false);
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipChildren(false); sc.setClipToPadding(false);
        sc.addView(rows);
        shelf.addView(sc, new FrameLayout.LayoutParams(-1, -1));

        for (int i = 0; i < sections.size(); i++) {
            ShelfSection sec = sections.get(i);
            TextView title = text(sec.name, 14, fMed, 0xF2FFFFFF);
            title.setShadowLayer(dp(3), 0, dp(1), 0xA6000000);   // ombre courte : lisible sur les photos claires
            title.setPadding(dp(6), 0, 0, dp(8));
            LinearLayout.LayoutParams lt = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lt.topMargin = dp(12);
            rows.addView(title, lt);

            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipChildren(false); hsv.setClipToPadding(false);
            hsv.setFocusable(false);
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setClipChildren(false); strip.setClipToPadding(false);
            for (String pkg : sec.pkgs) {
                View v = shelfTile(pkg, sec);
                if (v == null) continue;
                if (firstShelfView == null) firstShelfView = v;
                strip.addView(v);
            }
            hsv.addView(strip, new FrameLayout.LayoutParams(-2, -2));
            rows.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
            shelfRows.add(hsv);
        }
        // la reconstruction detruit les tuiles : on remet le focus dans le shelf
        if (shelfOpen && firstShelfView != null) {
            View cur = getCurrentFocus();
            if (cur == null || shelfRowOf(cur) < 0) firstShelfView.requestFocus();
        }
    }

    View shelfTile(final String pkg, final ShelfSection sec) {
        PackageManager pm = getPackageManager();
        Drawable d = null;
        final String label;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(ai).toString();
            d = pm.getApplicationBanner(ai);
            if (d == null) { Intent li = pm.getLeanbackLaunchIntentForPackage(pkg); if (li != null) d = pm.getActivityBanner(li); }
        } catch (Exception e) { return null; }
        FrameLayout f = roundedFrame(9, 0xFF1E1E1E);
        if (d != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(pkgIcon(pkg));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(30), dp(30));
            il.gravity = Gravity.CENTER_VERTICAL;
            il.leftMargin = dp(12);
            f.addView(iv, il);
            TextView tv = text(label, 11, fMed, 0xFFFFFFFF);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
            tl.gravity = Gravity.CENTER_VERTICAL;
            tl.leftMargin = dp(50);
            tl.rightMargin = dp(10);
            f.addView(tv, tl);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(146), dp(80));
        lp.rightMargin = dp(10);
        f.setLayoutParams(lp);
        f.setContentDescription(label);
        f.setTag(9);
        shelfPkg.put(f, pkg);
        shelfSec.put(f, sec);
        wire(f, new Runnable() { public void run() { closeShelf(); launch(pkg); } }, 1.10f, false);
        f.setLongClickable(true);
        f.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { shelfTileMenu(pkg, sec); return true; } });
        return f;
    }

    void openShelf() {
        if (sections.isEmpty()) loadSections();
        buildShelf();
        if (firstShelfView == null) return;      // aucune section : rien a ouvrir
        shelfOpen = true;
        shelf.setVisibility(View.VISIBLE);
        final View content = shelf.getChildAt(0);
        if (content != null) {
            content.animate().cancel();
            content.setAlpha(0f);
            content.setTranslationY(dp(24));
            content.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(fluid()).start();
        }
        dock.animate().cancel();
        dock.animate().translationY(-dp(SHELF_H - 12)).setDuration(420).setInterpolator(fluid()).start();
        firstShelfView.requestFocus();
    }

    void closeShelf() {
        if (!shelfOpen) return;
        shelfOpen = false;
        focusDock();
        final View content = shelf.getChildAt(0);
        if (content != null) {
            content.animate().cancel();
            content.animate().alpha(0f).translationY(dp(18)).setDuration(170).setInterpolator(fluid())
                    .withEndAction(new Runnable() { public void run() {
                        if (!shelfOpen) shelf.setVisibility(View.GONE); } }).start();
        } else {
            shelf.setVisibility(View.GONE);
        }
        dock.animate().cancel();
        dock.animate().translationY(0).setDuration(340).setInterpolator(fluid()).start();
    }

    /** Rangee du Top Shelf qui contient la vue (index), -1 si la vue n'est pas dans le shelf. */
    int shelfRowOf(View v) {
        while (v != null) {
            int i = shelfRows.indexOf(v);
            if (i >= 0) return i;
            if (v == shelf) return -1;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return -1;
    }

    int shelfColOf(View v, int row) {
        if (row < 0 || row >= shelfRows.size()) return 0;
        View strip = shelfRows.get(row).getChildAt(0);
        if (!(strip instanceof ViewGroup)) return 0;
        ViewGroup g = (ViewGroup) strip;
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i) == v) return i;
        return 0;
    }

    void focusShelfTile(int row, int col) {
        while (row >= 0 && row < shelfRows.size()) {
            View strip = shelfRows.get(row).getChildAt(0);
            if (strip instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) strip;
                if (g.getChildCount() > 0) {
                    g.getChildAt(Math.max(0, Math.min(g.getChildCount() - 1, col))).requestFocus();
                    return;
                }
            }
            row += col < 0 ? -1 : 1;
        }
    }

    boolean attached(View v) {
        while (v != null) {
            if (v == root) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    // ---------- Personnalisation des sections ----------
    void shelfTileMenu(final String pkg, final ShelfSection sec) {
        List<String> l = new ArrayList<>(); List<Runnable> a = new ArrayList<>();
        l.add(t("Ouvrir", "Open")); a.add(new Runnable() { public void run() { closeShelf(); launch(pkg); } });
        l.add(t("Retirer de cette section", "Remove from this section")); a.add(new Runnable() { public void run() {
            sec.pkgs.remove(pkg); saveSections(); buildShelf(); } });
        l.add(t("Gérer les sections", "Manage sections")); a.add(new Runnable() { public void run() { showSectionsMenu(); } });
        showMenu(appLabel(pkg), l, a);
    }

    void showSectionsMenu() {
        if (sections.isEmpty()) loadSections();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            final int idx = i;
            ShelfSection s = sections.get(i);
            labels.add(s.name + "  ·  " + s.pkgs.size() + t(" applis", " apps"));
            actions.add(new Runnable() { public void run() { showSectionMenu(idx); } });
        }
        labels.add(t("Nouvelle section", "New section"));
        actions.add(new Runnable() { public void run() { newSection(); } });
        showMenu(t("Sections du Top Shelf", "Top Shelf sections"), labels, actions);
    }

    void showSectionMenu(final int idx) {
        if (idx < 0 || idx >= sections.size()) return;
        final ShelfSection s = sections.get(idx);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add(t("Renommer la section", "Rename section"));
        actions.add(new Runnable() { public void run() { renameSection(idx); } });
        labels.add(t("Ajouter une appli", "Add an app"));
        actions.add(new Runnable() { public void run() {
            showApps(new Picker() { public void picked(String pkg) {
                if (!s.pkgs.contains(pkg)) s.pkgs.add(pkg);
                saveSections(); if (shelfOpen) buildShelf(); } }); } });
        if (!s.pkgs.isEmpty()) {
            labels.add(t("Retirer une appli", "Remove an app"));
            actions.add(new Runnable() { public void run() { removeFromSection(idx); } });
            labels.add(t("Supprimer la section", "Delete section"));
            actions.add(new Runnable() { public void run() {
                sections.remove(idx); saveSections(); if (shelfOpen) buildShelf(); } });
        }
        showMenu(s.name, labels, actions);
    }

    void removeFromSection(final int idx) {
        final ShelfSection s = sections.get(idx);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (String p : s.pkgs) {
            final String pkg = p;
            labels.add(appLabel(pkg));
            actions.add(new Runnable() { public void run() {
                s.pkgs.remove(pkg); saveSections(); if (shelfOpen) buildShelf(); } });
        }
        showMenu(t("Retirer de ", "Remove from ") + s.name, labels, actions);
    }

    /** Themes proposes pour les sections : evite de taper un nom a la telecommande. */
    String[] sectionPresets() {
        return new String[]{t("Streaming", "Streaming"), t("Films et séries", "Movies & shows"),
                t("Musique", "Music"), t("Jeux", "Games"), t("Enfants", "Kids"),
                t("Sport", "Sports"), t("À la une", "Featured")};
    }

    void renameSection(final int idx) {
        if (idx < 0 || idx >= sections.size()) return;
        String[] presets = sectionPresets();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (final String name : presets) {
            labels.add((name.equals(sections.get(idx).name) ? "✓  " : "     ") + name);
            actions.add(new Runnable() { public void run() {
                sections.get(idx).name = name; saveSections(); if (shelfOpen) buildShelf(); } });
        }
        labels.add(t("Autre nom…", "Custom name…"));
        actions.add(new Runnable() { public void run() {
            promptText(t("Nom de la section", "Section name"),
                    t("Laissez vide pour annuler.", "Leave empty to cancel."),
                    sections.get(idx).name, new TextCallback() { public void done(String s) {
                        if (s.isEmpty()) return;
                        sections.get(idx).name = s; saveSections(); if (shelfOpen) buildShelf(); } }); } });
        showMenu(t("Nom de la section", "Section name"), labels, actions);
    }

    void newSection() {
        String[] presets = sectionPresets();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (final String name : presets) {
            labels.add(name);
            actions.add(new Runnable() { public void run() { addSection(name); } });
        }
        labels.add(t("Autre nom…", "Custom name…"));
        actions.add(new Runnable() { public void run() {
            promptText(t("Nouvelle section", "New section"),
                    t("Le nom apparaîtra au-dessus de la rangée.", "The name is shown above the row."),
                    "", new TextCallback() { public void done(String s) {
                        if (!s.isEmpty()) addSection(s); } }); } });
        showMenu(t("Nouvelle section", "New section"), labels, actions);
    }

    void addSection(String name) {
        final ShelfSection sec = new ShelfSection();
        sec.name = name;
        sections.add(sec);
        saveSections();
        showApps(new Picker() { public void picked(String pkg) {
            sec.pkgs.add(pkg); saveSections(); if (shelfOpen) buildShelf(); } });
    }

    interface TextCallback { void done(String s); }

    void promptText(String title, String msg, String initial, final TextCallback cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(initial);
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setView(box)
                .setPositiveButton(t("Valider", "Save"), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { cb.done(et.getText().toString().trim()); } })
                .setNegativeButton(t("Annuler", "Cancel"), null)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    public void onDismiss(android.content.DialogInterface d) { focusDock(); } })
                .show();
    }

    void editTitle() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setText("custom".equals(titleMode) ? titleText : "");
        et.setHint(deviceName());
        et.setSelection(et.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(t("Titre de l'écran d'accueil", "Home screen title"))
                .setMessage(t("Laissez vide pour afficher le nom de la TV.", "Leave empty to show the TV's name."))
                .setView(box)
                .setPositiveButton(t("Valider", "Save"), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String s = et.getText().toString().trim();
                        if (s.isEmpty()) { titleMode = "device"; titleText = ""; } else { titleMode = "custom"; titleText = s; }
                        saveConfig(); applyTitle(); } })
                .setNeutralButton(t("Aucun titre", "No title"), new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { titleMode = "none"; saveConfig(); applyTitle(); } })
                .setNegativeButton(t("Annuler", "Cancel"), null)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    public void onDismiss(android.content.DialogInterface d) { focusDock(); } })
                .show();
    }

    void startMove(int index) {
        moveIndex = index;
        hintView.setText(t("◀  ▶  pour déplacer   ·   OK pour valider", "◀  ▶  to move   ·   OK to confirm"));
        hintView.setVisibility(View.VISIBLE);
        buildDock(index);
    }

    void endMove() {
        moveIndex = -1;
        hintView.setVisibility(View.GONE);
        saveConfig();
        int keep = 0;
        View f = getCurrentFocus();
        if (f != null && bigTiles.contains(f)) keep = bigTiles.indexOf(f);
        buildDock(keep);
    }

    boolean isInDock(View v) {
        while (v != null) { if (v == dock) return true; v = v.getParent() instanceof View ? (View) v.getParent() : null; }
        return false;
    }

    boolean swallowOkUp = false;

    // Certaines telecommandes (Sony a infrarouge) envoient un appui maintenu comme une rafale
    // d'appuis courts : BURST_PRESSES appuis espaces de moins de BURST_GAP_MS = appui long.
    static final long BURST_GAP_MS = 250;
    static final int BURST_PRESSES = 3;
    Runnable pendingClick;            // clic du dock en attente (annule si une rafale commence)
    View burstView;
    int okBurst = 0;
    long lastOkUp = 0, lastOkEvent = 0;
    boolean burstSwallow = false;     // rafale deja transformee en appui long : on avale la suite

    static boolean isOk(int c) {
        return c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER || c == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        // appui long sur OK, aussi pour les telecommandes qui envoient la touche en repetition
        if (isOk(kc) && moveIndex < 0) {
            long t = e.getEventTime();
            if (burstSwallow) {
                if (t - lastOkEvent < BURST_GAP_MS) { lastOkEvent = t; return true; }
                burstSwallow = false;
            }
            lastOkEvent = t;
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && overlays.isEmpty()) {
                View f = getCurrentFocus();
                if (isLauncherTile(f)) {
                    okBurst = (f == burstView && t - lastOkUp < BURST_GAP_MS) ? okBurst + 1 : 1;
                    burstView = f;
                    if (okBurst >= 2 && pendingClick != null) { h.removeCallbacks(pendingClick); pendingClick = null; }
                    if (okBurst >= BURST_PRESSES) {
                        okBurst = 0;
                        burstSwallow = true;
                        f.cancelLongPress(); f.setPressed(false);
                        tileMenuFor(f);
                        return true;
                    }
                }
            }
            if (e.getAction() == KeyEvent.ACTION_UP) lastOkUp = t;
            if (e.getAction() == KeyEvent.ACTION_UP && swallowOkUp) { swallowOkUp = false; return true; }
            if (e.getAction() == KeyEvent.ACTION_DOWN && overlays.isEmpty() && (e.getRepeatCount() >= 1 || e.isLongPress())) {
                View f = getCurrentFocus();
                if (isLauncherTile(f)) {
                    swallowOkUp = true;
                    f.cancelLongPress(); f.setPressed(false);
                    tileMenuFor(f);
                    return true;
                }
            }
            if (swallowOkUp && e.getAction() == KeyEvent.ACTION_DOWN) return true;
        }
        if (moveIndex >= 0 && e.getAction() == KeyEvent.ACTION_DOWN) {
            int c = e.getKeyCode();
            int target = moveIndex;
            if (c == KeyEvent.KEYCODE_DPAD_LEFT) target = moveIndex - 1;
            else if (c == KeyEvent.KEYCODE_DPAD_RIGHT) target = moveIndex + 1;
            else if (c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER || c == KeyEvent.KEYCODE_BACK
                    || c == KeyEvent.KEYCODE_NUMPAD_ENTER || c == KeyEvent.KEYCODE_BUTTON_A) {
                endMove(); return true;
            } else return true;
            if (target >= 0 && target < dockPkgs.size() && target != moveIndex) {
                Collections.swap(dockPkgs, moveIndex, target);
                moveIndex = target;
                buildDock(target);
            }
            return true;
        }
        if (moveIndex >= 0) return true; // ignore les relachements pendant le deplacement
        // fleche bas depuis le dock : ouvre les sections ; dans le shelf, haut/bas passent d'une rangee a l'autre
        if (e.getAction() == KeyEvent.ACTION_DOWN && overlays.isEmpty()) {
            View f = getCurrentFocus();
            if (kc == KeyEvent.KEYCODE_DPAD_DOWN && f != null && isInDock(f) && !shelfOpen) { openShelf(); return true; }
            if (shelfOpen && f != null) {
                int row = shelfRowOf(f);
                if (row >= 0) {
                    int col = shelfColOf(f, row);
                    if (kc == KeyEvent.KEYCODE_DPAD_DOWN) { focusShelfTile(row + 1, col); return true; }
                    if (kc == KeyEvent.KEYCODE_DPAD_UP) {
                        if (row == 0) closeShelf(); else focusShelfTile(row - 1, col);
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    void pickAppToAdd() {
        showApps(new Picker() { public void picked(String pkg) {
            dockPkgs.remove(pkg);
            if (dockPkgs.size() >= layout) return;
            dockPkgs.add(pkg); saveConfig(); buildDock(dockPkgs.indexOf(pkg)); } });
    }

    void pickAppToReplace(final int index) {
        showApps(new Picker() { public void picked(String pkg) {
            if (index >= dockPkgs.size()) return;
            int existing = dockPkgs.indexOf(pkg);
            if (existing >= 0 && existing != index) Collections.swap(dockPkgs, existing, index);
            else dockPkgs.set(index, pkg);
            saveConfig(); buildDock(index); } });
    }

    // ---------- Menus en surimpression ----------
    void showMenu(String header, List<String> labels, final List<Runnable> actions) {
        final FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0x99000000);
        ov.setClickable(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pb = new GradientDrawable(); pb.setCornerRadius(dp(22)); pb.setColor(0xF21C1E24);
        pb.setStroke(Math.max(1, dp(0.75f)), 0x33FFFFFF);
        panel.setBackground(pb);
        panel.setPadding(dp(14), dp(18), dp(14), dp(14));
        panel.setClipChildren(false); panel.setClipToPadding(false);
        TextView hd = text(header, 16, fMed, 0xFFFFFFFF); hd.setShadowLayer(0, 0, 0, 0);
        hd.setPadding(dp(12), 0, dp(12), dp(10)); hd.setSingleLine(true); hd.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(hd);
        View first = null;
        for (int i = 0; i < labels.size(); i++) {
            final Runnable a = actions.get(i);
            TextView row = text(labels.get(i), 13, fReg, 0xFFFFFFFF); row.setShadowLayer(0, 0, 0, 0);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setSingleLine(true);
            final GradientDrawable rb = new GradientDrawable(); rb.setCornerRadius(dp(12)); rb.setColor(0x00FFFFFF);
            row.setBackground(rb);
            row.setFocusable(true); row.setClickable(true);
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                rb.setColor(has ? 0xFFFFFFFF : 0x00FFFFFF); ((TextView) v).setTextColor(has ? 0xFF111111 : 0xFFFFFFFF); } });
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeOverlay(ov, false); a.run(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(2), 0, dp(2));
            panel.addView(row, lp);
            if (first == null) first = row;
        }
        FrameLayout.LayoutParams lpp = new FrameLayout.LayoutParams(dp(360), -2, Gravity.CENTER);
        ov.addView(panel, lpp);
        openOverlay(ov, first);
    }

    interface Picker { void picked(String pkg); }

    /** Liste de toutes les applis. picker == null : ouvre l'appli ; sinon renvoie l'appli choisie. */
    /** Toutes les applis : grille pleine page facon Apple TV (icones, nom dessous, focus qui grandit). */
    void showApps(final Picker picker) {
        final PackageManager pm = getPackageManager();
        LinkedHashMap<String, ResolveInfo> map = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN); q.addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (!map.containsKey(p) && !p.equals(getPackageName())) map.put(p, ri);
            }
        }
        List<ResolveInfo> list = new ArrayList<>(map.values());
        Collections.sort(list, new java.util.Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) { return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()); } });

        final FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xFF0A0B0E);
        ov.setClickable(true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false); sv.setClipToPadding(false);
        GridLayout g = new GridLayout(this);
        g.setColumnCount(6);
        g.setPadding(dp(56), dp(104), dp(56), dp(40));
        g.setClipChildren(false); g.setClipToPadding(false);
        sv.addView(g, new FrameLayout.LayoutParams(-1, -2));

        View first = null;
        for (ResolveInfo ri : list) {
            final String pkg = ri.activityInfo.packageName;
            final String label = ri.loadLabel(pm).toString();
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ri.loadIcon(pm));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(iv, new LinearLayout.LayoutParams(dp(104), dp(104)));
            TextView tv = text(label, 11, fMed, 0xE6FFFFFF);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setShadowLayer(dp(3), 0, dp(1), 0xA6000000);
            LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(dp(132), -2);
            lpLabel.topMargin = dp(8);
            cell.addView(tv, lpLabel);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(148);
            lp.height = -2;
            lp.setMargins(dp(10), dp(12), dp(10), dp(16));
            cell.setLayoutParams(lp);
            cell.setContentDescription(label);
            cell.setTag(18);
            wire(cell, new Runnable() { public void run() {
                closeOverlay(ov, true);
                if (picker != null) picker.picked(pkg); else launch(pkg); } }, 1.16f, false);
            if (picker == null) {
                cell.setLongClickable(true);
                cell.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                    List<String> l = new ArrayList<>(); List<Runnable> a = new ArrayList<>();
                    l.add(t("Ouvrir", "Open")); a.add(new Runnable() { public void run() { closeOverlay(ov, false); launch(pkg); } });
                    if (!dockPkgs.contains(pkg) && dockPkgs.size() < layout) {
                        l.add(t("Ajouter au dock", "Add to dock")); a.add(new Runnable() { public void run() {
                            closeOverlay(ov, false); dockPkgs.add(pkg); saveConfig(); buildDock(dockPkgs.indexOf(pkg)); } });
                    } else if (!dockPkgs.contains(pkg)) {
                        l.add(t("Dock plein : remplacer une appli…", "Dock full: replace an app…")); a.add(new Runnable() { public void run() {
                            closeOverlay(ov, false); chooseSlotFor(pkg); } });
                    }
                    showMenu(appLabel(pkg), l, a); return true; } });
            }
            g.addView(cell);
            if (first == null) first = cell;
        }

        TextView hdr = text(picker == null ? t("Applis", "Apps") : t("Choisissez une appli", "Choose an app"),
                30, fMed, 0xFFFFFFFF);
        hdr.setShadowLayer(dp(9), 0, dp(2), 0x99000000);
        FrameLayout.LayoutParams lh = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lh.setMargins(dp(60), dp(32), 0, 0);
        if (picker == null) {
            TextView tip = text(t("Appui long sur OK : ajouter au dock", "Long-press OK: add to dock"), 11, fReg, 0x8CFFFFFF);
            tip.setShadowLayer(dp(3), 0, dp(1), 0xA6000000);
            FrameLayout.LayoutParams lt = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            lt.setMargins(0, 0, dp(60), dp(36));
            ov.addView(tip, lt);
        }
        ov.addView(sv, 0, new FrameLayout.LayoutParams(-1, -1));
        ov.addView(hdr, lh);
        openOverlay(ov, first);

        // la grille monte doucement pendant que le fond apparait : pas de saut
        g.setAlpha(0f);
        g.setTranslationY(dp(22));
        g.animate().alpha(1f).translationY(0f).setDuration(440).setInterpolator(fluid()).start();
        hdr.setAlpha(0f);
        hdr.setTranslationY(dp(14));
        hdr.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(fluid()).start();
    }

    void chooseSlotFor(final String pkg) {
        List<String> l = new ArrayList<>(); List<Runnable> a = new ArrayList<>();
        for (int i = 0; i < dockPkgs.size(); i++) {
            final int idx = i;
            l.add(t("Remplacer ", "Replace ") + appLabel(dockPkgs.get(i)));
            a.add(new Runnable() { public void run() { dockPkgs.set(idx, pkg); saveConfig(); buildDock(idx); } });
        }
        showMenu(appLabel(pkg), l, a);
    }

    void openOverlay(View ov, View focus) {
        overlayReturn.add(getCurrentFocus());
        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        overlays.add(ov);
        ov.setAlpha(0f);
        ov.animate().alpha(1f).setDuration(220).setInterpolator(fluid()).start();
        if (focus != null) focus.requestFocus();
    }

    void closeOverlay(View ov, boolean refocus) {
        root.removeView(ov);
        overlays.remove(ov);
        View back = overlayReturn.isEmpty() ? null : overlayReturn.remove(overlayReturn.size() - 1);
        if (refocus) {
            if (back != null && attached(back) && back.isFocusable()) back.requestFocus();
            else focusDock();
        }
    }

    void closeTopOverlay() {
        if (overlays.isEmpty()) return;
        View ov = overlays.get(overlays.size() - 1);
        closeOverlay(ov, true);
    }

    void focusDock() {
        if (shelfOpen && firstShelfView != null && attached(firstShelfView)) {
            firstShelfView.requestFocus();
            return;
        }
        if (!bigTiles.isEmpty()) bigTiles.get(0).requestFocus();
        else if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) { // l'accueil ne se ferme jamais
            if (shelfOpen && overlays.isEmpty()) { closeShelf(); return true; }
            closeTopOverlay();
            return true;
        }
        // touche Source / TV : sur certaines TV, seul l'accueil d'origine la gerait
        if ((code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV) && overlays.isEmpty() && moveIndex < 0) {
            openInputs();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (!overlays.isEmpty()) return true;
            View f = getCurrentFocus();
            if (f != null && shelfOpen) { showSectionsMenu(); return true; }
            int idx = f != null ? bigTiles.indexOf(f) : -1;
            showTileMenu(idx);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onBackPressed() { closeTopOverlay(); }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // touche Accueil : ferme tout ce qui est ouvert
        if (moveIndex >= 0) endMove();
        closeShelf();
        while (!overlays.isEmpty()) closeOverlay(overlays.get(overlays.size() - 1), false);
        focusDock();
    }

    // =====================================================================
    // Lancements
    String[] findInput() {
        for (String[] in : KNOWN_INPUTS) {
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(in[0], 0);
                if (!ai.enabled) continue;
                if (in[1] != null || launchIntent(in[0]) != null) return in;
            } catch (Exception ignored) { }
        }
        return null;
    }

    void openInput(String[] in) {
        if (in[1] != null) {
            try {
                Intent i = new Intent(); i.setComponent(new ComponentName(in[0], in[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return;
            } catch (Exception ignored) { }
        }
        launch(in[0]);
    }

    /** Premier ecran du systeme qui gere l'action « voir les entrees » (null s'il n'y en a pas). */
    ComponentName viewInputsTarget() {
        try {
            java.util.List<android.content.pm.ResolveInfo> l = getPackageManager().queryIntentActivities(
                    new Intent("com.android.tv.action.VIEW_INPUTS"), 0);
            if (l != null) for (android.content.pm.ResolveInfo ri : l) {
                if (ri.activityInfo != null && ri.activityInfo.enabled)
                    return new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Menu des entrees du systeme s'il existe, sinon l'appli TV de la marque. */
    void openInputs() {
        Intent vi = new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName target = viewInputsTarget();
        if (target != null) {
            // on vise l'écran trouvé : sinon, quand plusieurs applis le gèrent (accueil Google + XGIMI), Android affiche un choix
            try { startActivity(new Intent(vi).setComponent(target)); return; } catch (Exception ignored) { }
        }
        if (vi.resolveActivity(getPackageManager()) != null) {
            try { startActivity(vi); return; } catch (Exception ignored) { }
        }
        String[] in = findInput();
        if (in != null) openInput(in);
        else toast(t("Aucun menu des entrées trouvé sur cette TV", "No inputs menu found on this TV"));
    }

    Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    void launch(String pkg) {
        Intent i = launchIntent(pkg);
        if (i == null) { toast(t("Appli introuvable", "App not found")); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (ActivityNotFoundException e) { toast(t("Impossible d'ouvrir", "Cannot open")); }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    ImageView newWall() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        return v;
    }

    TextView text(String s, float size, Typeface tf, int color) {
        TextView tv = new TextView(this);
        tv.setText(s); tv.setTextColor(color); tv.setTypeface(tf);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * density);
        tv.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    // =====================================================================
    // Fonds d'ecran
    void loadWalls() {
        walls.clear();
        wallsFromFiles = false;
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "ZenHome");
            File[] fs = dir.listFiles();
            if (fs != null) {
                Arrays.sort(fs);
                for (File f : fs) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")))
                        walls.add(f.getAbsolutePath());
                }
            }
        } catch (Throwable ignored) { }
        if (!walls.isEmpty()) { wallsFromFiles = true; }
        else {
            try {
                String[] a = getAssets().list("walls");
                if (a != null) { Arrays.sort(a); for (String n : a) { String l = n.toLowerCase(Locale.ROOT); if (l.endsWith(".jpg") || l.endsWith(".png")) walls.add(n); } }
            } catch (Exception ignored) { }
        }
        wallIdx = walls.isEmpty() ? 0 : (int) ((System.currentTimeMillis() / rotationMs) % walls.size());
    }

    Bitmap decode(String name) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            int maxW = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
            if (wallsFromFiles) {
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(name, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= maxW) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeFile(name, o);
            }
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            InputStream is = getAssets().open("walls/" + name);
            Bitmap b = BitmapFactory.decodeStream(is, null, o);
            is.close();
            return b;
        } catch (Throwable t) { return null; }
    }

    void showWall(boolean instant) {
        if (walls.isEmpty()) return;
        final ImageView next = aOnTop ? wallB : wallA;
        final ImageView cur = aOnTop ? wallA : wallB;
        Bitmap bm = decode(walls.get(wallIdx));
        if (bm == null) return;
        Drawable old = next.getDrawable();
        next.setImageBitmap(bm);
        if (old instanceof BitmapDrawable) {
            Bitmap ob = ((BitmapDrawable) old).getBitmap();
            if (ob != null && ob != bm) ob.recycle();
        }
        next.bringToFront(); // uniquement dans le calque des photos
        next.setScaleX(1f); next.setScaleY(1f);
        next.animate().cancel();
        if (instant) { next.setAlpha(1f); cur.setAlpha(0f); }
        else {
            next.setAlpha(0f);
            android.animation.ObjectAnimator.ofFloat(next, "alpha", 0f, 1f).setDuration(FADE_MS).start();
        }
        next.animate().scaleX(1.06f).scaleY(1.06f).setDuration(rotationMs + FADE_MS)
                .setInterpolator(new LinearInterpolator()).start();
        aOnTop = !aOnTop;
    }

    final Runnable rotate = new Runnable() {
        public void run() {
            if (walls.size() > 1) { wallIdx = (wallIdx + 1) % walls.size(); showWall(false); }
            h.postDelayed(this, rotationMs);
        }
    };

    final Runnable tick = new Runnable() {
        public void run() {
            Date n = new Date();
            clock.setText(DateFormat.is24HourFormat(Home.this) ? DateFormat.format("HH:mm", n) : DateFormat.format("h:mm", n));
            Locale loc = Locale.getDefault();
            String d = new SimpleDateFormat(fr ? "EEEE d MMMM" : "EEEE, MMMM d", loc).format(n);
            date.setText(d.substring(0, 1).toUpperCase(loc) + d.substring(1));
            h.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        // la configuration a pu changer via le kit pendant que l'accueil etait en fond
        String rev = setting("zenhome_rev");
        if (rev != null && !rev.equals(prefs.getString("rev", null)) && moveIndex < 0 && overlays.isEmpty()) {
            loadConfig(); applyTitle(); buildDock(0);
        }
        h.removeCallbacksAndMessages(null);
        h.post(tick);
        h.post(statusTick);
        h.postDelayed(rotate, rotationMs);
        for (Bundled b : BUNDLED) if (installed(b)) ApkProvider.apkFile(this, b.file).delete();
        if (pendingPermissionFor >= 0 && canInstallApks()) {
            int i = pendingPermissionFor;
            setPendingPermission(-1);
            installBundled(BUNDLED[i], i);
        } else if (awaitingBundle >= 0) {
            int i = awaitingBundle;
            setAwaitingBundle(-1);
            if (installed(BUNDLED[i])) {
                installNextBundled(i);
                // Android ne livre le BOOT_COMPLETED qu'apres un premier lancement : on ouvre
                // AirPlay une fois pour qu'il soit deja pret au prochain allumage de la TV.
                if (awaitingBundle < 0 && installed(AIRPLAY)) {
                    toast(t("AirPlay est activé : il démarrera à chaque allumage de la TV.",
                            "AirPlay is set up: it will start every time the TV boots."));
                    h.postDelayed(new Runnable() { public void run() { launch(AIRPLAY.pkg); } }, 1200);
                }
            }
        }
        maybeOfferBundled();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeShelf();                       // on revient toujours sur un accueil propre
        h.removeCallbacksAndMessages(null); // rien ne tourne pendant qu'une appli est ouverte
        bundledOffered = false;             // la proposition repartira au prochain retour
    }

    @Override
    protected void onStop() {
        super.onStop();
        // libere la memoire des photos pendant qu'on regarde une video
        for (ImageView v : new ImageView[]{wallA, wallB}) {
            v.animate().cancel();
            Drawable d = v.getDrawable(); v.setImageDrawable(null);
            if (d instanceof BitmapDrawable) { Bitmap b = ((BitmapDrawable) d).getBitmap(); if (b != null) b.recycle(); }
        }
        released = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (released) { released = false; loadWalls(); showWall(true); }
    }

    // =====================================================================
    // Icones de l'en-tete, dessinees dans un bitmap : trait fin facon tvOS, net a l'ecran
    static final int IC_WIFI = 0, IC_VPN = 1, IC_AIRPLAY = 2, IC_GEAR = 3;

    Bitmap headIcon(int kind) {
        int px = Math.max(24, Math.round(46 * density));
        Bitmap bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setShadowLayer(Math.max(1f, px * 0.045f), 0, Math.max(1f, px * 0.018f), 0x8C000000);
        float s = px * 0.76f, cx = px / 2f, cy = px / 2f;
        if (kind == IC_WIFI) drawWifi(c, p, cx, cy, s);
        else if (kind == IC_VPN) drawVpn(c, p, cx, cy, s);
        else if (kind == IC_AIRPLAY) drawAirPlay(c, p, cx, cy, s);
        else drawGear(c, p, cx, cy, s);
        return bm;
    }

    void drawWifi(Canvas c, Paint p, float cx, float cy, float s) {
        float baseY = cy + s * 0.18f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.085f);
        for (int i = 0; i < 3; i++) {
            float r = s * (0.15f + i * 0.14f);
            boolean full = wifiOn && wifiLvl >= i + 1;
            p.setColor(!wifiOn ? 0x66FFFFFF : (full ? 0xFFFFFFFF : 0x99FFFFFF));
            c.drawArc(new RectF(cx - r, baseY - r, cx + r, baseY + r), 225f, 90f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(wifiOn ? 0xFFFFFFFF : 0x66FFFFFF);
        c.drawCircle(cx, baseY, s * 0.062f, p);
        if (!wifiOn) {   // pas de reseau : barre diagonale
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.075f);
            p.setColor(0xFFFFFFFF);
            c.drawLine(cx - s * 0.28f, baseY - s * 0.34f, cx + s * 0.28f, baseY + s * 0.06f, p);
        }
    }

    void drawVpn(Canvas c, Paint p, float cx, float cy, float s) {
        Path shield = new Path();
        float hw = s * 0.30f, top = cy - s * 0.40f, shoulder = top + s * 0.14f, bottom = cy + s * 0.42f;
        shield.moveTo(cx, top);
        shield.lineTo(cx + hw, shoulder);
        shield.lineTo(cx + hw, cy + s * 0.02f);
        shield.cubicTo(cx + hw, cy + s * 0.24f, cx + s * 0.13f, bottom - s * 0.04f, cx, bottom);
        shield.cubicTo(cx - s * 0.13f, bottom - s * 0.04f, cx - hw, cy + s * 0.24f, cx - hw, cy + s * 0.02f);
        shield.lineTo(cx - hw, shoulder);
        shield.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.072f);
        p.setColor(vpnOn ? ACCENT : 0xCCFFFFFF);
        c.drawPath(shield, p);
        p.setStyle(Paint.Style.FILL);   // serrure
        c.drawRoundRect(new RectF(cx - s * 0.028f, cy - s * 0.06f, cx + s * 0.028f, cy + s * 0.10f),
                s * 0.028f, s * 0.028f, p);
        c.drawCircle(cx, cy - s * 0.075f, s * 0.05f, p);
    }

    void drawAirPlay(Canvas c, Paint p, float cx, float cy, float s) {
        int col = airOn ? 0xFFFFFFFF : 0x66FFFFFF;
        float hw = s * 0.34f, top = cy - s * 0.32f, bot = cy + s * 0.04f, r = s * 0.10f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.075f);
        p.setColor(col);
        c.drawRoundRect(new RectF(cx - hw, top, cx + hw, bot), r, r, p);
        Path beam = new Path();   // faisceau qui sort de l'ecran
        beam.moveTo(cx, bot - s * 0.03f);
        beam.lineTo(cx + s * 0.19f, cy + s * 0.36f);
        beam.lineTo(cx - s * 0.19f, cy + s * 0.36f);
        beam.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(beam, p);
    }

    void drawGear(Canvas c, Paint p, float cx, float cy, float s) {
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.13f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            c.drawLine(cx + ca * s * 0.20f, cy + sa * s * 0.20f,
                       cx + ca * s * 0.34f, cy + sa * s * 0.34f, p);
        }
        p.setStrokeWidth(s * 0.072f);
        c.drawCircle(cx, cy, s * 0.255f, p);
        c.drawCircle(cx, cy, s * 0.075f, p);
    }
}
