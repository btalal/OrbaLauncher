package fr.maison.accueil;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * Rendu « premium » d'Orba, repris d'OrbaFlux 2.1 (Views.java) et réduit à ce dont le lanceur a besoin :
 * verre translucide, reflet glossy, halo dégradé cyan → bleu → violet qui respire, options de menu en pilule.
 * Tout est dessiné en code (aucune image ajoutée : l'APK ne grossit presque pas).
 */
final class Views {
    static final int CYAN = 0xFF00E5FF, BLEU = 0xFF0078FF, INDIGO = 0xFF4B3DFF, VIOLET = 0xFFA64DFF;
    static final int PANEL = 0xFF0B1739, PANEL_FOCUS = 0xFF102B5E;   // panneaux OrbaTV
    static final int[] DEGRADE = {CYAN, BLEU, VIOLET};

    // ---------- Thèmes (Réglages Orba > Thème) ----------
    /** « Orba » : le style sobre d'origine ; « Glossy » : le rendu OrbaTV (verre, halo qui respire, reflets) ; « OLED » : noir profond et accent ambre, comme OrbaTV. */
    static final int ORBA = 0, GLOSSY = 1, OLED = 2;
    static final int AMBRE = 0xFFFFC24D, AMBRE_CLAIR = 0xFFFFE27A, AMBRE_CUIVRE = 0xFFFF9A4D;
    static final int OLED_FOND = 0xFF0A0A0C, OLED_DOUX = 0xFFB4B4BE;
    static int theme = GLOSSY;

    static boolean glossy() { return theme == GLOSSY; }
    static boolean oled() { return theme == OLED; }
    /** Accent du thème : cyan, ou ambre doré en OLED. */
    static int accent() { return theme == OLED ? AMBRE : CYAN; }
    /** Fond d'écran de base du thème. */
    static int fond() { return theme == OLED ? OLED_FOND : 0xFF00020F; }
    /** Texte secondaire (date, signes) du thème. */
    static int doux() { return theme == OLED ? OLED_DOUX : 0xFF8FA0C8; }

    private Views() { }

    static float px(Context c, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    /** Respiration lente (0..1) commune à tous les halos : ils battent ensemble, comme sur OrbaTV. */
    static float souffle() {
        return 0.5f + 0.5f * (float) Math.sin(android.os.SystemClock.uptimeMillis() / 1700.0 * Math.PI);
    }

    /**
     * Halo autour d'un rectangle arrondi : couches de plus en plus larges et transparentes.
     * p doit porter le dégradé (ou la couleur) ; force 0..1 ; ext = largeur du halo en pixels.
     */
    static void halo(Canvas c, RectF r, float rayon, float ext, float force, Paint p, RectF tmp, int couches, int alphaMax) {
        if (force <= 0.01f) return;
        for (int i = couches; i >= 1; i--) {
            float g = ext * i / couches;
            p.setAlpha(Math.round(force * alphaMax * (couches + 1 - i) / couches));
            tmp.set(r.left - g, r.top - g, r.right + g, r.bottom + g);
            c.drawRoundRect(tmp, rayon + g, rayon + g, p);
        }
    }

    static Shader degrade(float x0, float x1) {
        return new LinearGradient(x0, 0, x1, 0, DEGRADE, null, Shader.TileMode.CLAMP);
    }

    /** Reflet glossy sur la moitié haute de r (à appeler avec le canevas déjà découpé à la forme). */
    static void reflet(Canvas c, RectF r, float rayon, int alpha, Paint p) {
        p.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.5f,
                (alpha << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r.left, r.top, r.right, r.top + r.height() * 0.5f, rayon, rayon, p);
    }

    /** Filet « verre » : blanc en haut, cyan en bas. */
    static void filet(Canvas c, RectF r, float rayon, float epaisseur, int alphaHaut, int alphaBas, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(epaisseur);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, (alphaHaut << 24) | 0xFFFFFF,
                (alphaBas << 24) | (CYAN & 0xFFFFFF), Shader.TileMode.CLAMP));
        float e = epaisseur / 2f;
        c.drawRoundRect(r.left + e, r.top + e, r.right - e, r.bottom - e, rayon, rayon, p);
    }

    /** Contour uni (thèmes Orba et OLED). */
    static void contour(Canvas c, RectF r, float rayon, float epaisseur, int couleur, int alpha, Paint p) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(epaisseur);
        p.setColor(couleur);
        p.setAlpha(alpha);
        float e = epaisseur / 2f;
        c.drawRoundRect(r.left + e, r.top + e, r.right - e, r.bottom - e, rayon, rayon, p);
    }

    // ---------- Carte « verre » (fenêtres de menu, bandeau d'aide) ----------

    static Drawable verre(Context c, float rayonDp) {
        if (theme == GLOSSY) return new Verre(px(c, rayonDp), px(c, 1));
        GradientDrawable g;
        if (theme == OLED) {   // noir profond, filet de verre clair (OrbaTV OLED)
            g = new GradientDrawable();
            g.setColor(0xF50A0A0C);
            g.setStroke(Math.round(px(c, 1)), 0x2EFFFFFF);
        } else {               // style Orba d'origine : bleu nuit
            g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0E1A42, 0xFF070C20});
            g.setStroke(Math.round(px(c, 1)), 0xFF22305F);
        }
        g.setCornerRadius(px(c, rayonDp));
        return g;
    }

    /** Fond translucide, reflet en haut, contour dégradé blanc → cyan : l'effet verre poli d'OrbaTV. */
    static final class Verre extends Drawable {
        private final float r, trait;
        private final Paint fond = new Paint(Paint.ANTI_ALIAS_FLAG), brillance = new Paint(Paint.ANTI_ALIAS_FLAG),
                bord = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF b = new RectF(), s = new RectF();
        private final Path clip = new Path();

        Verre(float rayon, float trait) {
            this.r = rayon;
            this.trait = trait;
            bord.setStyle(Paint.Style.STROKE);
            bord.setStrokeWidth(trait);
        }

        @Override protected void onBoundsChange(Rect bo) {
            float h = bo.height();
            fond.setShader(new LinearGradient(0, bo.top, 0, bo.bottom, 0xF7132466, 0xFA0A1238, Shader.TileMode.CLAMP));
            brillance.setShader(new LinearGradient(0, bo.top, 0, bo.top + Math.min(h * 0.55f, r * 4), 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            bord.setShader(new LinearGradient(0, bo.top, 0, bo.bottom, 0x99FFFFFF, 0x5500E5FF, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            Rect bo = getBounds();
            b.set(bo.left + trait, bo.top + trait, bo.right - trait, bo.bottom - trait);
            c.drawRoundRect(b, r, r, fond);
            clip.reset();
            clip.addRoundRect(b, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            s.set(b.left, b.top, b.right, b.top + Math.min(b.height() * 0.55f, r * 4));
            c.drawRect(s, brillance);
            c.restore();
            c.drawRoundRect(b, r, r, bord);
        }

        @Override public void setAlpha(int alpha) { fond.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------- Option de menu : pilule glossy avec halo (télécommande + tactile) ----------

    static final class Option extends TextView {
        private final float glow;
        private float halo, eclat;
        private final Paint haloP = new Paint(Paint.ANTI_ALIAS_FLAG), corps = new Paint(Paint.ANTI_ALIAS_FLAG),
                reflet = new Paint(Paint.ANTI_ALIAS_FLAG), bord = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF body = new RectF(), tmp = new RectF();
        private final Path clip = new Path();
        private ValueAnimator anim, animEclat;

        Option(Context c, String texte, Typeface police, float sp, int couleur) {
            super(c);
            glow = px(c, 9);                     // marge réservée au halo : jamais rogné par la liste
            setText(texte);
            setTypeface(police);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            setTextColor(couleur);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            setTextAlignment(TEXT_ALIGNMENT_VIEW_START);   // arabe : aligné à droite, même pour un nom latin
            setSingleLine(true);
            setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);   // texte trop long : il défile au focus
            setMarqueeRepeatLimit(-1);
            setHorizontalFadingEdgeEnabled(true);
            int h = Math.round(px(c, 18) + glow), v = Math.round(px(c, 12) + glow);
            setPaddingRelative(h, v, h, v);
            setFocusable(true);
            setClickable(true);
            setWillNotDraw(false);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0) return;
            float sx = getScrollX();             // le défilement du texte décale le canevas : le fond reste fixe
            body.set(sx + glow, glow, sx + w - glow, h - glow);
            float f = Math.max(halo, eclat);
            if (theme != GLOSSY) {
                // ORBA : fond bleu nuit + contour cyan ; OLED : voile ambre + contour ambre. Sobres : ni halo, ni reflet.
                if (f > 0.01f) {
                    float rs = px(getContext(), 14);
                    int ac = accent();
                    corps.setShader(null);
                    corps.setColor(theme == OLED ? (ac & 0x00FFFFFF) | (0x29 << 24) : 0xFF15245A);
                    corps.setAlpha(Math.round(Color.alpha(corps.getColor()) * f));
                    c.drawRoundRect(body, rs, rs, corps);
                    bord.setShader(null);
                    bord.setStyle(Paint.Style.STROKE);
                    bord.setStrokeWidth(px(getContext(), 2));
                    bord.setColor(ac);
                    bord.setAlpha(Math.round(255 * f));
                    c.drawRoundRect(body, rs, rs, bord);
                }
                super.onDraw(c);
                return;
            }
            float r = Math.min(body.height() / 2f, px(getContext(), 16));
            if (f > 0.01f) {
                haloP.setShader(degrade(body.left, body.right));
                halo(c, body, r, glow, f * (0.8f + 0.2f * souffle()), haloP, tmp, 6, 26);
                corps.setShader(new LinearGradient(0, body.top, 0, body.bottom, 0xFF16397A, PANEL_FOCUS, Shader.TileMode.CLAMP));
                corps.setAlpha(Math.round(255 * f));
                c.drawRoundRect(body, r, r, corps);
                clip.reset();
                clip.addRoundRect(body, r, r, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                reflet(c, body, r, Math.round(0x3C * f), reflet);
                c.restore();
                filet(c, body, r, px(getContext(), 2), Math.round(0xF5 * f), Math.round(0xCC * f), bord);
                if (isFocused()) postInvalidateOnAnimation();   // le halo respire tant que l'option est choisie
            }
            super.onDraw(c);
        }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            setSelected(gain);                   // marquee : défile seulement au focus
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(halo, gain ? 1f : 0f);
            anim.setDuration(140);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { halo = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
            float zoom = theme == GLOSSY ? 1.03f : 1f;
            animate().scaleX(gain ? zoom : 1f).scaleY(gain ? zoom : 1f).setDuration(140)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { halo = 1f; invalidate(); }
            else if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !isFocused()) { halo = 0f; invalidate(); }
            return super.onTouchEvent(e);
        }

        @Override public boolean performClick() {
            if (animEclat != null) animEclat.cancel();
            animEclat = ValueAnimator.ofFloat(1f, 0f);
            animEclat.setDuration(420);
            animEclat.setInterpolator(new AccelerateDecelerateInterpolator());
            animEclat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { eclat = (Float) a.getAnimatedValue(); invalidate(); }
            });
            animEclat.start();
            return super.performClick();
        }
    }

    /** Pastille colorée des titres de section (petite gélule dégradée cyan → violet, avec reflet). */
    static final class Pastille extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            r.set(b);
            float rr = Math.min(r.width(), r.height()) / 2f;
            if (theme == ORBA) {   // filet plat d'origine : cyan → bleu
                p.setShader(new LinearGradient(r.left, 0, r.right, 0, CYAN, BLEU, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rr, rr, p);
                return;
            }
            p.setShader(theme == OLED
                    ? new LinearGradient(0, r.top, 0, r.bottom, AMBRE_CLAIR, AMBRE_CUIVRE, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, r.top, 0, r.bottom, CYAN, VIOLET, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rr, rr, p);
            if (theme == GLOSSY) reflet(c, r, rr, 0x80, g);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static int blanc(int alpha) { return Color.argb(alpha, 255, 255, 255); }
}
