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
        // Le thème choisi dans les réglages (la veille peut démarrer dans un processus neuf ou comme économiseur Android)
        Views.theme = Math.max(0, Math.min(2, c.getSharedPreferences("accueil", Context.MODE_PRIVATE).getInt("theme", Views.GLOSSY)));
        FrameLayout racine = new FrameLayout(c);
        racine.setBackgroundColor(Views.fond());
        racine.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);   // positions calculées depuis la gauche, aussi en arabe
        MainActivity.OndesView ondes = new MainActivity.OndesView(c);
        ondes.setAttenuation(0.45f);                  // vagues plus discrètes la nuit
        if (Views.oled()) ondes.setMode(2);           // thème OLED : noir profond, sans vagues
        racine.addView(ondes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        racine.addView(new Horloge(c), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        racine.setTag(ondes);
        return racine;
    }

    static void demarrer(View racine) { ((MainActivity.OndesView) racine.getTag()).start(); }
    static void arreter(View racine)  { ((MainActivity.OndesView) racine.getTag()).stop(); }

    /**
     * Logo qui tourne + heure + date, en dérive lente.
     *
     * Fluidité : rien n'est redessiné pendant l'animation. Le halo, l'anneau et le texte sont dessinés
     * une seule fois chacun dans un calque de la carte graphique (le texte, une fois par minute) ;
     * à chaque image, on ne change que leur position, leur rotation, leur échelle et leur opacité.
     * La carte graphique déplace ces calques au sous-pixel près : mouvement continu, sans saccade,
     * et le texte ne scintille plus (il n'est plus re-rastérisé à une position différente à chaque image).
     */
    static final class Horloge extends FrameLayout {
        private final float d;
        private final FrameLayout groupe;      // halo + anneau + texte, déplacé d'un bloc
        private final View halo, anneau;
        private final Texte texte;
        private final int gw, gh, ra;          // taille du groupe, rayon de l'anneau
        private final long t0 = SystemClock.uptimeMillis();
        private boolean actif;

        private final Runnable image = new Runnable() {
            @Override public void run() {
                if (!actif) return;
                animer();
                postOnAnimation(this);          // une mise à jour par image affichée, sans dessiner
            }
        };

        /** Le texte change une seule fois par minute, juste après le changement d'heure. */
        private final Runnable minute = new Runnable() {
            @Override public void run() {
                texte.invalidate();
                long now = System.currentTimeMillis();
                postDelayed(this, 60_000 - now % 60_000 + 50);
            }
        };

        Horloge(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            ra = Math.round(62 * d);
            gw = Math.round(560 * d);
            int rh = Math.round(ra * 2.1f);                // rayon du halo
            gh = rh * 2 + Math.round(150 * d);             // halo + heure + date + marque
            groupe = new FrameLayout(c);
            groupe.setClipChildren(false);
            groupe.setClipToPadding(false);
            setClipChildren(false);

            halo = new Halo(c, rh);
            halo.setLayerType(LAYER_TYPE_HARDWARE, null);
            groupe.addView(halo, place(rh * 2, rh * 2, (gw - rh * 2) / 2, 0));

            anneau = new Anneau(c);
            anneau.setLayerType(LAYER_TYPE_HARDWARE, null);
            groupe.addView(anneau, place(ra * 2, ra * 2, (gw - ra * 2) / 2, rh - ra));

            texte = new Texte(c);
            texte.setLayerType(LAYER_TYPE_HARDWARE, null);
            int yTexte = rh + ra + Math.round(8 * d);
            groupe.addView(texte, place(gw, gh - yTexte, 0, yTexte));

            addView(groupe, new FrameLayout.LayoutParams(gw, gh));
        }

        private static FrameLayout.LayoutParams place(int w, int h, int x, int y) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            lp.leftMargin = x;
            lp.topMargin = y;
            return lp;
        }

        /** Position, rotation, pulsation et respiration à l'instant présent (aucun dessin). */
        private void animer() {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0) return;
            // Trajectoire en huit (Lissajous) très lente : traverse l'écran en quelques minutes.
            float ampX = Math.max(0f, (W - gw) / 2f - 40 * d);
            float ampY = Math.max(0f, (H - gh) / 2f - 30 * d);
            float x = (W - gw) / 2f + ampX * (float) Math.sin(2 * Math.PI * t / 173.0);
            float y = (H - gh) / 2f + ampY * (float) Math.sin(2 * Math.PI * t / 127.0 + 1.0);
            groupe.setTranslationX(x);
            groupe.setTranslationY(y);
            // Anneau : un tour en 24 s et légère pulsation ; halo qui respire au même rythme.
            float onde = (float) Math.sin(t * 1.1);
            anneau.setRotation((t * 15f) % 360f);
            float echelle = 1f + 0.035f * onde;
            anneau.setScaleX(echelle);
            anneau.setScaleY(echelle);
            halo.setAlpha(0.47f + 0.53f * (0.5f + 0.5f * onde));
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            actif = true;
            postOnAnimation(image);
            removeCallbacks(minute);
            post(minute);
        }

        @Override protected void onDetachedFromWindow() {
            actif = false;
            removeCallbacks(image);
            removeCallbacks(minute);
            super.onDetachedFromWindow();
        }

        private static float sp(Context c, float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
        }

        private static Typeface police(Context c, String fichier, Typeface secours) {
            try { return Typeface.createFromAsset(c.getAssets(), fichier); } catch (RuntimeException e) { return secours; }
        }

        /** Lueur bleue derrière l'anneau, dessinée une fois à pleine intensité (l'opacité varie ensuite). */
        private static final class Halo extends View {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            private final int r;
            Halo(Context c, int r) { super(c); this.r = r; }
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                if (Views.oled())   // OLED : lueur ambre, plus discrète
                    p.setShader(new RadialGradient(w / 2f, h / 2f, r, Color.argb(60, 255, 194, 77), 0x00FFC24D, Shader.TileMode.CLAMP));
                else
                    p.setShader(new RadialGradient(w / 2f, h / 2f, r, Color.argb(85, 0, 150, 255), 0x000096FF, Shader.TileMode.CLAMP));
            }
            @Override protected void onDraw(Canvas c) { c.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p); }
        }

        /** Anneau du logo, dessiné une fois ; la rotation et l'échelle sont appliquées par la carte graphique. */
        private static final class Anneau extends View {
            private final Bitmap image;
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            private final android.graphics.Rect cadre = new android.graphics.Rect();
            Anneau(Context c) {
                super(c);
                image = BitmapFactory.decodeResource(c.getResources(),
                        c.getResources().getIdentifier("logo_anneau", "drawable", c.getPackageName()));
            }
            @Override protected void onDraw(Canvas c) {
                if (image == null) return;
                cadre.set(0, 0, getWidth(), getHeight());
                c.drawBitmap(image, null, cadre, p);
            }
        }

        /** Heure, date et marque, à des positions entières fixes dans leur calque. */
        private final class Texte extends View {
            private final Paint pHeure = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            private final Paint pDate = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            private final Paint pMarque = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final SimpleDateFormat fHeure = new SimpleDateFormat("HH:mm", Locale.getDefault());
            private final SimpleDateFormat fDate;

            Texte(Context c) {
                super(c);
                fDate = new SimpleDateFormat(c.getString(R.string.format_date), Locale.getDefault());
                pHeure.setTypeface(police(c, "inter-200.ttf", Typeface.create("sans-serif-thin", Typeface.NORMAL)));
                pHeure.setTextSize(sp(c, 72));
                pHeure.setColor(Views.oled() ? 0xFFDADADF : 0xFFD6DEF2);   // pas de blanc pur : moins d'usure OLED
                pHeure.setTextAlign(Paint.Align.CENTER);
                pDate.setTypeface(police(c, "inter-300.ttf", Typeface.DEFAULT));
                pDate.setTextSize(sp(c, 20));
                pDate.setColor(Views.oled() ? 0xFF8C8C96 : 0xFF7F8DB3);
                pDate.setTextAlign(Paint.Align.CENTER);
                pMarque.setTypeface(police(c, "montserrat-800.ttf", Typeface.DEFAULT_BOLD));
                pMarque.setTextSize(sp(c, 13));
                pMarque.setColor(Views.oled() ? 0x80FFC24D : 0x66A9B8DD);
                pMarque.setLetterSpacing(0.6f);
                pMarque.setTextAlign(Paint.Align.CENTER);
            }

            @Override protected void onDraw(Canvas c) {
                Date maintenant = new Date();
                float cx = Math.round(getWidth() / 2f);
                float yHeure = Math.round(14 * d - pHeure.ascent());
                c.drawText(fHeure.format(maintenant), cx, yHeure, pHeure);
                String date = fDate.format(maintenant);
                if (!date.isEmpty()) date = date.substring(0, 1).toUpperCase(Locale.getDefault()) + date.substring(1);
                c.drawText(date, cx, Math.round(yHeure + 34 * d), pDate);
                c.drawText("ORBA", cx, Math.round(yHeure + 62 * d), pMarque);
            }
        }
    }
}
