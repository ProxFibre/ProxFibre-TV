package com.proxfibre.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three languages, French by default: the launcher's own text goes through here, so a TextView
 * shows the right words wherever it was filled in. The APK ships no translated resources; the
 * table below is the whole dictionary, and pattern rules cover the strings built at runtime
 * ("After 20 s", "3 to allow", "Closed 2 apps").
 *
 * Setup and Settings both offer the switch; changing it recreates the screen.
 */
public final class Lang {
    static final int FR = 0, EN = 1, ES = 2;
    static final String[] NAMES = {"Français", "English", "Español"};

    private static final String PREF = "lang";
    private static int current = -1;
    private static Map<String, String[]> map;

    private Lang() {
    }

    static int get(Context c) {
        if (current < 0) {
            SharedPreferences p = c.getSharedPreferences("proxfibre", Context.MODE_PRIVATE);
            current = p.getInt(PREF, FR);
            if (current < 0 || current > 2) current = FR;
        }
        return current;
    }

    static void set(Context c, int lang) {
        current = lang;
        c.getSharedPreferences("proxfibre", Context.MODE_PRIVATE).edit().putInt(PREF, lang).apply();
        Locale.setDefault(locale(lang));
    }

    static Locale locale(int lang) {
        return lang == ES ? new Locale("es", "ES") : lang == EN ? Locale.ENGLISH : Locale.FRENCH;
    }

    static Locale locale(Context c) {
        return locale(get(c));
    }

    static String name(int lang) {
        return NAMES[lang];
    }

    /** Called at the top of each activity: remembers the language and sets number/date formats. */
    public static void init(Context c) {
        Locale.setDefault(locale(get(c)));
    }

    static String t(String s) {
        if (s == null || s.isEmpty()) return s;
        int lang = current < 0 ? FR : current;
        if (lang == EN) return s;
        String[] tr = dict().get(s);
        if (tr != null) return tr[lang == ES ? 2 : 1];
        String p = pattern(s, lang);
        return p != null ? p : s;
    }

    static String t(String s, int lang) {
        if (lang == EN) return s;
        String[] tr = dict().get(s);
        return tr != null ? tr[lang == ES ? 2 : 1] : s;
    }

    /** "Comedy, Drama" from TMDB, translated genre by genre. */
    static String genres(String s) {
        if (s == null || s.isEmpty()) return s;
        int lang = current < 0 ? FR : current;
        if (lang == EN) return s;
        String sep = s.contains(" \u00b7 ") ? " \u00b7 " : ", ";
        String[] parts = s.split(sep);
        for (int i = 0; i < parts.length; i++) parts[i] = t(parts[i].trim());
        return String.join(sep, parts);
    }

    /** Inline choice for sentences assembled around a name: Lang.s(english, français, español). */
    static String s(String en, String fr, String es) {
        int lang = current < 0 ? FR : current;
        return lang == EN ? en : lang == FR ? fr : es;
    }

    // ---------------------------------------------------------------- visual text

    /** TextView that translates whatever it is given, wherever the text came from. */
    public static class Tv extends TextView {
        public Tv(Context c) {
            super(c);
        }

        public Tv(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text == null ? null : Lang.t(text.toString()), type);
        }
    }

    static class TButton extends Button {
        public TButton(Context c) {
            super(c);
        }

        public TButton(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text == null ? null : Lang.t(text.toString()), type);
        }
    }

    static class TChecked extends CheckedTextView {
        public TChecked(Context c) {
            super(c);
        }

        public TChecked(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text == null ? null : Lang.t(text.toString()), type);
        }
    }

    static class TRadio extends RadioButton {
        public TRadio(Context c) {
            super(c);
        }

        public TRadio(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text == null ? null : Lang.t(text.toString()), type);
        }
    }

    /**
     * Dialogs inflate their own title, message, buttons and list rows through the activity's
     * LayoutInflater: substituting our TextViews there translates them too.
     */
    public static void views(Activity a) {
        LayoutInflater li = a.getLayoutInflater();
        if (li.getFactory2() != null) return;
        li.setFactory2(new LayoutInflater.Factory2() {
            @Override
            public View onCreateView(View parent, String name, Context c, AttributeSet attrs) {
                switch (name) {
                    case "TextView":
                    case "android.widget.TextView":
                        return new Tv(c, attrs);
                    case "Button":
                    case "android.widget.Button":
                        return new TButton(c, attrs);
                    case "CheckedTextView":
                    case "android.widget.CheckedTextView":
                        return new TChecked(c, attrs);
                    case "RadioButton":
                    case "android.widget.RadioButton":
                        return new TRadio(c, attrs);
                    default:
                        return null;
                }
            }

            @Override
            public View onCreateView(String name, Context c, AttributeSet attrs) {
                return onCreateView(null, name, c, attrs);
            }
        });
    }

    // ---------------------------------------------------------------- runtime strings

    private static Matcher m(String re, String s) {
        Matcher x = Pattern.compile(re).matcher(s);
        return x.matches() ? x : null;
    }

    private static boolean isNum(String t) {
        if (t == null || t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** Number at the start of s when it ends with suffix, else null. */
    private static String lead(String s, String suffix) {
        if (!s.endsWith(suffix)) return null;
        String n = s.substring(0, s.length() - suffix.length());
        return isNum(n) ? n : null;
    }

    private static String tail(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : null;
    }

    private static String pattern(String s, int lang) {
        String n;
        if ((n = lead(s, " minutes")) != null)
            return lang == FR ? n + " minutes" : n + " minutos";
        if ((n = lead(s, " selected")) != null)
            return lang == FR ? n + " sélectionnées" : n + " seleccionadas";
        if ((n = lead(s, " French newsrooms")) != null)
            return lang == FR ? n + " rédactions françaises" : n + " redacciones francesas";
        if ((n = lead(s, " available")) != null)
            return n + " disponible";
        if ((n = lead(s, " fonds")) != null)
            return n + (lang == FR ? " fonds" : " fondos");
        if ((n = lead(s, " min ago")) != null)
            return lang == FR ? "il y a " + n + " min" : "hace " + n + " min";
        if ((n = lead(s, " h ago")) != null)
            return lang == FR ? "il y a " + n + " h" : "hace " + n + " h";
        if ((n = lead(s, " days ago")) != null)
            return lang == FR ? "il y a " + n + " jours" : "hace " + n + " días";
        if ((n = lead(s, " to allow")) != null)
            return lang == FR ? n + " à autoriser" : n + " por autorizar";
        if ((n = lead(s, " connected")) != null)
            return lang == FR ? n + " connecté" : n + " conectado";
        if ((n = lead(s, " min left")) != null)
            return n + " min restantes";
        if ((n = lead(s, "% used  ·  Clean")) != null)
            return lang == FR ? n + " % utilisé  ·  Nettoyer" : n + " % usado  ·  Limpiar";
        if ((n = tail(s, "After ")) != null && n.endsWith(" s") && isNum(n.substring(0, n.length() - 2)))
            return (lang == FR ? "Après " + n : "Después de " + n);
        if ((n = tail(s, "After ")) != null && n.endsWith(" seconds") && isNum(n.substring(0, n.length() - 8)))
            return (lang == FR ? "Après " + n : "Después de " + n);
        if ((n = tail(s, "Every ")) != null && n.endsWith(" s") && isNum(n.substring(0, n.length() - 2)))
            return (lang == FR ? "Toutes les " : "Cada ") + n;
        if ((n = tail(s, "Version ")) != null)
            return (lang == FR ? "Version " : "Versión ") + n;
        if ((n = tail(s, "Updated ")) != null)
            return (lang == FR ? "Mis à jour " : "Actualizado ") + n;
        if ((n = tail(s, "Closed ")) != null && n.contains(" app") && (n.endsWith(" app") || n.endsWith(" apps"))) {
            String count = n.substring(0, n.indexOf(' '));
            if (isNum(count))
                return lang == FR ? count + " applis fermées" : count + " apps cerradas";
        }
        if ((n = tail(s, "Closed ")) != null && n.contains(" apps · freed ")) {
            int at = n.indexOf(" apps · freed ");
            String count = n.substring(0, at);
            String rest = n.substring(at + 14);
            String mb = rest.endsWith(" MB") ? rest.substring(0, rest.length() - 3) : rest;
            if (isNum(count))
                return lang == FR ? count + " applis fermées · " + mb + " Mo libérés"
                        : count + " apps cerradas · " + mb + " MB liberados";
        }
        if (s.startsWith("System screensaver  ("))
            return (lang == FR ? "Économiseur système  (" : "Salvapantallas del sistema  (") + s.substring(21);
        if (s.startsWith("No city called “") || s.startsWith("No city called \u201C"))
            return (lang == FR ? "Aucune ville nommée « " : "No hay ciudad llamada « ") + s.substring(16);
        if ((n = tail(s, "Can't switch to ")) != null)
            return (lang == FR ? "Impossible de passer à " : "No se puede cambiar a ") + n;
        if ((n = tail(s, "Can't open ")) != null)
            return (lang == FR ? "Impossible d'ouvrir " : "No se puede abrir ") + n;
        if ((n = tail(s, "Automatic · ")) != null)
            return (lang == FR ? "Automatique · " : "Automático · ") + n;
        if ((n = tail(s, "Looks like ")) != null)
            return (lang == FR ? "On dirait " : "Parece ") + n;
        if ((n = tail(s, "Preparing ")) != null && n.endsWith("…"))
            return (lang == FR ? "Préparation de " : "Preparando ") + n;
        if (s.endsWith(" could not be prepared"))
            return s.substring(0, s.length() - 22)
                    + (lang == FR ? " n'a pas pu être préparé" : " no se pudo preparar");
        if ((n = tail(s, "Removed ")) != null)
            return (lang == FR ? "Retiré : " : "Eliminado: ") + n;
        if (s.endsWith(" moved to More Apps"))
            return s.substring(0, s.length() - " moved to More Apps".length())
                    + (lang == FR ? " déplacé vers Plus d'applis" : " movido a Más apps");
        if (s.endsWith(" is back on Home"))
            return s.substring(0, s.length() - " is back on Home".length())
                    + (lang == FR ? " est de retour sur l'accueil" : " vuelve al inicio");
        if ((n = tail(s, "Open in ")) != null)
            return (lang == FR ? "Ouvrir dans " : "Abrir en ") + n;
        return null;
    }

    // ---------------------------------------------------------------- dictionary

    private static Map<String, String[]> dict() {
        if (map == null) {
            map = new HashMap<>();
            add(DATA);
            add(DATA2);
        }
        return map;
    }

    private static void add(String data) {
        for (String line : data.split("\n")) {
            if (line.isEmpty()) continue;
            String[] c = line.split("\u0001", -1);
            if (c.length == 3) map.put(c[0], new String[]{c[0], c[1], c[2]});
        }
    }

    /**
     * Every row is English\u0001Français\u0001Español. English itself is returned as typed, so only
     * the two other columns are ever read.
     */
    private static final String DATA =
            // --- general
            "On\u0001Activé\u0001Activado\n" +
            "Off\u0001Désactivé\u0001Desactivado\n" +
            "Cancel\u0001Annuler\u0001Cancelar\n" +
            "Done\u0001Terminé\u0001Listo\n" +
            "Save\u0001Enregistrer\u0001Guardar\n" +
            "Delete\u0001Supprimer\u0001Eliminar\n" +
            "None\u0001Aucun\u0001Ninguno\n" +
            "Clear\u0001Effacer\u0001Borrar\n" +
            "All\u0001Tout\u0001Todo\n" +
            "Any\u0001Toutes\u0001Todas\n" +
            "Both\u0001Les deux\u0001Ambos\n" +
            "Automatic\u0001Automatique\u0001Automático\n" +
            "Unknown\u0001Inconnu\u0001Desconocido\n" +
            "Connected\u0001Connecté\u0001Conectado\n" +
            "Not connected\u0001Non connecté\u0001No conectado\n" +
            "Not Connected\u0001Non connecté\u0001No conectado\n" +
            "Allowed\u0001Autorisé\u0001Permitido\n" +
            "Allow…\u0001Autoriser…\u0001Permitir…\n" +
            "Set…\u0001Définir…\u0001Definir…\n" +
            "Yes\u0001Oui\u0001Sí\n" +
            "Get\u0001Obtenir\u0001Obtener\n" +
            "Open\u0001Ouvrir\u0001Abrir\n" +
            "Resume\u0001Reprendre\u0001Reanudar\n" +
            "More Apps\u0001Plus d'applis\u0001Más apps\n" +
            "Suggestions\u0001Suggestions\u0001Sugerencias\n" +
            "Continue Watching\u0001Reprendre\u0001Seguir viendo\n" +
            "Top Shelf\u0001À la une\u0001Destacados\n" +
            "Recommendations\u0001Recommandations\u0001Recomendaciones\n" +
            "News\u0001Actualités\u0001Noticias\n" +
            "Movie\u0001Film\u0001Película\n" +
            "Series\u0001Série\u0001Serie\n" +
            "Action\u0001Action\u0001Acción\n" +
            "Adventure\u0001Aventure\u0001Aventura\n" +
            "Animation\u0001Animation\u0001Animación\n" +
            "Comedy\u0001Comédie\u0001Comedia\n" +
            "Crime\u0001Crime\u0001Crimen\n" +
            "Documentary\u0001Documentaire\u0001Documental\n" +
            "Drama\u0001Drame\u0001Drama\n" +
            "Family\u0001Famille\u0001Familia\n" +
            "Fantasy\u0001Fantastique\u0001Fantasía\n" +
            "History\u0001Histoire\u0001Historia\n" +
            "Horror\u0001Horreur\u0001Terror\n" +
            "Music\u0001Musique\u0001Música\n" +
            "Mystery\u0001Mystère\u0001Misterio\n" +
            "Romance\u0001Romance\u0001Romance\n" +
            "Science Fiction\u0001Science-fiction\u0001Ciencia ficción\n" +
            "TV Movie\u0001Téléfilm\u0001Película de TV\n" +
            "Thriller\u0001Thriller\u0001Suspense\n" +
            "War\u0001Guerre\u0001Guerra\n" +
            "Western\u0001Western\u0001Western\n" +
            "Nothing\u0001Rien\u0001Nada\n" +
            "Inputs\u0001Entrées\u0001Entradas\n" +
            "Presentation\u0001Présentation\u0001Presentación\n" +
            "Settings\u0001Réglages\u0001Ajustes\n" +
            "Search\u0001Rechercher\u0001Buscar\n" +
            "Home\u0001Accueil\u0001Inicio\n" +
            "Weather\u0001Météo\u0001Tiempo\n" +
            "Profiles\u0001Profils\u0001Perfiles\n" +
            "About\u0001À propos\u0001Acerca de\n" +
            "Permissions\u0001Autorisations\u0001Permisos\n" +
            "Setup\u0001Configuration\u0001Configuración\n" +
            "Turn on first\u0001À activer d'abord\u0001Activa primero\n" +
            "TV Settings\u0001Réglages de la TV\u0001Ajustes de la TV\n" +
            "This TV\u0001Cette TV\u0001Este TV\n" +
            "Background\u0001Fond d'écran\u0001Fondo\n" +
            "Access\u0001Accès\u0001Acceso\n" +
            "Content\u0001Contenu\u0001Contenido\n" +
            "Source\u0001Source\u0001Fuente\n" +
            "Location\u0001Lieu\u0001Ubicación\n" +
            "Temperature\u0001Température\u0001Temperatura\n" +
            "Languages\u0001Langues\u0001Idiomas\n" +
            "Language\u0001Langue\u0001Idioma\n" +
            "Newsrooms\u0001Rédactions\u0001Redacciones\n" +
            "Needs permission\u0001Autorisation requise\u0001Permiso necesario\n" +
            "All allowed\u0001Tout est autorisé\u0001Todo permitido\n" +
            "Bundled apps\u0001Applis incluses\u0001Apps incluidas\n" +
            "AirPlay Server\u0001AirPlay Server\u0001AirPlay Server\n" +
            "Armadillo  (VPN)\u0001Armadillo  (VPN)\u0001Armadillo  (VPN)\n" +
            "Run setup again\u0001Relancer la configuration\u0001Repetir la configuración\n" +
            "Reset app layout\u0001Réinitialiser la disposition\u0001Restablecer la disposición\n" +
            "Layout reset\u0001Disposition réinitialisée\u0001Disposición restablecida\n" +
            "Reset Layout\u0001Réinitialiser la disposition\u0001Restablecer la disposición\n" +
            "Navigation sounds\u0001Sons de navigation\u0001Sonidos de navegación\n" +
            "Refresh now\u0001Actualiser maintenant\u0001Actualizar ahora\n" +
            "Clear history\u0001Effacer l'historique\u0001Borrar historial\n" +
            "Current profile\u0001Profil actuel\u0001Perfil actual\n" +
            "Show profiles on wake\u0001Profils à l'allumage\u0001Perfiles al encender\n" +
            "TV default\u0001Par défaut de la TV\u0001Predeterminado del TV\n" +
            "System screensaver\u0001Économiseur d'écran système\u0001Salvapantallas del sistema\n" +
            "ProxFibre carousel\u0001Carrousel ProxFibre\u0001Carrusel ProxFibre\n" +
            "ProxFibre full-screen carousel\u0001Carrousel ProxFibre plein écran\u0001Carrusel ProxFibre a pantalla completa\n" +
            "Home screen\u0001Écran d'accueil\u0001Pantalla de inicio\n" +
            // --- main activity
            "Make ProxFibre your home screen?\u0001Définir ProxFibre comme écran d'accueil ?\u0001¿Usar ProxFibre como pantalla de inicio?\n" +
            "The Home button on the remote will open ProxFibre.\u0001Le bouton Accueil de la télécommande ouvrira ProxFibre.\u0001El botón Inicio del mando abrirá ProxFibre.\n" +
            "Make Home Screen\u0001Définir l'écran d'accueil\u0001Usar como inicio\n" +
            "Not Now\u0001Plus tard\u0001Ahora no\n" +
            "Back to return   ·   Hold OK for options\u0001Retour pour revenir   ·   OK maintenu : options\u0001Atrás para volver   ·   Mantén OK para opciones\n" +
            "✕  Remove\u0001✕  Retirer\u0001✕  Quitar\n" +
            "▶  Resume\u0001▶  Reprendre\u0001▶  Reanudar\n" +
            "▶  Open\u0001▶  Ouvrir\u0001▶  Abrir\n" +
            "Who’s Watching?\u0001Qui regarde ?\u0001¿Quién está viendo?\n" +
            "Switch Profile\u0001Changer de profil\u0001Cambiar de perfil\n" +
            "New Profile\u0001Nouveau profil\u0001Nuevo perfil\n" +
            "Rename Profile\u0001Renommer le profil\u0001Renombrar perfil\n" +
            "Rename\u0001Renommer\u0001Renombrar\n" +
            "Add User\u0001Ajouter un utilisateur\u0001Añadir usuario\n" +
            "App Info\u0001Infos de l'appli\u0001Info de la app\n" +
            "Uninstall\u0001Désinstaller\u0001Desinstalar\n" +
            "Show on Home Screen\u0001Afficher sur l'accueil\u0001Mostrar en inicio\n" +
            "Move\u0001Déplacer\u0001Mover\n" +
            "Hide\u0001Masquer\u0001Ocultar\n" +
            "Its layout and Continue Watching list will be removed.\u0001Sa disposition et sa liste Reprendre seront supprimées.\u0001Se borrarán su diseño y su lista Seguir viendo.\n" +
            "Change Emoji\u0001Changer l'emoji\u0001Cambiar emoji\n" +
            "Change Colour\u0001Changer la couleur\u0001Cambiar color\n" +
            "Emoji\u0001Emoji\u0001Emoji\n" +
            "Colour\u0001Couleur\u0001Color\n" +
            "Grin\u0001Sourire\u0001Sonrisa\n" +
            "Cool\u0001Cool\u0001Guay\n" +
            "Cowboy\u0001Cowboy\u0001Vaquero\n" +
            "Party\u0001Fête\u0001Fiesta\n" +
            "Fox\u0001Renard\u0001Zorro\n" +
            "Panda\u0001Panda\u0001Panda\n" +
            "Tiger\u0001Tigre\u0001Tigre\n" +
            "Lion\u0001Lion\u0001León\n" +
            "Frog\u0001Grenouille\u0001Rana\n" +
            "Sky\u0001Ciel\u0001Cielo\n" +
            "Pink\u0001Rose\u0001Rosa\n" +
            "Mint\u0001Menthe\u0001Menta\n" +
            "Lemon\u0001Citron\u0001Limón\n" +
            "Lavender\u0001Lavande\u0001Lavanda\n" +
            "Peach\u0001Pêche\u0001Melocotón\n" +
            "Silver\u0001Argent\u0001Plata\n" +
            "Aurora\u0001Aurore\u0001Aurora\n" +
            "Midnight\u0001Minuit\u0001Medianoche\n" +
            "Sunset\u0001Crépuscule\u0001Atardecer\n" +
            "Ocean\u0001Océan\u0001Océano\n" +
            "Graphite\u0001Graphite\u0001Grafito\n" +
            "No apps to close\u0001Aucune appli à fermer\u0001No hay apps que cerrar\n" +
            "Refreshing\u0001Actualisation…\u0001Actualizando…\n" +
            "Offline\u0001Hors ligne\u0001Sin conexión\n" +
            "Online\u0001En ligne\u0001En línea\n" +
            "No internet\u0001Pas d'internet\u0001Sin internet\n" +
            "Power Off\u0001Éteindre\u0001Apagar\n" +
            "Memory\u0001Mémoire\u0001Memoria\n" +
            "Wi-Fi\u0001Wi-Fi\u0001Wi-Fi\n" +
            "Do Not Disturb\u0001Ne pas déranger\u0001No molestar\n" +
            "Audio\u0001Audio\u0001Audio\n" +
            "Sleep Timer\u0001Minuteur\u0001Temporizador\n" +
            "TV Speakers\u0001Haut-parleurs de la TV\u0001Altavoces del TV\n" +
            "This TV has no inputs\u0001Cette TV n'a aucune entrée\u0001Este TV no tiene entradas\n" +
            "Not available on this TV\u0001Indisponible sur cette TV\u0001No disponible en este TV\n" +
            "Continue Watching isn't available on this TV (Android limits it on low-memory TVs).\u0001Reprendre n'est pas disponible sur cette TV (Android le limite sur les TV à mémoire réduite).\u0001Seguir viendo no está disponible en este TV (Android lo limita en TV de poca memoria).\n" +
            "Shows and movies you play will appear here.\u0001Les films et séries que vous lancez apparaîtront ici.\u0001Las películas y series que reproduzcas aparecerán aquí.\n" +
            "To see what you watch here, allow it in Settings → Permissions.\u0001Pour voir ce que vous regardez ici, autorisez-le dans Réglages → Autorisations.\u0001Para ver lo que ves aquí, permítelo en Ajustes → Permisos.\n" +
            "Use arrows to move · OK when done\u0001Flèches pour déplacer · OK pour terminer\u0001Flechas para mover · OK para terminar\n" +
            "Open in …\u0001Ouvrir dans …\u0001Abrir en …\n" +
            "Not installed\u0001Non installée\u0001No instalada\n" +
            "  ·  Not installed\u0001  ·  Non installée\u0001  ·  No instalada\n" +
            "Not streaming in India right now.\u0001Non disponible en streaming en Inde pour le moment.\u0001No disponible en streaming en India ahora.\n" +
            "Search apps, movies and shows\u0001Rechercher applis, films et séries\u0001Busca apps, películas y series\n" +
            "Listening…\u0001Écoute…\u0001Escuchando…\n" +
            "HOW TO WATCH\u0001COMMENT REGARDER\u0001CÓMO VERLO\n" +
            // --- settings
            "Show in carousel\u0001Afficher dans le carrousel\u0001Mostrar en el carrusel\n" +
            "Recommendations & News\u0001Recommandations et actualités\u0001Recomendaciones y noticias\n" +
            "Opens in\u0001Ouvre dans\u0001Se abre en\n" +
            "Titles open in Stremio, or in Nuvio when only Nuvio is installed.\u0001Les titres s'ouvrent dans Stremio, ou dans Nuvio si seul Nuvio est installé.\u0001Los títulos se abren en Stremio, o en Nuvio si solo Nuvio está instalado.\n" +
            "Streaming services\u0001Services de streaming\u0001Servicios de streaming\n" +
            "TMDB token\u0001Jeton TMDB\u0001Token de TMDB\n" +
            "TMDB connected\u0001TMDB connecté\u0001TMDB conectado\n" +
            "Not set\u0001Non défini\u0001Sin definir\n" +
            "French newsrooms\u0001Rédactions françaises\u0001Redacciones francesas\n" +
            "Local news follows the weather location (Settings → Weather → Location).\u0001Les actus locales suivent le lieu de la météo (Réglages → Météo → Lieu).\u0001Las noticias locales siguen la ubicación del tiempo (Ajustes → Tiempo → Ubicación).\n" +
            "Update news every\u0001Actualiser les actus toutes les\u0001Actualizar noticias cada\n" +
            "Carousel\u0001Carrousel\u0001Carrusel\n" +
            "Auto-advance\u0001Défilement automatique\u0001Avance automática\n" +
            "The carousel will refresh on Home\u0001Le carrousel s'actualisera sur l'accueil\u0001El carrusel se actualizará en inicio\n" +
            "Presentation mode\u0001Mode présentation\u0001Modo presentación\n" +
            "Presentation is on. Send content from a browser.\u0001La présentation est active. Envoyez du contenu depuis un navigateur.\u0001La presentación está activa. Envía contenido desde un navegador.\n" +
            "Start on boot\u0001Démarrer avec la TV\u0001Iniciar con el TV\n" +
            "Keep screen awake\u0001Garder l'écran allumé\u0001Mantener pantalla encendida\n" +
            "Address & pairing code\u0001Adresse et code d'appairage\u0001Dirección y código de emparejamiento\n" +
            "Start presentation now\u0001Lancer la présentation\u0001Iniciar presentación\n" +
            "Inputs card on Home\u0001Carte Entrées sur l'accueil\u0001Tarjeta Entradas en inicio\n" +
            "Show weather\u0001Afficher la météo\u0001Mostrar el tiempo\n" +
            "Celsius (°C)\u0001Celsius (°C)\u0001Celsius (°C)\n" +
            "Fahrenheit (°F)\u0001Fahrenheit (°F)\u0001Fahrenheit (°F)\n" +
            "Updates\u0001Mises à jour\u0001Actualizaciones\n" +
            "Weather source\u0001Source météo\u0001Fuente del tiempo\n" +
            "If it can't be reached, the other sources are used.\u0001Si elle est injoignable, les autres sources sont utilisées.\u0001Si no responde, se usan las otras fuentes.\n" +
            "Update every\u0001Actualiser toutes les\u0001Actualizar cada\n" +
            "Update weather every\u0001Actualiser la météo toutes les\u0001Actualizar el tiempo cada\n" +
            "Show Continue Watching\u0001Afficher Reprendre\u0001Mostrar Seguir viendo\n" +
            "Continue Watching titles won’t appear in the carousel\u0001Les titres Reprendre n'apparaîtront pas dans le carrousel\u0001Los títulos de Seguir viendo no aparecerán en el carrusel\n" +
            "Continue Watching cleared\u0001Historique Reprendre effacé\u0001Historial Seguir viendo borrado\n" +
            "Make ProxFibre the home screen…\u0001Définir ProxFibre comme accueil…\u0001Usar ProxFibre como inicio…\n" +
            "Full-screen carousel when idle\u0001Carrousel plein écran en veille\u0001Carrusel a pantalla completa en reposo\n" +
            "After 10 seconds\u0001Après 10 secondes\u0001Después de 10 segundos\n" +
            "After 20 seconds  (default)\u0001Après 20 secondes  (par défaut)\u0001Después de 20 segundos  (predeterminado)\n" +
            "After 30 seconds\u0001Après 30 secondes\u0001Después de 30 segundos\n" +
            "After 1 minute\u0001Après 1 minute\u0001Después de 1 minuto\n" +
            "Full-screen carousel\u0001Carrousel plein écran\u0001Carrusel a pantalla completa\n" +
            "Screensaver\u0001Économiseur d'écran\u0001Salvapantallas\n" +
            "Screensaver on Home\u0001Économiseur sur l'accueil\u0001Salvapantallas en inicio\n" +
            "The TV's screensaver starts when Home is idle\u0001L'économiseur de la TV démarre quand l'accueil est inactif\u0001El salvapantallas del TV se inicia cuando Inicio está inactivo\n" +
            "Home stays on and plays the carousel full screen instead\u0001L'accueil reste allumé et joue le carrousel plein écran\u0001Inicio permanece encendido y reproduce el carrusel a pantalla completa\n" +
            "Start screensaver now\u0001Lancer l'économiseur maintenant\u0001Iniciar salvapantallas ahora\n" +
            "This TV can't start the screensaver from an app\u0001Cette TV ne permet pas de lancer l'économiseur depuis une appli\u0001Este TV no permite iniciar el salvapantallas desde una app\n" +
            "Movies & TV in search\u0001Films et séries dans la recherche\u0001Películas y series en la búsqueda\n" +
            "Recent searches\u0001Recherches récentes\u0001Búsquedas recientes\n" +
            "Continue Watching & Do Not Disturb\u0001Reprendre et Ne pas déranger\u0001Seguir viendo y No molestar\n" +
            "Power Off & Sleep Timer\u0001Éteindre et minuteur\u0001Apagar y temporizador\n" +
            "ProxFibre as the home screen\u0001ProxFibre comme écran d'accueil\u0001ProxFibre como pantalla de inicio\n" +
            "Wi-Fi name in Control Center\u0001Nom du Wi-Fi dans le centre de contrôle\u0001Nombre del Wi-Fi en el centro de control\n" +
            "Based on\u0001D'après\u0001Basado en\n" +
            "Choose the screensaver in TV Settings → Device Preferences → Screen saver\u0001Choisissez l'économiseur dans Réglages TV → Préférences de l'appareil → Économiseur d'écran\u0001Elige el salvapantallas en Ajustes de la TV → Preferencias del dispositivo → Salvapantallas\n" +
            "JustWatch  (no setup needed)\u0001JustWatch  (aucune configuration)\u0001JustWatch  (sin configurar)\n" +
            "TMDB  (needs a free token)\u0001TMDB  (jeton gratuit requis)\u0001TMDB  (requiere un token gratuito)\n" +
            "Stremio  (opens in Stremio or Nuvio)\u0001Stremio  (ouvre dans Stremio ou Nuvio)\u0001Stremio  (abre en Stremio o Nuvio)\n" +
            "Stremio (not installed)\u0001Stremio (non installé)\u0001Stremio (no instalado)\n" +
            "Recommendation source\u0001Source des recommandations\u0001Fuente de recomendaciones\n" +
            "v3 API key (32 characters) or read-access token\u0001Clé API v3 (32 caractères) ou jeton en lecture\u0001Clave API v3 (32 caracteres) o token de lectura\n" +
            "Choose City…\u0001Choisir une ville…\u0001Elegir ciudad…\n" +
            "Weather location\u0001Lieu de la météo\u0001Ubicación del tiempo\n" +
            "City name, e.g. Bengaluru\u0001Nom de la ville, ex. Bengaluru\u0001Ciudad, p. ej. Bengaluru\n" +
            "Choose City\u0001Choisir une ville\u0001Elegir ciudad\n" +
            "Couldn't search. Check the internet connection.\u0001Recherche impossible. Vérifiez la connexion internet.\u0001No se pudo buscar. Comprueba la conexión a internet.\n" +
            "Turn on Presentation mode first\u0001Activez d'abord le mode présentation\u0001Activa primero el modo presentación\n" +
            "Send to this TV\u0001Envoyer à cette TV\u0001Enviar a este TV\n" +
            "Movies & Series\u0001Films et séries\u0001Películas y series\n" +
            "Movies\u0001Films\u0001Películas\n" +
            "TV Series\u0001Séries\u0001Series\n" +
            "Keep at least one service\u0001Gardez au moins un service\u0001Mantén al menos un servicio\n" +
            "Languages (none = any)\u0001Langues (aucune = toutes)\u0001Idiomas (ninguno = todos)\n" +
            "Languages (TMDB source only)\u0001Langues (source TMDB uniquement)\u0001Idiomas (solo fuente TMDB)\n" +
            "Every 6 s\u0001Toutes les 6 s\u0001Cada 6 s\n" +
            "Every 8 s\u0001Toutes les 8 s\u0001Cada 8 s\n" +
            "Every 12 s\u0001Toutes les 12 s\u0001Cada 12 s\n" +
            // --- search, folders and system rows
            "More Apps\u0001Plus d'applis\u0001Más apps\n" +
            "Apps & Settings\u0001Applis et réglages\u0001Apps y ajustes\n" +
            "Recent Searches\u0001Recherches récentes\u0001Búsquedas recientes\n" +
            "Type to search your apps, settings, movies and shows\u0001Tapez pour chercher des applis, réglages, films et séries\u0001Escribe para buscar apps, ajustes, películas y series\n" +
            "Keep typing to search movies and shows\u0001Continuez à taper pour chercher des films et séries\u0001Sigue escribiendo para buscar películas y series\n" +
            "No results\u0001Aucun résultat\u0001Sin resultados\n" +
            "No movies or shows found\u0001Aucun film ni série trouvé\u0001No se encontraron películas ni series\n" +
            "Can't reach the internet\u0001Pas de connexion Internet\u0001Sin conexión a Internet\n" +
            "Can’t reach the internet\u0001Pas de connexion Internet\u0001Sin conexión a Internet\n" +
            "Voice needs the internet\u0001La voix a besoin d'Internet\u0001La voz necesita Internet\n" +
            "Voice input isn't available on this TV\u0001La saisie vocale n'est pas disponible sur cette TV\u0001El dictado por voz no está disponible en este TV\n" +
            "Didn’t catch that — press the mic to try again\u0001Je n'ai pas compris — appuyez sur le micro pour réessayer\u0001No te entendí — pulsa el micrófono para reintentar\n" +
            "Who’s Watching?\u0001Qui regarde ?\u0001¿Quién está viendo?\n" +
            "How to Watch\u0001Comment regarder\u0001Cómo verlo\n" +
            "Control Center\u0001Centre de contrôle\u0001Centro de control\n" +
            "Weather Forecast\u0001Météo\u0001El tiempo\n" +
            "Screen Saver\u0001Économiseur d'écran\u0001Salvapantallas\n" +
            "Aerial Views\u0001Vues aériennes\u0001Vistas aéreas\n" +
            "Not on this TV\u0001Indisponible sur cette TV\u0001No disponible en este TV\n" +
            "Off in TV settings\u0001Désactivé dans les réglages TV\u0001Desactivado en los ajustes del TV\n";

    private static final String DATA2 =
            // --- setup
            "Welcome\u0001Bienvenue\u0001Bienvenido\n" +
            "Let’s set up ProxFibre.\u0001Configurons ProxFibre.\u0001Vamos a configurar ProxFibre.\n" +
            "Get Started\u0001Commencer\u0001Empezar\n" +
            "based on Hume Launcher\u0001d'après Hume Launcher\u0001basado en Hume Launcher\n" +
            "Create Your Profile\u0001Créez votre profil\u0001Crea tu perfil\n" +
            "Your name and an avatar. Each profile keeps its own apps, background and Home Screen. Up to three people can have one.\u0001Votre nom et un avatar. Chaque profil garde ses applis, son fond et son écran d'accueil. Trois personnes au maximum.\u0001Tu nombre y un avatar. Cada perfil guarda sus apps, su fondo y su pantalla de inicio. Hasta tres personas.\n" +
            "Name\u0001Nom\u0001Nombre\n" +
            "Your name\u0001Votre nom\u0001Tu nombre\n" +
            "Continue\u0001Continuer\u0001Continuar\n" +
            "Choose a look\u0001Choisissez un fond\u0001Elige un fondo\n" +
            "The background behind your apps. Move through the list to preview it.\u0001Le fond derrière vos applis. Parcourez la liste pour le prévisualiser.\u0001El fondo detrás de tus apps. Recorre la lista para previsualizarlo.\n" +
            "Your Home Screen\u0001Votre écran d'accueil\u0001Tu pantalla de inicio\n" +
            "What appears at the top of Home, behind your apps. Choose any, all or none.\u0001Ce qui apparaît en haut de l'accueil, derrière vos applis. Choisissez-en un, plusieurs ou aucun.\u0001Lo que aparece arriba en Inicio, detrás de tus apps. Elige uno, varios o ninguno.\n" +
            "Popular movies and series on your streaming services\u0001Films et séries populaires sur vos services de streaming\u0001Películas y series populares en tus servicios de streaming\n" +
            "France and the world, from French newsrooms\u0001La France et le monde, par des rédactions françaises\u0001Francia y el mundo, de redacciones francesas\n" +
            "What you were watching, mixed into the carousel\u0001Ce que vous regardiez, mêlé au carrousel\u0001Lo que estabas viendo, mezclado en el carrusel\n" +
            "Just your background for now\u0001Juste votre fond pour l'instant\u0001Solo tu fondo por ahora\n" +
            "A small weather card in the top-left corner of Home, with a 5-day forecast.\u0001Une petite carte météo en haut à gauche de l'accueil, avec les prévisions à 5 jours.\u0001Una pequeña tarjeta del tiempo arriba a la izquierda de Inicio, con previsión a 5 días.\n" +
            "Use My Location\u0001Ma position\u0001Mi ubicación\n" +
            "Found from your internet connection\u0001Déduite de votre connexion internet\u0001Según tu conexión a internet\n" +
            "Search by name\u0001Rechercher par nom\u0001Buscar por nombre\n" +
            "Choose a City\u2026\u0001Choisir une ville\u2026\u0001Elegir una ciudad\u2026\n" +
            "Don't Show Weather\u0001Sans météo\u0001No mostrar el tiempo\n" +
            "Images, videos and webpages sent from any phone or computer on the same Wi-Fi. For everyone on this TV.\u0001Images, vidéos et pages web envoyées depuis un téléphone ou un ordinateur sur le même Wi-Fi. Pour tous sur cette TV.\u0001Imágenes, vídeos y páginas web enviados desde un móvil u ordenador en el mismo Wi-Fi. Para todos en este TV.\n" +
            "Use Presentation\u0001Utiliser la présentation\u0001Usar presentación\n" +
            "Adds a Presentation card to Home\u0001Ajoute une carte Présentation à l'accueil\u0001Añade una tarjeta Presentación a Inicio\n" +
            "Start with the TV\u0001Démarrer avec la TV\u0001Iniciar con el TV\n" +
            "Play as soon as the TV turns on (signage)\u0001Démarre dès l'allumage de la TV (affichage dynamique)\u0001Reproduce al encender el TV (cartelería digital)\n" +
            "Keep Screen Awake\u0001Garder l'écran allumé\u0001Mantener pantalla encendida\n" +
            "No screensaver or sleep while presenting\u0001Pas d'économiseur ni de veille pendant la présentation\u0001Sin salvapantallas ni reposo durante la presentación\n" +
            "Finish Setting Up\u0001Finalisez la configuration\u0001Termina la configuración\n" +
            "ProxFibre works best as the home screen. Allow these once; anything this TV doesn't support is skipped.\u0001ProxFibre fonctionne mieux comme écran d'accueil. Autorisez ces accès une fois ; ce que cette TV ne prend pas en charge est ignoré.\u0001ProxFibre funciona mejor como pantalla de inicio. Permite estos accesos una vez; lo que este TV no admite se omite.\n" +
            "Make ProxFibre the Home Screen\u0001Définir ProxFibre comme accueil\u0001Usar ProxFibre como inicio\n" +
            "The Home button opens ProxFibre\u0001Le bouton Accueil ouvre ProxFibre\u0001El botón Inicio abre ProxFibre\n" +
            "Choose ProxFibre when asked\u0001Choisissez ProxFibre quand on vous le demande\u0001Elige ProxFibre cuando se pregunte\n" +
            "Show the Wi-Fi Name\u0001Afficher le nom du Wi-Fi\u0001Mostrar el nombre del Wi-Fi\n" +
            "In Control Center (needs location access)\u0001Dans le centre de contrôle (accès à la position requis)\u0001En el centro de control (requiere acceso a la ubicación)\n" +
            "Remember what you play in each app\u0001Mémoriser ce que vous lancez dans chaque appli\u0001Recordar lo que reproduces en cada app\n" +
            "Let ProxFibre turn the screen off\u0001Laisser ProxFibre éteindre l'écran\u0001Permitir que ProxFibre apague la pantalla\n" +
            "You're All Set\u0001Tout est prêt\u0001Todo listo\n" +
            "Change any of this later in Settings, from the gear in Control Center.\u0001Modifiez tout cela plus tard dans Réglages, via l'engrenage du centre de contrôle.\u0001Cambia todo esto después en Ajustes, desde el engranaje del centro de control.\n" +
            "Go to Home Screen\u0001Aller à l'accueil\u0001Ir a la pantalla de inicio\n" +
            // --- bundled apps
            "Install Armadillo and AirPlay?\u0001Installer Armadillo et AirPlay ?\u0001¿Instalar Armadillo y AirPlay?\n" +
            "Install\u0001Installer\u0001Instalar\n" +
            "Later\u0001Plus tard\u0001Más tarde\n" +
            "Allow installs\u0001Autoriser les installations\u0001Permitir instalaciones\n" +
            "Open settings\u0001Ouvrir les réglages\u0001Abrir ajustes\n" +
            "No installer available on this TV\u0001Aucun installeur sur cette TV\u0001No hay instalador en este TV\n" +
            // --- permissions
            "Turn on Power Off\u0001Activer Éteindre\u0001Activar Apagar\n" +
            "Open Accessibility\u0001Ouvrir Accessibilité\u0001Abrir Accesibilidad\n" +
            "OK\u0001OK\u0001OK\n" +
            // --- presentation
            "Nothing to present yet\u0001Rien à présenter pour l'instant\u0001Nada que presentar todavía\n" +
            "Add images, videos or webpages from a phone or computer on the same Wi-Fi.\u0001Ajoutez des images, vidéos ou pages web depuis un téléphone ou un ordinateur sur le même Wi-Fi.\u0001Añade imágenes, vídeos o páginas web desde un móvil u ordenador en el mismo Wi-Fi.\n" +
            "Scan the code or open this address on a phone or computer on the same Wi-Fi.\u0001Scannez le code ou ouvrez cette adresse sur un téléphone ou un ordinateur sur le même Wi-Fi.\u0001Escanea el código o abre esta dirección en un móvil u ordenador en el mismo Wi-Fi.\n" +
            "PAIRING CODE\u0001CODE D'APPAIRAGE\u0001CÓDIGO DE EMPAREJAMIENTO\n" +
            "Skipping this slide\u0001Passage de cette diapositive\u0001Saltando esta diapositiva\n" +
            "Presentation isn’t on screen\u0001La présentation n'est pas à l'écran\u0001La presentación no está en pantalla\n" +
            "Press Back again to exit\u0001Appuyez à nouveau sur Retour pour quitter\u0001Pulsa Atrás otra vez para salir\n" +
            "Media file is missing for slide\u0001Fichier média manquant pour la diapositive\u0001Falta el archivo multimedia de la diapositiva\n" +
            "No enabled slides in the active playlist\u0001Aucune diapositive active dans la liste\u0001No hay diapositivas activas en la lista\n" +
            "Webpage slide is missing a URL\u0001URL manquante pour la diapositive web\u0001Falta la URL de la diapositiva web\n" +
            "▶  Playing\u0001▶  Lecture\u0001▶  Reproduciendo\n" +
            "❚❚  Paused\u0001❚❚  En pause\u0001❚❚  En pausa\n" +
            // --- weather
            "Getting the weather…\u0001Récupération de la météo…\u0001Obteniendo el tiempo…\n" +
            "My Location\u0001Ma position\u0001Mi ubicación\n" +
            "No location\u0001Aucun lieu\u0001Sin ubicación\n" +
            "No current data\u0001Aucune donnée actuelle\u0001Sin datos actuales\n" +
            "HOURLY FORECAST\u0001PRÉVISIONS HORAIRES\u0001PREVISIÓN POR HORAS\n" +
            "5-DAY FORECAST\u0001PRÉVISIONS À 5 JOURS\u0001PREVISIÓN A 5 DÍAS\n" +
            "Now\u0001Maintenant\u0001Ahora\n" +
            "Today\u0001Aujourd'hui\u0001Hoy\n" +
            "Yesterday\u0001Hier\u0001Ayer\n" +
            "Trending\u0001Tendances\u0001Tendencias\n" +
            "France\u0001France\u0001Francia\n" +
            "World\u0001Monde\u0001Mundo\n" +
            "Just now\u0001À l'instant\u0001Ahora mismo\n" +
            "Clear\u0001Dégagé\u0001Despejado\n" +
            "Sunny\u0001Ensoleillé\u0001Soleado\n" +
            "Mostly Clear\u0001Plutôt dégagé\u0001Mayormente despejado\n" +
            "Mostly Sunny\u0001Plutôt ensoleillé\u0001Mayormente soleado\n" +
            "Partly Cloudy\u0001Partiellement nuageux\u0001Parcialmente nublado\n" +
            "Cloudy\u0001Nuageux\u0001Nublado\n" +
            "Foggy\u0001Brumeux\u0001Niebla\n" +
            "Drizzle\u0001Bruine\u0001Llovizna\n" +
            "Rain\u0001Pluie\u0001Lluvia\n" +
            "Heavy Rain\u0001Fortes pluies\u0001Lluvia fuerte\n" +
            "Snow\u0001Neige\u0001Nieve\n" +
            "Sleet\u0001Grésil\u0001Aguanieve\n" +
            "Thunderstorms\u0001Orages\u0001Tormentas\n" +
            // --- Jellyfin library
            "Library\u0001Bibliothèque\u0001Biblioteca\n" +
            "Server\u0001Serveur\u0001Servidor\n" +
            "Account\u0001Compte\u0001Cuenta\n" +
            "Show on Home\u0001Afficher sur l'accueil\u0001Mostrar en inicio\n" +
            "Client app\u0001Appli de lecture\u0001App de reproducción\n" +
            "Disconnect\u0001Déconnecter\u0001Desconectar\n" +
            "Connect\u0001Connecter\u0001Conectar\n" +
            "Not connected\u0001Non connecté\u0001Sin conexión\n" +
            "Jellyfin server\u0001Serveur Jellyfin\u0001Servidor Jellyfin\n" +
            "Disconnect Jellyfin?\u0001Déconnecter Jellyfin ?\u0001¿Desconectar Jellyfin?\n" +
            "not installed\u0001non installée\u0001no instalada\n" +
            "Wrong username or password\u0001Identifiants incorrects\u0001Usuario o contraseña incorrectos\n" +
            "Unexpected reply from the server\u0001Réponse inattendue du serveur\u0001Respuesta inesperada del servidor\n" +
            "Server address is empty\u0001Adresse du serveur vide\u0001Dirección del servidor vacía\n" +
            "Username is empty\u0001Nom d'utilisateur vide\u0001Usuario vacío\n" +
            "Username\u0001Nom d'utilisateur\u0001Usuario\n" +
            "Password\u0001Mot de passe\u0001Contraseña\n" +
            // --- updates
            "Update\u0001Mise à jour\u0001Actualización\n" +
            "Up to date\u0001À jour\u0001Actualizado\n" +
            "Not checked\u0001Non vérifiée\u0001Sin comprobar\n" +
            "Can't check\u0001Vérification impossible\u0001No se puede comprobar\n" +
            "No releases yet\u0001Aucune version publiée\u0001Sin versiones publicadas\n" +
            // --- wallpapers
            "Wallpapers\u0001Fonds d'écran\u0001Fondos\n" +
            "How it works\u0001Comment ça marche\u0001Cómo funciona\n" +
            "Idle\u0001En attente\u0001En espera\n" +
            "Installed\u0001Installée\u0001Instalada\n" +
            "Install…\u0001Installer…\u0001Instalar…\n" +
            "AirPlay Server is not installed\u0001AirPlay Server n'est pas installée\u0001AirPlay Server no está instalada\n";
}
