# Construit orbalauncher.apk sans Gradle, avec les outils du SDK Android.
# Sur un PC : SDK dans %LOCALAPPDATA%\Android\Sdk et JDK d'Android Studio.
# Sur GitHub Actions : ANDROID_HOME / JAVA_HOME fournis par le runner, et -RequireKey pour refuser
# de créer une nouvelle clé (un APK signé avec une autre clé ne s'installe pas par-dessus).
param([switch]$RequireKey)
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$sdk  = if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$bt   = (Get-ChildItem "$sdk\build-tools" | Where-Object { $_.Name -match '^\d+(\.\d+)*$' } | Sort-Object { [version]$_.Name } | Select-Object -Last 1).FullName
$jar  = (Get-ChildItem "$sdk\platforms" | Where-Object { $_.Name -match '^android-\d+(\.\d+)?$' } | Sort-Object { [version](($_.Name -replace 'android-','') + '.0') } | Select-Object -Last 1).FullName + "\android.jar"
$studio = "C:\Program Files\Android\Android Studio\jbr"
$jbr  = if (Test-Path "$studio\bin\javac.exe") { "$studio\bin" } else { "$env:JAVA_HOME\bin" }
$out  = "$here\build"
$env:JAVA_HOME = Split-Path $jbr; $env:PATH = "$jbr;$env:PATH"   # d8 et apksigner ont besoin de Java

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$out\gen", "$here\res\drawable-xhdpi" | Out-Null

# 1. Icône et bannière : dessinées par icone.ps1 (aurore + pilule + Inter), seulement si absentes
if (-not (Test-Path "$here\res\drawable-xhdpi\banniere.png") -or -not (Test-Path "$here\res\drawable-xxxhdpi\icone.png")) {
    & "$here\icone.ps1" -here $here | Out-Null
}
Remove-Item "$here\res\drawable-xhdpi\icone.png" -ErrorAction SilentlyContinue   # ancienne icône

# 2. Ressources
& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -I $jar --manifest "$here\AndroidManifest.xml" -o "$out\base.apk" "$out\res.zip" -A "$here\assets" --min-sdk-version 26 --target-sdk-version 34 --java "$out\gen"
if ($LASTEXITCODE) { throw "aapt2 link a échoué" }

# 3. Code Java -> .class -> .dex
$src = Get-ChildItem -Recurse "$here\src", "$out\gen" -Filter *.java | ForEach-Object FullName
& "$jbr\javac.exe" -source 8 -target 8 -bootclasspath $jar '-Xlint:-options' -encoding UTF-8 -d "$out\classes" $src
if ($LASTEXITCODE) { throw "javac a échoué" }
$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName
[IO.File]::WriteAllLines("$out\classes.txt", [string[]]$classes)   # liste dans un fichier : la ligne de commande serait trop longue
& "$bt\d8.bat" --release --min-api 26 --lib $jar --output $out "@$out\classes.txt"
if ($LASTEXITCODE) { throw "d8 a échoué" }

# 4. Ajouter le .dex, aligner, signer
Push-Location $out; & "$bt\aapt.exe" add base.apk classes.dex | Out-Null; Pop-Location
& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"
$ks = "$here\cle-accueil.jks"
if (-not (Test-Path $ks)) {
    if ($RequireKey) { throw "cle-accueil.jks absente : impossible de signer avec la clé d'origine" }
    & "$jbr\keytool.exe" -genkeypair -keystore $ks -alias accueil -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass accueil-local -keypass accueil-local -dname "CN=OrbaLauncher" | Out-Null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:accueil-local --key-pass pass:accueil-local --out "$here\orbalauncher.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner a échoué" }
& "$bt\apksigner.bat" verify "$here\orbalauncher.apk"
"OK : $here\orbalauncher.apk"
