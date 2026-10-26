# ProxFibre TV : rendez votre TV rapide et belle

**🇫🇷 Français** · [🇬🇧 English version](README-en.md)

<p align="center"><img src="docs/proxFibre-banner.png" alt="ProxFibre" width="760"></p>

Votre TV Android rame, affiche des pubs partout, et le son se décale pendant les films ? Ce kit l'allège en quelques minutes depuis un PC Windows, un Mac ou un PC Linux. Il installe aussi **ProxFibre**, un écran d'accueil minimaliste façon Apple TV.

<table>
  <tr>
    <th align="center">Avant</th>
    <th align="center">Après</th>
  </tr>
  <tr>
    <td><img src="docs/avant.jpg" alt="TV TCL avant : accueil Google TV chargé de pubs et d'applis" width="400"></td>
    <td><img src="docs/apres.jpg" alt="TV TCL après : écran d'accueil ProxFibre avec photo de paysage et dock" width="400"></td>
  </tr>
  <tr>
    <td align="center"><sub>Accueil d'origine : pubs, applis promo, lenteurs</sub></td>
    <td align="center"><sub>ProxFibre : photo de paysage, l'heure et vos applis</sub></td>
  </tr>
</table>

<sub>Photos réelles d'une TCL de 2019. Le titre affiché en haut à gauche est personnalisable.</sub>

> [!WARNING]
> **Projet personnel partagé tel quel, sans aucune garantie.** Je ne suis pas responsable des problèmes que son utilisation pourrait causer à votre TV, à vos appareils ou à vos données. Chaque TV est différente : lisez la section [Avant d'utiliser le kit sur votre TV](#avant-dutiliser-le-kit-sur-votre-tv).

---

## Sommaire

1. [En bref](#en-bref)
2. [Ce que fait le kit](#ce-que-fait-le-kit)
3. [Compatibilité](#compatibilité)
4. [Avant d'utiliser le kit sur votre TV](#avant-dutiliser-le-kit-sur-votre-tv)
5. [Préparer la TV (5 minutes)](#préparer-la-tv-5-minutes)
6. [Utilisation](#utilisation)
7. [Personnaliser ProxFibre](#personnaliser-proxfibre)
8. [Sécurité et retour en arrière](#sécurité-et-retour-en-arrière)
9. [Dépannage](#dépannage)
10. [Contenu du dossier](#contenu-du-dossier)
11. [Responsabilité](#responsabilité)
12. [Crédits et licence](#crédits-et-licence)

---

## En bref

1. Activez le mode développeur et le débogage sur la TV ([voir comment](#préparer-la-tv-5-minutes)).
2. Double-cliquez sur **`LANCER.bat`** (Windows) ou **`LANCER.command`** (macOS), ou lancez **`./LANCER.sh`** (Linux), puis tapez l'adresse IP de la TV.
3. Acceptez « Autoriser le débogage » sur la TV.
4. Faites dans l'ordre les options **1** (sauvegarde), **2** (allègement) et **3** (écran d'accueil).
5. Si quelque chose ne vous plaît pas, l'option **6** remet tout comme avant.

## Ce que fait le kit

| Option du menu | Ce qu'elle fait | Modifie la TV ? |
|---|---|---|
| **1. Diagnostic + sauvegarde** | Relève la version d'Android, la mémoire, le processeur et le Wi-Fi. Sauvegarde la liste des applis et des réglages, avec une capture de l'écran. | Non |
| **2. Alléger** | Désactive les pubs, la télémétrie et les services inutiles connus (Google, TCL, Sony, Xiaomi…). Pour les fonctions que vous utilisez peut-être (Chromecast, Assistant…), le kit vous pose la question. | Oui, réversible |
| **3. ProxFibre** | Installe l'écran d'accueil minimaliste : votre titre, une vue à 4, 6 ou 8 applis, et vos applis préférées. Au premier démarrage, ProxFibre propose aussi d'installer Armadillo (VPN) et AirPlay (réception iPhone/Mac). Si vous le voulez, il désactive en plus l'accueil d'origine, souvent très gourmand. | Oui, réversible |
| **4. Mes photos** | Remplace les photos fournies par les vôtres. | Oui, réversible |
| **5. Capture d'écran** | Prend une capture de ce qui est affiché sur la TV. | Non |
| **6. Tout restaurer** | Réactive tout ce que le kit a désactivé, remet l'écran d'accueil et les animations d'origine, et désinstalle ProxFibre. | Retour à l'origine |
| **7. Autre TV** | Change la TV ciblée. | Non |

**Rien n'est jamais désinstallé**, sauf ProxFibre si vous le demandez. Les applis sont seulement *désactivées*, et tout se réactive en un clic.

**Exemple réel** sur une TCL de 2019 (Android 9, 2 Go de RAM) :

| | Avant | Après |
|---|---|---|
| Processeur au repos | 61 % | 12 % |
| RAM disponible | 451 Mo | 720 Mo |
| Écran d'accueil | 187 Mo (Google TV) | 25 Mo (ProxFibre) |
| Services désactivés (pubs, télémétrie, inutiles) | 0 | 32 |

Ces chiffres viennent d'une seule TV. Le gain sera différent sur la vôtre.

## Compatibilité

| Appareil | Allègement | ProxFibre |
|---|---|---|
| TV **Android TV / Google TV** : TCL, Sony, Philips, Xiaomi, Hisense (modèles Android), Sharp, Toshiba… | ✅ | ✅ |
| Boîtiers : Nvidia Shield, Chromecast avec Google TV, Xiaomi Mi Box, Google TV Streamer | ✅ | ✅ |
| Projecteurs sous Android TV / Google TV : XGIMI… | ✅ | ✅ |
| **Amazon Fire TV / Fire Stick** (Fire OS) | ✅ | ❌ Utilisez un outil dédié ([voir ci-dessous](#fire-tv--fire-stick--changer-laccueil)) |
| Fire TV sous **Vega OS** (Fire TV Stick 4K Select et modèles sortis depuis fin 2025) | ❌ Pas Android | ❌ |
| Samsung (Tizen), LG (webOS), Hisense VIDAA, Roku, Apple TV | ❌ Pas Android | ❌ |

**Testé en conditions réelles :** une TCL BeyondTV (Android 9). Testés aussi par des utilisateurs : une TCL BeyondTV2 (Android 9) et un projecteur XGIMI MoGo 3 Pro (Google TV, Android 11). Les autres appareils de ce tableau devraient fonctionner, car ils utilisent les mêmes commandes Android, mais ils n'ont pas tous été testés.

### Fire TV / Fire Stick : changer l'accueil

Amazon protège son écran d'accueil : sans root, impossible de le désactiver ou de le remplacer comme le fait le kit sur les autres TV. Utilisez le kit pour l'**allègement** (options 1, 2 et 6), puis l'un de ces outils dédiés pour l'accueil. Ils redirigent la touche Accueil vers le launcher de votre choix grâce à un service d'accessibilité, sans root, sur Fire OS 7 et 8 :

- **[Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)** : un launcher complet, avec l'option « Remplacer le launcher actuel » dans ses réglages.
- **[FireStick Fixer](https://github.com/Hornet34/firestick-fixer)** (libre) ou **[Home on Fire](https://xdaforums.com/t/app-firetv-noroot-home-on-fire-use-any-launcher-as-your-fire-tv-home-home-button-boot-fire-os-8-7.4791567/)** : envoient la touche Accueil vers le launcher de votre choix.
- **[Launcher Manager](https://xdaforums.com/t/app-firetv-noroot-launcher-manager-change-launcher-without-root.4176349/)** : même principe, et permet aussi de reprogrammer les boutons d'applis de la télécommande.

L'accueil Amazon reste installé et peut apparaître une fraction de seconde. Après une mise à jour de Fire OS, il faut parfois réactiver l'outil. Évitez les méthodes « system user » trouvées sur les forums : elles peuvent bloquer la Fire TV au démarrage.

### Marques couvertes par les listes d'allègement

Le kit contient des listes spécifiques pour chaque grande marque, en plus de la partie Google commune à toutes les TV Android :

| Marque | Ce qui est désactivé d'office | Ce qui est demandé |
|---|---|---|
| **Toutes (Google)** | Rapports à Google, services publicitaires Android, recommandations de l'ancien accueil | Chromecast, Assistant, Mode Ambiant, recommandations, Play Films/Musique/Jeux |
| **TCL** (et Thomson fabriqué par TCL) | Télémétrie, pubs au démarrage, messages promo, mode démo | TCL Channel, recopie d'écran, Media Center, Alexa, boutique TCL |
| **Sony Bravia** | Samba TV (analyse de ce que vous regardez), Sony Select, barres de recommandations, mode démo, télémétrie | Recopie d'écran, AirPlay, Alexa, recherche vocale Sony, BRAVIA CORE |
| **Philips** | Analytique TP Vision, rangée « Top Picks », galerie d'applis, Inscape | Alexa, DLNA, Play-Fi, Mimi |
| **Xiaomi** | Télémétrie MIUI, rangées publicitaires, moteur de pub, Alphonso | Xiaomi TV+, Mi Cast, recopie d'écran, Mi Musique, Alexa |
| **Hisense** (Android) | Mode démo, analyse de ce que vous regardez, télémétrie | Chaînes VIDAA, Alexa, RemoteNOW, Miracast |
| **Sharp, Toshiba, Thomson, Philco** (Android) | Mode démo, Anoki, télémétrie Vestel, recommandations Skyworth | Chaînes gratuites, recopie d'écran |
| **Nvidia Shield** | Télémétrie et diagnostics Nvidia | GeForce NOW, GameStream, superposition jeux |
| **Amazon Fire TV** | Analyse de ce que vous regardez, identifiant pub, promos, métriques | Alexa, Amazon Photos/Music, jeux, Amazon Kids, Silk |
| **XGIMI** (projecteurs) | Rapports de bug envoyés à XGIMI | Douban FM (radio chinoise) |

Ces listes ont été établies en octobre 2026 à partir de plusieurs sources communautaires (Universal Android Debloater, listes par modèle CleanYourTV, shield_optimizer, dumbtv, forums). Les paquets connus pour casser quelque chose (entrées HDMI, télécommande, mises à jour, réglages) ont été volontairement exclus.

**Le kit ne propose que ce qui existe sur votre TV.** Une Sony ne verra que les lignes Google et Sony, par exemple. Si un paquet est absent ou si la TV refuse de le désactiver (certains sont verrouillés, surtout sur Fire TV), il est simplement ignoré et le kit continue.

**Il vous faut :** un ordinateur sous **Windows 10 ou 11**, **macOS** ou **Linux**, connecté au **même réseau** que la TV. Sur Linux, `curl` (ou `wget`) et `unzip` doivent être installés. ADB, l'outil officiel de Google pour parler à Android, est téléchargé automatiquement au premier lancement (environ 7 Mo).

## Avant d'utiliser le kit sur votre TV

Chaque fabricant installe ses propres applis, avec des noms différents selon les marques, les modèles et les versions d'Android. Le kit a été conçu pour être prudent, mais il ne peut pas connaître toutes les TV du marché.

**Vous devrez peut-être adapter le kit à votre TV.** Par exemple :

- ajouter ou retirer des lignes dans `scripts/listes/pubs_telemetrie.txt` et `scripts/listes/optionnel.txt` ;
- ajouter l'appli « Entrées TV » de votre marque dans le code de ProxFibre, si elle n'est pas reconnue ;
- ajuster une commande si votre version d'Android se comporte différemment.

**Faites relire le kit avant de l'utiliser.** Si vous n'êtes pas à l'aise avec le code, ouvrez le dossier du kit dans un assistant de code comme **[Claude Code](https://claude.com/claude-code)** et demandez-lui par exemple :

> « Relis ce kit avant que je l'utilise sur ma TV [marque et modèle, version d'Android]. Vérifie que les listes de paquets et les commandes sont adaptées à ma TV, signale ce qui pourrait poser problème et propose les modifications nécessaires. »

Vous pouvez lui donner le contenu du fichier `sauvegardes/<numéro-de-série>/sauvegarde_…/paquets_tous.txt`, créé par l'option 1 (qui ne modifie rien). Il connaîtra ainsi la liste exacte des applis de votre TV.

**Dans tous les cas, lancez d'abord l'option 1.** Lisez ce que l'option 2 propose de désactiver avant de confirmer, et répondez « non » à tout ce que vous ne comprenez pas.

## Préparer la TV (5 minutes)

### 1. Activer le mode développeur

1. Ouvrez **Paramètres → Système → À propos** (ou **Préférences de l'appareil → À propos**).
2. Descendez jusqu'à **Build** (ou **Version de l'OS Android TV**) et **appuyez 7 fois** dessus avec le bouton OK de la télécommande.
3. Le message « Vous êtes maintenant développeur » s'affiche.

### 2. Activer le débogage

1. Ouvrez **Paramètres → Système → Options pour les développeurs**.
2. Activez **Débogage USB**. S'ils existent, activez aussi **Débogage ADB réseau** ou **Débogage sans fil**.

### 3. Noter l'adresse IP de la TV

Dans **Paramètres → Réseau et Internet → (votre Wi-Fi)**, ou **À propos → État**, notez l'adresse du type `192.168.1.50`.

> [!TIP]
> Dans votre box, réservez cette adresse IP pour la TV (« bail DHCP statique »). Elle ne changera plus.

## Utilisation

1. Double-cliquez sur **`LANCER.bat`** (Windows) ou **`LANCER.command`** (macOS). Sur Linux, ouvrez un terminal dans le dossier du kit et tapez `./LANCER.sh`.
2. Choisissez une option du menu. La première fois, le kit demande l'adresse IP de la TV.
3. **Regardez la TV** : la fenêtre « Autoriser le débogage USB ? » s'affiche. Cochez **« Toujours autoriser depuis cet ordinateur »**, puis **OK**.
4. Le kit affiche la marque et le modèle trouvés. Vérifiez que c'est bien votre TV avant de confirmer.
5. **Option 3 :** pendant l'installation de ProxFibre, Google Play Protect peut afficher un avertissement sur la TV (appli installée hors du Play Store). Choisissez **« Détails »** puis **« Installer quand même »**.

**Ordre conseillé : 1, puis 2, puis 3.** Le kit fait de toute façon une sauvegarde automatique avant la première modification.

Le kit parle français si le système (Windows, macOS ou Linux) est en français, sinon anglais.

Les versions Windows (`scripts/kit.ps1`) et macOS/Linux (`scripts/kit.sh`) font exactement la même chose et partagent les mêmes listes et le même dossier `sauvegardes/` : vous pouvez commencer sur un ordinateur et restaurer depuis un autre.

## Personnaliser ProxFibre

| Quoi | Comment |
|---|---|
| **Titre** (« Salon », « Chambre de Léa »…) | Option 3 du menu. Tapez votre titre, laissez vide pour afficher le nom de la TV (celui des paramètres Android), ou tapez `-` pour ne rien afficher. |
| **Disposition** | Option 3 : **4**, **6** ou **8 applis**, toutes sur une seule ligne avec les raccourcis ; les tuiles rétrécissent quand il y en a plus. |
| **Applis du dock** | Option 3 : le kit liste les applis de streaming trouvées, choisissez les numéros dans l'ordre voulu. Sans choix, ProxFibre prend automatiquement les premières parmi Netflix, YouTube, Prime Video, Disney+, myCANAL, Max, Apple TV… |
| **Vos photos** | Copiez vos `.jpg` (format paysage, idéalement 1920×1080) dans le dossier **`mes_photos`**, puis lancez l'option 4. Pour revenir aux photos fournies, videz le dossier et relancez l'option 4. |
| **Vitesse de défilement** | Option 4 : nombre de secondes entre deux photos (60 par défaut). |
| **Ce qui est désactivé** | Modifiez `scripts/listes/pubs_telemetrie.txt` (désactivé après une seule confirmation) et `scripts/listes/optionnel.txt` (le kit pose la question pour chaque ligne). Une ligne = `nom.du.paquet | description FR | English description`. Pour regrouper plusieurs paquets d'une même fonction sous une seule question : `paquet1,paquet2 | ... | ...`. |

Avec la télécommande, dans ProxFibre :

- **Flèches et OK** pour ouvrir une appli.
- **Toutes** pour voir toutes les applis installées.
- **Entrées TV** pour les chaînes et les ports HDMI, si la marque est reconnue.
- **Flèche bas** depuis le dock pour ouvrir le **Top Shelf** : des rangées d'applis par thème, sous le dock. **Flèche haut** sur la première rangée pour le refermer.

### Modifier l'écran d'accueil directement sur la TV

Pas besoin du PC pour changer le dock. Sur une appli du dock, faites un **appui long sur OK** (ou appuyez sur la touche **Menu** de la télécommande) :

| Action | Ce qu'elle fait |
|---|---|
| **Déplacer** | L'appli s'entoure de bleu : déplacez-la avec les flèches, puis **OK** pour valider. |
| **Remplacer par une autre appli** | Ouvre la liste de vos applis : celle que vous choisissez prend sa place. |
| **Retirer du dock** | Enlève l'appli du dock (elle reste installée). |
| **Ajouter une appli** | Quand il reste de la place. Le **+** du dock fait la même chose. |
| **Disposition** | Passe en vue 4, 6 ou 8 applis. |
| **Modifier le titre** | Votre texte, ou vide pour le nom de la TV, ou « Aucun titre ». |
| **Sections du Top Shelf** | Renommer une section, y ajouter ou en retirer des applis, créer une section (« Streaming », « Films et séries », « Musique », « Jeux »… ou un nom libre), ou la supprimer. Un appui long sur une appli du Top Shelf permet aussi de la retirer de sa section. |

Dans **Toutes**, un appui long sur une appli permet aussi de l'**ajouter au dock**.

Ces réglages sont enregistrés sur la TV. Si vous relancez plus tard l'option 3 du kit, ce sont les choix faits dans le kit qui s'appliquent.

### Wi-Fi, VPN et AirPlay

En haut à droite, quatre icônes donnent l'état de la TV :

- **Wi-Fi** : blanc quand la TV a Internet (Wi-Fi ou câble Ethernet), grisé et barré sinon. Le nombre d'arcs suit la force du signal quand elle est connue.
- **VPN** : bouclier blanc au repos, **bleu** dès qu'un tunnel VPN est actif (Armadillo ou une autre appli VPN).
- **AirPlay** : icône AirPlay, grisée quand le récepteur est arrêté, blanche quand il attend un appareil, et blanche avec une pastille verte pendant qu'un iPhone ou un Mac diffuse. **OK** dessus ouvre AirPlay, ou le démarre s'il est arrêté. Le même état, en mots, est écrit dans **Centre de contrôle** et dans Réglages → Applications fournies ; une notification apparaît quand un appareil se connecte et quand il repart.
- **Réglages** : l'engrenage ouvre les paramètres Android de la TV. Il remplace la tuile « Réglages » qui se trouvait à droite du dock.

En haut à gauche, l'heure et la date, puis en dessous en plus petit le nom de la TV (ou votre titre).

Deux applis sont fournies dans ProxFibre : **Armadillo** (client VPN) et **AirPlay Server** (réception iPhone, iPad et Mac, open source). Au premier démarrage, l'écran d'accueil propose de les installer l'une après l'autre ; Android demande une confirmation pour chacune. Elles apparaissent ensuite dans **Toutes**, et le menu de ProxFibre (appui long sur OK) permet de les ouvrir ou de les réinstaller.

**AirPlay démarre tout seul à chaque allumage de la TV** et reste visible depuis l'iPhone ou le Mac sous le nom « Android AirPlay » (modifiable dans l'appli). Pour le lancer tout de suite après l'installation, ouvrez-le depuis **Toutes** ou depuis le menu.

Si Android refuse l'installation, ProxFibre ouvre l'écran « Installer des applis inconnues » : activez-le pour ProxFibre, revenez, l'installation reprend toute seule. Les entrées **Installer Armadillo** et **Installer AirPlay** restent dans le menu même après avoir répondu « Plus tard ». Si vous désinstallez ensuite ProxFibre, les deux applis restent installées : retirez-les depuis les réglages Android de la TV.

## Sécurité et retour en arrière

- **Verrou d'identité.** Le kit enregistre le numéro de série de votre TV. Avant chaque action, il vérifie que l'appareil à cette adresse est bien le même. Une autre TV, une box ou un Fire Stick qui récupère cette IP ne sera jamais modifié.
- **Applis protégées.** Le Play Store, les services Google, les réglages, le clavier, WebView… ne sont jamais désactivés, même s'ils sont ajoutés à une liste.
- **L'écran d'accueil actuel n'est jamais coupé par l'option 2.** Seule l'option 3 peut le désactiver, une fois ProxFibre installé.
- **Sauvegardes et captures.** Tout est rangé dans `sauvegardes/<numéro-de-série-de-la-TV>/`, avec un journal de chaque session.
- **Retour en arrière :** option 6. Si le PC n'est plus disponible, sur la TV : **Paramètres → Applis → Voir toutes les applis → Afficher les applis système →** choisissez l'appli → **Activer**.
- **Les mises à jour système** de la TV restent actives. Après une grosse mise à jour, certaines applis peuvent être réactivées : relancez simplement l'option 2. C'est fréquent sur Fire TV.
- **Philips :** attendez une dizaine de secondes après l'allègement avant d'éteindre la TV, le temps qu'elle enregistre les changements. Le kit attend déjà un peu, mais ne débranchez pas la TV juste après.

En dernier recours, une réinitialisation d'usine remet toujours la TV à neuf. Elle efface aussi vos comptes et vos applis.

## Dépannage

| Problème | Solution |
|---|---|
| « Connexion impossible » | Vérifiez que la TV est allumée (pas en veille), sur le même réseau que le PC, et que le **Débogage USB** est activé. Vérifiez aussi l'adresse IP. |
| Pas de fenêtre « Autoriser le débogage » | Désactivez puis réactivez le Débogage USB, puis relancez le kit. Sur certaines TV, il faut aussi activer **Débogage ADB réseau**. |
| TV en Android 11 ou plus récent, avec un écran « Débogage sans fil » | Choisissez « Associer avec un code » sur la TV. Quand le kit le propose, répondez **oui** et tapez l'IP:port et le code affichés. |
| L'appui long sur OK ne fait rien | Maintenez OK au moins une demi-seconde : ProxFibre reconnaît aussi les télécommandes qui envoient l'appui long comme une rafale d'appuis courts (Sony, par exemple). Sinon, utilisez la touche **Menu** (ou ☰) sur l'appli. |
| « Échec de l'installation » de ProxFibre | La TV a sans doute affiché un avertissement Google Play Protect resté sans réponse. Relancez l'option 3 et, sur la TV, choisissez **« Détails »** puis **« Installer quand même »**. |
| ProxFibre ne s'affiche pas avec le bouton Accueil | Appuyez sur Accueil : si un choix s'affiche, prenez **ProxFibre** puis **Toujours**. Sinon, relancez l'option 3 et acceptez de désactiver l'accueil d'origine. |
| La touche Source de la télécommande ne marche plus | Sur certaines TV (surtout Google TV), c'est l'accueil d'origine qui gère cette touche. Lancez l'option 6 pour le réactiver, puis refaites l'option 3 en répondant **non** quand le kit propose de le désactiver. |
| Logo Google TV en boucle après l'option 3 | Un écran de secours de Google a pris la place de l'accueil. Appuyez sur Accueil et choisissez **ProxFibre** puis **Toujours**. Sinon, lancez l'option 6 depuis le PC, redémarrez la TV, puis refaites l'option 3 avec la dernière version du kit. |
| L'ancien accueil revient (Google TV) | Sur Google TV, l'accueil d'origine peut se réactiver tout seul quelques minutes après l'option 3, ou après une mise à jour. ProxFibre reste en général l'accueil (l'option 1 signale ce qui a été réactivé). S'il reprend la main, relancez l'option 3. |
| La tuile « Entrées TV » est absente | La marque n'est pas reconnue : utilisez le bouton Source ou Input de la télécommande, ou ajoutez votre marque dans le code (voir [Avant d'utiliser le kit](#avant-dutiliser-le-kit-sur-votre-tv)). |
| « ignoré (non modifiable sur cette TV) » s'affiche | C'est normal : la TV protège ce paquet ou ne l'a pas. Rien n'a été modifié pour lui. |
| Une fonction de la TV ne marche plus | Relancez l'option 6 pour tout restaurer, puis refaites l'option 2 en répondant « oui » à cette fonction. |
| Ça rame encore pendant les films | Passez la TV en Wi-Fi **5 GHz** ou en câble Ethernet. Désactivez le lissage de mouvement (MEMC, Motion) dans les réglages d'image. Si ça ne suffit pas, la TV manque de puissance : un boîtier externe sera plus fluide. |
| Windows bloque `LANCER.bat` | Clic droit sur le fichier → **Propriétés** → cochez **Débloquer**. Ou « Informations complémentaires » → **Exécuter quand même**. |
| macOS refuse d'ouvrir `LANCER.command` | Clic droit (ou Ctrl+clic) sur le fichier → **Ouvrir** → **Ouvrir**. Si « permission refusée » s'affiche, ouvrez le Terminal dans le dossier du kit et tapez `bash LANCER.sh`. |
| Linux : « Permission non accordée » sur `LANCER.sh` | Tapez `chmod +x LANCER.sh` une fois, ou lancez `bash LANCER.sh`. |

## Contenu du dossier

```
ProxFibre-TV/
├── LANCER.bat              ← Windows : double-cliquez ici
├── LANCER.command          ← macOS : double-cliquez ici
├── LANCER.sh               ← Linux : ./LANCER.sh
├── README.md / README-en.md
├── CREDITS.md              ← auteurs et licences des photos
├── LICENSE                 ← licence MIT
├── docs/                 ← images du README
├── scripts/
│   ├── kit.ps1             ← le programme (PowerShell, Windows)
│   ├── kit.sh              ← le même programme (bash, macOS/Linux)
│   └── listes/             ← ce qui est désactivé (modifiable)
├── ecran-accueil/
│   ├── ProxFibre.apk       ← l'écran d'accueil prêt à installer
│   └── source/             ← code source Java + script de compilation
├── mes_photos/             ← vos photos pour ProxFibre (option 4)
├── outils/                 ← ADB, téléchargé automatiquement
└── sauvegardes/            ← créé automatiquement : une sauvegarde par TV
```

> [!NOTE]
> Si vous partagez le kit, ne partagez pas `sauvegardes/`, `outils/` ni `config_tv.json` : ils contiennent des informations sur votre TV.

### Compiler ProxFibre soi-même

Le launcher (`hume/`) se compile sans Gradle, avec un JDK 17+ et le SDK Android (build-tools 34/35, plateforme 34) : `hume/build-win.sh` sous Windows (Git Bash), `hume/build.sh` sous macOS et Linux. Le résultat est `hume/build/ProxFibre.apk`.

Les deux récepteurs fournis avec ProxFibre ne sont pas versionnés ici (ce sont des binaires tiers, 80 Mo à eux deux). Pour compiler l'APK complet, déposez-les avant de lancer le script :

- `ecran-accueil/source/assets/armadillo/armadillo.apk` — [Kazuryy/armadillo-android](https://github.com/Kazuryy/armadillo-android/releases), licence AGPL v3 ;
- `ecran-accueil/source/assets/airplay/airplay.apk` — [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server/releases), licence GPL v3.

Sans eux, l'APK se compile quand même et le launcher fonctionne normalement, mais l'installation d'Armadillo et d'AirPlay depuis ProxFibre échouera (les fichiers sont absents du paquet).

La vérification de mise à jour de ProxFibre lit les **releases GitHub** de ce dépôt (`ProxFibre/ProxFibre-TV`) : publier une release avec un fichier `.apk` en pièce jointe suffit donc à proposer la mise à jour sur la TV, sans toucher à l'application.

## Responsabilité

Ce kit est un projet personnel, partagé gratuitement **tel quel, sans aucune garantie**. En l'utilisant, vous acceptez que :

- **je ne suis pas responsable** des problèmes, pannes, pertes de données, dysfonctionnements ou annulations de garantie que son utilisation pourrait causer, directement ou indirectement ;
- vous l'utilisez **à vos propres risques**, sur un appareil qui vous appartient ;
- il vous revient de vérifier que le kit convient à votre TV avant de l'utiliser, et de l'adapter si nécessaire (voir [Avant d'utiliser le kit sur votre TV](#avant-dutiliser-le-kit-sur-votre-tv)).

Le kit n'est ni affilié, ni soutenu par Google, TCL, Sony, Philips, Xiaomi, Amazon ou un autre fabricant. Les noms de marques cités appartiennent à leurs propriétaires.

## Crédits et licence

- **Photos :** 19 « Featured pictures » de Wikimedia Commons, sous licence CC BY-SA. Les auteurs et les liens sont dans [CREDITS.md](CREDITS.md).
- **Listes d'allègement :** établies à partir de [Universal Android Debloater NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), [CleanYourTV](https://github.com/Gathin23/cleanyourtv), [shield_optimizer](https://github.com/bryanroscoe/shield_optimizer), [dumbtv](https://github.com/humblemuzzu/dumbtv) et de guides communautaires. Merci à leurs auteurs.
- **Armadillo (VPN) :** [Kazuryy/armadillo-android](https://github.com/Kazuryy/armadillo-android) v0.2.0, sous GNU AGPL v3, fourni tel quel dans l'APK de ProxFibre. Détails dans [CREDITS.md](CREDITS.md).
- **Police :** Poppins, SIL Open Font License.
- **ADB :** Android SDK Platform-Tools de Google, téléchargé depuis le site officiel au premier lancement.
- **Code du kit et de ProxFibre :** licence MIT, voir [LICENSE](LICENSE). Vous pouvez l'utiliser, le modifier et le partager librement.

---

**🇫🇷 Français** · [🇬🇧 English version](README-en.md)
