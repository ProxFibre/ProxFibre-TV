# =====================================================================
#  ProxFibre TV - allègement réversible + écran d'accueil
#  ProxFibre TV - reversible debloat + launcher
#  Windows PowerShell 5.1+  -  lancé par LANCER.bat / started by LANCER.bat
# =====================================================================
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Kit      = Split-Path -Parent $PSScriptRoot
$Scripts  = $PSScriptRoot
$Tools    = Join-Path $Kit "outils"
$Saves    = Join-Path $Kit "sauvegardes"
$ApkPath  = Join-Path $Kit "ecran-accueil\ProxFibre.apk"
$PhotosDir= Join-Path $Kit "mes_photos"
$ConfFile = Join-Path $Kit "config_tv.json"
$ZenPkg   = "tv.proxfibre"
$GtvFallback = "com.google.android.tungsten.setupwraith"   # accueil de secours de Google TV
$LangFr = ((Get-UICulture).Name -like "fr*") -or ((Get-Culture).Name -like "fr*")
New-Item -ItemType Directory -Force $Tools, $Saves | Out-Null

function T([string]$txtFr, [string]$txtEn) { if ($script:LangFr) { $txtFr } else { $txtEn } }
function Say([string]$fr, [string]$en, [string]$color = "Gray") { Write-Host (T $fr $en) -ForegroundColor $color }
function Ok([string]$fr, [string]$en)   { Say ("  ✔ " + $fr) ("  ✔ " + $en) "Green" }
function Warn([string]$fr, [string]$en) { Say ("  ! " + $fr) ("  ! " + $en) "Yellow" }
function Err([string]$fr, [string]$en)  { Say ("  ✖ " + $fr) ("  ✖ " + $en) "Red" }
function Ask([string]$fr, [string]$en) { Read-Host (T $fr $en) }
function YesNo([string]$fr, [string]$en, [bool]$default = $true) {
    $sfx = if ($default) { T " [O/n]" " [Y/n]" } else { T " [o/N]" " [y/N]" }
    $r = (Read-Host ((T $fr $en) + $sfx)).Trim().ToLower()
    if ($r -eq "") { return $default }
    return ($r -in @("o","oui","y","yes"))
}
function Pause-Kit { [void](Read-Host (T "`nEntrée pour revenir au menu" "`nPress Enter to go back to the menu")) }

# ---------------------------------------------------------------- ADB
function Get-Adb {
    $p = Join-Path $Tools "platform-tools\adb.exe"
    if (Test-Path $p) { return $p }
    $g = Get-Command adb -ErrorAction SilentlyContinue
    if ($g) { return $g.Source }
    Say "Téléchargement des outils ADB officiels de Google (environ 7 Mo)..." "Downloading Google's official ADB tools (about 7 MB)..." "Cyan"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $zip = Join-Path $Tools "platform-tools.zip"
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $Tools -Force
    Remove-Item $zip
    return $p
}
$adb = Get-Adb
$script:T_ = $null      # ip:port de la TV
$script:Id = $null      # identifiant unique de la TV
$script:Info = $null

function A { & $adb -s $script:T_ @args 2>&1 | ForEach-Object { "$_" } }
function Sh([string]$cmd) { (A shell $cmd) }
function Prop([string]$k) { ((Sh "getprop $k") -join "").Trim() }

function Get-TvIdentity {
    $serial = Prop "ro.serialno"
    if (-not $serial -or $serial -eq "unknown") { $serial = Prop "ro.boot.serialno" }
    $aid = ((Sh "settings get secure android_id") -join "").Trim()
    $id = if ($serial -and $serial -ne "unknown") { $serial } else { "aid-$aid" }
    [pscustomobject]@{
        Id = ($id -replace '[^A-Za-z0-9_-]', '_')
        Brand = Prop "ro.product.brand"; Model = Prop "ro.product.model"
        Manufacturer = Prop "ro.product.manufacturer"
        Android = Prop "ro.build.version.release"; Sdk = Prop "ro.build.version.sdk"
        Patch = Prop "ro.build.version.security_patch"; Build = Prop "ro.build.display.id"
        Name = ((Sh "settings get global device_name") -join "").Trim()
    }
}

function Connect-Target([string]$target) {
    if ($target -notmatch ":") { $target = "$target`:5555" }
    $script:T_ = $target
    $asked = $false; $refused = 0; $unreach = 0
    for ($i = 0; $i -lt 60; $i++) {
        $out = ((& $adb connect $target 2>&1) -join " ")
        $st = ((& $adb -s $target get-state 2>&1) -join "")
        if ($st -match "^device") { return $true }
        # on distingue : TV joignable qui attend l'autorisation / TV qui refuse (débogage réseau inactif) / TV injoignable
        if ("$out $st" -match "unauthorized|authenticate") {
            if (-not $asked) { Say ">>> Regardez la TV : « Autoriser le débogage ? » doit s'afficher. Cochez « Toujours autoriser » puis OK." ">>> Look at the TV: 'Allow debugging?' should appear. Tick 'Always allow' then OK." "Cyan"; $asked = $true }
        }
        elseif ($out -match "refused|10061") {
            if (++$refused -ge 3) {
                Err "La TV répond mais refuse la connexion : son débogage réseau n'est pas actif." "The TV answers but refuses the connection: its network debugging is not enabled."
                Say "   Dans les options pour les développeurs, activez « Débogage ADB réseau » (ou « ADB over network »). Sur Android 11 et plus, utilisez « Débogage sans fil » et le jumelage par code." "   In developer options, enable 'Network ADB debugging' (or 'ADB over network'). On Android 11 and later, use 'Wireless debugging' and pairing with a code." "DarkGray"
                return $false
            }
        }
        elseif ($out -match "timed out|10060|unreachable|10065|No route") {
            if (++$unreach -ge 2) {
                Err "Aucun appareil ne répond à l'adresse $target." "No device answers at $target."
                Say "   Vérifiez l'adresse IP, que la TV est allumée (pas en veille) et sur le même réseau que l'ordinateur (pas un réseau invité). Un VPN sur l'ordinateur peut aussi bloquer." "   Check the IP address, that the TV is on (not in standby) and on the same network as the computer (not a guest network). A VPN on the computer can also block it." "DarkGray"
                return $false
            }
        }
        elseif ($i -eq 1 -and -not $asked) { Say ">>> Regardez la TV : si « Autoriser le débogage ? » s'affiche, cochez « Toujours autoriser » puis OK." ">>> Look at the TV: if 'Allow debugging?' appears, tick 'Always allow' then OK." "Cyan"; $asked = $true }
        Start-Sleep 2
    }
    return $false
}

function Choose-TV {
    Write-Host ""
    Say "Trouvez l'adresse IP de la TV : Paramètres > Réseau (ou À propos > État)." "Find the TV's IP address: Settings > Network (or About > Status)." "Cyan"
    Say "Le débogage ADB doit être activé (voir README)." "ADB debugging must be enabled (see README)." "DarkGray"
    $ip = (Ask "Adresse IP de la TV (ex. 192.168.1.50)" "TV IP address (e.g. 192.168.1.50)").Trim()
    if (-not $ip) { return $false }
    if (-not (Connect-Target $ip)) {
        Err "Connexion impossible." "Could not connect."
        if (YesNo "Votre TV affiche-t-elle un code de jumelage (Débogage sans fil, Android 11+) ?" "Does your TV show a pairing code (Wireless debugging, Android 11+)?" $false) {
            $pp = (Ask "IP:port de JUMELAGE affiché sur la TV" "PAIRING IP:port shown on the TV").Trim()
            $code = (Ask "Code de jumelage" "Pairing code").Trim()
            & $adb pair $pp $code
            $cp = (Ask "IP:port de CONNEXION (écran Débogage sans fil)" "CONNECTION IP:port (Wireless debugging screen)").Trim()
            if (-not (Connect-Target $cp)) { Err "Toujours impossible. Voir README > Dépannage." "Still failing. See README > Troubleshooting."; return $false }
        } else { return $false }
    }
    $info = Get-TvIdentity
    Write-Host ""
    Say "Appareil trouvé :" "Device found:" "White"
    Write-Host ("   {0} {1}  ({2})  - Android {3}, patch {4}" -f $info.Brand, $info.Model, $info.Name, $info.Android, $info.Patch)
    Write-Host ("   ID : {0}" -f $info.Id)
    if (-not (YesNo "Est-ce bien la TV que vous voulez modifier ?" "Is this the TV you want to modify?" $true)) {
        & $adb disconnect $script:T_ | Out-Null; $script:T_ = $null; return $false
    }
    $script:Id = $info.Id; $script:Info = $info
    @{ target = $script:T_; id = $info.Id; label = "$($info.Brand) $($info.Model)" } | ConvertTo-Json | Set-Content $ConfFile -Encoding UTF8
    Ok "TV enregistrée. Toutes les actions vérifieront qu'il s'agit bien d'elle." "TV saved. Every action will check it is the same TV."
    return $true
}

# Reconnecte la TV enregistrée et vérifie son identité avant toute action
function Ensure-TV {
    if (-not (Test-Path $ConfFile)) { return (Choose-TV) }
    $c = Get-Content $ConfFile -Raw | ConvertFrom-Json
    if (-not (Connect-Target $c.target)) {
        Err "La TV $($c.label) ($($c.target)) ne répond pas. Allumée ? Même Wi-Fi ? IP changée ?" "TV $($c.label) ($($c.target)) is not answering. On? Same Wi-Fi? IP changed?"
        if (YesNo "Saisir une autre adresse ?" "Enter another address?" $true) { Remove-Item $ConfFile; return (Choose-TV) }
        return $false
    }
    $info = Get-TvIdentity
    if ($info.Id -ne $c.id) {
        Err "SÉCURITÉ : l'appareil à cette adresse n'est PAS la TV enregistrée ($($info.Brand) $($info.Model)). Rien n'a été fait." "SAFETY: the device at this address is NOT the saved TV ($($info.Brand) $($info.Model)). Nothing was done."
        & $adb disconnect $script:T_ | Out-Null
        return $false
    }
    $script:Id = $info.Id; $script:Info = $info
    Ok "TV vérifiée : $($c.label)" "TV verified: $($c.label)"
    return $true
}

# ---------------------------------------------------------------- état / sauvegarde
function TvDir { $d = Join-Path $Saves $script:Id; New-Item -ItemType Directory -Force $d | Out-Null; $d }
function StateFile { Join-Path (TvDir) "etat_kit.json" }
function Get-State {
    $f = StateFile
    if (Test-Path $f) { $s = Get-Content $f -Raw | ConvertFrom-Json } else { $s = $null }
    $h = @{ disabled = @(); anim = $null; original_home = $null; launcher_disabled = $null; backup = $null }
    if ($s) { foreach ($p in $s.PSObject.Properties) { $h[$p.Name] = $p.Value } }
    if ($h.disabled -eq $null) { $h.disabled = @() }
    $h.disabled = @($h.disabled)
    return $h
}
function Save-State($h) { $h | ConvertTo-Json -Depth 4 | Set-Content (StateFile) -Encoding UTF8 }

function Capture-TV([string]$name) {
    $dir = Join-Path (TvDir) "captures"; New-Item -ItemType Directory -Force $dir | Out-Null
    $dest = Join-Path $dir ((Get-Date -Format "yyyyMMdd_HHmmss") + "_" + $name + ".png")
    A shell screencap -p /sdcard/_kit_cap.png | Out-Null
    A pull /sdcard/_kit_cap.png $dest | Out-Null
    A shell rm /sdcard/_kit_cap.png | Out-Null
    if (Test-Path $dest) { Write-Host ("   " + (T "Capture : " "Screenshot: ") + $dest) -ForegroundColor DarkGray }
    return $dest
}

function Get-Perf {
    $mem = (Sh "cat /proc/meminfo") -join "`n"
    $tot = if ($mem -match "MemTotal:\s+(\d+)") { [int]$Matches[1] / 1024 } else { 0 }
    $av  = if ($mem -match "MemAvailable:\s+(\d+)") { [int]$Matches[1] / 1024 } else { 0 }
    $cpu = $null
    $top = (Sh "top -b -n 1 -m 5") -join "`n"
    if ($top -match "(\d+)%cpu.*?(\d+)%idle") { $cpu = [math]::Round(100 * (1 - [double]$Matches[2] / [double]$Matches[1])) }
    [pscustomobject]@{ RamTotalMo = [math]::Round($tot); RamDispoMo = [math]::Round($av); CpuPct = $cpu }
}

function Get-Home {
    $r = Sh "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    $line = ($r | ForEach-Object { $_.Trim() } | Where-Object { $_ -match "^[\w\.]+/[\w\.\$]+$" } | Select-Object -Last 1)
    if ($line -and $line -notmatch "^android/") { return $line.Trim() }
    return $null
}
function Get-Packages([string]$flag) { Sh "pm list packages $flag" | Where-Object { $_ -like "package:*" } | ForEach-Object { ($_ -replace "^package:", "").Trim() } }

function Do-Backup([bool]$quiet = $false) {
    $d = Join-Path (TvDir) ("sauvegarde_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
    New-Item -ItemType Directory -Force $d | Out-Null
    Say "Sauvegarde de l'état de la TV (lecture seule)..." "Backing up the TV's state (read-only)..." "Cyan"
    $script:Info | Format-List | Out-String | Set-Content "$d\appareil.txt" -Encoding UTF8
    Sh "getprop" | Set-Content "$d\getprop.txt" -Encoding UTF8
    Sh "pm list packages -f" | Set-Content "$d\paquets_tous.txt" -Encoding UTF8
    Sh "pm list packages -d" | Set-Content "$d\paquets_desactives.txt" -Encoding UTF8
    Sh "pm list packages -3" | Set-Content "$d\paquets_installes_par_vous.txt" -Encoding UTF8
    foreach ($ns in "global","secure","system") { Sh "settings list $ns" | Set-Content "$d\settings_$ns.txt" -Encoding UTF8 }
    $homeAct = Get-Home
    "$homeAct" | Set-Content "$d\ecran_accueil.txt" -Encoding UTF8
    Sh "df -h" | Set-Content "$d\stockage.txt" -Encoding UTF8
    Sh "top -b -n 1 -m 25" | Set-Content "$d\processus.txt" -Encoding UTF8
    $wifi = (Sh "dumpsys wifi") -join "`n"
    $perf = Get-Perf
    $freq = if ($wifi -match "Frequency: (\d+)") { [int]$Matches[1] } else { 0 }
    $rssi = if ($wifi -match "RSSI: (-?\d+)") { [int]$Matches[1] } else { 0 }
    $speed= if ($wifi -match "Link speed: (\d+)") { [int]$Matches[1] } else { 0 }
    $st = Get-State
    if (-not $st.backup) { $st.backup = $d; $st.original_home = $homeAct }   # 1re sauvegarde = état d'origine
    if (-not $st.anim) {
        $st.anim = @{
            window = ((Sh "settings get global window_animation_scale") -join "").Trim()
            transition = ((Sh "settings get global transition_animation_scale") -join "").Trim()
            animator = ((Sh "settings get global animator_duration_scale") -join "").Trim()
        }
    }
    Save-State $st
    # ce que le kit a désactivé mais que la TV a réactivé d'elle-même (fréquent sur Google TV)
    if ($st.disabled.Count -gt 0) {
        $offNow = @(Get-Packages "-d")
        $back = @($st.disabled | Where-Object { $offNow -notcontains $_ })
        if ($back.Count -gt 0) { Warn ("Réactivés par la TV depuis le passage du kit : " + ($back -join ", ") + ". Relancez l'option 2 ou 3 si besoin.") ("Re-enabled by the TV since the kit ran: " + ($back -join ", ") + ". Run option 2 or 3 again if needed.") }
    }
    $cap = Capture-TV "etat_initial"
    if ($cap -and (Test-Path $cap)) { Copy-Item $cap "$d\capture.png" }
    $sum = @(
        (T "Appareil" "Device") + " : $($script:Info.Brand) $($script:Info.Model) - Android $($script:Info.Android) (SDK $($script:Info.Sdk)), patch $($script:Info.Patch)",
        (T "Écran d'accueil" "Home screen") + " : $homeAct",
        "RAM : $($perf.RamDispoMo) / $($perf.RamTotalMo) Mo " + (T "disponibles" "available"),
        (T "Processeur utilisé (instantané)" "CPU used (snapshot)") + " : $($perf.CpuPct) %",
        "Wi-Fi : " + $(if ($freq -gt 4000) { "5 GHz" } elseif ($freq -gt 0) { "2.4 GHz" } else { (T "câble ou inconnu" "cable or unknown") }) + ", $rssi dBm, $speed Mb/s"
    )
    $sum | Set-Content "$d\RESUME.txt" -Encoding UTF8
    if (-not $quiet) { Write-Host ""; $sum | ForEach-Object { Write-Host "   $_" } }
    if ($freq -gt 0 -and $freq -lt 3000) { Warn "La TV est en Wi-Fi 2,4 GHz : préférez le 5 GHz ou un câble pour le streaming." "The TV uses 2.4 GHz Wi-Fi: prefer 5 GHz or a cable for streaming." }
    if ($perf.RamTotalMo -gt 0 -and $perf.RamDispoMo -lt 400) { Warn "Peu de mémoire disponible : l'allègement (menu 2) devrait aider." "Low available memory: slimming (menu 2) should help." }
    Ok "Sauvegarde : $d" "Backup: $d"
    return $d
}
function Ensure-Backup { $st = Get-State; if (-not $st.backup) { Say "Première utilisation sur cette TV : sauvegarde d'abord." "First use on this TV: backing up first." "Cyan"; [void](Do-Backup $true) } }

# ---------------------------------------------------------------- listes
function Read-List([string]$file) {
    $out = @()
    foreach ($l in Get-Content (Join-Path $Scripts "listes\$file") -Encoding UTF8) {
        $l = $l.Trim()
        if (-not $l -or $l.StartsWith("#")) { continue }
        $parts = $l -split "\|"
        # une ligne peut regrouper plusieurs paquets d'une meme fonction : paquet1,paquet2 | ...
        $pk = @($parts[0] -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $out += [pscustomobject]@{ Pkgs = $pk; Fr = $(if ($parts.Count -gt 1) { $parts[1].Trim() } else { "" }); En = $(if ($parts.Count -gt 2) { $parts[2].Trim() } else { "" }) }
    }
    return $out
}
$Protected = @("android","com.android.systemui","com.android.settings","com.android.tv.settings","com.android.vending",
    "com.google.android.gms","com.google.android.gsf","com.android.providers.settings","com.android.providers.tv",
    "com.google.android.webview","com.android.webview","com.android.bluetooth","com.android.phone","com.android.shell",
    "com.android.packageinstaller","com.google.android.packageinstaller","com.android.inputdevices","com.android.externalstorage",
    "com.android.providers.media","com.android.providers.downloads","com.google.android.inputmethod.latin",$ZenPkg)

function Disable-Packages([string[]]$pkgs, [string]$why) {
    $st = Get-State
    $homePkg = (Get-Home) -replace "/.*$", ""
    $skipped = 0
    foreach ($p in $pkgs) {
        if ($Protected -contains $p) { Warn "$p protégé, ignoré" "$p is protected, skipped"; continue }
        if ($p -eq $homePkg -and $why -ne "launcher") { Warn "$p est l'écran d'accueil actuel : ignoré (voir menu 3)" "$p is the current home screen: skipped (see menu 3)"; continue }
        $r = (Sh "pm disable-user --user 0 $p") -join " "
        if ($r -match "disabled") { Ok $p $p; if ($st.disabled -notcontains $p) { $st.disabled += $p } }
        else {
            # la TV refuse (paquet systeme verrouille, absent, etc.) : on ignore et on continue
            Write-Host ("  - " + $p + (T " : ignoré (non modifiable sur cette TV)" " : skipped (cannot be changed on this TV)")) -ForegroundColor DarkGray
            $skipped++
        }
    }
    Save-State $st
    if ($skipped -gt 0) { Say "  $skipped élément(s) ignoré(s) : c'est normal, rien n'a été modifié pour eux." "  $skipped item(s) skipped: that's normal, nothing was changed for them." "DarkGray" }
}

# ---------------------------------------------------------------- 2. allègement
function Do-Slim {
    Ensure-Backup
    $enabled = @(Get-Packages "-e")
    $homePkg = (Get-Home) -replace "/.*$", ""
    # ne garde que les paquets presents et actifs sur CETTE TV (les autres sont ignores)
    $keep = { param($e) $e.Pkgs = @($e.Pkgs | Where-Object { $enabled -contains $_ -and $_ -ne $homePkg }); $e.Pkgs.Count -gt 0 }
    $safe = @(Read-List "pubs_telemetrie.txt" | Where-Object { & $keep $_ })
    $opt  = @(Read-List "optionnel.txt"       | Where-Object { & $keep $_ })
    $before = Get-Perf
    Capture-TV "avant_allegement" | Out-Null
    Write-Host ""
    if ($safe.Count -gt 0) {
        Say "Publicités, télémétrie et services inutiles trouvés sur cette TV :" "Ads, telemetry and useless services found on this TV:" "White"
        $safe | ForEach-Object { Write-Host ("   - {0,-48} {1}" -f ($_.Pkgs -join ", "), $(if ($LangFr) { $_.Fr } else { $_.En })) }
        if (YesNo "Les désactiver (réversible, rien n'est désinstallé) ?" "Disable them (reversible, nothing is uninstalled)?" $true) {
            Disable-Packages @($safe | ForEach-Object { $_.Pkgs }) "slim"
        }
    } else { Ok "Aucune pub/télémétrie connue trouvée." "No known ads/telemetry found." }

    if ($opt.Count -gt 0) {
        Write-Host ""
        Say "Fonctions et applis que vous utilisez peut-être (une question par élément) :" "Features and apps you may use (one question each):" "White"
        $toOff = @()
        foreach ($o in $opt) {
            $lbl = if ($LangFr) { $o.Fr } else { $o.En }
            if (-not (YesNo "   Utilisez-vous : $lbl ?" "   Do you use: $lbl ?" $true)) { $toOff += $o.Pkgs }
        }
        if ($toOff.Count -gt 0) { Disable-Packages $toOff "slim" }
    }

    Write-Host ""
    if (YesNo "Accélérer les animations (×0,5) ?" "Speed up animations (×0.5)?" $true) {
        foreach ($k in "window_animation_scale","transition_animation_scale","animator_duration_scale") { Sh "settings put global $k 0.5" | Out-Null }
        Ok "Animations ×0,5" "Animations ×0.5"
    }
    Say "Libération de la mémoire..." "Freeing memory..." "Cyan"
    Sh "am kill-all" | Out-Null
    Start-Sleep 15
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep 6
    $after = Get-Perf
    Capture-TV "apres_allegement" | Out-Null
    Write-Host ""
    Write-Host ("   RAM " + (T "disponible" "available") + " : $($before.RamDispoMo) Mo → $($after.RamDispoMo) Mo") -ForegroundColor White
    Write-Host ("   CPU : $($before.CpuPct) % → $($after.CpuPct) %  " + (T "(instantané, varie d'une mesure à l'autre)" "(snapshot, varies between readings)")) -ForegroundColor White
    Say "Astuce : le plus gros gain vient souvent du remplacement de l'écran d'accueil (menu 3)." "Tip: the biggest gain often comes from replacing the home screen (menu 3)." "DarkGray"
}

# ---------------------------------------------------------------- 3. écran d'accueil
$KnownDock = [ordered]@{
    "com.netflix.ninja"="Netflix"; "com.google.android.youtube.tv"="YouTube"; "com.amazon.amazonvideo.livingroom"="Prime Video"
    "com.disney.disneyplus"="Disney+"; "com.canal.android.canal"="myCANAL"; "com.wbd.stream"="Max"
    "com.apple.atve.androidtv.appletv"="Apple TV"; "com.spotify.tv.android"="Spotify"; "com.plexapp.android"="Plex"
    "fr.francetv.pluzz"="france.tv"; "fr.tf1.mytf1"="TF1+"; "fr.m6.m6replay"="M6+"; "tv.arte.plus7"="Arte"
    "tv.molotov.app"="Molotov"; "com.cbs.ott"="Paramount+"; "com.dazn"="DAZN"; "tv.twitch.android.app"="Twitch"
    "org.xbmc.kodi"="Kodi"; "com.google.android.youtube.tvkids"="YouTube Kids"
}
# Android 10+ : l'accueil par défaut est un « rôle » système, en plus de set-home-activity
function Set-HomeRole([string]$pkg) {
    if ([int]("0" + $script:Info.Sdk) -ge 29) { Sh "cmd role add-role-holder android.app.role.HOME $pkg" | Out-Null }
}

# Désactive l'accueil d'origine (et l'écran de secours Google TV), puis vérifie que ProxFibre a bien pris la place.
# Si la TV n'a plus d'accueil utilisable, tout est réactivé aussitôt : la TV ne peut pas rester sans accueil.
function Disable-OrigHome([string]$origPkg) {
    $toOff = @($origPkg)
    # Google TV : sans son accueil, l'écran de secours setupwraith prend la main (logo Google TV en boucle)
    if ((Get-Packages "-e") -contains $GtvFallback) { $toOff += $GtvFallback }
    Disable-Packages $toOff "launcher"
    $st = Get-State; $st.launcher_disabled = $origPkg; Save-State $st
    Sh "cmd package set-home-activity $ZenPkg/.Home" | Out-Null
    Set-HomeRole $ZenPkg
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep 5
    if ((Get-Home) -like "$ZenPkg/*") { return $true }
    Err "La TV n'accepte pas ProxFibre sans son accueil d'origine : réactivation immédiate, rien n'est perdu." "The TV won't accept ProxFibre without its original home screen: re-enabling it now, nothing is lost."
    foreach ($q in $toOff) { Sh "pm enable $q" | Out-Null }
    $st = Get-State; $st.disabled = @($st.disabled | Where-Object { $toOff -notcontains $_ }); $st.launcher_disabled = $null; Save-State $st
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    return $false
}

function Do-Launcher {
    if ($script:Info.Brand -match "Amazon" -or $script:Info.Manufacturer -match "Amazon") {
        Err "Fire TV : Amazon bloque le remplacement de l'écran d'accueil. Utilisez seulement l'allègement (menu 2)." "Fire TV: Amazon blocks replacing the home screen. Use slimming only (menu 2)."
        return
    }
    if (-not (Test-Path $ApkPath)) { Err "ProxFibre.apk introuvable dans ecran-accueil\" "ProxFibre.apk not found in ecran-accueil\"; return }
    Ensure-Backup
    $st = Get-State
    $curHome = Get-Home
    if ($curHome -and $curHome -notlike "$ZenPkg/*" -and -not $st.original_home) { $st.original_home = $curHome; Save-State $st }

    Write-Host ""
    # --- titre ---
    $devName = ((Sh "settings get global device_name") -join "").Trim()
    if (-not $devName -or $devName -eq "null") { $devName = $script:Info.Model }
    Say "Titre affiché en haut à gauche de l'écran d'accueil :" "Title shown at the top left of the home screen:" "White"
    Say "   Entrée = nom de la TV (« $devName »)   ·   tapez - pour ne rien afficher" "   Enter = the TV's name ('$devName')   ·   type - to show nothing" "DarkGray"
    $title = (Ask "Titre (ex. « Salon », « Chambre de Léa »)" "Title (e.g. 'Living room', 'Emma's room')").Trim()
    $titleMode = if ($title -eq "") { "device" } elseif ($title -eq "-") { "none" } else { "custom" }

    # --- disposition ---
    Write-Host ""
    Say "Disposition du dock :" "Dock layout:" "White"
    Write-Host ("   1. " + (T "4 applis, grandes tuiles" "4 apps, large tiles"))
    Write-Host ("   2. " + (T "6 applis sur une ligne" "6 apps in one row"))
    Write-Host ("   3. " + (T "8 applis sur deux lignes" "8 apps in two rows"))
    $lc = (Ask "Votre choix (Entrée = 1)" "Your choice (Enter = 1)").Trim()
    $layout = switch ($lc) { "2" { 6 } "3" { 8 } default { 4 } }

    # --- applis du dock ---
    $enabled = @(Get-Packages "-e")
    $found = @($KnownDock.Keys | Where-Object { $enabled -contains $_ })
    $dockCsv = "auto"
    if ($found.Count -gt 0) {
        Write-Host ""
        Say "Applis trouvées pour le dock ($layout maximum) :" "Apps found for the dock ($layout max):" "White"
        for ($i = 0; $i -lt $found.Count; $i++) { Write-Host ("   {0}. {1}" -f ($i + 1), $KnownDock[$found[$i]]) }
        $sel = Ask "Numéros dans l'ordre voulu, ex. 1,2,4 (Entrée = choix automatique)" "Numbers in the order you want, e.g. 1,2,4 (Enter = automatic)"
        if ($sel.Trim()) {
            $pk = @(); foreach ($n in ($sel -split "[,; ]+")) { if ($n -match "^\d+$") { $k = [int]$n - 1; if ($k -ge 0 -and $k -lt $found.Count -and $pk -notcontains $found[$k]) { $pk += $found[$k] } } }
            if ($pk.Count -gt 0) { $dockCsv = ($pk | Select-Object -First $layout) -join "," }
        }
    }
    Say "   Vous pourrez tout changer ensuite directement sur la TV (appui long sur OK)." "   You can change everything later right on the TV (long-press OK)." "DarkGray"

    Say "Installation de ProxFibre..." "Installing ProxFibre..." "Cyan"
    Say ">>> Regardez la TV : si Google Play Protect affiche un avertissement, choisissez « Détails » puis « Installer quand même »." ">>> Look at the TV: if Google Play Protect shows a warning, choose 'Details' then 'Install anyway'." "Cyan"
    $r = (A install -r $ApkPath) -join " "
    if ($r -notmatch "Success") { Err "Échec de l'installation : $r" "Install failed: $r"; Say "   Si la TV a affiché un avertissement Play Protect, relancez l'option 3 et choisissez « Détails » puis « Installer quand même »." "   If the TV showed a Play Protect warning, run option 3 again and choose 'Details' then 'Install anyway'." "DarkGray"; return }
    Ok "Installé" "Installed"
    Sh "settings put global zenhome_title_mode $titleMode" | Out-Null
    if ($titleMode -eq "custom") { Sh ("settings put global zenhome_title_b64 " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($title))) | Out-Null }
    else { Sh "settings delete global zenhome_title_b64" | Out-Null }
    Sh "settings put global zenhome_layout $layout" | Out-Null
    Sh "settings put global zenhome_dock $dockCsv" | Out-Null
    Sh ("settings put global zenhome_rev " + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) | Out-Null   # la TV applique cette config
    Sh "pm grant $ZenPkg android.permission.READ_EXTERNAL_STORAGE" | Out-Null

    $setOk = ((Sh "cmd package set-home-activity $ZenPkg/.Home") -join " ")
    Set-HomeRole $ZenPkg
    Sh "am force-stop $ZenPkg" | Out-Null
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep 6
    $now = Get-Home
    $origPkg = if ($st.original_home) { $st.original_home -replace "/.*$", "" } else { $null }
    $hasOrig = $origPkg -and $origPkg -ne $ZenPkg
    Write-Host ""
    if ($now -like "$ZenPkg/*") {
        Ok "ProxFibre est l'écran d'accueil." "ProxFibre is the home screen."
        if ($hasOrig) {
            Say "L'écran d'accueil d'origine ($origPkg) tourne souvent en fond et consomme beaucoup." "The original home screen ($origPkg) often runs in the background and uses a lot of resources." "White"
            Warn "Sur certaines TV (surtout Google TV), la touche Source de la télécommande ne marche plus dans les applis une fois l'accueil d'origine désactivé. L'option 6 remet tout." "On some TVs (especially Google TV), the remote's Source button stops working inside apps once the original home screen is disabled. Option 6 puts everything back."
            if (YesNo "Le désactiver (recommandé, réversible) ?" "Disable it (recommended, reversible)?" $true) { [void](Disable-OrigHome $origPkg) }
        }
    }
    elseif ($hasOrig) {
        # Google TV et d'autres refusent un autre accueil tant que le leur est actif : seule solution, le désactiver
        Warn "La TV garde son accueil d'origine ($origPkg) et refuse ProxFibre tant qu'il est actif (fréquent sur Google TV)." "The TV keeps its original home screen ($origPkg) and refuses ProxFibre while it is active (common on Google TV)."
        Say "Le kit peut le désactiver pour forcer ProxFibre. Il vérifie ensuite que la TV accepte ProxFibre, et réactive tout de suite l'accueil d'origine sinon." "The kit can disable it to force ProxFibre. It then checks that the TV accepts ProxFibre, and re-enables the original home screen right away otherwise." "White"
        Warn "Sur certaines TV (surtout Google TV), la touche Source de la télécommande ne marche plus dans les applis une fois l'accueil d'origine désactivé. L'option 6 remet tout." "On some TVs (especially Google TV), the remote's Source button stops working inside apps once the original home screen is disabled. Option 6 puts everything back."
        if (YesNo "Forcer ProxFibre (réversible) ?" "Force ProxFibre (reversible)?" $true) {
            if (Disable-OrigHome $origPkg) { Ok "ProxFibre est l'écran d'accueil." "ProxFibre is the home screen." }
        } else { Say "Rien n'a été désactivé. ProxFibre reste disponible dans « Toutes les applis »." "Nothing was disabled. ProxFibre stays available in 'All apps'." "DarkGray" }
    }
    else {
        Warn "La TV n'a pas confirmé ProxFibre par défaut ($now). Appuyez sur la touche Accueil : si un choix s'affiche, prenez « ProxFibre » puis « Toujours »." "The TV did not confirm ProxFibre as default ($now). Press Home: if a chooser appears, pick 'ProxFibre' then 'Always'."
    }
    Capture-TV "zen_home" | Out-Null
    Write-Host ""
    Say "Sur la TV : appui long sur OK (ou touche Menu) sur une appli du dock pour la déplacer, la remplacer, la retirer, changer la disposition ou le titre." "On the TV: long-press OK (or the Menu key) on a dock app to move, replace or remove it, or change the layout or title." "Cyan"
    $p = Get-Perf
    Write-Host ("   RAM " + (T "disponible" "available") + " : $($p.RamDispoMo) Mo") -ForegroundColor White
}

# ---------------------------------------------------------------- 4. photos perso
function Do-Photos {
    if (-not (Test-Path $PhotosDir)) { New-Item -ItemType Directory -Force $PhotosDir | Out-Null }
    $files = @(Get-ChildItem $PhotosDir -File | Where-Object { $_.Extension -match "^\.(jpe?g|png|webp)$" })
    Write-Host ""
    Say "Dossier de vos photos : $PhotosDir" "Your photos folder: $PhotosDir" "White"
    if ($files.Count -eq 0) {
        Warn "Aucune photo dedans. Copiez-y des .jpg (paysage, 1920×1080 idéalement) puis revenez ici." "No photos in it. Copy .jpg files there (landscape, ideally 1920×1080) then come back."
        if (YesNo "Revenir aux photos fournies avec ProxFibre ?" "Go back to the photos bundled with ProxFibre?" $false) {
            Sh "rm -rf /sdcard/ZenHome" | Out-Null; Sh "am force-stop $ZenPkg" | Out-Null; Sh "input keyevent KEYCODE_HOME" | Out-Null
            Ok "Photos d'origine rétablies." "Bundled photos restored."
        }
        Start-Process explorer.exe $PhotosDir
        return
    }
    Say "$($files.Count) photo(s) à envoyer." "$($files.Count) photo(s) to send." "White"
    Sh "rm -rf /sdcard/ZenHome" | Out-Null
    Sh "mkdir -p /sdcard/ZenHome" | Out-Null
    foreach ($f in $files) { A push $f.FullName "/sdcard/ZenHome/$($f.Name)" | Out-Null; Write-Host "   → $($f.Name)" }
    Sh "pm grant $ZenPkg android.permission.READ_EXTERNAL_STORAGE" | Out-Null
    Sh "am force-stop $ZenPkg" | Out-Null
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep 5
    Capture-TV "photos_perso" | Out-Null
    Ok "Photos envoyées. Elles remplacent celles fournies." "Photos sent. They replace the bundled ones."
    $iv = Ask "Secondes entre deux photos (Entrée = 60)" "Seconds between photos (Enter = 60)"
    if ($iv -match "^\d+$") { Sh "settings put global zenhome_interval $iv" | Out-Null; Sh "am force-stop $ZenPkg" | Out-Null; Sh "input keyevent KEYCODE_HOME" | Out-Null }
}

# ---------------------------------------------------------------- 6. restauration
function Do-Restore {
    $st = Get-State
    Write-Host ""
    Say "Restauration : réactive tout ce que le kit a désactivé sur cette TV, remet l'écran d'accueil et les animations d'origine." "Restore: re-enables everything the kit disabled on this TV, puts back the original home screen and animations." "White"
    if (-not (YesNo "Continuer ?" "Continue?" $true)) { return }
    foreach ($p in $st.disabled) {
        $r = (Sh "pm enable $p") -join " "
        if ($r -match "enabled") { Ok $p $p }
        else { Write-Host ("  - " + $p + (T " : déjà actif ou introuvable, ignoré" " : already enabled or not found, skipped")) -ForegroundColor DarkGray }
    }
    if ($st.anim) {
        foreach ($pair in @(@("window_animation_scale",$st.anim.window),@("transition_animation_scale",$st.anim.transition),@("animator_duration_scale",$st.anim.animator))) {
            if ($pair[1] -and $pair[1] -ne "null") { Sh "settings put global $($pair[0]) $($pair[1])" | Out-Null } else { Sh "settings delete global $($pair[0])" | Out-Null }
        }
        Ok "Animations d'origine" "Original animations"
    }
    if ($st.original_home -and $st.original_home -notlike "$ZenPkg/*") { Sh "cmd package set-home-activity $($st.original_home)" | Out-Null; Set-HomeRole ($st.original_home -replace "/.*$", ""); Ok "Écran d'accueil : $($st.original_home)" "Home screen: $($st.original_home)" }
    $zen = @(Get-Packages "") -contains $ZenPkg
    if ($zen) {
        if (YesNo "Désinstaller ProxFibre ?" "Uninstall ProxFibre?" $true) { A uninstall $ZenPkg | Out-Null; Ok "ProxFibre désinstallé" "ProxFibre uninstalled" }
        foreach ($k in "zenhome_title_b64","zenhome_title_mode","zenhome_layout","zenhome_dock","zenhome_rev","zenhome_interval") { Sh "settings delete global $k" | Out-Null }
        Sh "rm -rf /sdcard/ZenHome" | Out-Null
    }
    $st.disabled = @(); $st.launcher_disabled = $null
    Save-State $st
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep 6
    Capture-TV "apres_restauration" | Out-Null
    Ok "Restauration terminée. Redémarrez la TV pour finir proprement." "Restore done. Restart the TV to finish cleanly."
}

# ---------------------------------------------------------------- menu
while ($true) {
    Clear-Host
    Write-Host "  ╔══════════════════════════════════════════════╗" -ForegroundColor DarkCyan
    Write-Host ("  ║   " + (T "ProxFibre TV - allègement + accueil      " "ProxFibre TV - slimming + launcher       ") + "  ║") -ForegroundColor DarkCyan
    Write-Host "  ╚══════════════════════════════════════════════╝" -ForegroundColor DarkCyan
    if (Test-Path $ConfFile) { $c = Get-Content $ConfFile -Raw | ConvertFrom-Json; Write-Host ("   TV : " + $c.label + "  (" + $c.target + ")") -ForegroundColor Gray }
    else { Say "   Aucune TV choisie" "   No TV selected" "DarkGray" }
    Write-Host ""
    Write-Host ("   1. " + (T "Diagnostic + sauvegarde (ne modifie rien)" "Diagnosis + backup (changes nothing)"))
    Write-Host ("   2. " + (T "Alléger : pubs, télémétrie, applis inutiles" "Slim down: ads, telemetry, useless apps"))
    Write-Host ("   3. " + (T "Installer l'écran d'accueil ProxFibre" "Install the ProxFibre launcher"))
    Write-Host ("   4. " + (T "Mettre mes propres photos" "Use my own photos"))
    Write-Host ("   5. " + (T "Capture d'écran de la TV" "Take a TV screenshot"))
    Write-Host ("   6. " + (T "Tout restaurer comme avant" "Restore everything as before"))
    Write-Host ("   7. " + (T "Choisir une autre TV" "Choose another TV"))
    Write-Host ("   0. " + (T "Quitter" "Quit"))
    Write-Host ""
    $ch = (Read-Host (T "Votre choix" "Your choice")).Trim()
    if ($ch -eq "0") { break }
    if ($ch -eq "7") { if (Test-Path $ConfFile) { Remove-Item $ConfFile }; [void](Choose-TV); Pause-Kit; continue }
    if ($ch -notin @("1","2","3","4","5","6")) { continue }
    $log = Join-Path $Saves ("journal_" + (Get-Date -Format "yyyyMMdd") + ".txt")
    Start-Transcript -Path $log -Append | Out-Null
    try {
        if (Ensure-TV) {
            switch ($ch) {
                "1" { [void](Do-Backup) }
                "2" { Do-Slim }
                "3" { Do-Launcher }
                "4" { Do-Photos }
                "5" { $f = Capture-TV "capture"; if ($f -and (Test-Path $f)) { Start-Process $f } }
                "6" { Do-Restore }
            }
        }
    } catch { Err "Erreur : $_" "Error: $_" }
    Stop-Transcript | Out-Null
    Pause-Kit
}
