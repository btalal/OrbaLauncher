# Contributing to Orba

**English** · [Français](#contribuer-à-orba)

Thank you for helping! Orba stays small on purpose: plain Java, no Gradle, no Internet permission, no
tracking. Ideas and bug reports are as welcome as code.

## Ideas, questions, bugs
- **A question or an idea to discuss:** [Discussions](https://github.com/btalal/OrbaLauncher/discussions).
- **A bug or a precise feature request:** [open an issue](https://github.com/btalal/OrbaLauncher/issues/new/choose) and
  fill in the form. Always give the TV model, Android / Google TV version and Orba version (*Settings → Apps →
  Orba*).
- Suggestions from the [Orba Tech](https://www.youtube.com/@OrbaTechFR) YouTube comments end up here too.

## Code
1. Fork the repository and create a branch.
2. Build with `.\build.ps1` (Windows, Android SDK and Android Studio's JDK, see the README). The script creates a
   local signing key the first time; keep it out of the repository (`*.jks` is ignored).
3. Install on a real TV with `adb install -r orbalauncher.apk` and check your change with the remote: every
   direction from every edge, and the Home key.
4. Keep to the house rules:
   - no new permission, no network access, no background service;
   - every visible text in the 5 languages, in `res/values*/strings.xml` (English, French, Spanish, Italian, Arabic), never hard-coded; start/end margins only (Arabic is right-to-left);
   - nothing specific to one TV brand in the general code path without a fallback for the others.
5. Open a pull request: say what changed, the TV you tried it on, and add a screenshot or a short video.

GitHub builds the APK on every pull request (*Actions* tab) with a throwaway key, so the reviewer can try it.

## TV cleanup lists
`tools/` holds lists tested on a real TV. A new list goes in `tools/<brand>-<model>/debloat.txt`, starts with
the model, Android version and firmware, lists only packages you disabled and lived with for a few days, and
only **disables** (never uninstalls). See [tools/README.md](tools/README.md).

## Releases (maintainer)
1. Bump `versionCode` and `versionName` in `AndroidManifest.xml`, add the version to `CHANGELOG.md`.
2. Push a tag `v<versionName>` (e.g. `git tag v3.8 && git push origin v3.8`). The *Build APK* workflow builds
   `orba.apk` signed with the original key and attaches it to the release (created as a draft if it does not
   exist yet).
3. One-time setup: the original key must be stored as the repository secret `ORBA_KEYSTORE_B64`. From the
   folder that holds `cle-accueil.jks`, with the GitHub CLI logged in:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\cle-accueil.jks")) | gh secret set ORBA_KEYSTORE_B64 -R btalal/OrbaLauncher
   ```
   Without that secret the tag build stops instead of signing with a new key (an APK signed with another key
   cannot be installed over the existing app).

By contributing you agree that your work is released under the [MIT licence](LICENSE). The Orba name and logo
are not covered by it (see [NOTICE](NOTICE)).

---

# Contribuer à Orba

[English](#contributing-to-orba) · **Français**

Merci de votre aide ! Orba reste volontairement léger : Java simple, sans Gradle, sans accès à Internet, sans
pistage. Les idées et les signalements de bugs comptent autant que le code.

- **Une question, une idée à discuter :** [Discussions](https://github.com/btalal/OrbaLauncher/discussions).
- **Un bug ou une demande précise :** [ouvrir un ticket](https://github.com/btalal/OrbaLauncher/issues/new/choose) et
  remplir le formulaire, avec le modèle de télé, la version d'Android / Google TV et la version d'Orba.
- **Du code :** forkez le dépôt, construisez avec `.\build.ps1`, testez sur une vraie télé avec la télécommande,
  gardez les règles ci-dessus (aucune nouvelle permission, aucun accès réseau, textes dans les 5 langues, dans
  `res/values*/strings.xml`), puis ouvrez une pull request avec la télé utilisée et une capture.
- **Une liste de nettoyage :** dans `tools/<marque>-<modèle>/debloat.txt`, seulement des applis désactivées et
  testées quelques jours, jamais désinstallées. Voir [tools/README.md](tools/README.md).

En contribuant, vous acceptez que votre travail soit publié sous [licence MIT](LICENSE). Le nom et le logo Orba
n'en font pas partie (voir [NOTICE](NOTICE)).
