# Construit accueil-simple.apk sans Gradle, avec les outils du SDK Android.
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = (Get-ChildItem "$sdk\build-tools" | Sort-Object { [version]($_.Name -replace '[^\d.]','') } | Select-Object -Last 1).FullName
$jar  = (Get-ChildItem "$sdk\platforms" | Sort-Object Name | Select-Object -Last 1).FullName + "\android.jar"
$jbr  = "C:\Program Files\Android\Android Studio\jbr\bin"
$out  = "$here\build"
$env:JAVA_HOME = Split-Path $jbr; $env:PATH = "$jbr;$env:PATH"   # d8 et apksigner ont besoin de Java

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$here\res\drawable-xhdpi" | Out-Null

# 1. Icône et bannière : dessinées par icone.ps1 (aurore + pilule + Inter), seulement si absentes
if (-not (Test-Path "$here\res\drawable-xhdpi\banniere.png") -or -not (Test-Path "$here\res\drawable-xxxhdpi\icone.png")) {
    & "$here\icone.ps1" -here $here | Out-Null
}
Remove-Item "$here\res\drawable-xhdpi\icone.png" -ErrorAction SilentlyContinue   # ancienne icône

# 2. Ressources
& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -I $jar --manifest "$here\AndroidManifest.xml" -o "$out\base.apk" "$out\res.zip" -A "$here\assets" --min-sdk-version 28 --target-sdk-version 34
if ($LASTEXITCODE) { throw "aapt2 link a échoué" }

# 3. Code Java -> .class -> .dex
$src = Get-ChildItem -Recurse "$here\src" -Filter *.java | ForEach-Object FullName
& "$jbr\javac.exe" -source 8 -target 8 -bootclasspath $jar '-Xlint:-options' -encoding UTF-8 -d "$out\classes" $src
if ($LASTEXITCODE) { throw "javac a échoué" }
$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --min-api 28 --lib $jar --output $out $classes
if ($LASTEXITCODE) { throw "d8 a échoué" }

# 4. Ajouter le .dex, aligner, signer
Push-Location $out; & "$bt\aapt.exe" add base.apk classes.dex | Out-Null; Pop-Location
& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"
$ks = "$here\cle-accueil.jks"
if (-not (Test-Path $ks)) {
    & "$jbr\keytool.exe" -genkeypair -keystore $ks -alias accueil -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass accueil-local -keypass accueil-local -dname "CN=Accueil simple" | Out-Null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:accueil-local --key-pass pass:accueil-local --out "$here\accueil-simple.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner a échoué" }
& "$bt\apksigner.bat" verify "$here\accueil-simple.apk"
"OK : $here\accueil-simple.apk"
