package fr.maison.accueil;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orba : écran d'accueil simple et épuré pour Google TV.
 * Graphisme d'après la planche de marque : anneau « O » cyan -> violet, fond bleu nuit,
 * vagues lumineuses animées, tuiles arrondies avec contour cyan sur l'appli choisie.
 */
public class MainActivity extends Activity {

    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final String PLUS = "__plus__";
    private static final int COLONNES = 5;

    // Palette Orba
    static final int CYAN = 0xFF00E5FF, BLEU = 0xFF0078FF, INDIGO = 0xFF4B3DFF, VIOLET = 0xFFA64DFF;
    static final int NUIT = 0xFF00020F, NUIT_CLAIR = 0xFF00062E, TUILE = 0xFF0C1640;   // bleu nuit du logo
    static final int TEXTE = 0xFFEAF0FF, TEXTE_DOUX = 0xFF8FA0C8;

    static final class App {
        String pkg;
        String label;
        Intent intent;
        Drawable image;
        boolean isBanner;
        int fond;   // couleur de fond de la tuile (bord de la bannière)
        int halo;   // couleur vive du halo lumineux (tirée du logo de l'appli)
    }

    /** Applis masquées par défaut (applis Philips peu utiles). Réaffichables via la tuile « + ». */
    private static final String[] MASQUEES_PAR_DEFAUT = {
            "org.droidtv.aurora",   // Aurora
            "org.droidtv.eum",      // Aide
            "org.droidtv.sunrise"   // Alarme lever de soleil
    };

    private final List<App> apps = new ArrayList<>();        // applis visibles, dans l'ordre affiché
    private final List<App> toutes = new ArrayList<>();      // toutes les applis (visibles + masquées)
    private final java.util.Map<String, App> cache = new java.util.HashMap<>();
    private android.content.SharedPreferences prefs;
    private App enDeplacement = null;                        // tuile en cours de déplacement
    private String focusPkg = null;                          // tuile à resélectionner après reconstruction
    private TextView aide;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout header;
    private LinearLayout rows;
    private OndesView ondes;
    private ReseauView reseau;

    /** Met à jour l'icône réseau toutes les 10 s. */
    private final Runnable majReseau = new Runnable() {
        @Override public void run() {
            reseau.majEtat();
            handler.postDelayed(this, 10_000);
        }
    };
    private String signature = "";
    private int shift = 0;
    private Typeface interFin, gras, moyen;
    private App plus;

    /** Décale l'en-tête de quelques pixels chaque minute (protection OLED). */
    private final Runnable mover = new Runnable() {
        @Override public void run() {
            shift = (shift + 1) % 8;
            header.setTranslationX(dp((shift % 4) * 2 - 3));
            header.setTranslationY(dp((shift / 4) * 2 - 1));
            handler.postDelayed(this, 60_000);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    /** Charge une police intégrée ; en cas de souci, police système (jamais de plantage). */
    private Typeface police(String fichier) {
        try {
            return Typeface.createFromAsset(getAssets(), fichier);
        } catch (RuntimeException e) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        interFin = police("inter-300.ttf");          // heure et date : Inter (choisie au début du projet)
        gras = police("montserrat-800.ttf");         // ORBA et titres : Montserrat ExtraBold (police du logo)
        moyen = police("montserrat-600.ttf");        // noms d'applis, section, menus : Montserrat SemiBold

        prefs = getSharedPreferences("accueil", MODE_PRIVATE);
        if (!prefs.getBoolean("init", false)) {
            Set<String> m = new HashSet<>();
            Collections.addAll(m, MASQUEES_PAR_DEFAUT);
            prefs.edit().putStringSet("masquees", m).putBoolean("init", true).apply();
        }

        plus = new App();
        plus.pkg = PLUS;
        plus.label = "Réafficher une appli";
        plus.fond = TUILE;
        plus.halo = CYAN;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NUIT);

        ondes = new OndesView(this);
        root.addView(ondes, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(52), dp(30), dp(52), 0);

        // ---- En-tête : logo à gauche ; Wi-Fi, Paramètres et heure à droite (date dessous)
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(7), 0, 0, 0);   // logo décalé d'environ 1 cm vers la droite (bord coupé par la télé)

        // Logo Orba : anneau vectoriel (d'après orba_symbole.svg) + ORBA en Montserrat ExtraBold
        // (dessin vectoriel natif d'Android : res/drawable/logo_anneau.xml, rendu net à la taille exacte)
        android.widget.ImageView anneau = new android.widget.ImageView(this);
        anneau.setImageResource(getResources().getIdentifier("logo_anneau", "drawable", getPackageName()));
        anneau.setContentDescription("Orba");
        header.addView(anneau, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView nom = new TextView(this);
        nom.setText("ORBA");
        nom.setTypeface(gras);
        nom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);   // proportions de orba_logo_horizontal.svg
        nom.setLetterSpacing(0.025f);
        nom.setTextColor(Color.WHITE);
        nom.setIncludeFontPadding(false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.leftMargin = dp(14);
        header.addView(nom, np);

        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Wi-Fi, roue dentée, puis bloc heure + date : tous directement dans l'en-tête
        LinearLayout ligne = header;
        reseau = new ReseauView(this);
        reseau.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ouvrir(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS);
            }
        });
        ligne.addView(reseau, new LinearLayout.LayoutParams(dp(52), dp(52)));
        ReglagesView reglages = new ReglagesView(this);
        reglages.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ouvrir(Settings.ACTION_SETTINGS); }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(52), dp(52));
        rp.leftMargin = dp(10);
        rp.rightMargin = dp(18);
        ligne.addView(reglages, rp);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTypeface(interFin);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        time.setTextColor(TEXTE);
        time.setIncludeFontPadding(false);
        time.setSingleLine(true);
        time.setGravity(Gravity.END);
        time.setMinWidth(Math.round(time.getPaint().measureText("00:00")) + dp(4));   // place réservée : jamais coupée
        LinearLayout droite = new LinearLayout(this);
        droite.setOrientation(LinearLayout.VERTICAL);
        droite.setGravity(Gravity.END);
        droite.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTypeface(interFin);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        date.setTextColor(TEXTE_DOUX);
        date.setGravity(Gravity.END);
        date.setPadding(0, dp(2), 0, 0);
        droite.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.rightMargin = dp(8);              // un peu plus loin du bord (bords d'image coupés par la télé)
        header.addView(droite, hp);

        LinearLayout.LayoutParams hdr = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdr.topMargin = dp(8);               // en-tête descendu d'environ 1 cm sur une 65 pouces
        column.addView(header, hdr);

        // ---- Titre de section
        TextView section = new TextView(this);
        section.setText("Vos applications");
        section.setTypeface(moyen);
        section.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        section.setTextColor(TEXTE_DOUX);
        section.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(30);
        sl.leftMargin = dp(14);
        column.addView(section, sl);

        // ---- Grille
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(28));
        scroll.setClipChildren(false);
        scroll.setPadding(0, dp(4), 0, dp(24));
        scroll.setClipToPadding(true);
        scroll.setSmoothScrollingEnabled(true);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        column.addView(scroll, sp);
        root.addView(column);

        // Bandeau d'aide affiché seulement pendant un déplacement.
        aide = new TextView(this);
        aide.setTypeface(moyen);
        aide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        aide.setTextColor(TEXTE);
        aide.setText("Flèches : déplacer   ·   OK ou Retour : valider");
        aide.setPadding(dp(20), dp(9), dp(20), dp(9));
        GradientDrawable pastille = new GradientDrawable();
        pastille.setColor(0xE60B1430);
        pastille.setStroke(dp(1.5f), CYAN);
        pastille.setCornerRadius(dp(22));
        aide.setBackground(pastille);
        aide.setVisibility(View.GONE);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ap.bottomMargin = dp(16);
        root.addView(aide, ap);

        setContentView(root);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        loadApps();
        ondes.start();
        handler.removeCallbacks(mover);
        handler.post(mover);
        handler.removeCallbacks(majReseau);
        handler.post(majReseau);
    }

    @Override protected void onPause() {
        super.onPause();
        if (enDeplacement != null) finirDeplacement();
        ondes.stop();
        handler.removeCallbacks(mover);
        handler.removeCallbacks(majReseau);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packageReceiver); } catch (IllegalArgumentException ignored) { }
    }

    /** Sur un écran d'accueil, Retour ne doit mener nulle part. */
    @Override public void onBackPressed() { }

    // ------------------------------------------------------------------ applis

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);

        List<App> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            a.isBanner = banner != null;
            a.image = banner != null ? banner : ri.loadIcon(pm);
            list.add(a);
        }
        final Collator collator = Collator.getInstance(Locale.FRANCE);
        Collections.sort(list, new Comparator<App>() {
            @Override public int compare(App x, App y) { return collator.compare(x.label, y.label); }
        });

        if (!seen.contains(SETTINGS_PKG)) {
            App s = new App();
            s.pkg = SETTINGS_PKG;
            s.label = "Paramètres";
            s.intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                Drawable d = pm.getApplicationBanner(SETTINGS_PKG);
                s.isBanner = d != null;
                s.image = d != null ? d : pm.getApplicationIcon(SETTINGS_PKG);
            } catch (PackageManager.NameNotFoundException e) {
                s.image = getDrawable(android.R.drawable.ic_menu_preferences);
            }
            list.add(s);
        }

        // Couleur de fond calculée une seule fois par appli.
        for (int i = 0; i < list.size(); i++) {
            App a = list.get(i);
            App deja = cache.get(a.pkg);
            if (deja != null) { list.set(i, deja); } else { couleurFond(a); cache.put(a.pkg, a); }
        }
        toutes.clear();
        toutes.addAll(list);
        appliquerOrdre();
    }

    /** Applique l'ordre mémorisé et retire les applis masquées ; reconstruit si besoin. */
    private void appliquerOrdre() {
        List<String> ordre = new ArrayList<>();
        String o = prefs.getString("ordre", "");
        if (!o.isEmpty()) Collections.addAll(ordre, o.split("\\|"));
        Set<String> masquees = prefs.getStringSet("masquees", new HashSet<String>());

        List<App> visibles = new ArrayList<>();
        for (String pkg : ordre) {
            App a = cache.get(pkg);
            if (a != null && toutes.contains(a) && !masquees.contains(pkg)) visibles.add(a);
        }
        for (App a : toutes) {   // nouvelles applis : à la fin, par ordre alphabétique
            if (!visibles.contains(a) && !masquees.contains(a.pkg)) visibles.add(a);
        }
        // Paramètres n'est plus une tuile : roue dentée en haut à droite.
        for (int i = visibles.size() - 1; i >= 0; i--) if (SETTINGS_PKG.equals(visibles.get(i).pkg)) visibles.remove(i);

        StringBuilder sig = new StringBuilder();
        for (App a : visibles) sig.append(a.pkg).append('|');
        if (sig.toString().equals(signature)) return;   // rien n'a changé : on garde la sélection
        signature = sig.toString();
        apps.clear();
        apps.addAll(visibles);
        buildGrid();
    }

    private void memoriserOrdre() {
        StringBuilder sb = new StringBuilder();
        for (App a : apps) { if (sb.length() > 0) sb.append('|'); sb.append(a.pkg); }
        prefs.edit().putString("ordre", sb.toString()).apply();
    }

    // ------------------------------------------------------------------ menus

    interface Choix { void choisi(int index); }

    /** Fenêtre de choix aux couleurs d'Orba (bleu nuit, contour cyan sur l'option choisie). */
    private void choisir(String titre, final String[] options, final Choix action) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panneau = new LinearLayout(this);
        panneau.setOrientation(LinearLayout.VERTICAL);
        panneau.setPadding(dp(26), dp(24), dp(26), dp(20));
        GradientDrawable fond = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0E1A42, 0xFF070C20});
        fond.setCornerRadius(dp(22));
        fond.setStroke(dp(1), 0xFF22305F);
        panneau.setBackground(fond);

        TextView t = new TextView(this);
        t.setText(titre);
        t.setTypeface(gras);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        t.setTextColor(Color.WHITE);
        panneau.addView(t);

        View trait = new View(this);
        GradientDrawable tr = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{CYAN, BLEU});
        tr.setCornerRadius(dp(2));
        trait.setBackground(tr);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(40), dp(3));
        tp.topMargin = dp(10);
        tp.bottomMargin = dp(14);
        panneau.addView(trait, tp);

        TextView premier = null;
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView o = new TextView(this);
            o.setText(options[i]);
            o.setTypeface(moyen);
            o.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            o.setTextColor(TEXTE);
            o.setPadding(dp(18), dp(13), dp(18), dp(13));
            o.setFocusable(true);
            o.setClickable(true);
            GradientDrawable sel = new GradientDrawable();
            sel.setColor(0xFF15245A);
            sel.setStroke(dp(2), CYAN);
            sel.setCornerRadius(dp(14));
            StateListDrawable st = new StateListDrawable();
            st.addState(new int[]{android.R.attr.state_focused}, sel);
            st.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            o.setBackground(st);
            o.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); action.choisi(idx); }
            });
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            op.topMargin = dp(4);
            panneau.addView(o, op);
            if (premier == null) premier = o;
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(panneau);
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(440), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
        }
        d.show();
        if (premier != null) premier.requestFocus();
    }

    /** Menu de l'appui long : déplacer, masquer, réafficher. */
    private void menu(final App a) {
        final boolean protege = SETTINGS_PKG.equals(a.pkg);
        final List<String> choix = new ArrayList<>();
        choix.add("Déplacer");
        if (!protege) choix.add("Masquer de l'accueil");
        choix.add("Applis masquées…");
        choisir(a.label, choix.toArray(new String[0]), new Choix() {
            @Override public void choisi(int which) {
                String c = choix.get(which);
                if (c.startsWith("Déplacer")) commencerDeplacement(a);
                else if (c.startsWith("Masquer")) masquer(a);
                else montrerMasquees();
            }
        });
    }

    private void masquer(App a) {
        Set<String> m = new HashSet<>(prefs.getStringSet("masquees", new HashSet<String>()));
        m.add(a.pkg);
        int idx = apps.indexOf(a);
        prefs.edit().putStringSet("masquees", m).apply();
        if (idx >= 0 && idx + 1 < apps.size()) focusPkg = apps.get(idx + 1).pkg;
        else if (idx > 0) focusPkg = apps.get(idx - 1).pkg;
        appliquerOrdre();
        Toast.makeText(this, a.label + " est masqué (tuile « + » pour le réafficher)", Toast.LENGTH_LONG).show();
    }

    private void montrerMasquees() {
        Set<String> m = prefs.getStringSet("masquees", new HashSet<String>());
        final List<App> liste = new ArrayList<>();
        for (App a : toutes) if (m.contains(a.pkg) && !SETTINGS_PKG.equals(a.pkg)) liste.add(a);
        if (liste.isEmpty()) {
            Toast.makeText(this, "Aucune appli masquée", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] noms = new String[liste.size()];
        for (int i = 0; i < noms.length; i++) noms[i] = liste.get(i).label;
        choisir("Réafficher une appli", noms, new Choix() {
            @Override public void choisi(int which) {
                App a = liste.get(which);
                Set<String> mm = new HashSet<>(prefs.getStringSet("masquees", new HashSet<String>()));
                mm.remove(a.pkg);
                prefs.edit().putStringSet("masquees", mm).apply();
                focusPkg = a.pkg;
                appliquerOrdre();
            }
        });
    }

    private void commencerDeplacement(App a) {
        enDeplacement = a;
        focusPkg = a.pkg;
        aide.setVisibility(View.VISIBLE);
        rafraichirTuiles();
    }

    private void finirDeplacement() {
        enDeplacement = null;
        aide.setVisibility(View.GONE);
        memoriserOrdre();
        rafraichirTuiles();
    }

    private void rafraichirTuiles() {
        for (int r = 0; r < rows.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) rows.getChildAt(r);
            for (int k = 0; k < row.getChildCount(); k++) row.getChildAt(k).invalidate();
        }
    }

    /** Pendant un déplacement, les flèches font glisser la tuile dans la grille. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (enDeplacement == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(e);
        }
        int i = apps.indexOf(enDeplacement);
        int j = i;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  j = i - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: j = i + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP:    j = i - COLONNES; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  j = Math.min(i + COLONNES, apps.size() - 1); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                finirDeplacement();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
        if (j >= 0 && j < apps.size() && j != i) {
            App moved = apps.remove(i);
            apps.add(j, moved);
            focusPkg = moved.pkg;
            StringBuilder sig = new StringBuilder();
            for (App a : apps) sig.append(a.pkg).append('|');
            signature = sig.toString();
            buildGrid();
        }
        return true;
    }

    private void buildGrid() {
        rows.removeAllViews();
        Tile first = null, cible = null;
        LinearLayout row = null;
        List<App> tuiles = new ArrayList<>(apps);
        tuiles.add(plus);   // tuile « + » toujours en dernier
        for (int i = 0; i < tuiles.size(); i++) {
            if (i % COLONNES == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                rows.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final App a = tuiles.get(i);
            Tile t = new Tile(this, a);
            if (a == plus) {
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { montrerMasquees(); }
                });
            } else {
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { launch(a); }
                });
                t.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { menu(a); return true; }
                });
            }
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (first == null) first = t;
            if (a.pkg.equals(focusPkg)) cible = t;
        }
        // Compléter la dernière ligne pour garder 5 colonnes égales.
        if (row != null) {
            for (int k = tuiles.size() % COLONNES; k != 0 && k < COLONNES; k++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        Tile focus = cible != null ? cible : first;
        if (focus != null) focus.requestFocus();
    }

    /** Ouvre le premier écran de réglages disponible parmi ceux proposés. */
    private void ouvrir(String... actions) {
        for (String a : actions) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) { }
        }
        Toast.makeText(this, "Impossible d'ouvrir les paramètres", Toast.LENGTH_SHORT).show();
    }

    private void launch(App a) {
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Couleurs de la tuile : fond (bord moyen de la bannière, ou bleu nuit pour les icônes)
     * et halo (couleur la plus vive du logo, comme le premier effet de halo coloré).
     */
    private static void couleurFond(App a) {
        final int w = 32, h = 18;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Rect old = new Rect(a.image.getBounds());
        a.image.setBounds(0, 0, w, h);
        a.image.draw(c);
        a.image.setBounds(old);
        long er = 0, eg = 0, eb = 0, en = 0;
        double vr = 0, vg = 0, vb = 0, vw = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = b.getPixel(x, y);
                float al = Color.alpha(p) / 255f;
                int r = Math.round(Color.red(p) * al), g = Math.round(Color.green(p) * al), bl = Math.round(Color.blue(p) * al);
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) { er += r; eg += g; eb += bl; en++; }
                Color.RGBToHSV(r, g, bl, hsv);
                double wgt = hsv[1] * hsv[2]; wgt *= wgt;
                vr += r * wgt; vg += g * wgt; vb += bl * wgt; vw += wgt;
            }
        }
        b.recycle();
        a.fond = a.isBanner ? Color.rgb((int) (er / en), (int) (eg / en), (int) (eb / en)) : TUILE;
        int vivid = vw > 0.5 ? Color.rgb((int) (vr / vw), (int) (vg / vw), (int) (vb / vw)) : 0xFF9AA4B8;
        Color.colorToHSV(vivid, hsv);
        if (vw <= 0.5) hsv[1] = 0.25f; else hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        a.halo = Color.HSVToColor(hsv);
    }

    // ------------------------------------------------------------------ tuile

    /** Tuile arrondie : logo d'origine, contour cyan lumineux et nom quand elle est choisie. */
    final class Tile extends View {
        final App app;
        final int marge = dp(16);           // place réservée au halo autour de la tuile
        final int hautNom = dp(30);         // place du nom sous la tuile
        final float rayon = dp(14);
        final RectF carte = new RectF();
        final RectF tmp = new RectF();
        final Path clip = new Path();
        final Paint fond = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint lueur = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint contour = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint voile = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint nom = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint signe = new Paint(Paint.ANTI_ALIAS_FLAG);
        float focus = 0f;
        ValueAnimator anim;

        Tile(Context c, App a) {
            super(c);
            app = a;
            setFocusable(true);
            setClickable(true);
            setContentDescription(a.label);
            setOutlineProvider(null);       // pas d'ombre système quand on la passe au premier plan
            fond.setColor(a.fond);
            lueur.setColor(a.halo);
            contour.setStyle(Paint.Style.STROKE);
            contour.setStrokeWidth(dp(2.5f));
            contour.setColor(CYAN);
            voile.setColor(NUIT);
            nom.setColor(TEXTE);
            nom.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics()));
            nom.setTypeface(moyen);
            nom.setTextAlign(Paint.Align.CENTER);
            signe.setColor(TEXTE_DOUX);
            signe.setStyle(Paint.Style.STROKE);
            signe.setStrokeCap(Paint.Cap.ROUND);
            signe.setStrokeWidth(dp(3));
        }

        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int h = Math.round((w - 2 * marge) * 9f / 16f);
            setMeasuredDimension(w, h + 2 * marge + hautNom);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            carte.set(marge, marge, w - marge, h - marge - hautNom);
            clip.reset();
            clip.addRoundRect(carte, rayon, rayon, Path.Direction.CW);
        }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
            anim.setDuration(200);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) {
                    focus = (Float) va.getAnimatedValue();
                    float s = 1f + 0.10f * focus;   // comme le premier effet
                    setScaleX(s); setScaleY(s);
                    invalidate();
                }
            });
            anim.start();
            setPivotX(getWidth() / 2f);
            setPivotY(carte.centerY());
            // Passer devant les voisines (et la ligne devant les autres lignes).
            setTranslationZ(gain ? 2f : 0f);
            View row = (View) getParent();
            if (row != null) row.setTranslationZ(gain ? 1f : 0f);
            if (row != null) row.setOutlineProvider(null);
        }

        /** Contour blanc autour de la tuile en cours de déplacement. */
        @Override public void draw(Canvas c) {
            super.draw(c);
            if (enDeplacement == app) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(3));
                p.setColor(0xFFF2F2F2);
                float g = dp(6);
                tmp.set(carte.left - g, carte.top - g, carte.right + g, carte.bottom + g);
                c.drawRoundRect(tmp, rayon + g, rayon + g, p);
            }
        }

        @Override protected void onDraw(Canvas c) {
            // 1. Halo lumineux de la couleur de l'appli : couches de plus en plus larges et transparentes.
            if (focus > 0.01f) {
                for (int i = 10; i >= 1; i--) {
                    float g = marge * i / 10f;
                    lueur.setAlpha(Math.round(focus * 22f * (11 - i) / 10f));
                    tmp.set(carte.left - g, carte.top - g, carte.right + g, carte.bottom + g);
                    c.drawRoundRect(tmp, rayon + g, rayon + g, lueur);
                }
            }

            // 2. Tuile et logo d'origine.
            c.save();
            c.clipPath(clip);
            c.drawRect(carte, fond);
            if (app == plus) {
                float s = carte.height() * 0.18f;
                signe.setColor(focus > 0.5f ? CYAN : TEXTE_DOUX);
                c.drawLine(carte.centerX() - s, carte.centerY(), carte.centerX() + s, carte.centerY(), signe);
                c.drawLine(carte.centerX(), carte.centerY() - s, carte.centerX(), carte.centerY() + s, signe);
            } else {
                Drawable d = app.image;
                if (app.isBanner) {
                    // Bannière d'origine en plein cadre (même format 16:9), coins arrondis.
                    d.setBounds(Math.round(carte.left), Math.round(carte.top), Math.round(carte.right), Math.round(carte.bottom));
                } else {
                    float s = carte.height() * 0.62f;
                    RectF zone = new RectF(carte.centerX() - s / 2, carte.centerY() - s / 2, carte.centerX() + s / 2, carte.centerY() + s / 2);
                    int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
                    if (iw > 0 && ih > 0) {
                        float k = Math.min(zone.width() / iw, zone.height() / ih);
                        float dw = iw * k, dh = ih * k;
                        d.setBounds(Math.round(zone.centerX() - dw / 2), Math.round(zone.centerY() - dh / 2),
                                Math.round(zone.centerX() + dw / 2), Math.round(zone.centerY() + dh / 2));
                    } else {
                        d.setBounds(Math.round(zone.left), Math.round(zone.top), Math.round(zone.right), Math.round(zone.bottom));
                    }
                }
                d.draw(c);
            }
            // 3. Les tuiles non choisies sont légèrement atténuées.
            voile.setAlpha(Math.round((1f - focus) * 80f));
            c.drawRect(carte, voile);
            c.restore();

            // 4. Nom de l'appli sous la tuile choisie (pas de contour : le halo suffit).
            if (focus > 0.01f) {
                nom.setAlpha(Math.round(focus * 255));
                String txt = app.label;
                float max = getWidth() - dp(8);
                if (nom.measureText(txt) > max) {
                    while (txt.length() > 1 && nom.measureText(txt + "…") > max) txt = txt.substring(0, txt.length() - 1);
                    txt = txt + "…";
                }
                c.drawText(txt, getWidth() / 2f, carte.bottom + dp(24), nom);
            }
        }
    }

    // ------------------------------------------------------------------ boutons d'en-tête

    /**
     * Bouton rond de l'en-tête, sélectionnable à la télécommande : au repos l'icône seule ;
     * sélectionné, un disque bleu nuit cerclé de cyan (net, sans flou) et l'icône en blanc.
     */
    abstract class BoutonIcone extends View {
        private final Paint disque = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cercle = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected float focus = 0f;
        private ValueAnimator anim;

        BoutonIcone(Context c, String description) {
            super(c);
            setFocusable(true);
            setClickable(true);
            setContentDescription(description);
            disque.setColor(0xFF15245A);
            cercle.setStyle(Paint.Style.STROKE);
            cercle.setColor(CYAN);
            cercle.setStrokeWidth(dp(2));
        }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
            anim.setDuration(160);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) {
                    focus = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        /** Couleur de l'icône : gris-bleu clair au repos, blanc quand sélectionnée. */
        protected int couleurIcone() {
            return focus > 0.5f ? Color.WHITE : TEXTE;
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f - dp(1.5f);
            if (focus > 0.01f) {
                disque.setAlpha(Math.round(focus * 255));
                c.drawCircle(cx, cy, r, disque);
                cercle.setAlpha(Math.round(focus * 255));
                c.drawCircle(cx, cy, r, cercle);
            }
            float t = Math.min(getWidth(), getHeight()) * 0.50f;   // taille de l'icône (zone 24x24)
            dessiner(c, cx - t / 2, cy - t / 2, t);
        }

        /** Dessine l'icône dans le carré (x, y, côté t) qui correspond à une grille 24x24. */
        abstract void dessiner(Canvas c, float x, float y, float t);
    }

    /** Roue « Paramètres » au trait fin (icône vectorielle res/drawable/ic_reglages.xml). */
    final class ReglagesView extends BoutonIcone {
        private final Drawable icone;

        ReglagesView(Context c) {
            super(c, "Paramètres");
            icone = c.getDrawable(c.getResources().getIdentifier("ic_reglages", "drawable", c.getPackageName())).mutate();
        }

        @Override void dessiner(Canvas c, float x, float y, float t) {
            icone.setBounds(Math.round(x), Math.round(y), Math.round(x + t), Math.round(y + t));
            icone.setTint(couleurIcone());
            icone.draw(c);
        }
    }

    /**
     * Wi-Fi au trait fin (géométrie de Lucide « wifi ») : 3 arcs + point, les arcs allumés
     * indiquent la force du signal (0 à 3). Câble Ethernet si la télé est branchée,
     * Wi-Fi barré si elle est hors ligne. Un clic ouvre les réglages Wi-Fi.
     */
    final class ReseauView extends BoutonIcone {
        private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private int type = 1;      // 0 = hors ligne, 1 = Wi-Fi, 2 = Ethernet
        private int niveau = 3;    // 0..3

        ReseauView(Context c) {
            super(c, "Réglages Wi-Fi");
            trait.setStyle(Paint.Style.STROKE);
            trait.setStrokeCap(Paint.Cap.ROUND);
            trait.setStrokeJoin(Paint.Join.ROUND);
        }

        void majEtat() {
            int t = 0, n = 0;
            try {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (nc != null) {
                    if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) { t = 2; n = 3; }
                    else if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                        t = 1;
                        int rssi = nc.getSignalStrength();
                        n = rssi == Integer.MIN_VALUE ? 3 : rssi >= -60 ? 3 : rssi >= -70 ? 2 : rssi >= -80 ? 1 : 0;
                    }
                }
            } catch (Exception ignored) { }
            if (t != type || n != niveau) { type = t; niveau = n; invalidate(); }
            String[] force = {"très faible", "faible", "bon", "excellent"};
            setContentDescription(type == 2 ? "Réseau : câble Ethernet" : type == 1 ? "Wi-Fi : signal " + force[niveau] : "Hors ligne");
        }

        @Override void dessiner(Canvas c, float x, float y, float t) {
            float k = t / 24f;
            int coul = couleurIcone();
            int eteint = (coul & 0x00FFFFFF) | 0x50000000;   // arcs non atteints : très atténués
            trait.setStrokeWidth(1.8f * k);
            if (type == 2) {
                // prise Ethernet au trait fin
                trait.setColor(coul);
                c.drawRoundRect(x + 6 * k, y + 9 * k, x + 18 * k, y + 19 * k, 1.5f * k, 1.5f * k, trait);
                c.drawLine(x + 12 * k, y + 4 * k, x + 12 * k, y + 9 * k, trait);
                c.drawLine(x + 9.5f * k, y + 13 * k, x + 9.5f * k, y + 15 * k, trait);
                c.drawLine(x + 12 * k, y + 13 * k, x + 12 * k, y + 15 * k, trait);
                c.drawLine(x + 14.5f * k, y + 13 * k, x + 14.5f * k, y + 15 * k, trait);
                return;
            }
            // arcs centrés en (12, 20) de rayons 5, 10, 15 (comme Lucide « wifi »)
            float cx = x + 12 * k, cy = y + 20 * k;
            float[] rayons = {5f, 10f, 15f};
            float[] demiAngles = {44.4f, 44.4f, 41.8f};
            for (int i = 0; i < 3; i++) {
                float r = rayons[i] * k;
                arc.set(cx - r, cy - r, cx + r, cy + r);
                boolean allume = type == 1 && i < niveau;
                trait.setColor(allume ? coul : eteint);
                c.drawArc(arc, 270 - demiAngles[i], 2 * demiAngles[i], false, trait);
            }
            point.setColor(type == 1 ? coul : eteint);
            c.drawCircle(cx, cy, 1.3f * k, point);
            if (type == 0) {   // hors ligne : barré
                trait.setColor(coul);
                c.drawLine(x + 3 * k, y + 3 * k, x + 21 * k, y + 21 * k, trait);
            }
        }
    }

    // ------------------------------------------------------------------ fond

    /**
     * Fond Orba : bleu nuit du logo, lueur douce en haut à droite et vagues lumineuses
     * (cyan -> violet) qui ondulent lentement en bas de l'écran. Dessiné directement
     * à la pleine résolution de l'écran : bords et lignes de crête nets.
     * S'arrête quand l'écran n'est pas visible.
     */
    static final class OndesView extends View {
        private final Paint fond = new Paint(Paint.DITHER_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint[] vague = new Paint[3];
        private final Paint creteLueur = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crete = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path forme = new Path();
        private final Path ligne = new Path();
        private final long t0 = SystemClock.uptimeMillis();
        private final float densite;
        private boolean running;

        // base (hauteur), amplitude, fréquence, vitesse, phase, opacité de chaque vague
        private static final float[][] VAGUES = {
                {0.86f, 0.050f, 0.9f, 0.10f, 0.3f, 45},
                {0.91f, 0.045f, 0.7f, -0.07f, 2.0f, 75},
                {0.96f, 0.030f, 1.2f, 0.05f, 4.0f, 105},
        };

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                invalidate();
                if (running) postDelayed(this, 40);   // ~25 images/s, mouvement lent et fluide
            }
        };

        OndesView(Context c) {
            super(c);
            densite = c.getResources().getDisplayMetrics().density;
            for (int i = 0; i < vague.length; i++) vague[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            creteLueur.setStyle(Paint.Style.STROKE);
            creteLueur.setStrokeWidth(5 * densite);
            creteLueur.setStrokeCap(Paint.Cap.ROUND);
            crete.setStyle(Paint.Style.STROKE);
            crete.setStrokeWidth(1.2f * densite);
            crete.setStrokeCap(Paint.Cap.ROUND);
        }

        void start() { if (!running) { running = true; post(tick); } }
        void stop()  { running = false; removeCallbacks(tick); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fond.setShader(new LinearGradient(0, 0, 0, h, NUIT_CLAIR, NUIT, Shader.TileMode.CLAMP));
            halo.setShader(new RadialGradient(w * 0.88f, -h * 0.1f, w * 0.55f, 0x262A5BFF, 0x002A5BFF, Shader.TileMode.CLAMP));
            for (int i = 0; i < vague.length; i++) {
                int a = ((int) VAGUES[i][5]) << 24;
                vague[i].setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{a | (CYAN & 0xFFFFFF), a | (BLEU & 0xFFFFFF), a | (INDIGO & 0xFFFFFF), a | (VIOLET & 0xFFFFFF)},
                        new float[]{0f, 0.38f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            }
        }

        @Override protected void onDraw(Canvas c) {
            float t = (SystemClock.uptimeMillis() - t0) / 1000f;
            float W = getWidth(), H = getHeight();
            c.drawRect(0, 0, W, H, fond);
            c.save();
            c.translate(W * 0.03f * (float) Math.sin(t * 0.05), 0);   // la lueur dérive doucement
            c.drawRect(-W * 0.1f, 0, W * 1.1f, H, halo);
            c.restore();
            final int n = 120;
            for (int k = 0; k < VAGUES.length; k++) {
                float[] v = VAGUES[k];
                forme.reset(); ligne.reset();
                forme.moveTo(0, H);
                for (int i = 0; i <= n; i++) {
                    float x = W * i / n;
                    float y = H * v[0] - H * v[1] * (float) Math.sin(2 * Math.PI * v[2] * i / n + v[4] + t * v[3]);
                    forme.lineTo(x, y);
                    if (i == 0) ligne.moveTo(x, y); else ligne.lineTo(x, y);
                }
                forme.lineTo(W, H);
                forme.close();
                c.drawPath(forme, vague[k]);
                int a = (int) v[5];
                creteLueur.setColor(Color.argb(Math.min(255, a / 3), 0, 229, 255));
                c.drawPath(ligne, creteLueur);
                crete.setColor(Color.argb(Math.min(255, a + 90), 170, 240, 255));
                c.drawPath(ligne, crete);
            }
        }
    }
}
