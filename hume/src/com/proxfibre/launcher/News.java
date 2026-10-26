package com.proxfibre.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * News for the top shelf from French newsrooms' RSS feeds, which carry a large image per story
 * (free, no key): France and World stories mixed together. Each story becomes a carousel item
 * \u2014 headline, summary, image, section, source and age (no button: stories are read on the shelf).
 */
final class News {
    static final String K_SOURCES = "news_sources";
    static final String FRANCE = "france", WORLD = "world";
    static final String[] SECTIONS = {FRANCE, WORLD};

    /** A newsroom and its feed for each section (null = none). */
    static final class Source {
        final String id, name, france, world;

        Source(String id, String name, String france, String world) {
            this.id = id;
            this.name = name;
            this.france = france;
            this.world = world;
        }
    }

    static final List<Source> SOURCES = new ArrayList<>();

    static {
        SOURCES.add(new Source("francetvinfo", "France Info",
                "https://www.francetvinfo.fr/france.rss",
                "https://www.francetvinfo.fr/monde.rss"));
        SOURCES.add(new Source("lemonde", "Le Monde",
                "https://www.lemonde.fr/rss/une.xml",
                "https://www.lemonde.fr/international/rss_full.xml"));
        SOURCES.add(new Source("lefigaro", "Le Figaro",
                "https://www.lefigaro.fr/rss/figaro_actualites.xml",
                "https://www.lefigaro.fr/rss/figaro_international.xml"));
        SOURCES.add(new Source("france24", "France 24",
                "https://www.france24.com/fr/rss",
                "https://www.france24.com/fr/rss"));
        SOURCES.add(new Source("bfmtv", "BFMTV",
                "https://www.bfmtv.com/rss/news-24-7/",
                "https://www.bfmtv.com/rss/news-24-7/"));
        SOURCES.add(new Source("20minutes", "20 Minutes",
                "https://www.20minutes.fr/feeds/rss-une.xml",
                "https://www.20minutes.fr/feeds/rss-une.xml"));
    }

    private News() {
    }

    static final String K_REFRESH = "news_refresh_min";
    static final int[] REFRESH_CHOICES = {15, 30, 60};

    /** Minutes between news updates (30 by default). */
    static int refreshMinutes(Context c) {
        return Suggestions.prefs(c).getInt(K_REFRESH, 30);
    }

    static Set<String> sources(Context c) {
        Set<String> def = new HashSet<>(java.util.Arrays.asList("francetvinfo", "lemonde", "lefigaro", "france24", "bfmtv"));
        return new HashSet<>(Suggestions.prefs(c).getStringSet(K_SOURCES, def));
    }

    static void setSources(Context c, Set<String> ids) {
        SharedPreferences p = Suggestions.prefs(c);
        p.edit().putStringSet(K_SOURCES, ids).apply();
    }

    /** Latest stories: France and the world mixed together, alternating between newsrooms. Blocking. */
    static List<Suggestions.Item> fetch(Context c, int max) {
        List<List<Suggestions.Item>> lists = new ArrayList<>();
        Set<String> chosen = sources(c);
        for (String sec : SECTIONS) {
            for (Source s : SOURCES) {
                if (!chosen.contains(s.id)) continue;
                String url = sec.equals(WORLD) ? s.world : s.france;
                if (url == null) continue;
                String label = sec.equals(WORLD) ? "World" : "France";
                try {
                    List<Suggestions.Item> l = read(url, label + "  \u00b7  " + s.name, 2 * 86400000L);
                    if (!l.isEmpty()) lists.add(l);
                } catch (Exception e) {
                    // skip a feed that can't be reached
                    android.util.Log.w("TvHome", "news feed " + url + ": " + e);
                }
            }
        }
        List<Suggestions.Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int round = 0; out.size() < max; round++) {
            boolean any = false;
            for (List<Suggestions.Item> l : lists) {
                if (round < l.size()) {
                    any = true;
                    Suggestions.Item it = l.get(round);
                    if (out.size() < max && seen.add(it.title.toLowerCase(Locale.ROOT))) out.add(it);
                }
            }
            if (!any) break;
        }
        return out;
    }

    private static List<Suggestions.Item> read(String url, String label, long maxAge) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) ProxFibreLauncher");
        List<Suggestions.Item> out = new ArrayList<>();
        try (InputStream in = con.getInputStream()) {
            XmlPullParser x = XmlPullParserFactory.newInstance().newPullParser();
            x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            x.setInput(in, null);
            Suggestions.Item cur = null;
            String tag = null, rawDesc = "";
            int seenItems = 0;
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && out.size() < 30 && seenItems < 80; ev = x.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = x.getName();
                    if (tag.equals("item")) {
                        seenItems++;
                        cur = new Suggestions.Item();
                        cur.type = "news";
                        cur.title = cur.overview = cur.backdrop = cur.year = "";
                        cur.genres = label;
                        cur.logo = "";
                        rawDesc = "";
                    } else if (cur != null && (tag.equals("media:content") || tag.equals("media:thumbnail") || tag.equals("enclosure"))) {
                        String u = x.getAttributeValue(null, "url");
                        String type = x.getAttributeValue(null, "type");
                        if (u != null && (type == null || type.startsWith("image")) && cur.backdrop.isEmpty()) cur.backdrop = bigger(u);
                    }
                } else if (ev == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = x.getText();
                    switch (tag) {
                        case "title": cur.title += t; break;
                        case "link": cur.link += t.trim(); break;
                        case "description": cur.overview += t; rawDesc += t; break;
                        case "pubDate": cur.published = parseDate(t.trim()); break;
                        default: break;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (x.getName().equals("item") && cur != null) {
                        cur.title = clean(cur.title);
                        cur.overview = clean(cur.overview);
                        if (cur.backdrop.isEmpty()) {  // some feeds put the image in the summary
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]+src=\"([^\"]+)\"").matcher(rawDesc);
                            if (m.find()) cur.backdrop = bigger(m.group(1).replace("&amp;", "&"));
                        }
                        boolean fresh = cur.published == 0 || System.currentTimeMillis() - cur.published < maxAge;
                        if (!cur.title.isEmpty() && !cur.backdrop.isEmpty() && fresh) out.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } finally {
            con.disconnect();
        }
        out.sort((a, b) -> Long.compare(b.published, a.published));  // newest first
        return out.size() > 8 ? new ArrayList<>(out.subList(0, 8)) : out;
    }

    /** Ask for a larger rendition where the newsroom's image URLs allow it. */
    private static String bigger(String u) {
        return u.replaceAll("/LANDSCAPE_\\d+/", "/LANDSCAPE_1200/");
    }

    /** Strip tags and entities; keep one short paragraph. */
    static String clean(String s) {
        String t = s.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
        return t.length() > 260 ? t.substring(0, 257).trim() + "\u2026" : t;
    }

    /** RSS dates: RFC 822 ("Thu, 22 Oct 2026 13:33:54 +0200") or ISO 8601. */
    static long parseDate(String pub) {
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"};
        for (String f : fmts) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(pub).getTime();
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return 0;
    }

    /** "Just now", "5 min ago", "3 h ago", "Yesterday" \u2014 worked out when shown, so it never goes stale. */
    static String age(long published) {
        if (published <= 0) return "";
        long min = (System.currentTimeMillis() - published) / 60000;
        if (min < 1) return "Just now";
        if (min < 60) return min + " min ago";
        if (min < 24 * 60) return (min / 60) + " h ago";
        return min < 48 * 60 ? "Yesterday" : (min / 1440) + " days ago";
    }
}
