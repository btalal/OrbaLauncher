package fr.maison.accueil;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Écran de veille Orba : l'anneau du logo tourne lentement en pulsant, l'heure et la date
 * sont posées dessous, et l'ensemble dérive doucement sur tout l'écran (trajectoire en
 * huit, sur plusieurs minutes) au-dessus des vagues atténuées. Rien ne reste au même
 * endroit : l'écran OLED ne marque pas.
 */
final class Veille {
    private Veille() { }

    /** Vue complète de la veille : vagues atténuées + logo, heure et date animés. */
    static FrameLayout creer(Context c) {
        FrameLayout racine = new FrameLayout(c);
        racine.setBackgroundColor(MainActivity.NUIT);
        MainActivity.OndesView ondes = new MainActivity.OndesView(c);
        ondes.setAttenuation(0.45f);                  // vagues plus discrètes la nuit
        racine.addView(ondes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        racine.addView(new Horloge(c), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        racine.setTag(ondes);
        return racine;
    }

    static void demarrer(View racine) { ((MainActivity.OndesView) racine.getTag()).start(); }
    static void arreter(View racine)  { ((MainActivity.OndesView) racine.getTag()).stop(); }

    /** Logo qui tourne + heure + date, en dérive lente. */
    static final class Horloge extends View {
        private final Bitmap anneau;
        private final Paint pImage = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint pHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pHeure = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pMarque = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SimpleDateFormat fHeure = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat fDate = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());
        private final long t0 = SystemClock.uptimeMillis();
        private final float d;

        Horloge(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            anneau = BitmapFactory.decodeResource(c.getResources(),
                    c.getResources().getIdentifier("logo_anneau", "drawable", c.getPackageName()));
            pHeure.setTypeface(police(c, "inter-200.ttf", Typeface.create("sans-serif-thin", Typeface.NORMAL)));
            pHeure.setTextSize(sp(c, 72));
            pHeure.setColor(0xFFD6DEF2);               // pas de blanc pur : moins d'usure OLED
            pHeure.setTextAlign(Paint.Align.CENTER);
            pDate.setTypeface(police(c, "inter-300.ttf", Typeface.DEFAULT));
            pDate.setTextSize(sp(c, 20));
            pDate.setColor(0xFF7F8DB3);
            pDate.setTextAlign(Paint.Align.CENTER);
            pMarque.setTypeface(police(c, "montserrat-800.ttf", Typeface.DEFAULT_BOLD));
            pMarque.setTextSize(sp(c, 13));
            pMarque.setColor(0x66A9B8DD);
            pMarque.setLetterSpacing(0.6f);
            pMarque.setTextAlign(Paint.Align.CENTER);
        }

        private static float sp(Context c, float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
        }

        private static Typeface police(Context c, String fichier, Typeface secours) {
            try { return Typeface.createFromAsset(c.getAssets(), fichier); } catch (RuntimeException e) { return secours; }
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); postInvalidateOnAnimation(); }

        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float W = getWidth(), H = getHeight();
            float ra = 62 * d;                                       // rayon de l'anneau
            float bloc = ra * 2 + 160 * d;                           // hauteur logo + heure + date + marque
            // Trajectoire en huit (Lissajous) très lente : traverse l'écran en quelques minutes.
            float cx = W / 2f + (W / 2f - 230 * d) * (float) Math.sin(2 * Math.PI * t / 173.0);
            float cy = H / 2f + Math.max(0f, H / 2f - bloc / 2f - 50 * d) * (float) Math.sin(2 * Math.PI * t / 127.0 + 1.0);
            float yLogo = cy - bloc / 2f + ra;

            // Halo qui respire derrière l'anneau
            float respire = 0.5f + 0.5f * (float) Math.sin(t * 1.1);
            int a = Math.round(40 + 45 * respire);
            pHalo.setShader(new RadialGradient(cx, yLogo, ra * 2.1f, Color.argb(a, 0, 150, 255), 0x000096FF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, yLogo, ra * 2.1f, pHalo);

            // Anneau : rotation lente (un tour en 24 s) et légère pulsation
            if (anneau != null) {
                float echelle = 1f + 0.035f * (float) Math.sin(t * 1.1);
                float taille = ra * 2 * echelle;
                c.save();
                c.rotate((t * 15f) % 360f, cx, yLogo);
                c.drawBitmap(anneau, null, new android.graphics.RectF(cx - taille / 2, yLogo - taille / 2, cx + taille / 2, yLogo + taille / 2), pImage);
                c.restore();
            }

            // Heure, date et marque
            Date maintenant = new Date();
            float yHeure = yLogo + ra + 22 * d - pHeure.ascent();
            c.drawText(fHeure.format(maintenant), cx, yHeure, pHeure);
            String date = fDate.format(maintenant);
            if (!date.isEmpty()) date = date.substring(0, 1).toUpperCase(Locale.getDefault()) + date.substring(1);
            c.drawText(date, cx, yHeure + 34 * d, pDate);
            c.drawText("ORBA", cx, yHeure + 62 * d, pMarque);

            postInvalidateOnAnimation();
        }
    }
}
