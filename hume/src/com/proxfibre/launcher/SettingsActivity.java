package com.proxfibre.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * tvOS-style settings: a main list of sections, each opening its own page, with the page's icon
 * and title on the left and its rows on the right; Back returns to the main list.
 *
 * A TMDB token can also be supplied over adb:
 *   adb shell am start -n com.proxfibre.launcher/.SettingsActivity --es tmdb_token &lt;token&gt;
 */
public class SettingsActivity extends Activity {

    LinearLayout list;
    final List<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Lang.init(this);
        Lang.views(this);
        Clicks.init(this);
        String token = getIntent().getStringExtra("tmdb_token");
        if (token != null && !token.trim().isEmpty()) {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, token.trim()).apply();
            Suggestions.changed(this);
            Toast.makeText(this, Lang.t("TMDB connected"), Toast.LENGTH_SHORT).show();
        }
        build();
        String pg = getIntent().getStringExtra("page");  // adb: --es page "Top Shelf"
        if (pg != null) show(pg);
    }

    // tvOS-style pages: the main list opens a page per section; Back returns to the main list.
    static final String ROOT = "root", PROFILES = "Profiles", SHELF = "Top Shelf", PRESENT = "Presentation",
            INPUTS = "Inputs", WEATHER = "Weather", WATCHING = "Continue Watching", HOME = "Home",
            PERMS = "Permissions", JELLYFIN = "Jellyfin", APPS = "Apps", ABOUT = "About";

    FrameLayout leftPane;
    ScrollView scroller;
    String page = ROOT, lastPage = null;
    final java.util.Map<String, android.view.View> navRows = new java.util.HashMap<>();

    void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B10);
        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        leftPane = new FrameLayout(this);
        split.addView(leftPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(0, Ui.px(this, 60), Ui.px(this, 44), Ui.px(this, 60));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(Ui.px(this, 12), 0, Ui.px(this, 12), 0);
        scroller.addView(list);
        split.addView(scroller, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));
        setContentView(root);
        show(ROOT);
    }

    /** Builds one page: its icon and title on the left, its rows on the right. */
    void show(String p) {
        lastPage = page;
        page = p;
        list.removeAllViews();
        refreshers.clear();
        navRows.clear();
        buildLeft(p);
        switch (p) {
            case PROFILES: pageProfiles(); break;
            case SHELF: pageShelf(); break;
            case PRESENT: pagePresentation(); break;
            case INPUTS: pageInputs(); break;
            case WEATHER: pageWeather(); break;
            case WATCHING: pageWatching(); break;
            case HOME: pageHome(); break;
            case PERMS: pagePermissions(); break;
            case JELLYFIN: pageJellyfin(); break;
            case APPS: pageApps(); break;
            case ABOUT: pageAbout(); break;
            default: pageRoot(); break;
        }
        scroller.scrollTo(0, 0);
        // Returning to the main list puts focus back on the section you came from.
        android.view.View focus = p.equals(ROOT) && lastPage != null ? navRows.get(lastPage) : null;
        if (focus == null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (list.getChildAt(i).isFocusable()) {
                    focus = list.getChildAt(i);
                    break;
                }
            }
        }
        if (focus != null) focus.requestFocus();
        list.setAlpha(0f);
        list.setTranslationX(Ui.px(this, p.equals(ROOT) ? -16 : 16));
        list.animate().alpha(1f).translationX(0).setDuration(220).start();
    }

    @Override
    public void onBackPressed() {
        if (!page.equals(ROOT)) show(ROOT);
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A permission (install unknown apps, location…) may have just been granted on a system
        // screen: continue any install that was waiting for it, then refresh what it shows.
        Bundled.resume(this);
        refreshAll();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshAll();
    }

    /** Left side: the page's icon (the ProxFibre mark on the main list), its title, and the brand line. */
    void buildLeft(String p) {
        leftPane.removeAllViews();
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        android.view.View icon;
        if (p.equals(ROOT)) {
            // The ProxFibre mark on its dark plate, the same shape and radius as the Home cards.
            FrameLayout tile = new FrameLayout(this);
            GradientDrawable plate = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF17171C, 0xFF0B0B0E});
            plate.setCornerRadius(Ui.px(this, 12));
            tile.setBackground(plate);
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.proxfibre_mark);
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(mark, new FrameLayout.LayoutParams(Ui.px(this, 58), Ui.px(this, 58), Gravity.CENTER));
            icon = tile;
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pageIcon(p, Ui.px(this, 150), Ui.px(this, 90)));
            iv.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Ui.px(this, 12));
            iv.setBackground(shape);
            icon = iv;
        }
        icon.setElevation(Ui.px(this, 8));
        left.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 150), Ui.px(this, 90)));

        TextView title = new Lang.Tv(this);
        title.setText(p.equals(ROOT) ? "Settings" : p);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Ui.font(this, "display"));
        title.setLetterSpacing(-0.02f);
        Ui.textSize(title, p.equals(ROOT) ? 34 : 28);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(this, 22);
        left.addView(title, tlp);

        if (p.equals(ROOT)) {
            TextView by = new Lang.Tv(this);
            by.setText("ProxFibre");
            by.setTextColor(0x8CFFFFFF);
            by.setTypeface(Ui.font(this, "regular"));
            Ui.textSize(by, 13);
            by.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.px(this, 4);
            left.addView(by, blp);
        }
        leftPane.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** A 5:3 icon for a section page: a gradient tile with a white glyph (or the section's own art). */
    android.graphics.Bitmap pageIcon(String p, int w, int h) {
        if (p.equals(PRESENT)) return com.proxfibre.launcher.present.PresentIcon.banner(w, h);
        int[] grad;
        String glyph;
        switch (p) {
            case PROFILES: grad = new int[]{0xFFBF5AF2, 0xFF7D3CCB}; glyph = null; break;
            case SHELF: grad = new int[]{0xFF64D2FF, 0xFF0A84FF}; glyph = "shelf"; break;
            case INPUTS: grad = new int[]{0xFF6E6E73, 0xFF2C2C2E}; glyph = "input"; break;
            case WEATHER: grad = new int[]{0xFF5AC8FA, 0xFF2D6BD8}; glyph = null; break;
            case WATCHING: grad = new int[]{0xFFFF6482, 0xFFD6305A}; glyph = "present"; break;
            case HOME: grad = new int[]{0xFF30D158, 0xFF1E9E48}; glyph = "house"; break;
            case PERMS: grad = new int[]{0xFF8E8E93, 0xFF48484A}; glyph = "shield"; break;
            case JELLYFIN: grad = new int[]{0xFF5E5CE6, 0xFF3634A3}; glyph = "shelf"; break;
            case APPS: grad = new int[]{0xFFFF9F0A, 0xFFC2730A}; glyph = "memory"; break;
            default: grad = new int[]{0xFF8E8E93, 0xFF3A3A3C}; glyph = "gear"; break;
        }
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, grad[0], grad[1], android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        android.graphics.Bitmap g;
        if (p.equals(PROFILES)) g = Profiles.avatar(this, Profiles.current(this), (int) (h * 0.62f));
        else if (p.equals(WEATHER)) g = WeatherIcons.get(0, true, (int) (h * 0.7f));
        else g = Glyphs.get(glyph, (int) (h * 0.56f), Color.WHITE);
        c.drawBitmap(g, (w - g.getWidth()) / 2f, (h - g.getHeight()) / 2f, null);
        return b;
    }

    // ---------------------------------------------------------------- pages

    void pageRoot() {
        nav(PROFILES, () -> Profiles.current(this).name);
        nav(SHELF, () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_WALLS: return "Wallpapers";
                case Suggestions.SHELF_NONE: return "Off";
                default: return "Recommendations & News";
            }
        });
        nav(PRESENT, () -> com.proxfibre.launcher.present.Presenter.enabled(this) ? "On" : "Off");
        if (!Inputs.list(this).isEmpty()) nav(INPUTS, () -> Inputs.showCard(this) ? "On" : "Off");
        nav(WEATHER, () -> !Weather.enabled(this) ? "Off" : Weather.place(this).isEmpty() ? "On" : Weather.place(this));
        nav(WATCHING, () -> !Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "Off" : MediaWatchService.granted(this) ? "On"
                : Perms.listenerSupported(this) ? "Needs permission" : "Not on this TV");
        nav(JELLYFIN, () -> Jellyfin.configured(this) ? Jellyfin.status(this) : "Not connected");
        nav(APPS, () -> AppCatalog.custom(this).isEmpty() ? "Armadillo, AirPlay…" : AppCatalog.custom(this).size() + " custom");
        nav(HOME, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        });
        nav(PERMS, () -> {
            int missing = (Perms.hasListener(this) || !Perms.listenerSupported(this) ? 0 : 1) + (Perms.hasLocation(this) ? 0 : 1)
                    + (Perms.hasAdmin(this) || !Perms.adminSupported(this) ? 0 : 1) + (Perms.isDefaultHome(this) ? 0 : 1);
            return missing == 0 ? "All allowed" : missing + " to allow";
        });
        nav(ABOUT, () -> "Version " + version());
        row("Language", () -> Lang.name(Lang.get(this)), () -> dialog("Language")
                .setSingleChoiceItems(Lang.NAMES, Lang.get(this), (d, w) -> {
                    Lang.set(this, w);
                    d.dismiss();
                    recreate();
                }).show());
        row("TV Settings", () -> "", () -> Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS)));
    }

    void pageProfiles() {
        row("Current profile", () -> Profiles.current(this).name, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra("pick_profile", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        row("Show profiles on wake", () -> Profiles.askOnWake(this) ? "On" : "Off",
                () -> Profiles.setAskOnWake(this, !Profiles.askOnWake(this)));
    }

    void pageShelf() {
        row("Show in carousel", () -> {
            switch (Suggestions.shelf(this)) {
                case Suggestions.SHELF_RECS: return "Recommendations";
                case Suggestions.SHELF_NEWS: return "News";
                case Suggestions.SHELF_WALLS: return "Wallpapers";
                case Suggestions.SHELF_NONE: return "Nothing";
                default: return "Both";
            }
        }, () -> {
            String[] keys = {Suggestions.SHELF_BOTH, Suggestions.SHELF_RECS, Suggestions.SHELF_NEWS,
                    Suggestions.SHELF_WALLS, Suggestions.SHELF_NONE};
            String[] names = {"Recommendations & News", "Recommendations", "News", "Wallpapers", "Nothing"};
            int sel = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.shelf(this)));
            dialog("Show in carousel").setSingleChoiceItems(names, sel, (d, w) -> {
                Suggestions.setShelf(this, keys[w]);
                d.dismiss();
                show(SHELF);
            }).show();
        });
        if (Suggestions.SHELF_WALLS.equals(Suggestions.shelf(this))) {
            section("Wallpapers");
            row("How it works", () -> Wallpapers.KEYS.length + " fonds", () -> Toast.makeText(this,
                    Lang.s("The carousel shows your generated fonds full screen with a very slow zoom. OK on one makes it the Home background.",
                            "Le carrousel affiche vos fonds générés en plein écran avec un zoom très lent. OK sur l'un d'eux le définit comme fond de l'accueil.",
                            "El carrusel muestra tus fondos generados a pantalla completa con un zoom muy lento. OK en uno lo pone como fondo de Inicio."),
                    Toast.LENGTH_LONG).show());
        }
        if (Suggestions.showRecs(this)) {
            section("Recommendations");
            row("Source", () -> Suggestions.sourceName(Suggestions.source(this)), this::chooseSource);
            row("Content", () -> {
                String t = Suggestions.types(this);
                return t.equals("movie") ? "Movies" : t.equals("tv") ? "TV Series" : "Movies & Series";
            }, this::chooseTypes);
        }
        if (Suggestions.showRecs(this) && Suggestions.source(this).equals(Suggestions.SRC_STREMIO)) {
            row("Opens in", () -> {
                Suggestions.Provider p = Suggestions.stremioApp(this);
                return Suggestions.installed(this, p.pkg) ? p.name : "Stremio (not installed)";
            }, () -> Toast.makeText(this, Lang.t("Titles open in Stremio, or in Nuvio when only Nuvio is installed."),
                    Toast.LENGTH_LONG).show());
        } else if (Suggestions.showRecs(this)) {
            row("Streaming services", () -> {
                int n = Suggestions.providers(this).size();
                return n == Suggestions.PROVIDERS.size() ? "All" : n + " selected";
            }, this::chooseProviders);
            row("Languages", () -> {
                Set<String> l = Suggestions.languages(this);
                if (l.isEmpty()) return "Any";
                List<String> names = new ArrayList<>();
                for (int i = 0; i < Suggestions.LANG_CODES.length; i++) {
                    if (l.contains(Suggestions.LANG_CODES[i])) names.add(Suggestions.LANG_NAMES[i]);
                }
                return names.size() > 2 ? names.size() + " languages" : String.join(", ", names);
            }, this::chooseLanguages);
            row("TMDB token", () -> Suggestions.hasToken(this) ? "Connected" : "Not set", this::enterToken);
        }
        if (Suggestions.showNews(this)) {
            section("News");
            row("Newsrooms", () -> {
                int n = News.sources(this).size();
                return n == News.SOURCES.size() ? "All" : n + " selected";
            }, this::chooseNewsrooms);
            row("Update news every", () -> News.refreshMinutes(this) + " minutes", () -> {
                int[] mins = News.REFRESH_CHOICES;
                String[] names = new String[mins.length];
                int sel = 1;
                for (int i = 0; i < mins.length; i++) {
                    names[i] = mins[i] + " minutes" + (mins[i] == 30 ? "  (default)" : "");
                    if (mins[i] == News.refreshMinutes(this)) sel = i;
                }
                dialog("Update news every").setSingleChoiceItems(names, sel, (d, w) -> {
                    Suggestions.prefs(this).edit().putInt(News.K_REFRESH, mins[w]).apply();
                    refreshAll();
                    d.dismiss();
                }).show();
            });
        }
        if (Suggestions.enabled(this)) {
            section("Carousel");
            row("Auto-advance", () -> {
                int sec = Suggestions.advanceSeconds(this);
                return sec == 0 ? "Off" : "Every " + sec + " s";
            }, this::chooseAdvance);
            row("Refresh now", () -> "", () -> {
                Suggestions.changed(this);
                Toast.makeText(this, Lang.t("The carousel will refresh on Home"), Toast.LENGTH_SHORT).show();
            });
        }
    }

    void chooseNewsrooms() {
        String[] names = new String[News.SOURCES.size()];
        boolean[] on = new boolean[names.length];
        Set<String> cur = News.sources(this);
        for (int i = 0; i < names.length; i++) {
            names[i] = News.SOURCES.get(i).name;
            on[i] = cur.contains(News.SOURCES.get(i).id);
        }
        dialog("Newsrooms").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton(Lang.t("Done"), (d, w) -> {
                    Set<String> ids = new java.util.HashSet<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) ids.add(News.SOURCES.get(i).id);
                    if (ids.isEmpty()) ids.add("toi");
                    News.setSources(this, ids);
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton(Lang.t("Cancel"), null).show();
    }

    void pagePresentation() {
        row("Presentation mode", () -> com.proxfibre.launcher.present.Presenter.enabled(this) ? "On" : "Off", () -> {
            boolean on = !com.proxfibre.launcher.present.Presenter.enabled(this);
            com.proxfibre.launcher.present.Presenter.set(this, com.proxfibre.launcher.present.Presenter.KEY_ENABLED, on);
            if (on) Toast.makeText(this, Lang.t("Presentation is on. Send content from a browser."), Toast.LENGTH_LONG).show();
        });
        row("Start on boot", () -> com.proxfibre.launcher.present.Presenter.startOnBoot(this) ? "On" : "Off", () ->
                com.proxfibre.launcher.present.Presenter.set(this, com.proxfibre.launcher.present.Presenter.KEY_BOOT,
                        !com.proxfibre.launcher.present.Presenter.startOnBoot(this)));
        row("Keep screen awake", () -> com.proxfibre.launcher.present.Presenter.keepAwake(this) ? "On" : "Off", () ->
                com.proxfibre.launcher.present.Presenter.set(this, com.proxfibre.launcher.present.Presenter.KEY_AWAKE,
                        !com.proxfibre.launcher.present.Presenter.keepAwake(this)));
        row("Address & pairing code", () -> com.proxfibre.launcher.present.Presenter.enabled(this)
                ? com.proxfibre.launcher.present.Presenter.address().replace("http://", "") : "Turn on first", this::showPresentInfo);
        row("Start presentation now", () -> "", () -> {
            if (!com.proxfibre.launcher.present.Presenter.enabled(this)) {
                com.proxfibre.launcher.present.Presenter.set(this, com.proxfibre.launcher.present.Presenter.KEY_ENABLED, true);
            }
            com.proxfibre.launcher.present.Presenter.open(this);
        });
    }

    void pageInputs() {
        row("Inputs card on Home", () -> Inputs.showCard(this) ? "On" : "Off", () ->
                Inputs.prefs(this).edit().putBoolean(Inputs.K_CARD, !Inputs.showCard(this)).apply());
        section("This TV");
        for (Inputs.Input in : Inputs.list(this)) {
            row(in.label, () -> in.disconnected ? "Not connected" : "", () -> {
                if (!Inputs.open(this, in)) Toast.makeText(this, Lang.t("Can't switch to ") + in.label, Toast.LENGTH_SHORT).show();
            });
        }
    }

    void pageWeather() {
        row("Show weather", () -> Weather.enabled(this) ? "On" : "Off", () -> {
            Weather.prefs(this).edit().putBoolean(Weather.K_ENABLED, !Weather.enabled(this)).apply();
            Weather.changed(this);
        });
        row("Location", () -> Weather.manual(this) ? Weather.place(this)
                : Weather.place(this).isEmpty() ? "Automatic" : "Automatic \u00b7 " + Weather.place(this), this::chooseLocation);
        row("Temperature", () -> {
            String u = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            return u.equals("c") ? "Celsius" : u.equals("f") ? "Fahrenheit" : "Automatic";
        }, () -> {
            String[] keys = {"auto", "c", "f"};
            String[] names = {"Automatic", "Celsius (\u00b0C)", "Fahrenheit (\u00b0F)"};
            String cur = Weather.prefs(this).getString(Weather.K_UNITS, "auto");
            int sel = cur.equals("c") ? 1 : cur.equals("f") ? 2 : 0;
            dialog("Temperature").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_UNITS, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Updates");
        row("Source", () -> Weather.sourceName(Weather.source(this)), () -> {
            String[] keys = Weather.SOURCES;
            String[] names = {"Open-Meteo", "MET Norway (Yr)", "wttr.in  (3 days)"};
            int sel = java.util.Arrays.asList(keys).indexOf(Weather.source(this));
            dialog("Weather source").setSingleChoiceItems(names, Math.max(0, sel), (d, w) -> {
                Weather.prefs(this).edit().putString(Weather.K_SOURCE, keys[w]).apply();
                Weather.changed(this);
                refreshAll();
                d.dismiss();
                Toast.makeText(this, Lang.t("If it can't be reached, the other sources are used."), Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Update every", () -> Weather.refreshMinutes(this) + " minutes", () -> {
            int[] mins = Weather.REFRESH_CHOICES;
            String[] names = new String[mins.length];
            int sel = 1;
            for (int i = 0; i < mins.length; i++) {
                names[i] = mins[i] + " minutes" + (mins[i] == 15 ? "  (default)" : "");
                if (mins[i] == Weather.refreshMinutes(this)) sel = i;
            }
            dialog("Update weather every").setSingleChoiceItems(names, sel, (d, w) -> {
                Weather.prefs(this).edit().putInt(Weather.K_REFRESH, mins[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
    }

    void pageWatching() {
        row("Show Continue Watching", () -> Profiles.homePrefs(this).getBoolean("cw_enabled", true) ? "On" : "Off", () -> {
            boolean on = !Profiles.homePrefs(this).getBoolean("cw_enabled", true);
            Profiles.homePrefs(this).edit().putBoolean("cw_enabled", on).apply();
            if (!on) Toast.makeText(this, "Continue Watching titles won\u2019t appear in the carousel", Toast.LENGTH_SHORT).show();
        });
        row("Access", () -> MediaWatchService.granted(this) ? "Allowed"
                : Perms.listenerSupported(this) ? "Allow\u2026" : "Not on this TV", () -> Perms.requestListener(this));
        row("Clear history", () -> "", () -> {
            MediaWatchService.Store.clear(this);
            Toast.makeText(this, Lang.t("Continue Watching cleared"), Toast.LENGTH_SHORT).show();
        });
    }

    void pageHome() {
        row("Home screen", () -> Perms.isDefaultHome(this) ? "ProxFibre" : "Make ProxFibre the home screen\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Background", () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int i = java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k);
            return MainActivity.WALL_NAMES[Math.max(0, i)];
        }, () -> {
            String k = Profiles.homePrefs(this).getString("wallpaper", "aurora");
            int cur = Math.max(0, java.util.Arrays.asList(MainActivity.WALL_KEYS).indexOf(k));
            dialog("Background").setSingleChoiceItems(MainActivity.WALL_NAMES, cur, (d, w) -> {
                Profiles.homePrefs(this).edit().putString("wallpaper", MainActivity.WALL_KEYS[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        row("Navigation sounds", () -> Clicks.enabled(this) ? "On" : "Off",
                () -> Clicks.setEnabled(this, !Clicks.enabled(this)));
        row("Full-screen carousel when idle", () -> {
            int sec = Profiles.homePrefs(this).getInt("idle_sec", 20);
            return sec == 0 ? "Off" : "After " + sec + " s";
        }, () -> {
            int[] secs = {0, 10, 20, 30, 60};
            String[] names = {"Off", "After 10 seconds", "After 20 seconds  (default)", "After 30 seconds", "After 1 minute"};
            int cur = Profiles.homePrefs(this).getInt("idle_sec", 20), sel = 2;
            for (int i = 0; i < secs.length; i++) if (secs[i] == cur) sel = i;
            dialog("Full-screen carousel").setMessage(null).setSingleChoiceItems(names, sel, (d, w) -> {
                Profiles.homePrefs(this).edit().putInt("idle_sec", secs[w]).apply();
                refreshAll();
                d.dismiss();
            }).show();
        });
        section("Screensaver");
        row("Screensaver on Home", () -> screensaverAllowed() ? "System screensaver" : "ProxFibre carousel", () -> {
            String[] names = {"System screensaver  (" + dreamLabel() + ")", "ProxFibre full-screen carousel"};
            dialog("Screensaver on Home").setSingleChoiceItems(names, screensaverAllowed() ? 0 : 1, (d, w) -> {
                getSharedPreferences("screensaver", MODE_PRIVATE).edit().putBoolean("allow", w == 0).apply();
                refreshAll();
                d.dismiss();
                Toast.makeText(this, w == 0 ? "The TV's screensaver starts when Home is idle"
                        : "Home stays on and plays the carousel full screen instead", Toast.LENGTH_LONG).show();
            }).show();
        });
        row("Screensaver", () -> dreamLabel(), this::openDreamSettings);
        row("Start screensaver now", () -> "", () -> {
            Intent i = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Suggestions.start(this, i)) Toast.makeText(this, Lang.t("This TV can't start the screensaver from an app"), Toast.LENGTH_LONG).show();
        });
        section("Search");
        row("Movies & TV in search", () -> Search.titlesEnabled(this) ? "On" : "Off", () -> {
            Search.prefs(this).edit().putBoolean(Search.K_TITLES, !Search.titlesEnabled(this)).apply();
            refreshAll();
        });
        row("Recent searches", () -> Search.recent(this).isEmpty() ? "None" : "Clear", () -> {
            Search.clearRecent(this);
            refreshAll();
        });
        section("Bundled apps");
        row("Armadillo  (VPN)", () -> Bundled.status(this, Bundled.ARMADILLO_INDEX),
                () -> Bundled.installNow(this, Bundled.ARMADILLO_INDEX));
        row("AirPlay Server", () -> AirPlay.installed(this) ? AirPlay.status(this)
                        : Bundled.status(this, Bundled.AIRPLAY_INDEX),
                () -> {
                    if (!AirPlay.installed(this)) {
                        Bundled.installNow(this, Bundled.AIRPLAY_INDEX);
                        return;
                    }
                    if (AirPlay.state(this) == AirPlay.OFF) {
                        AirPlay.start(this);
                        Toast.makeText(this, Lang.s("Starting AirPlay\u2026", "Démarrage d'AirPlay\u2026",
                                "Iniciando AirPlay\u2026"), Toast.LENGTH_SHORT).show();
                        new android.os.Handler(getMainLooper()).postDelayed(() -> new Thread(() -> {
                            AirPlay.refresh(this);
                            runOnUiThread(this::refreshAll);
                        }).start(), 4000);
                        return;
                    }
                    Intent i = AirPlay.launch(this);
                    if (i != null) startActivity(i);
                });
        section("Setup");
        row("Run setup again", () -> "", () -> startActivity(new Intent(this, SetupActivity.class)));
        row("Reset app layout", () -> "", () -> {
            Profiles.homePrefs(this).edit().remove("order").apply();
            Toast.makeText(this, Lang.t("Layout reset"), Toast.LENGTH_SHORT).show();
        });
    }

    void pagePermissions() {
        row("Continue Watching & Do Not Disturb", () -> Perms.hasListener(this) ? "Allowed"
                : Perms.listenerSupported(this) ? "Allow\u2026" : "Not on this TV",
                () -> Perms.requestListener(this));
        row("Power Off & Sleep Timer", () -> Perms.hasAdmin(this) ? "Allowed"
                : Perms.adminSupported(this) ? "Allow\u2026" : "Not on this TV", () -> Perms.requestAdmin(this));
        row("ProxFibre as the home screen", () -> Perms.isDefaultHome(this) ? "Yes" : "Set\u2026",
                () -> Perms.requestDefaultHome(this));
        row("Wi-Fi name in Control Center", () -> Perms.hasLocation(this) ? "Allowed" : "Allow\u2026",
                () -> Perms.requestLocation(this));
    }

    void pageAbout() {
        row("ProxFibre", () -> "Version " + Update.current(this), () -> Update.checkNow(this));
        row("Update", () -> Update.status(this), () -> {
            if (Update.available(this) && !Update.apkUrl(this).isEmpty()) Update.install(this, Update.apkUrl(this));
            else Update.checkNow(this);
        });
        row("Based on", () -> "Hume Launcher by Petrobot", () -> { });
        row("Weather", () -> Weather.sourceName(Weather.source(this)), () -> { });
        row("News", () -> News.sources(this).size() + " French newsrooms", () -> { });
    }

    // ---------------------------------------------------------------- Apps

    /** Armadillo and AirPlay (fetched live from their own GitHub releases) plus apps added here. */
    void pageApps() {
        section("Built in");
        for (AppCatalog.Entry e : AppCatalog.all(this)) {
            if (e.custom) continue;
            row(e.name, () -> AppCatalog.status(this, e), () -> AppCatalog.install(this, e));
        }
        List<AppCatalog.Entry> customApps = AppCatalog.custom(this);
        if (!customApps.isEmpty()) {
            section("Your apps");
            for (AppCatalog.Entry e : customApps) {
                row(e.name, () -> AppCatalog.status(this, e), () -> dialog(e.name)
                        .setItems(new String[]{Lang.t("Install"), Lang.t("Remove")}, (d, w) -> {
                            if (w == 0) AppCatalog.install(this, e);
                            else {
                                AppCatalog.removeCustom(this, e.id);
                                show(APPS);
                            }
                        }).show());
            }
        }
        section("Add an app");
        row("Add from GitHub or a link", () -> "", this::addCustomApp);
    }

    /** Adds a custom app: a name, and either a GitHub "owner/repo" or a direct .apk link. */
    void addCustomApp() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(this, 18);
        box.setPadding(pad, Ui.px(this, 6), pad, 0);
        final android.widget.EditText name = field("Name, e.g. Stremio", "");
        final android.widget.EditText source = field("owner/repo or https://…/app.apk", "");
        box.addView(name);
        box.addView(source);
        dialog("Add an app")
                .setMessage(Lang.s("A GitHub repo (owner/repo) installs its latest release .apk; a direct link installs that file.",
                        "Un dépôt GitHub (owner/repo) installe sa dernière release .apk ; un lien direct installe ce fichier.",
                        "Un repositorio de GitHub (owner/repo) instala su última versión .apk; un enlace directo instala ese archivo."))
                .setView(box)
                .setPositiveButton(Lang.t("Add"), (d, w) -> {
                    String n = name.getText().toString().trim();
                    String src = source.getText().toString().trim();
                    if (n.isEmpty() || src.isEmpty()) return;
                    AppCatalog.addCustom(this, n, src);
                    show(APPS);
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
        name.requestFocus();
    }

    // ---------------------------------------------------------------- Jellyfin

    void pageJellyfin() {
        section("Library");
        row("Server", () -> Jellyfin.configured(this) ? Jellyfin.status(this) : "Not connected", this::connectJellyfin);
        row("Account", () -> Jellyfin.prefs(this).getString(Jellyfin.K_USER, ""), this::connectJellyfin);
        row("Show on Home", () -> Jellyfin.enabled(this) ? "On" : "Off", () -> {
            Jellyfin.setEnabled(this, !Jellyfin.enabled(this));
            refreshAll();
        });
        row("Top shelf shows", () -> {
            switch (Jellyfin.shelfMode(this)) {
                case Jellyfin.MODE_RESUME: return "Continue Watching";
                case Jellyfin.MODE_LATEST: return "Recently added";
                default: return "Continue Watching & Recently added";
            }
        }, () -> {
            String[] keys = {Jellyfin.MODE_BOTH, Jellyfin.MODE_RESUME, Jellyfin.MODE_LATEST};
            String[] names = {"Continue Watching & Recently added", "Continue Watching", "Recently added"};
            int sel = Math.max(0, java.util.Arrays.asList(keys).indexOf(Jellyfin.shelfMode(this)));
            dialog("Top shelf shows").setSingleChoiceItems(names, sel, (d, w) -> {
                Jellyfin.setShelfMode(this, keys[w]);
                d.dismiss();
                show(JELLYFIN);
            }).show();
        });
        row("Client app", () -> Jellyfin.clientName(this)
                + (Jellyfin.clientInstalled(this) ? "" : "  \u00b7  " + Lang.t("not installed")), () -> {
            String pkg = Jellyfin.clientPackage(this);
            if (!Jellyfin.clientInstalled(this)) {
                Suggestions.start(this, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)));
            } else {
                Toast.makeText(this, Lang.s("Cards open in " + Jellyfin.clientName(this), "Les cartes s'ouvrent dans "
                        + Jellyfin.clientName(this), "Las tarjetas se abren en " + Jellyfin.clientName(this)), Toast.LENGTH_SHORT).show();
            }
        });
        if (Jellyfin.configured(this)) {
            row("Disconnect", () -> "", () -> dialog("Disconnect Jellyfin?")
                    .setMessage(Lang.s("The server settings are forgotten; your library stays untouched.",
                            "Les réglages du serveur sont oubliés ; votre bibliothèque n'est pas modifiée.",
                            "Se olvidan los ajustes del servidor; tu biblioteca no se toca."))
                    .setPositiveButton(Lang.t("Disconnect"), (d, w) -> {
                        Jellyfin.disconnect(this);
                        refreshAll();
                    })
                    .setNegativeButton(Lang.t("Cancel"), null).show());
        }
    }

    /** Server, username and password on the TV itself; the token is kept, the password is not. */
    void connectJellyfin() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(this, 18);
        box.setPadding(pad, Ui.px(this, 6), pad, 0);
        final android.widget.EditText server = field("http://192.168.1.20:8096", Jellyfin.server(this));
        final android.widget.EditText user = field("Username", Jellyfin.prefs(this).getString(Jellyfin.K_USER, ""));
        final android.widget.EditText pass = field("Password", "");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(server);
        box.addView(user);
        box.addView(pass);
        dialog("Jellyfin server")
                .setMessage(Lang.s("The address of your Jellyfin or Emby server, and an account on it.",
                        "L'adresse de votre serveur Jellyfin ou Emby, et un compte dessus.",
                        "La dirección de tu servidor Jellyfin o Emby, y una cuenta en él."))
                .setView(box)
                .setPositiveButton(Lang.t("Connect"), (d, w) -> {
                    final String url = server.getText().toString();
                    final String name = user.getText().toString();
                    final String pw = pass.getText().toString();
                    Toast.makeText(this, Lang.s("Connecting…", "Connexion…", "Conectando…"), Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        final String err = Jellyfin.login(this, url, name, pw);
                        runOnUiThread(() -> {
                            Toast.makeText(this, err == null
                                            ? Lang.s("Jellyfin connected", "Jellyfin connecté", "Jellyfin conectado")
                                            : Lang.t(err),
                                    Toast.LENGTH_LONG).show();
                            refreshAll();
                            show(JELLYFIN);
                        });
                    }, "hume-jellyfin").start();
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
    }

    android.widget.EditText field(String hint, String value) {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint(Lang.t(hint));
        in.setText(value);
        in.setSelection(in.getText().length());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(this, 4);
        in.setLayoutParams(lp);
        return in;
    }

    boolean screensaverAllowed() {
        return getSharedPreferences("screensaver", MODE_PRIVATE).getBoolean("allow", true);
    }

    /** The TV's chosen screensaver (readable without permission), e.g. "Aerial Views". */
    String dreamLabel() {
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        if ("0".equals(Settings.Secure.getString(getContentResolver(), "screensaver_enabled"))) return "Off in TV settings";
        if (comp == null || comp.isEmpty()) return "TV default";
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn == null) return "TV default";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cn.getPackageName(), 0)).toString();
        } catch (Exception e) {
            return "TV default";
        }
    }

    /** Choose / set up the screensaver: the TV's screensaver settings, else the screensaver app itself. */
    void openDreamSettings() {
        if (Suggestions.start(this, new Intent("android.settings.DREAM_SETTINGS"))) return;
        String comp = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        android.content.ComponentName cn = comp == null ? null : android.content.ComponentName.unflattenFromString(comp.split(",")[0]);
        if (cn != null) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(cn.getPackageName());
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(cn.getPackageName());
            if (l != null && Suggestions.start(this, l)) return;
        }
        Toast.makeText(this, "Choose the screensaver in TV Settings \u2192 Device Preferences \u2192 Screen saver", Toast.LENGTH_LONG).show();
        Suggestions.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void section(String name) {
        TextView t = new Lang.Tv(this);
        t.setText(name);
        t.setAllCaps(true);
        t.setTextColor(0x8CFFFFFF);
        t.setTypeface(Ui.font(this, "medium"));
        t.setLetterSpacing(0.06f);
        Ui.textSize(t, 11.5f);
        t.setPadding(Ui.px(this, 14), Ui.px(this, 18), 0, Ui.px(this, 6));
        list.addView(t);
    }

    interface Value {
        String get();
    }

    /** A row that opens a section page: label, current value and a chevron. */
    void nav(String target, Value value) {
        android.view.View r = row(target, () -> Lang.t(value.get()) + "   \u203a", () -> show(target));
        navRows.put(target, r);
    }

    /** A settings row: label left, current value right; focus lifts it onto a light plate. */
    android.view.View row(String label, Value value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        int padH = Ui.px(this, 16);
        r.setPadding(padH, 0, padH, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.px(this, 10));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);

        TextView l = new Lang.Tv(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(l, 15);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new Lang.Tv(this);
        v.setTextColor(0x99FFFFFF);
        v.setTypeface(Ui.font(this, "regular"));
        Ui.textSize(v, 15);
        v.setSingleLine(true);
        r.addView(v);
        Runnable refresh = () -> v.setText(Lang.t(value.get()));
        refresh.run();
        refreshers.add(refresh);

        r.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            l.setTextColor(has ? 0xFF111114 : Color.WHITE);
            v.setTextColor(has ? 0x99111114 : 0x99FFFFFF);
            view.animate().withLayer().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(180).start();
        });
        r.setOnClickListener(view -> {
            action.run();
            for (Runnable x : refreshers) x.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(this, 40));
        lp.bottomMargin = Ui.px(this, 6);
        list.addView(r, lp);
        return r;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        MainActivity.navSound(this, ev);
        return super.dispatchKeyEvent(ev);
    }

    AlertDialog.Builder dialog(String title) {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(Lang.t(title));
    }

    void refreshAll() {
        for (Runnable x : refreshers) x.run();
    }

    void chooseSource() {
        String[] keys = {Suggestions.SRC_JUSTWATCH, Suggestions.SRC_TMDB, Suggestions.SRC_STREMIO};
        String[] names = {"JustWatch  (no setup needed)", "TMDB  (needs a free token)", "Stremio  (opens in Stremio or Nuvio)"};
        int cur = Math.max(0, java.util.Arrays.asList(keys).indexOf(Suggestions.source(this)));
        dialog("Recommendation source").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_SOURCE, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
            show(SHELF);
            if (w == 1 && !Suggestions.hasToken(this)) enterToken();
        }).show();
    }

    /** Token entry on the TV itself (no adb needed). Accepts the v4 read token or the short v3 API key. */
    void enterToken() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("v3 API key (32 characters) or read-access token");
        in.setText(Suggestions.prefs(this).getString(Suggestions.K_TOKEN, ""));
        in.setSelection(in.getText().length());
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("TMDB token")
                .setMessage("Get it free at themoviedb.org \u2192 Settings \u2192 API. The short \"API Key\" is easiest to type.")
                .setView(box)
                .setPositiveButton(Lang.t("Save"), (d, w) -> {
                    Suggestions.prefs(this).edit().putString(Suggestions.K_TOKEN, in.getText().toString().trim()).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNeutralButton("Clear", (d, w) -> {
                    Suggestions.prefs(this).edit().remove(Suggestions.K_TOKEN).apply();
                    Suggestions.changed(this);
                    refreshAll();
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
        in.requestFocus();
    }

    /** Automatic (from the internet address) or a city found by name. */
    void chooseLocation() {
        String[] names = {"Automatic", "Choose City\u2026"};
        dialog("Weather location").setSingleChoiceItems(names, Weather.manual(this) ? 1 : 0, (d, w) -> {
            d.dismiss();
            if (w == 0) {
                Weather.setPlace(this, null);
                refreshAll();
            } else {
                searchCity();
            }
        }).show();
    }

    void searchCity() {
        android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("City name, e.g. Bengaluru");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = Ui.px(this, 14);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        dialog("Choose City")
                .setView(box)
                .setPositiveButton(Lang.t("Search"), (d, w) -> {
                    String q = in.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new Thread(() -> {
                        List<Weather.Place> found;
                        try {
                            found = Weather.search(q);
                        } catch (Exception e) {
                            found = null;
                        }
                        List<Weather.Place> res = found;
                        runOnUiThread(() -> showCities(q, res));
                    }).start();
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
        in.requestFocus();
    }

    void showCities(String q, List<Weather.Place> res) {
        if (isFinishing()) return;
        if (res == null) {
            Toast.makeText(this, Lang.t("Couldn't search. Check the internet connection."), Toast.LENGTH_LONG).show();
            return;
        }
        if (res.isEmpty()) {
            Toast.makeText(this, "No city called \u201c" + q + "\u201d", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[res.size()];
        for (int i = 0; i < names.length; i++) {
            Weather.Place pl = res.get(i);
            names[i] = pl.detail.isEmpty() ? pl.name : pl.name + "  \u00b7  " + pl.detail;
        }
        dialog("Choose City").setItems(names, (d, w) -> {
            Weather.setPlace(this, res.get(w));
            refreshAll();
        }).setNegativeButton(Lang.t("Cancel"), null).show();
    }

    /** Address, QR and pairing code for sending content from a phone or computer. */
    void showPresentInfo() {
        if (!com.proxfibre.launcher.present.Presenter.enabled(this)) {
            Toast.makeText(this, Lang.t("Turn on Presentation mode first"), Toast.LENGTH_SHORT).show();
            return;
        }
        com.proxfibre.launcher.present.Presenter.ensureRunning(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.px(this, 16);
        box.setPadding(pad, pad, pad, pad / 2);
        android.widget.ImageView qr = new android.widget.ImageView(this);
        qr.setImageBitmap(com.proxfibre.launcher.present.Presenter.qr(Ui.px(this, 110)));
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6), Ui.px(this, 6));
        box.addView(qr, new LinearLayout.LayoutParams(Ui.px(this, 122), Ui.px(this, 122)));
        TextView info = new Lang.Tv(this);
        String pin = com.proxfibre.launcher.present.Presenter.pin(this);
        info.setText(Lang.s("Open on a phone or computer on the same Wi-Fi:",
                        "Ouvrez sur un téléphone ou un ordinateur sur le même Wi-Fi :",
                        "Abre en un móvil u ordenador en el mismo Wi-Fi:") + "\n\n"
                + com.proxfibre.launcher.present.Presenter.address() + "\n\n"
                + Lang.s("Pairing code", "Code d'appairage", "Código de emparejamiento") + "\n"
                + pin.substring(0, 3) + " " + pin.substring(3));
        info.setTextColor(Color.WHITE);
        info.setTypeface(Ui.font(this, "medium"));
        Ui.textSize(info, 14);
        info.setPadding(Ui.px(this, 18), 0, 0, 0);
        box.addView(info);
        dialog("Send to this TV").setView(box).setPositiveButton(Lang.t("Done"), null).show();
    }

    void chooseTypes() {
        String[] keys = {"both", "movie", "tv"};
        String[] names = {"Movies & Series", "Movies", "TV Series"};
        int cur = java.util.Arrays.asList(keys).indexOf(Suggestions.types(this));
        dialog("Content").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putString(Suggestions.K_TYPES, keys[w]).apply();
            Suggestions.changed(this);
            refreshAll();
            d.dismiss();
        }).show();
    }

    void chooseProviders() {
        int n = Suggestions.PROVIDERS.size();
        String[] names = new String[n];
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.providers(this);
        for (int i = 0; i < n; i++) {
            Suggestions.Provider p = Suggestions.PROVIDERS.get(i);
            names[i] = p.name + (Suggestions.installed(this, p.pkg) ? "" : "  (not installed)");
            on[i] = cur.contains(String.valueOf(p.id));
        }
        dialog("Streaming services").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton(Lang.t("Done"), (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(String.valueOf(Suggestions.PROVIDERS.get(i).id));
                    if (sel.isEmpty()) {
                        Toast.makeText(this, Lang.t("Keep at least one service"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_PROVIDERS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton(Lang.t("Cancel"), null).show();
    }

    void chooseLanguages() {
        int n = Suggestions.LANG_CODES.length;
        boolean[] on = new boolean[n];
        Set<String> cur = Suggestions.languages(this);
        for (int i = 0; i < n; i++) on[i] = cur.contains(Suggestions.LANG_CODES[i]);
        dialog(Suggestions.source(this).equals(Suggestions.SRC_TMDB) ? "Languages (none = any)" : "Languages (TMDB source only)")
                .setMultiChoiceItems(Suggestions.LANG_NAMES, on, (d, w, c) -> on[w] = c)
                .setPositiveButton(Lang.t("Done"), (d, w) -> {
                    Set<String> sel = new java.util.HashSet<>();
                    for (int i = 0; i < n; i++) if (on[i]) sel.add(Suggestions.LANG_CODES[i]);
                    Suggestions.prefs(this).edit().putStringSet(Suggestions.K_LANGS, sel).apply();
                    Suggestions.changed(this);
                    refreshAll();
                }).setNegativeButton(Lang.t("Cancel"), null).show();
    }

    void chooseAdvance() {
        int[] secs = {0, 6, 8, 12};
        String[] names = {"Off", "Every 6 s", "Every 8 s", "Every 12 s"};
        int cur = 0;
        for (int i = 0; i < secs.length; i++) if (secs[i] == Suggestions.advanceSeconds(this)) cur = i;
        dialog("Auto-advance").setSingleChoiceItems(names, cur, (d, w) -> {
            Suggestions.prefs(this).edit().putInt(Suggestions.K_ADVANCE, secs[w]).apply();
            refreshAll();
            d.dismiss();
        }).show();
    }
}
