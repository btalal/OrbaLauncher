# Icône, logo et bannière d'Orba, découpés dans le logo fourni (source-logo\logo-orba.png),
# dont l'utilisateur préfère le rendu dégradé :
#  - anneau seul (disque, creux d'origine gardé) -> res\drawable-nodpi\logo_anneau.png (en-tête de l'appli)
#  - icône carrée de l'image                      -> res\drawable-xxxhdpi\icone.png (192x192)
#  - bannière 320x180 : anneau + ORBA de l'image, « LAUNCHER TV » bien lisible, vagues lumineuses.
param([string]$here = $PSScriptRoot)
Add-Type -AssemblyName System.Drawing
Add-Type -Path "$here\Logo.cs" -ReferencedAssemblies System.Drawing.Common, System.Drawing.Primitives

function Col($hex, [int]$a = 255) { $c = [System.Drawing.ColorTranslator]::FromHtml($hex); [System.Drawing.Color]::FromArgb($a, $c) }
function Rect($x, $y, $w, $h) { New-Object System.Drawing.Rectangle $x, $y, $w, $h }
$CYAN = Col "#00E5FF"

function NewCanvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = "AntiAlias"; $g.TextRenderingHint = "AntiAlias"; $g.CompositingQuality = "HighQuality"
    $g.PixelOffsetMode = "HighQuality"; $g.InterpolationMode = "HighQualityBicubic"; @($bmp, $g)
}
function SaveScaled($bmp, $w, $h, $path) {
    $out = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = "HighQualityBicubic"; $g.PixelOffsetMode = "HighQuality"; $g.CompositingMode = "SourceCopy"
    $g.DrawImage($bmp, 0, 0, $w, $h); $g.Dispose()
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $out.Dispose()
}
function Lueur($g, $path, $couleur, [float]$rayon, [int]$alpha = 14) {
    for ($i = 12; $i -ge 1; $i--) {
        $p = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($alpha, $couleur)), ($rayon * $i / 6)
        $p.LineJoin = "Round"; $g.DrawPath($p, $path); $p.Dispose()
    }
}
function Degrade4($rect, [int]$a) {
    $br = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $CYAN, $CYAN, 0.0
    $bl = New-Object System.Drawing.Drawing2D.ColorBlend 4
    $bl.Colors = @((Col "#00E5FF" $a), (Col "#0078FF" $a), (Col "#4B3DFF" $a), (Col "#A64DFF" $a)); $bl.Positions = @(0.0, 0.38, 0.70, 1.0)
    $br.InterpolationColors = $bl; $br
}

$src = [System.Drawing.Bitmap]::FromFile("$here\source-logo\logo-orba.png")
$fond = [LogoOutils]::Fond($src)
$cIcone  = [LogoOutils]::Cadre($src, (Rect 60 40 560 600), 30)
$cAnneau = [LogoOutils]::Cadre($src, (Rect 700 40 420 600), 30)
$cTexte  = [LogoOutils]::Cadre($src, (Rect 1120 40 860 600), 30)

# ---------- Anneau seul, découpé en disque (en-tête de l'appli)
$cx = $cAnneau.Left + $cAnneau.Width / 2.0; $cy = $cAnneau.Top + $cAnneau.Height / 2.0
$r = [math]::Min($cAnneau.Width, $cAnneau.Height) / 2.0 - 0.5
$anneau = [LogoOutils]::Disque($src, $cx, $cy, $r, 1)
New-Item -ItemType Directory -Force "$here\res\drawable-nodpi" | Out-Null
$anneau.Save("$here\res\drawable-nodpi\logo_anneau.png", [System.Drawing.Imaging.ImageFormat]::Png)
$anneau.Dispose()

# ---------- Icône 192x192 : le carré de l'image, coins arrondis transparents
$cote = [math]::Min($cIcone.Width, $cIcone.Height)
$S = 768
$bmp, $g = NewCanvas $S $S
$g.Clear([System.Drawing.Color]::Transparent)
$clip = New-Object System.Drawing.Drawing2D.GraphicsPath
$rr = $S * 0.19; $d = 2 * $rr; $in = 4
$clip.AddArc($in, $in, $d, $d, 180, 90); $clip.AddArc($S - $d - $in, $in, $d, $d, 270, 90)
$clip.AddArc($S - $d - $in, $S - $d - $in, $d, $d, 0, 90); $clip.AddArc($in, $S - $d - $in, $d, $d, 90, 90); $clip.CloseFigure()
$g.SetClip($clip)
$g.DrawImage($src, (Rect 0 0 $S $S), $cIcone.Left, $cIcone.Top, $cote, $cote, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
New-Item -ItemType Directory -Force "$here\res\drawable-xxxhdpi" | Out-Null
SaveScaled $bmp 192 192 "$here\res\drawable-xxxhdpi\icone.png"
$bmp.Save("$here\apercu-icone.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# ---------- Bannière 320x180 (dessinée en 1280x720)
$W = 1280; $H = 720; $px = [System.Drawing.GraphicsUnit]::Pixel
$bmp, $g = NewCanvas $W $H
$g.Clear($fond)
$rect = New-Object System.Drawing.RectangleF 0, 0, $W, $H
foreach ($v in @(@{ y = 0.90; amp = 0.040; per = 1.1; ph = 0.3; a = 80 }, @{ y = 0.95; amp = 0.030; per = 0.8; ph = 2.0; a = 130 })) {
    $pts = New-Object System.Collections.Generic.List[System.Drawing.PointF]
    for ($i = 0; $i -le 120; $i++) {
        $pts.Add((New-Object System.Drawing.PointF ($W * $i / 120), ($H * $v.y - $H * $v.amp * [math]::Sin(2 * [math]::PI * $v.per * $i / 120 + $v.ph))))
    }
    $crete = New-Object System.Drawing.Drawing2D.GraphicsPath; $crete.AddLines($pts.ToArray())
    $pts.Add((New-Object System.Drawing.PointF $W, $H)); $pts.Add((New-Object System.Drawing.PointF 0, $H))
    $forme = New-Object System.Drawing.Drawing2D.GraphicsPath; $forme.AddPolygon($pts.ToArray())
    $g.FillPath((Degrade4 $rect $v.a), $forme)
    Lueur $g $crete $CYAN 2 8
    $g.DrawPath((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(190, 170, 240, 255)), 3), $crete)
}
# anneau + ORBA de l'image, détourés
$m = 6
$zone = [System.Drawing.Rectangle]::FromLTRB($cAnneau.Left - $m, $cAnneau.Top - $m, $cTexte.Right + $m, $cAnneau.Bottom + $m)
$logo = [LogoOutils]::Detourer($src, $zone, 14, 50)
$lw = 1080; $lh = [int]($logo.Height * $lw / $logo.Width); $lx = ($W - $lw) / 2; $ly = 95
$g.DrawImage($logo, (Rect $lx $ly $lw $lh))
# « LAUNCHER TV » : Montserrat SemiBold, gros et clair, sur la largeur du mot ORBA
$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile("$here\assets\montserrat-600.ttf")
$k = $lw / $logo.Width
$tx = $lx + ($cTexte.Left - $zone.Left) * $k; $tw = $cTexte.Width * $k
$f = New-Object System.Drawing.Font $fonts.Families[0], 76, ([System.Drawing.FontStyle]::Regular), $px
$fmt = [System.Drawing.StringFormat]::GenericTypographic
$txt = "LAUNCHER TV"
$ws = @($txt.ToCharArray() | ForEach-Object { $g.MeasureString([string]$_, $f, 10000, $fmt).Width })
$esp = ($tw - ($ws | Measure-Object -Sum).Sum) / ($txt.Length - 1)
$x = $tx; $y = $ly + $lh + 18
$br = New-Object System.Drawing.SolidBrush (Col "#E3EAFF")
for ($i = 0; $i -lt $txt.Length; $i++) { $g.DrawString([string]$txt[$i], $f, $br, $x, $y, $fmt); $x += $ws[$i] + $esp }
$g.Dispose()
New-Item -ItemType Directory -Force "$here\res\drawable-xhdpi" | Out-Null
SaveScaled $bmp 320 180 "$here\res\drawable-xhdpi\banniere.png"
$bmp.Save("$here\apercu-banniere.png", [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
$src.Dispose(); $logo.Dispose()
"Logo, icône et bannière prêts (depuis l'image fournie)"
