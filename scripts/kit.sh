#!/usr/bin/env bash
# =====================================================================
#  ProxFibre TV - allègement réversible + écran d'accueil
#  ProxFibre TV - reversible debloat + launcher
#  macOS / Linux (bash 3.2+)  -  lancé par LANCER.command ou LANCER.sh
#  Portage de kit.ps1 : mêmes listes, mêmes fichiers de sauvegarde.
# =====================================================================
# shellcheck disable=SC2034  # C_Cyan/Green/Yellow/Red are read indirectly by color() via eval
KIT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS="$KIT/scripts"
TOOLS="$KIT/outils"
SAVES="$KIT/sauvegardes"
APK_PATH="$KIT/ecran-accueil/ProxFibre.apk"
PHOTOS_DIR="$KIT/mes_photos"
CONF_FILE="$KIT/config_tv.json"
ZEN_PKG="tv.proxfibre"
GTV_FALLBACK="com.google.android.tungsten.setupwraith"   # accueil de secours de Google TV
mkdir -p "$TOOLS" "$SAVES"

LANG_FR=0
case "${LC_ALL:-${LC_MESSAGES:-${LANG:-}}}" in fr*) LANG_FR=1 ;; esac
if [ "$(uname)" = "Darwin" ]; then
    case "$(defaults read -g AppleLocale 2>/dev/null)" in fr*) LANG_FR=1 ;; esac
fi

if [ -t 1 ]; then
    C_Gray=$'\033[0;37m'; C_DarkGray=$'\033[0;90m'; C_White=$'\033[1;37m'; C_Cyan=$'\033[0;36m'
    C_DarkCyan=$'\033[0;36m'; C_Green=$'\033[0;32m'; C_Yellow=$'\033[0;33m'; C_Red=$'\033[0;31m'; C_0=$'\033[0m'
else
    C_Gray=; C_DarkGray=; C_White=; C_Cyan=; C_DarkCyan=; C_Green=; C_Yellow=; C_Red=; C_0=
fi
color() { eval "printf '%s' \"\$C_$1\""; }

T()    { if [ "$LANG_FR" = 1 ]; then printf '%s' "$1"; else printf '%s' "$2"; fi; }
Say()  { printf '%s%s%s\n' "$(color "${3:-Gray}")" "$(T "$1" "$2")" "$C_0"; }
Ok()   { Say "  ✔ $1" "  ✔ $2" Green; }
Warn() { Say "  ! $1" "  ! $2" Yellow; }
Err()  { Say "  ✖ $1" "  ✖ $2" Red; }
Ask()  { local r; printf '%s: ' "$(T "$1" "$2")" >&2; IFS= read -r r; printf '%s' "$r"; }
YesNo() {
    local sfx r
    if [ "${3:-1}" = 1 ]; then sfx="$(T " [O/n]" " [Y/n]")"; else sfx="$(T " [o/N]" " [y/N]")"; fi
    printf '%s%s: ' "$(T "$1" "$2")" "$sfx" >&2
    IFS= read -r r
    r="$(printf '%s' "$r" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    [ -z "$r" ] && { [ "${3:-1}" = 1 ]; return; }
    case "$r" in o|oui|y|yes) return 0 ;; *) return 1 ;; esac
}
Pause_Kit() { printf '\n%s' "$(T "Entrée pour revenir au menu" "Press Enter to go back to the menu")"; read -r _; }
trim() { printf '%s' "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'; }
now()  { date +%Y%m%d_%H%M%S; }
open_path() { if command -v open >/dev/null 2>&1 && [ "$(uname)" = "Darwin" ]; then open "$1"; elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1 & fi; }

# ---------------------------------------------------------------- JSON minimal
# Lit aussi les fichiers écrits par kit.ps1 (BOM, ConvertTo-Json multi-lignes).
BOM="$(printf '\357\273\277')"
json_flat() { LC_ALL=C sed "1s/^$BOM//" "$1" | tr -d '\r\n'; }
json_str() {
    json_flat "$1" | LC_ALL=C grep -oE "\"$2\"[[:space:]]*:[[:space:]]*\"([^\"\\\\]|\\\\.)*\"" | head -n 1 \
        | LC_ALL=C sed -e 's/^[^:]*:[[:space:]]*"//' -e 's/"$//' -e 's/\\"/"/g' -e 's/\\\\/\\/g'
}
json_arr() {
    local flat; flat="$(json_flat "$1")"
    if printf '%s' "$flat" | grep -q "\"$2\"[[:space:]]*:[[:space:]]*\["; then
        printf '%s' "$flat" | LC_ALL=C grep -o "\"$2\"[[:space:]]*:[[:space:]]*\[[^]]*\]" | head -n 1 \
            | LC_ALL=C sed 's/^[^[]*\[//' | LC_ALL=C grep -o '"[^"]*"' | tr -d '"'
    else
        json_str "$1" "$2"
    fi
}
jq_s() { if [ -z "$1" ]; then printf 'null'; else printf '"%s"' "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"; fi; }

# ---------------------------------------------------------------- ADB
Get_Adb() {
    local p="$TOOLS/platform-tools/adb" os zip
    if [ -x "$p" ]; then printf '%s' "$p"; return; fi
    if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
    case "$(uname)" in Darwin) os=darwin ;; *) os=linux ;; esac
    Say "Téléchargement des outils ADB officiels de Google (environ 7 Mo)..." "Downloading Google's official ADB tools (about 7 MB)..." Cyan >&2
    zip="$TOOLS/platform-tools.zip"
    if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip"
    else wget -q -O "$zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip"; fi
    unzip -q -o "$zip" -d "$TOOLS" && rm -f "$zip"
    printf '%s' "$p"
}
ADB="$(Get_Adb)"
if [ ! -x "$ADB" ] && ! command -v "$ADB" >/dev/null 2>&1; then
    Err "ADB introuvable et téléchargement impossible (curl/wget et unzip nécessaires)." "ADB not found and download failed (curl/wget and unzip required)."
    exit 1
fi
T_=""      # ip:port de la TV
ID=""      # identifiant unique de la TV

A()    { "$ADB" -s "$T_" "$@" 2>&1 | tr -d '\r'; }
Sh()   { A shell "$1"; }
Prop() { trim "$(Sh "getprop $1" | tr -d '\n')"; }

Get_TvIdentity() {
    local serial aid id
    serial="$(Prop ro.serialno)"
    if [ -z "$serial" ] || [ "$serial" = "unknown" ]; then serial="$(Prop ro.boot.serialno)"; fi
    aid="$(trim "$(Sh "settings get secure android_id" | tr -d '\n')")"
    if [ -n "$serial" ] && [ "$serial" != "unknown" ]; then id="$serial"; else id="aid-$aid"; fi
    INFO_Id="$(printf '%s' "$id" | sed 's/[^A-Za-z0-9_-]/_/g')"
    INFO_Brand="$(Prop ro.product.brand)"; INFO_Model="$(Prop ro.product.model)"
    INFO_Manufacturer="$(Prop ro.product.manufacturer)"
    INFO_Android="$(Prop ro.build.version.release)"; INFO_Sdk="$(Prop ro.build.version.sdk)"
    INFO_Patch="$(Prop ro.build.version.security_patch)"
    INFO_Name="$(trim "$(Sh "settings get global device_name" | tr -d '\n')")"
}

Connect_Target() {
    local target="$1" i st out asked=0 refused=0 unreach=0
    case "$target" in *:*) ;; *) target="$target:5555" ;; esac
    T_="$target"
    i=0
    while [ $i -lt 60 ]; do
        out="$("$ADB" connect "$target" 2>&1 | tr -d '\r' | tr '\n' ' ')"
        st="$("$ADB" -s "$target" get-state 2>&1 | tr -d '\r\n')"
        case "$st" in device*) return 0 ;; esac
        # on distingue : TV joignable qui attend l'autorisation / TV qui refuse (débogage réseau inactif) / TV injoignable
        case "$out $st" in
            *unauthorized*|*authenticate*)
                [ $asked = 0 ] && { Say ">>> Regardez la TV : « Autoriser le débogage ? » doit s'afficher. Cochez « Toujours autoriser » puis OK." ">>> Look at the TV: 'Allow debugging?' should appear. Tick 'Always allow' then OK." Cyan; asked=1; } ;;
            *refused*|*10061*)
                refused=$((refused + 1))
                if [ $refused -ge 3 ]; then
                    Err "La TV répond mais refuse la connexion : son débogage réseau n'est pas actif." "The TV answers but refuses the connection: its network debugging is not enabled."
                    Say "   Dans les options pour les développeurs, activez « Débogage ADB réseau » (ou « ADB over network »). Sur Android 11 et plus, utilisez « Débogage sans fil » et le jumelage par code." "   In developer options, enable 'Network ADB debugging' (or 'ADB over network'). On Android 11 and later, use 'Wireless debugging' and pairing with a code." DarkGray
                    return 1
                fi ;;
            *"timed out"*|*10060*|*nreachable*|*10065*|*"No route"*)
                unreach=$((unreach + 1))
                if [ $unreach -ge 2 ]; then
                    Err "Aucun appareil ne répond à l'adresse $target." "No device answers at $target."
                    Say "   Vérifiez l'adresse IP, que la TV est allumée (pas en veille) et sur le même réseau que l'ordinateur (pas un réseau invité). Un VPN sur l'ordinateur peut aussi bloquer." "   Check the IP address, that the TV is on (not in standby) and on the same network as the computer (not a guest network). A VPN on the computer can also block it." DarkGray
                    return 1
                fi ;;
            *)
                [ $i -eq 1 ] && [ $asked = 0 ] && { Say ">>> Regardez la TV : si « Autoriser le débogage ? » s'affiche, cochez « Toujours autoriser » puis OK." ">>> Look at the TV: if 'Allow debugging?' appears, tick 'Always allow' then OK." Cyan; asked=1; } ;;
        esac
        sleep 2
        i=$((i + 1))
    done
    return 1
}

Choose_TV() {
    local ip pp code cp
    echo
    Say "Trouvez l'adresse IP de la TV : Paramètres > Réseau (ou À propos > État)." "Find the TV's IP address: Settings > Network (or About > Status)." Cyan
    Say "Le débogage ADB doit être activé (voir README)." "ADB debugging must be enabled (see README)." DarkGray
    ip="$(trim "$(Ask "Adresse IP de la TV (ex. 192.168.1.50)" "TV IP address (e.g. 192.168.1.50)")")"
    [ -z "$ip" ] && return 1
    if ! Connect_Target "$ip"; then
        Err "Connexion impossible." "Could not connect."
        if YesNo "Votre TV affiche-t-elle un code de jumelage (Débogage sans fil, Android 11+) ?" "Does your TV show a pairing code (Wireless debugging, Android 11+)?" 0; then
            pp="$(trim "$(Ask "IP:port de JUMELAGE affiché sur la TV" "PAIRING IP:port shown on the TV")")"
            code="$(trim "$(Ask "Code de jumelage" "Pairing code")")"
            "$ADB" pair "$pp" "$code"
            cp="$(trim "$(Ask "IP:port de CONNEXION (écran Débogage sans fil)" "CONNECTION IP:port (Wireless debugging screen)")")"
            if ! Connect_Target "$cp"; then Err "Toujours impossible. Voir README > Dépannage." "Still failing. See README > Troubleshooting."; return 1; fi
        else return 1; fi
    fi
    Get_TvIdentity
    echo
    Say "Appareil trouvé :" "Device found:" White
    printf '   %s %s  (%s)  - Android %s, patch %s\n' "$INFO_Brand" "$INFO_Model" "$INFO_Name" "$INFO_Android" "$INFO_Patch"
    printf '   ID : %s\n' "$INFO_Id"
    if ! YesNo "Est-ce bien la TV que vous voulez modifier ?" "Is this the TV you want to modify?" 1; then
        "$ADB" disconnect "$T_" >/dev/null 2>&1; T_=""; return 1
    fi
    ID="$INFO_Id"
    printf '{\n    "target":  %s,\n    "id":  %s,\n    "label":  %s\n}\n' "$(jq_s "$T_")" "$(jq_s "$INFO_Id")" "$(jq_s "$INFO_Brand $INFO_Model")" > "$CONF_FILE"
    Ok "TV enregistrée. Toutes les actions vérifieront qu'il s'agit bien d'elle." "TV saved. Every action will check it is the same TV."
    return 0
}

# Reconnecte la TV enregistrée et vérifie son identité avant toute action
Ensure_TV() {
    local c_target c_id c_label
    [ -f "$CONF_FILE" ] || { Choose_TV; return; }
    c_target="$(json_str "$CONF_FILE" target)"; c_id="$(json_str "$CONF_FILE" id)"; c_label="$(json_str "$CONF_FILE" label)"
    if ! Connect_Target "$c_target"; then
        Err "La TV $c_label ($c_target) ne répond pas. Allumée ? Même Wi-Fi ? IP changée ?" "TV $c_label ($c_target) is not answering. On? Same Wi-Fi? IP changed?"
        if YesNo "Saisir une autre adresse ?" "Enter another address?" 1; then rm -f "$CONF_FILE"; Choose_TV; return; fi
        return 1
    fi
    Get_TvIdentity
    if [ "$INFO_Id" != "$c_id" ]; then
        Err "SÉCURITÉ : l'appareil à cette adresse n'est PAS la TV enregistrée ($INFO_Brand $INFO_Model). Rien n'a été fait." "SAFETY: the device at this address is NOT the saved TV ($INFO_Brand $INFO_Model). Nothing was done."
        "$ADB" disconnect "$T_" >/dev/null 2>&1
        return 1
    fi
    ID="$INFO_Id"
    Ok "TV vérifiée : $c_label" "TV verified: $c_label"
    return 0
}

# ---------------------------------------------------------------- état / sauvegarde
TvDir() { mkdir -p "$SAVES/$ID"; printf '%s' "$SAVES/$ID"; }
StateFile() { printf '%s/etat_kit.json' "$(TvDir)"; }
# état chargé dans des variables ST_* (ST_DISABLED = un paquet par ligne)
Get_State() {
    local f; f="$(StateFile)"
    ST_DISABLED=""; ST_HAS_ANIM=0; ST_ANIM_WINDOW=""; ST_ANIM_TRANSITION=""; ST_ANIM_ANIMATOR=""
    ST_ORIGINAL_HOME=""; ST_LAUNCHER_DISABLED=""; ST_BACKUP=""
    [ -f "$f" ] || return 0
    ST_DISABLED="$(json_arr "$f" disabled)"
    if json_flat "$f" | grep -q '"anim"[[:space:]]*:[[:space:]]*{'; then
        ST_HAS_ANIM=1
        ST_ANIM_WINDOW="$(json_str "$f" window)"; ST_ANIM_TRANSITION="$(json_str "$f" transition)"; ST_ANIM_ANIMATOR="$(json_str "$f" animator)"
    fi
    ST_ORIGINAL_HOME="$(json_str "$f" original_home)"
    ST_LAUNCHER_DISABLED="$(json_str "$f" launcher_disabled)"
    ST_BACKUP="$(json_str "$f" backup)"
}
Save_State() {
    local arr="" p anim="null"
    while IFS= read -r p; do [ -n "$p" ] && arr="$arr${arr:+,}
        $(jq_s "$p")"; done <<EOF
$ST_DISABLED
EOF
    if [ "$ST_HAS_ANIM" = 1 ]; then
        anim="{
        \"window\":  $(jq_s "$ST_ANIM_WINDOW"),
        \"transition\":  $(jq_s "$ST_ANIM_TRANSITION"),
        \"animator\":  $(jq_s "$ST_ANIM_ANIMATOR")
    }"
    fi
    cat > "$(StateFile)" <<EOF
{
    "disabled":  [$arr
    ],
    "anim":  $anim,
    "original_home":  $(jq_s "$ST_ORIGINAL_HOME"),
    "launcher_disabled":  $(jq_s "$ST_LAUNCHER_DISABLED"),
    "backup":  $(jq_s "$ST_BACKUP")
}
EOF
}
in_list() { printf '%s\n' "$2" | grep -qxF -- "$1"; }

Capture_TV() {
    local dir dest
    dir="$(TvDir)/captures"; mkdir -p "$dir"
    dest="$dir/$(now)_$1.png"
    A shell screencap -p /sdcard/_kit_cap.png >/dev/null
    A pull /sdcard/_kit_cap.png "$dest" >/dev/null
    A shell rm /sdcard/_kit_cap.png >/dev/null
    [ -f "$dest" ] && printf '%s   %s%s%s\n' "$C_DarkGray" "$(T "Capture : " "Screenshot: ")" "$dest" "$C_0" >&2
    CAPTURE="$dest"
}

# PERF_TOTAL / PERF_DISPO en Mo, PERF_CPU en %
Get_Perf() {
    local mem top line tot idle
    mem="$(Sh "cat /proc/meminfo")"
    PERF_TOTAL="$(printf '%s\n' "$mem" | awk '/^MemTotal:/ { printf "%d", $2 / 1024 + 0.5 }')"; PERF_TOTAL="${PERF_TOTAL:-0}"
    PERF_DISPO="$(printf '%s\n' "$mem" | awk '/^MemAvailable:/ { printf "%d", $2 / 1024 + 0.5 }')"; PERF_DISPO="${PERF_DISPO:-0}"
    PERF_CPU=""
    top="$(Sh "top -b -n 1 -m 5")"
    line="$(printf '%s\n' "$top" | grep -o '[0-9][0-9]*%cpu.*[^0-9][0-9][0-9]*%idle' | head -n 1)"
    if [ -n "$line" ]; then
        tot="$(printf '%s' "$line" | sed 's/%cpu.*//')"
        idle="$(printf '%s' "$line" | sed 's/.*[^0-9]\([0-9][0-9]*\)%idle$/\1/')"
        PERF_CPU="$(awk -v t="$tot" -v i="$idle" 'BEGIN { if (t > 0) printf "%d", 100 * (1 - i / t) + 0.5 }')"
    fi
}

Get_Home() {
    local line
    line="$(Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME" \
        | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | grep -E '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' | tail -n 1)"
    case "$line" in ""|android/*) ;; *) printf '%s' "$line" ;; esac
}
Get_Packages() { Sh "pm list packages $1" | grep '^package:' | sed -e 's/^package://' -e 's/[[:space:]]*$//'; }

Do_Backup() {
    local quiet="${1:-0}" d homeAct wifi freq rssi speed wf sum
    d="$(TvDir)/sauvegarde_$(now)"
    mkdir -p "$d"
    Say "Sauvegarde de l'état de la TV (lecture seule)..." "Backing up the TV's state (read-only)..." Cyan
    {
        for k in Id Brand Model Manufacturer Android Sdk Patch Build Name; do eval "printf '%-12s : %s\n' \"$k\" \"\$INFO_$k\""; done
    } > "$d/appareil.txt"
    Sh "getprop" > "$d/getprop.txt"
    Sh "pm list packages -f" > "$d/paquets_tous.txt"
    Sh "pm list packages -d" > "$d/paquets_desactives.txt"
    Sh "pm list packages -3" > "$d/paquets_installes_par_vous.txt"
    for ns in global secure system; do Sh "settings list $ns" > "$d/settings_$ns.txt"; done
    homeAct="$(Get_Home)"
    printf '%s\n' "$homeAct" > "$d/ecran_accueil.txt"
    Sh "df -h" > "$d/stockage.txt"
    Sh "top -b -n 1 -m 25" > "$d/processus.txt"
    wifi="$(Sh "dumpsys wifi")"
    Get_Perf
    freq="$(printf '%s\n' "$wifi" | sed -n 's/.*Frequency: \([0-9][0-9]*\).*/\1/p' | head -n 1)"; freq="${freq:-0}"
    rssi="$(printf '%s\n' "$wifi" | sed -n 's/.*RSSI: \(-\{0,1\}[0-9][0-9]*\).*/\1/p' | head -n 1)"; rssi="${rssi:-0}"
    speed="$(printf '%s\n' "$wifi" | sed -n 's/.*Link speed: \([0-9][0-9]*\).*/\1/p' | head -n 1)"; speed="${speed:-0}"
    Get_State
    if [ -z "$ST_BACKUP" ]; then ST_BACKUP="$d"; ST_ORIGINAL_HOME="$homeAct"; fi   # 1re sauvegarde = état d'origine
    if [ "$ST_HAS_ANIM" != 1 ]; then
        ST_HAS_ANIM=1
        ST_ANIM_WINDOW="$(trim "$(Sh "settings get global window_animation_scale" | tr -d '\n')")"
        ST_ANIM_TRANSITION="$(trim "$(Sh "settings get global transition_animation_scale" | tr -d '\n')")"
        ST_ANIM_ANIMATOR="$(trim "$(Sh "settings get global animator_duration_scale" | tr -d '\n')")"
    fi
    Save_State
    # ce que le kit a désactivé mais que la TV a réactivé d'elle-même (fréquent sur Google TV)
    if [ -n "$ST_DISABLED" ]; then
        local offNow back="" q
        offNow="$(Get_Packages -d)"
        for q in $ST_DISABLED; do in_list "$q" "$offNow" || back="$back${back:+, }$q"; done
        [ -n "$back" ] && Warn "Réactivés par la TV depuis le passage du kit : $back. Relancez l'option 2 ou 3 si besoin." "Re-enabled by the TV since the kit ran: $back. Run option 2 or 3 again if needed."
    fi
    Capture_TV "etat_initial"
    [ -f "$CAPTURE" ] && cp "$CAPTURE" "$d/capture.png"
    if [ "$freq" -gt 4000 ]; then wf="5 GHz"; elif [ "$freq" -gt 0 ]; then wf="2.4 GHz"; else wf="$(T "câble ou inconnu" "cable or unknown")"; fi
    sum="$(T "Appareil" "Device") : $INFO_Brand $INFO_Model - Android $INFO_Android (SDK $INFO_Sdk), patch $INFO_Patch
$(T "Écran d'accueil" "Home screen") : $homeAct
RAM : $PERF_DISPO / $PERF_TOTAL Mo $(T "disponibles" "available")
$(T "Processeur utilisé (instantané)" "CPU used (snapshot)") : $PERF_CPU %
Wi-Fi : $wf, $rssi dBm, $speed Mb/s"
    printf '%s\n' "$sum" > "$d/RESUME.txt"
    if [ "$quiet" != 1 ]; then echo; printf '%s\n' "$sum" | sed 's/^/   /'; fi
    if [ "$freq" -gt 0 ] && [ "$freq" -lt 3000 ]; then Warn "La TV est en Wi-Fi 2,4 GHz : préférez le 5 GHz ou un câble pour le streaming." "The TV uses 2.4 GHz Wi-Fi: prefer 5 GHz or a cable for streaming."; fi
    if [ "$PERF_TOTAL" -gt 0 ] && [ "$PERF_DISPO" -lt 400 ]; then Warn "Peu de mémoire disponible : l'allègement (menu 2) devrait aider." "Low available memory: slimming (menu 2) should help."; fi
    Ok "Sauvegarde : $d" "Backup: $d"
}
Ensure_Backup() { Get_State; if [ -z "$ST_BACKUP" ]; then Say "Première utilisation sur cette TV : sauvegarde d'abord." "First use on this TV: backing up first." Cyan; Do_Backup 1; fi; }

# ---------------------------------------------------------------- listes
# Remplit L_PKGS[i] (paquets séparés par des espaces), L_FR[i], L_EN[i]
Read_List() {
    local l pk fr en n=0
    L_PKGS=(); L_FR=(); L_EN=()
    while IFS= read -r l || [ -n "$l" ]; do
        l="$(trim "$(printf '%s' "$l" | LC_ALL=C sed "s/^$BOM//" | tr -d '\r')")"
        case "$l" in ""|"#"*) continue ;; esac
        # une ligne peut regrouper plusieurs paquets d'une meme fonction : paquet1,paquet2 | ...
        pk="$(printf '%s' "$l" | cut -d'|' -f1 | tr ',' ' ' | xargs)"
        fr=""; en=""
        case "$l" in *"|"*) fr="$(trim "$(printf '%s' "$l" | cut -d'|' -f2)")" ;; esac
        case "$l" in *"|"*"|"*) en="$(trim "$(printf '%s' "$l" | cut -d'|' -f3)")" ;; esac
        [ -z "$pk" ] && continue
        L_PKGS[$n]="$pk"; L_FR[$n]="$fr"; L_EN[$n]="$en"; n=$((n + 1))
    done < "$SCRIPTS/listes/$1"
}
PROTECTED="android com.android.systemui com.android.settings com.android.tv.settings com.android.vending
com.google.android.gms com.google.android.gsf com.android.providers.settings com.android.providers.tv
com.google.android.webview com.android.webview com.android.bluetooth com.android.phone com.android.shell
com.android.packageinstaller com.google.android.packageinstaller com.android.inputdevices com.android.externalstorage
com.android.providers.media com.android.providers.downloads com.google.android.inputmethod.latin $ZEN_PKG"
PROTECTED="$(printf '%s' "$PROTECTED" | tr ' ' '\n')"

Disable_Packages() {
    local why="$1" homePkg skipped=0 p r; shift
    Get_State
    homePkg="$(Get_Home)"; homePkg="${homePkg%%/*}"
    for p in "$@"; do
        if in_list "$p" "$PROTECTED"; then Warn "$p protégé, ignoré" "$p is protected, skipped"; continue; fi
        if [ "$p" = "$homePkg" ] && [ "$why" != "launcher" ]; then Warn "$p est l'écran d'accueil actuel : ignoré (voir menu 3)" "$p is the current home screen: skipped (see menu 3)"; continue; fi
        r="$(Sh "pm disable-user --user 0 $p" | tr '\n' ' ')"
        case "$r" in
            *disabled*) Ok "$p" "$p"; in_list "$p" "$ST_DISABLED" || ST_DISABLED="$ST_DISABLED${ST_DISABLED:+
}$p" ;;
            *)  # la TV refuse (paquet systeme verrouille, absent, etc.) : on ignore et on continue
                printf '%s  - %s%s%s\n' "$C_DarkGray" "$p" "$(T " : ignoré (non modifiable sur cette TV)" " : skipped (cannot be changed on this TV)")" "$C_0"
                skipped=$((skipped + 1)) ;;
        esac
    done
    Save_State
    [ $skipped -gt 0 ] && Say "  $skipped élément(s) ignoré(s) : c'est normal, rien n'a été modifié pour eux." "  $skipped item(s) skipped: that's normal, nothing was changed for them." DarkGray
    return 0
}

# ne garde que les paquets presents et actifs sur CETTE TV (les autres sont ignores)
Filter_List() {
    local i p keep
    F_PKGS=(); F_FR=(); F_EN=()
    for i in "${!L_PKGS[@]}"; do
        keep=""
        for p in ${L_PKGS[$i]}; do in_list "$p" "$ENABLED" && [ "$p" != "$HOME_PKG" ] && keep="$keep${keep:+ }$p"; done
        [ -n "$keep" ] && { F_PKGS[${#F_PKGS[@]}]="$keep"; F_FR[${#F_FR[@]}]="${L_FR[$i]}"; F_EN[${#F_EN[@]}]="${L_EN[$i]}"; }
    done
}

# ---------------------------------------------------------------- 2. allègement
Do_Slim() {
    local i lbl toOff="" safeAll="" b_dispo b_cpu k
    Ensure_Backup
    ENABLED="$(Get_Packages -e)"
    HOME_PKG="$(Get_Home)"; HOME_PKG="${HOME_PKG%%/*}"
    Read_List "pubs_telemetrie.txt"; Filter_List
    S_PKGS=("${F_PKGS[@]}"); S_FR=("${F_FR[@]}"); S_EN=("${F_EN[@]}")
    Read_List "optionnel.txt"; Filter_List
    Get_Perf; b_dispo="$PERF_DISPO"; b_cpu="$PERF_CPU"
    Capture_TV "avant_allegement"
    echo
    if [ ${#S_PKGS[@]} -gt 0 ]; then
        Say "Publicités, télémétrie et services inutiles trouvés sur cette TV :" "Ads, telemetry and useless services found on this TV:" White
        for i in "${!S_PKGS[@]}"; do
            printf '   - %-48s %s\n' "$(printf '%s' "${S_PKGS[$i]}" | sed 's/ /, /g')" "$(T "${S_FR[$i]}" "${S_EN[$i]}")"
            safeAll="$safeAll ${S_PKGS[$i]}"
        done
        if YesNo "Les désactiver (réversible, rien n'est désinstallé) ?" "Disable them (reversible, nothing is uninstalled)?" 1; then
            Disable_Packages slim $safeAll
        fi
    else Ok "Aucune pub/télémétrie connue trouvée." "No known ads/telemetry found."; fi

    if [ ${#F_PKGS[@]} -gt 0 ]; then
        echo
        Say "Fonctions et applis que vous utilisez peut-être (une question par élément) :" "Features and apps you may use (one question each):" White
        for i in "${!F_PKGS[@]}"; do
            lbl="$(T "${F_FR[$i]}" "${F_EN[$i]}")"
            YesNo "   Utilisez-vous : $lbl ?" "   Do you use: $lbl ?" 1 || toOff="$toOff ${F_PKGS[$i]}"
        done
        [ -n "$toOff" ] && Disable_Packages slim $toOff
    fi

    echo
    if YesNo "Accélérer les animations (×0,5) ?" "Speed up animations (×0.5)?" 1; then
        for k in window_animation_scale transition_animation_scale animator_duration_scale; do Sh "settings put global $k 0.5" >/dev/null; done
        Ok "Animations ×0,5" "Animations ×0.5"
    fi
    Say "Libération de la mémoire..." "Freeing memory..." Cyan
    Sh "am kill-all" >/dev/null
    sleep 15
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    sleep 6
    Get_Perf
    Capture_TV "apres_allegement"
    echo
    printf '%s   RAM %s : %s Mo → %s Mo%s\n' "$C_White" "$(T "disponible" "available")" "$b_dispo" "$PERF_DISPO" "$C_0"
    printf '%s   CPU : %s %% → %s %%  %s%s\n' "$C_White" "$b_cpu" "$PERF_CPU" "$(T "(instantané, varie d'une mesure à l'autre)" "(snapshot, varies between readings)")" "$C_0"
    Say "Astuce : le plus gros gain vient souvent du remplacement de l'écran d'accueil (menu 3)." "Tip: the biggest gain often comes from replacing the home screen (menu 3)." DarkGray
}

# ---------------------------------------------------------------- 3. écran d'accueil
DOCK_PKGS=(com.netflix.ninja com.google.android.youtube.tv com.amazon.amazonvideo.livingroom
    com.disney.disneyplus com.canal.android.canal com.wbd.stream
    com.apple.atve.androidtv.appletv com.spotify.tv.android com.plexapp.android
    fr.francetv.pluzz fr.tf1.mytf1 fr.m6.m6replay tv.arte.plus7
    tv.molotov.app com.cbs.ott com.dazn tv.twitch.android.app
    org.xbmc.kodi com.google.android.youtube.tvkids)
DOCK_NAMES=("Netflix" "YouTube" "Prime Video"
    "Disney+" "myCANAL" "Max"
    "Apple TV" "Spotify" "Plex"
    "france.tv" "TF1+" "M6+" "Arte"
    "Molotov" "Paramount+" "DAZN" "Twitch"
    "Kodi" "YouTube Kids")
# Android 10+ : l'accueil par défaut est un « rôle » système, en plus de set-home-activity
Set_HomeRole() { [ "${INFO_Sdk:-0}" -ge 29 ] 2>/dev/null && Sh "cmd role add-role-holder android.app.role.HOME $1" >/dev/null; return 0; }

# Désactive l'accueil d'origine (et l'écran de secours Google TV), puis vérifie que ProxFibre a bien pris la place.
# Si la TV n'a plus d'accueil utilisable, tout est réactivé aussitôt : la TV ne peut pas rester sans accueil.
Disable_OrigHome() {
    local orig="$1" toOff q
    toOff="$orig"
    # Google TV : sans son accueil, l'écran de secours setupwraith prend la main (logo Google TV en boucle)
    in_list "$GTV_FALLBACK" "$(Get_Packages -e)" && toOff="$toOff $GTV_FALLBACK"
    Disable_Packages launcher $toOff
    Get_State; ST_LAUNCHER_DISABLED="$orig"; Save_State
    Sh "cmd package set-home-activity $ZEN_PKG/.Home" >/dev/null
    Set_HomeRole "$ZEN_PKG"
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    sleep 5
    case "$(Get_Home)" in "$ZEN_PKG"/*) return 0 ;; esac
    Err "La TV n'accepte pas ProxFibre sans son accueil d'origine : réactivation immédiate, rien n'est perdu." "The TV won't accept ProxFibre without its original home screen: re-enabling it now, nothing is lost."
    for q in $toOff; do Sh "pm enable $q" >/dev/null; done
    Get_State
    for q in $toOff; do ST_DISABLED="$(printf '%s\n' "$ST_DISABLED" | grep -vxF -- "$q")"; done
    ST_LAUNCHER_DISABLED=""; Save_State
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    return 1
}

Do_Launcher() {
    local curHome title titleMode devName lc layout enabled i n k sel dockCsv="auto" found=() fnames=() pk=() r origPkg nowHome hasOrig
    case "$INFO_Brand $INFO_Manufacturer" in *Amazon*)
        Err "Fire TV : Amazon bloque le remplacement de l'écran d'accueil. Utilisez seulement l'allègement (menu 2)." "Fire TV: Amazon blocks replacing the home screen. Use slimming only (menu 2)."
        return ;;
    esac
    if [ ! -f "$APK_PATH" ]; then Err "ProxFibre.apk introuvable dans ecran-accueil/" "ProxFibre.apk not found in ecran-accueil/"; return; fi
    Ensure_Backup
    Get_State
    curHome="$(Get_Home)"
    case "$curHome" in ""|"$ZEN_PKG"/*) ;; *) [ -z "$ST_ORIGINAL_HOME" ] && { ST_ORIGINAL_HOME="$curHome"; Save_State; } ;; esac

    echo
    # --- titre ---
    devName="$(trim "$(Sh "settings get global device_name" | tr -d '\n')")"
    { [ -z "$devName" ] || [ "$devName" = "null" ]; } && devName="$INFO_Model"
    Say "Titre affiché en haut à gauche de l'écran d'accueil :" "Title shown at the top left of the home screen:" White
    Say "   Entrée = nom de la TV (« $devName »)   ·   tapez - pour ne rien afficher" "   Enter = the TV's name ('$devName')   ·   type - to show nothing" DarkGray
    title="$(trim "$(Ask "Titre (ex. « Salon », « Chambre de Léa »)" "Title (e.g. 'Living room', 'Emma's room')")")"
    if [ -z "$title" ]; then titleMode="device"; elif [ "$title" = "-" ]; then titleMode="none"; else titleMode="custom"; fi

    # --- disposition ---
    echo
    Say "Disposition du dock :" "Dock layout:" White
    echo "   1. $(T "4 applis, grandes tuiles" "4 apps, large tiles")"
    echo "   2. $(T "6 applis sur une ligne" "6 apps in one row")"
    echo "   3. $(T "8 applis sur deux lignes" "8 apps in two rows")"
    lc="$(trim "$(Ask "Votre choix (Entrée = 1)" "Your choice (Enter = 1)")")"
    case "$lc" in 2) layout=6 ;; 3) layout=8 ;; *) layout=4 ;; esac

    # --- applis du dock ---
    enabled="$(Get_Packages -e)"
    for i in "${!DOCK_PKGS[@]}"; do
        in_list "${DOCK_PKGS[$i]}" "$enabled" && { found[${#found[@]}]="${DOCK_PKGS[$i]}"; fnames[${#fnames[@]}]="${DOCK_NAMES[$i]}"; }
    done
    if [ ${#found[@]} -gt 0 ]; then
        echo
        Say "Applis trouvées pour le dock ($layout maximum) :" "Apps found for the dock ($layout max):" White
        for i in "${!found[@]}"; do printf '   %d. %s\n' $((i + 1)) "${fnames[$i]}"; done
        sel="$(Ask "Numéros dans l'ordre voulu, ex. 1,2,4 (Entrée = choix automatique)" "Numbers in the order you want, e.g. 1,2,4 (Enter = automatic)")"
        if [ -n "$(trim "$sel")" ]; then
            for n in $(printf '%s' "$sel" | tr ',;' '  '); do
                case "$n" in *[!0-9]*|"") continue ;; esac
                k=$((10#$n - 1))
                if [ $k -ge 0 ] && [ $k -lt ${#found[@]} ] && [ ${#pk[@]} -lt $layout ]; then
                    in_list "${found[$k]}" "$(printf '%s\n' "${pk[@]}")" || pk[${#pk[@]}]="${found[$k]}"
                fi
            done
            [ ${#pk[@]} -gt 0 ] && dockCsv="$(IFS=,; printf '%s' "${pk[*]}")"
        fi
    fi
    Say "   Vous pourrez tout changer ensuite directement sur la TV (appui long sur OK)." "   You can change everything later right on the TV (long-press OK)." DarkGray

    Say "Installation de ProxFibre..." "Installing ProxFibre..." Cyan
    Say ">>> Regardez la TV : si Google Play Protect affiche un avertissement, choisissez « Détails » puis « Installer quand même »." ">>> Look at the TV: if Google Play Protect shows a warning, choose 'Details' then 'Install anyway'." Cyan
    r="$(A install -r "$APK_PATH" | tr '\n' ' ')"
    case "$r" in *Success*) ;; *) Err "Échec de l'installation : $r" "Install failed: $r"; Say "   Si la TV a affiché un avertissement Play Protect, relancez l'option 3 et choisissez « Détails » puis « Installer quand même »." "   If the TV showed a Play Protect warning, run option 3 again and choose 'Details' then 'Install anyway'." DarkGray; return ;; esac
    Ok "Installé" "Installed"
    Sh "settings put global zenhome_title_mode $titleMode" >/dev/null
    if [ "$titleMode" = "custom" ]; then Sh "settings put global zenhome_title_b64 $(printf '%s' "$title" | base64 | tr -d '\n')" >/dev/null
    else Sh "settings delete global zenhome_title_b64" >/dev/null; fi
    Sh "settings put global zenhome_layout $layout" >/dev/null
    Sh "settings put global zenhome_dock $dockCsv" >/dev/null
    Sh "settings put global zenhome_rev $(date +%s)" >/dev/null   # la TV applique cette config
    Sh "pm grant $ZEN_PKG android.permission.READ_EXTERNAL_STORAGE" >/dev/null

    Sh "cmd package set-home-activity $ZEN_PKG/.Home" >/dev/null
    Set_HomeRole "$ZEN_PKG"
    Sh "am force-stop $ZEN_PKG" >/dev/null
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    sleep 6
    nowHome="$(Get_Home)"
    origPkg="${ST_ORIGINAL_HOME%%/*}"
    hasOrig=0; [ -n "$origPkg" ] && [ "$origPkg" != "$ZEN_PKG" ] && hasOrig=1
    echo
    case "$nowHome" in
        "$ZEN_PKG"/*)
            Ok "ProxFibre est l'écran d'accueil." "ProxFibre is the home screen."
            if [ $hasOrig = 1 ]; then
                Say "L'écran d'accueil d'origine ($origPkg) tourne souvent en fond et consomme beaucoup." "The original home screen ($origPkg) often runs in the background and uses a lot of resources." White
                Warn "Sur certaines TV (surtout Google TV), la touche Source de la télécommande ne marche plus dans les applis une fois l'accueil d'origine désactivé. L'option 6 remet tout." "On some TVs (especially Google TV), the remote's Source button stops working inside apps once the original home screen is disabled. Option 6 puts everything back."
                if YesNo "Le désactiver (recommandé, réversible) ?" "Disable it (recommended, reversible)?" 1; then Disable_OrigHome "$origPkg"; fi
            fi ;;
        *)
            if [ $hasOrig = 1 ]; then
                # Google TV et d'autres refusent un autre accueil tant que le leur est actif : seule solution, le désactiver
                Warn "La TV garde son accueil d'origine ($origPkg) et refuse ProxFibre tant qu'il est actif (fréquent sur Google TV)." "The TV keeps its original home screen ($origPkg) and refuses ProxFibre while it is active (common on Google TV)."
                Say "Le kit peut le désactiver pour forcer ProxFibre. Il vérifie ensuite que la TV accepte ProxFibre, et réactive tout de suite l'accueil d'origine sinon." "The kit can disable it to force ProxFibre. It then checks that the TV accepts ProxFibre, and re-enables the original home screen right away otherwise." White
                Warn "Sur certaines TV (surtout Google TV), la touche Source de la télécommande ne marche plus dans les applis une fois l'accueil d'origine désactivé. L'option 6 remet tout." "On some TVs (especially Google TV), the remote's Source button stops working inside apps once the original home screen is disabled. Option 6 puts everything back."
                if YesNo "Forcer ProxFibre (réversible) ?" "Force ProxFibre (reversible)?" 1; then
                    Disable_OrigHome "$origPkg" && Ok "ProxFibre est l'écran d'accueil." "ProxFibre is the home screen."
                else Say "Rien n'a été désactivé. ProxFibre reste disponible dans « Toutes les applis »." "Nothing was disabled. ProxFibre stays available in 'All apps'." DarkGray; fi
            else
                Warn "La TV n'a pas confirmé ProxFibre par défaut ($nowHome). Appuyez sur la touche Accueil : si un choix s'affiche, prenez « ProxFibre » puis « Toujours »." "The TV did not confirm ProxFibre as default ($nowHome). Press Home: if a chooser appears, pick 'ProxFibre' then 'Always'."
            fi ;;
    esac
    Capture_TV "zen_home"
    echo
    Say "Sur la TV : appui long sur OK (ou touche Menu) sur une appli du dock pour la déplacer, la remplacer, la retirer, changer la disposition ou le titre." "On the TV: long-press OK (or the Menu key) on a dock app to move, replace or remove it, or change the layout or title." Cyan
    Get_Perf
    printf '%s   RAM %s : %s Mo%s\n' "$C_White" "$(T "disponible" "available")" "$PERF_DISPO" "$C_0"
}

# ---------------------------------------------------------------- 4. photos perso
Do_Photos() {
    local files=() f iv
    mkdir -p "$PHOTOS_DIR"
    for f in "$PHOTOS_DIR"/*; do
        [ -f "$f" ] || continue
        case "$(printf '%s' "$f" | tr '[:upper:]' '[:lower:]')" in *.jpg|*.jpeg|*.png|*.webp) files[${#files[@]}]="$f" ;; esac
    done
    echo
    Say "Dossier de vos photos : $PHOTOS_DIR" "Your photos folder: $PHOTOS_DIR" White
    if [ ${#files[@]} -eq 0 ]; then
        Warn "Aucune photo dedans. Copiez-y des .jpg (paysage, 1920×1080 idéalement) puis revenez ici." "No photos in it. Copy .jpg files there (landscape, ideally 1920×1080) then come back."
        if YesNo "Revenir aux photos fournies avec ProxFibre ?" "Go back to the photos bundled with ProxFibre?" 0; then
            Sh "rm -rf /sdcard/ZenHome" >/dev/null; Sh "am force-stop $ZEN_PKG" >/dev/null; Sh "input keyevent KEYCODE_HOME" >/dev/null
            Ok "Photos d'origine rétablies." "Bundled photos restored."
        fi
        open_path "$PHOTOS_DIR"
        return
    fi
    Say "${#files[@]} photo(s) à envoyer." "${#files[@]} photo(s) to send." White
    Sh "rm -rf /sdcard/ZenHome" >/dev/null
    Sh "mkdir -p /sdcard/ZenHome" >/dev/null
    for f in "${files[@]}"; do A push "$f" "/sdcard/ZenHome/$(basename "$f")" >/dev/null; printf '   → %s\n' "$(basename "$f")"; done
    Sh "pm grant $ZEN_PKG android.permission.READ_EXTERNAL_STORAGE" >/dev/null
    Sh "am force-stop $ZEN_PKG" >/dev/null
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    sleep 5
    Capture_TV "photos_perso"
    Ok "Photos envoyées. Elles remplacent celles fournies." "Photos sent. They replace the bundled ones."
    iv="$(Ask "Secondes entre deux photos (Entrée = 60)" "Seconds between photos (Enter = 60)")"
    case "$iv" in ""|*[!0-9]*) ;; *) Sh "settings put global zenhome_interval $iv" >/dev/null; Sh "am force-stop $ZEN_PKG" >/dev/null; Sh "input keyevent KEYCODE_HOME" >/dev/null ;; esac
}

# ---------------------------------------------------------------- 6. restauration
Do_Restore() {
    local p r k v
    Get_State
    echo
    Say "Restauration : réactive tout ce que le kit a désactivé sur cette TV, remet l'écran d'accueil et les animations d'origine." "Restore: re-enables everything the kit disabled on this TV, puts back the original home screen and animations." White
    YesNo "Continuer ?" "Continue?" 1 || return
    for p in $ST_DISABLED; do
        r="$(Sh "pm enable $p" | tr '\n' ' ')"
        case "$r" in
            *enabled*) Ok "$p" "$p" ;;
            *) printf '%s  - %s%s%s\n' "$C_DarkGray" "$p" "$(T " : déjà actif ou introuvable, ignoré" " : already enabled or not found, skipped")" "$C_0" ;;
        esac
    done
    if [ "$ST_HAS_ANIM" = 1 ]; then
        for k in window_animation_scale:"$ST_ANIM_WINDOW" transition_animation_scale:"$ST_ANIM_TRANSITION" animator_duration_scale:"$ST_ANIM_ANIMATOR"; do
            v="${k#*:}"; k="${k%%:*}"
            if [ -n "$v" ] && [ "$v" != "null" ]; then Sh "settings put global $k $v" >/dev/null; else Sh "settings delete global $k" >/dev/null; fi
        done
        Ok "Animations d'origine" "Original animations"
    fi
    case "$ST_ORIGINAL_HOME" in ""|"$ZEN_PKG"/*) ;; *) Sh "cmd package set-home-activity $ST_ORIGINAL_HOME" >/dev/null; Set_HomeRole "${ST_ORIGINAL_HOME%%/*}"; Ok "Écran d'accueil : $ST_ORIGINAL_HOME" "Home screen: $ST_ORIGINAL_HOME" ;; esac
    if in_list "$ZEN_PKG" "$(Get_Packages "")"; then
        if YesNo "Désinstaller ProxFibre ?" "Uninstall ProxFibre?" 1; then A uninstall "$ZEN_PKG" >/dev/null; Ok "ProxFibre désinstallé" "ProxFibre uninstalled"; fi
        for k in zenhome_title_b64 zenhome_title_mode zenhome_layout zenhome_dock zenhome_rev zenhome_interval; do Sh "settings delete global $k" >/dev/null; done
        Sh "rm -rf /sdcard/ZenHome" >/dev/null
    fi
    ST_DISABLED=""; ST_LAUNCHER_DISABLED=""
    Save_State
    Sh "input keyevent KEYCODE_HOME" >/dev/null
    sleep 6
    Capture_TV "apres_restauration"
    Ok "Restauration terminée. Redémarrez la TV pour finir proprement." "Restore done. Restart the TV to finish cleanly."
}

Run_Choice() {
    Ensure_TV || return 0
    case "$1" in
        1) Do_Backup ;;
        2) Do_Slim ;;
        3) Do_Launcher ;;
        4) Do_Photos ;;
        5) Capture_TV "capture"; [ -f "$CAPTURE" ] && open_path "$CAPTURE" ;;
        6) Do_Restore ;;
    esac
}

# ---------------------------------------------------------------- menu
while true; do
    clear
    printf '%s  ╔══════════════════════════════════════════════╗%s\n' "$C_DarkCyan" "$C_0"
    printf '%s  ║   %s  ║%s\n' "$C_DarkCyan" "$(T "ProxFibre TV - allègement + accueil      " "ProxFibre TV - slimming + launcher       ")" "$C_0"
    printf '%s  ╚══════════════════════════════════════════════╝%s\n' "$C_DarkCyan" "$C_0"
    if [ -f "$CONF_FILE" ]; then printf '%s   TV : %s  (%s)%s\n' "$C_Gray" "$(json_str "$CONF_FILE" label)" "$(json_str "$CONF_FILE" target)" "$C_0"
    else Say "   Aucune TV choisie" "   No TV selected" DarkGray; fi
    echo
    echo "   1. $(T "Diagnostic + sauvegarde (ne modifie rien)" "Diagnosis + backup (changes nothing)")"
    echo "   2. $(T "Alléger : pubs, télémétrie, applis inutiles" "Slim down: ads, telemetry, useless apps")"
    echo "   3. $(T "Installer l'écran d'accueil ProxFibre" "Install the ProxFibre launcher")"
    echo "   4. $(T "Mettre mes propres photos" "Use my own photos")"
    echo "   5. $(T "Capture d'écran de la TV" "Take a TV screenshot")"
    echo "   6. $(T "Tout restaurer comme avant" "Restore everything as before")"
    echo "   7. $(T "Choisir une autre TV" "Choose another TV")"
    echo "   0. $(T "Quitter" "Quit")"
    echo
    ch="$(trim "$(Ask "Votre choix" "Your choice")")"
    [ "$ch" = "0" ] && break
    if [ "$ch" = "7" ]; then rm -f "$CONF_FILE"; Choose_TV; Pause_Kit; continue; fi
    case "$ch" in 1|2|3|4|5|6) ;; *) continue ;; esac
    log="$SAVES/journal_$(date +%Y%m%d).txt"
    # journal de session, comme Start-Transcript dans kit.ps1
    { echo "===== $(date) - menu $ch ====="; Run_Choice "$ch"; } 2>&1 | tee -a "$log"
    Pause_Kit
done
