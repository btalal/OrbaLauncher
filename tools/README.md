# TV cleanup tools

**English** · [Français](#outils-de-nettoyage-de-la-télé)

Two PowerShell scripts that switch off the bloat on an Android TV over Wi-Fi with ADB, and a list tested on a
**Philips 65OLED908/12** (Google TV 14). They only **disable** apps (`pm disable-user`): nothing is uninstalled,
and everything comes back with one command.

| File | What it does |
|---|---|
| [`philips-65oled908/debloat.txt`](philips-65oled908/debloat.txt) | 17 packages: Samba TV and Philips analytics, Google feedback, retail demo, NFT gallery, Control4, Alexa, Play-Fi, services for other countries. Each line says why |
| [`disable.ps1`](disable.ps1) | Saves the TV's package lists, then disables every package of a list and writes a log |
| [`restore.ps1`](restore.ps1) | Re-enables the packages of a list (leaves alone what the firmware had disabled itself) |

## Use

1. On the TV: *Settings → System → About → Android TV OS build*, press OK seven times, then turn on
   *Developer options → Network debugging* (or *USB debugging*). Note the TV's IP address.
2. On a Windows PC with [platform-tools](https://developer.android.com/tools/releases/platform-tools) (ADB):
   ```powershell
   .\tools\disable.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt -DryRun   # shows what it would do
   .\tools\disable.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt
   ```
   Accept the debugging prompt on the TV the first time.
3. Undo:
   ```powershell
   .\tools\restore.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt
   ```

Comment out (`#`) any line you want to keep before running it. Another TV model needs its own list: start from
this one and only keep what you understand.

> **Philips: never disable `org.droidtv.eum`** (the Help app). The Sources panel needs it: without it, the panel
> crashes as soon as it opens and the HDMI inputs can no longer be selected. Hide it from the home screen instead.

The same list is shared with [Clean Your TV](https://github.com/Gathin23/cleanyourtv), whose own scripts
(`tools/disable.sh`, `tools/restore.sh`) also run on macOS and Linux.

---

# Outils de nettoyage de la télé

[English](#tv-cleanup-tools) · **Français**

Deux scripts PowerShell qui désactivent les applis inutiles d'une télé Android en Wi-Fi avec ADB, et une liste
testée sur une **Philips 65OLED908/12** (Google TV 14). Ils **désactivent** seulement les applis
(`pm disable-user`) : rien n'est désinstallé, et tout revient en une commande.

| Fichier | Rôle |
|---|---|
| [`philips-65oled908/debloat.txt`](philips-65oled908/debloat.txt) | 17 applis : Samba TV et statistiques Philips, rapports Google, mode démo magasin, galerie NFT, Control4, Alexa, Play-Fi, services d'autres pays. Chaque ligne dit pourquoi |
| [`disable.ps1`](disable.ps1) | Sauvegarde la liste des applis de la télé, puis désactive celles de la liste et tient un journal |
| [`restore.ps1`](restore.ps1) | Réactive les applis de la liste (sans toucher à celles que la télé avait désactivées d'elle-même) |

## Utilisation

1. Sur la télé : *Paramètres → Système → À propos → Version de l'OS*, appuyer 7 fois sur OK, puis activer
   *Options pour les développeurs → Débogage réseau* (ou *Débogage USB*). Noter l'adresse IP de la télé.
2. Sur un PC Windows avec les [platform-tools](https://developer.android.com/tools/releases/platform-tools) (ADB) :
   ```powershell
   .\tools\disable.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt -DryRun   # montre ce qu'il ferait
   .\tools\disable.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt
   ```
   La première fois, accepter l'autorisation de débogage sur la télé.
3. Pour annuler :
   ```powershell
   .\tools\restore.ps1 -TvHost 192.168.1.50 .\tools\philips-65oled908\debloat.txt
   ```

Mettez un `#` devant une ligne pour garder l'appli. Un autre modèle de télé a besoin de sa propre liste : partez
de celle-ci et ne gardez que ce que vous comprenez.

> **Philips : ne désactivez jamais `org.droidtv.eum`** (l'appli Aide). Le panneau Sources en a besoin : sans elle,
> il se ferme dès qu'il s'ouvre et on ne peut plus choisir les entrées HDMI. Masquez-la plutôt de l'accueil.

La même liste est partagée avec [Clean Your TV](https://github.com/Gathin23/cleanyourtv), dont les scripts
(`tools/disable.sh`, `tools/restore.sh`) marchent aussi sous macOS et Linux.
