<p align="center">
  <img src="docs/banniere.png" alt="Orba — Launcher TV" width="640">
</p>

<h1 align="center">Orba</h1>
<p align="center"><b>Le launcher TV simple et épuré pour Google TV et Android TV</b></p>

<p align="center">
  <a href="README.md">English</a> · <b>Français</b>
</p>

<p align="center">
  <a href="https://github.com/btalal/orba/releases/latest"><img src="https://img.shields.io/github/v/release/btalal/orba?style=flat&label=version&color=00E5FF&labelColor=0A1433" alt="Version"></a>
  <a href="https://github.com/btalal/orba/releases"><img src="https://img.shields.io/github/downloads/btalal/orba/total?style=flat&label=t%C3%A9l%C3%A9chargements&color=0078FF&labelColor=0A1433" alt="Téléchargements"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/btalal/orba?style=flat&label=licence&color=4B3DFF&labelColor=0A1433" alt="Licence MIT"></a>
  <img src="https://img.shields.io/badge/Android%20TV-9%2B-A64DFF?style=flat&labelColor=0A1433" alt="Android TV 9+">
  <a href="https://www.youtube.com/@OrbaTechFR"><img src="https://img.shields.io/badge/YouTube-Orba%20Tech-FF0000?style=flat&logo=youtube&logoColor=white&labelColor=0A1433" alt="Chaîne YouTube Orba Tech"></a>
</p>

---

Orba remplace l'écran d'accueil chargé de Google TV par un écran calme et lisible :
l'heure, la date et **vos applications**, rien d'autre. Pas de publicités, pas de
recommandations, pas de bandeaux « À regarder ensuite ».

<p align="center">
  <img src="docs/demo.gif" alt="Démonstration : navigation dans Orba et ouverture du bouton Sources" width="800">
</p>

<p align="center">
  <img src="docs/capture-accueil.png" alt="Écran d'accueil Orba sur une Philips OLED 65 pouces" width="900">
</p>

## Nouveau dans la version 3.6

### Écran de veille animé
Après quelques minutes sans toucher la télécommande, Orba affiche son écran de veille : **l'anneau du logo
tourne lentement en pulsant**, avec l'heure et la date en dessous, et l'ensemble **dérive doucement sur tout
l'écran** au-dessus des vagues atténuées. Rien ne reste au même endroit : idéal pour les écrans **OLED**.
N'importe quelle touche revient à l'accueil (le volume reste utilisable pendant la veille).

<p align="center">
  <img src="docs/capture-veille.png" alt="L'écran de veille d'Orba : anneau du logo, heure et date sur fond de vagues" width="900">
</p>

L'écran de veille est aussi déclaré comme **économiseur d'écran Android** (nom : *Orba*), que la télé
peut utiliser partout si sa marque propose le choix.

### Réglages directement sur la télé
Sélectionnez la **roue dentée affichée en haut d'Orba** et **maintenez la touche OK** (le bouton central de la
télécommande) pour ouvrir les *Réglages Orba* — pas le bouton Paramètres de la télécommande, qui ouvre toujours
ceux de la télé. Autre accès : appui long sur OK sur n'importe quelle tuile → *Réglages Orba…*.
Chaque choix s'applique tout de suite :

| Réglage | Choix |
|---|---|
| Tuiles par ligne | 4, 5, 6 ou 7 |
| Fond | Vagues animées · Vagues fixes · Noir pur (OLED) |
| Halo de sélection | Couleur de l'appli · Cyan Orba |
| Écran de veille | Désactivé · après 5, 10, 20 ou 30 min |
| Voir l'écran de veille | Aperçu immédiat |

Un appui court sur la roue ouvre toujours les paramètres de la télé.

## Le bouton Sources (version 3.5)

Un bouton **Sources** (icône téléviseur) dans l'en-tête, à côté du Wi-Fi : flèche haut depuis la grille,
puis OK, et la liste des entrées de la télé s'ouvre (HDMI, antenne, USB…).

<p align="center">
  <img src="docs/capture-sources.png" alt="Le bouton Sources ouvre la liste des entrées HDMI de la télé" width="900">
</p>

- **Télés Philips** : le panneau Sources d'origine s'ouvre.
- **Autres télés Android** : la liste des entrées HDMI s'affiche dans une fenêtre aux couleurs d'Orba.

▶️ Démonstration en vidéo : [Orba 3.5 : le nouveau bouton Sources](https://www.youtube.com/watch?v=y7Pzcqimt1k)

## Fonctionnalités

- **Accueil épuré** : logo, heure, date et une grille d'applications (4 à 7 par ligne) avec les logos d'origine.
- **Halo lumineux** à la couleur de chaque appli quand on la sélectionne, et son nom affiché dessous.
- **Fond animé** bleu nuit avec des vagues lumineuses cyan → violet, qui protègent les écrans **OLED** du marquage.
- **Écran de veille animé** (logo, heure, date en dérive lente) et **réglages sur la télé**.
- **En-tête** avec le bouton **Sources**, l'icône **Wi-Fi** (force du signal ; un clic ouvre les réglages réseau) et la roue **Paramètres**.
- **Organisation libre** : appui long sur OK sur une tuile → *Déplacer*, *Masquer de l'accueil* ou *Applis masquées…*. La tuile **+** réaffiche une appli masquée.
- **Démarre en premier** : à l'allumage de la télé et sur la touche **Accueil**, c'est Orba qui s'affiche.
  **Appui long** sur Accueil : l'accueil Google TV d'origine reste accessible.
- Très léger : environ 300 Ko, aucune autorisation sensible, aucune connexion à Internet.

## Installer Orba sur une télé Android

Prérequis : une télé ou un boîtier **Google TV / Android TV** sous **Android 9 ou plus récent**.

1. **Télécharger l'APK** depuis la page [Releases](../../releases) du projet (`orba.apk`).
2. **L'installer sur la télé**, au choix :
   - avec l'appli **Downloader** (Play Store) : saisir l'adresse de l'APK, puis *Installer* ;
   - avec une **clé USB** et un gestionnaire de fichiers ;
   - depuis un ordinateur avec **ADB** (débogage réseau activé sur la télé) :
     ```bash
     adb connect <adresse-ip-de-la-tele>:5555
     adb install -r orba.apk
     ```
   Android demandera d'autoriser l'installation d'applis de cette source : accepter pour l'appli utilisée.
3. **Activer le raccourci Accueil** (indispensable pour qu'Orba s'ouvre au démarrage et sur la touche Accueil) :
   *Paramètres → Système → Accessibilité → Services → Touche Accueil → Orba → Activer*
   (l'intitulé exact peut varier légèrement selon la marque de la télé).
4. Appuyer sur la touche **Accueil** : Orba s'affiche. C'est prêt.

> **Pourquoi un service d'accessibilité ?** Google TV ne permet pas de choisir un autre écran d'accueil.
> Le service « Touche Accueil → Orba » voit **uniquement** la touche Accueil et le nom de l'appli affichée
> (pour remplacer l'accueil Google quand il apparaît). Il ne lit pas le contenu de l'écran et n'enregistre rien.

> **Télés Philips :** ne désactivez pas l'appli *Aide* (`org.droidtv.eum`) : le panneau Sources en a besoin
> pour s'ouvrir. Orba la masque simplement de l'accueil.

### Revenir en arrière

Désactiver le service dans *Paramètres → Système → Accessibilité*, ou désinstaller Orba
(*Paramètres → Applications → Orba → Désinstaller*). L'accueil Google TV revient tel quel.

## Construire l'APK soi-même (Windows)

Aucun Gradle : l'APK est construit directement avec les outils du SDK Android.

- Android SDK (build-tools et une plateforme récente) dans `%LOCALAPPDATA%\Android\Sdk`
- Le JDK fourni avec Android Studio (`C:\Program Files\Android\Android Studio\jbr`)

```powershell
.\build.ps1        # compile, assemble, aligne et signe -> accueil-simple.apk
.\icone.ps1        # (re)génère l'icône, la bannière et le logo depuis source-logo\
```

`build.ps1` crée une clé de signature locale `cle-accueil.jks` si elle n'existe pas.
**Elle n'est pas dans le dépôt** : la conserver précieusement, car une mise à jour ne peut
s'installer par-dessus que si elle est signée avec la même clé.

## Organisation du code

| Chemin | Rôle |
|---|---|
| `src/fr/maison/accueil/MainActivity.java` | Écran d'accueil : en-tête, grille, halo, menus, réglages, fond animé |
| `src/fr/maison/accueil/Veille.java`, `VeilleActivity.java`, `OrbaDream.java` | Écran de veille animé (et économiseur d'écran Android) |
| `src/fr/maison/accueil/ToucheAccueilService.java` | Service d'accessibilité : touche Accueil et remplacement de l'accueil Google |
| `res/` | Icône, bannière, logo, icône Paramètres, textes |
| `assets/` | Polices Inter et Montserrat (licence OFL) |
| `source-logo/` | Logo Orba d'origine (image et SVG) |
| `build.ps1`, `icone.ps1`, `Logo.cs` | Construction de l'APK et génération des visuels |

## Licence et crédits

- Code sous **licence MIT** (voir [LICENSE](LICENSE)). Le nom et le logo « Orba » ne sont pas couverts par cette licence (voir [NOTICE](NOTICE)).
- Polices **[Inter](https://rsms.me/inter/)** et **[Montserrat](https://github.com/JulietaUla/Montserrat)** — SIL Open Font License 1.1 (licences dans `assets/`).
- Icônes Paramètres et Wi-Fi d'après **[Lucide](https://lucide.dev)** — licence ISC.
- Logos des applications : ceux fournis par chaque application installée (marques de leurs propriétaires respectifs).
