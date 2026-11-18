// Outils pour extraire le logo Orba de l'image fournie (fond bleu nuit uni).
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class LogoOutils {
    static int[] Lire(Bitmap b) {
        var d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var px = new int[b.Width * b.Height];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        b.UnlockBits(d);
        return px;
    }
    static Bitmap Ecrire(int[] px, int w, int h) {
        var b = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        var d = b.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(px, 0, d.Scan0, px.Length);
        b.UnlockBits(d);
        return b;
    }
    /// Couleur du fond : moyenne d'un petit carré dans le coin haut-gauche.
    public static Color Fond(Bitmap b) {
        long r = 0, g = 0, bl = 0; int n = 0;
        for (int y = 5; y < 25; y++) for (int x = 5; x < 25; x++) { var c = b.GetPixel(x, y); r += c.R; g += c.G; bl += c.B; n++; }
        return Color.FromArgb((int)(r / n), (int)(g / n), (int)(bl / n));
    }
    static int Ecart(int p, Color f) {
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        return Math.Max(Math.Abs(r - f.R), Math.Max(Math.Abs(g - f.G), Math.Abs(b - f.B)));
    }
    /// Cadre des pixels qui s'écartent du fond, dans une zone donnée.
    public static Rectangle Cadre(Bitmap b, Rectangle zone, int seuil) {
        var px = Lire(b); var f = Fond(b);
        int x0 = int.MaxValue, y0 = int.MaxValue, x1 = -1, y1 = -1;
        for (int y = zone.Top; y < zone.Bottom; y++)
            for (int x = zone.Left; x < zone.Right; x++)
                if (Ecart(px[y * b.Width + x], f) > seuil) { x0 = Math.Min(x0, x); y0 = Math.Min(y0, y); x1 = Math.Max(x1, x); y1 = Math.Max(y1, y); }
        return x1 < 0 ? Rectangle.Empty : Rectangle.FromLTRB(x0, y0, x1 + 1, y1 + 1);
    }
    /// Découpe une zone et rend le fond transparent (avec bords adoucis, sans liseré sombre).
    public static Bitmap Detourer(Bitmap b, Rectangle zone, int bas, int haut) {
        var px = Lire(b); var f = Fond(b);
        var o = new int[zone.Width * zone.Height];
        for (int y = 0; y < zone.Height; y++)
            for (int x = 0; x < zone.Width; x++) {
                int p = px[(zone.Top + y) * b.Width + zone.Left + x];
                int e = Ecart(p, f);
                double a = e <= bas ? 0 : e >= haut ? 1 : (double)(e - bas) / (haut - bas);
                if (a <= 0) { o[y * zone.Width + x] = 0; continue; }
                int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
                // retirer la part du fond mélangée au bord : c = a*vraie + (1-a)*fond
                r = Clamp((r - (1 - a) * f.R) / a); g = Clamp((g - (1 - a) * f.G) / a); bl = Clamp((bl - (1 - a) * f.B) / a);
                o[y * zone.Width + x] = ((int)Math.Round(a * 255) << 24) | (r << 16) | (g << 8) | bl;
            }
        return Ecrire(o, zone.Width, zone.Height);
    }
    /// Découpe un disque (centre cx, cy ; rayon r) : extérieur transparent avec bord adouci,
    /// intérieur gardé tel quel (le creux de l'anneau conserve son ombrage d'origine).
    public static Bitmap Disque(Bitmap b, double cx, double cy, double r, int marge) {
        var px = Lire(b);
        int taille = (int)Math.Ceiling(2 * r) + 2 * marge;
        int x0 = (int)Math.Floor(cx - r) - marge, y0 = (int)Math.Floor(cy - r) - marge;
        var o = new int[taille * taille];
        for (int y = 0; y < taille; y++)
            for (int x = 0; x < taille; x++) {
                int sx = x0 + x, sy = y0 + y;
                if (sx < 0 || sy < 0 || sx >= b.Width || sy >= b.Height) continue;
                double d = Math.Sqrt((sx + 0.5 - cx) * (sx + 0.5 - cx) + (sy + 0.5 - cy) * (sy + 0.5 - cy));
                double a = Math.Max(0, Math.Min(1, r - d + 0.5));
                if (a <= 0) continue;
                int p = px[sy * b.Width + sx];
                o[y * taille + x] = ((int)Math.Round(a * 255) << 24) | (p & 0xFFFFFF);
            }
        return Ecrire(o, taille, taille);
    }
    static int Clamp(double v) { return v < 0 ? 0 : v > 255 ? 255 : (int)Math.Round(v); }
}
