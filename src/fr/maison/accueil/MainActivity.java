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
    private static final int COLONNES_DEFAUT = 5;
    private static final int MAX_FAVORIS = 4;
    /** Favoris proposés au premier lancement de la 3.8, parmi les applis installées (dans cet ordre). */
    private static final String[] FAVORIS_PAR_DEFAUT = {
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.canal.android.canal", "com.apple.atve.androidtv.appletv"
    };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(Langue.appliquer(base)); }

    private boolean rtl;   // arabe : interface de droite à gauche

    /** « Libellé : valeur » dans la ponctuation de la langue. */
    private String paire(int libelle, String valeur) {
        return getString(R.string.paire, getString(libelle), Langue.nom(this, valeur));
    }

    // Réglages Orba (modifiables sur la télé : appui long sur la roue dentée)
    private int colonnes = COLONNES_DEFAUT;   // 4 à 7 tuiles par ligne
    private int fondMode = 0;                 // 0 vagues animées, 1 vagues fixes, 2 noir pur
    private boolean haloCyan = false;         // halo : couleur de l'appli (false) ou cyan Orba (true)
    private static boolean rouvrirReglages = false;   // après un changement de thème, l'activité est recréée puis rouvre les réglages
    private int veilleMinutes = 10;           // 0 = écran de veille Orba désactivé

    /** Lance l'écran de veille Orba après le délai d'inactivité choisi. */
    private final Runnable lancerVeille = new Runnable() {
        @Override public void run() {
            if (veilleMinutes > 0 && hasWindowFocus()) startActivity(new Intent(MainActivity.this, VeilleActivity.class));
        }
    };

    private void relancerVeille() {
        handler.removeCallbacks(lancerVeille);
        if (veilleMinutes > 0) handler.postDelayed(lancerVeille, veilleMinutes * 60_000L);
    }

    // Palette Orba
    static final int CYAN = 0xFF00E5FF, BLEU = 0xFF0078FF, INDIGO = 0xFF4B3DFF, VIOLET = 0xFFA64DFF;
    static final int NUIT = 0xFF00020F, NUIT_CLAIR = 0xFF00062E, TUILE = 0xFF0C1640;   // bleu nuit du logo
    static final int TEXTE = 0xFFEAF0FF, TEXTE_DOUX = 0xFF8FA0C8;
    // Force du signal Wi-Fi : tons adoucis pour rester lisibles et calmes sur le bleu nuit
    static final int SIGNAL_VERT = 0xFF4ADE80, SIGNAL_JAUNE = 0xFFFACC15, SIGNAL_ROUGE = 0xFFF87171;

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

    private final List<App> favoris = new ArrayList<>();     // dock : 4 favoris au maximum, en haut
    private TextView nomFavori;                              // nom en grand de la tuile du dock choisie
    private LinearLayout dock;
    private ScrollView defilement;
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
    private View boutonReglages;

    // Appui long sur la roue géré par Orba lui-même (fiable quelle que soit la télécommande)
    private boolean okRoueEnfonce = false, okRoueLongTraite = false;
    private final Runnable appuiLongRoue = new Runnable() {
        @Override public void run() {
            if (okRoueEnfonce) { okRoueLongTraite = true; montrerReglages(); }
        }
    };

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
        prefs = getSharedPreferences("accueil", MODE_PRIVATE);
        rtl = Langue.rtl(this);
        Views.theme = Math.max(0, Math.min(2, prefs.getInt("theme", Views.GLOSSY)));   // Réglages Orba > Thème
        interFin = police("inter-300.ttf");          // heure et date : Inter (choisie au début du projet)
        if (Views.oled()) {                          // thème OLED : Inter partout, comme OrbaTV
            gras = police("inter-500.ttf");
            moyen = police("inter-500.ttf");
        } else {
            gras = police("montserrat-800.ttf");     // titres : Montserrat ExtraBold (police du logo)
            moyen = police("montserrat-600.ttf");    // noms d'applis, section, menus : Montserrat SemiBold
        }
        if (!prefs.getBoolean("init", false)) {
            Set<String> m = new HashSet<>();
            Collections.addAll(m, MASQUEES_PAR_DEFAUT);
            prefs.edit().putStringSet("masquees", m).putBoolean("init", true).apply();
        }
        colonnes = prefs.getInt("colonnes", COLONNES_DEFAUT);
        fondMode = prefs.getInt("fond", 0);
        haloCyan = prefs.getBoolean("halo_cyan", false);
        veilleMinutes = prefs.getInt("veille_minutes", 10);

        plus = new App();
        plus.pkg = PLUS;
        plus.label = getString(R.string.reafficher);
        plus.fond = TUILE;
        plus.halo = CYAN;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Views.fond());
        root.setLayoutDirection(Langue.sens(this));   // arabe : droite à gauche (marges toujours en début/fin, jamais gauche/droite)

        ondes = new OndesView(this);
        ondes.setMode(Views.oled() ? 2 : fondMode);   // thème OLED : fond noir profond, sans vagues
        root.addView(ondes, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(52), dp(30), dp(52), 0);

        // ---- En-tête : logo à gauche ; Wi-Fi, Paramètres et heure à droite (date dessous)
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(7), 0, 0, 0);   // logo décalé d'environ 1 cm vers la droite (bord coupé par la télé)
        header.setClipChildren(false);       // halo des boutons jamais rogné
        header.setClipToPadding(false);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        // Logo Orba : anneau vectoriel (d'après orba_symbole.svg) + ORBA en Montserrat ExtraBold
        // (dessin vectoriel natif d'Android : res/drawable/logo_anneau.xml, rendu net à la taille exacte)
        android.widget.ImageView anneau = new android.widget.ImageView(this);
        anneau.setImageResource(getResources().getIdentifier("logo_anneau", "drawable", getPackageName()));
        anneau.setContentDescription("Orba");
        LinearLayout marque = new LinearLayout(this);   // anneau puis « Orba », dans cet ordre même en arabe
        marque.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        marque.setGravity(Gravity.CENTER_VERTICAL);
        marque.addView(anneau, new LinearLayout.LayoutParams(dp(40), dp(40)));   // même taille que OrbaFlux et OrbaTV
        TextView nom = new TextView(this);
        nom.setText("Orba");
        nom.setTypeface(police("montserrat-500.ttf"));     // même écriture fine que OrbaTV : Montserrat Medium, « Orba » (plus de majuscules grasses)
        nom.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);   // 0,55 × l'anneau (40 dp), comme OrbaFlux et OrbaTV
        nom.setLetterSpacing(0.04f);
        nom.setTextColor(Color.WHITE);
        nom.setIncludeFontPadding(false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMarginStart(dp(6));
        marque.addView(nom, np);
        header.addView(marque, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Wi-Fi, roue dentée, puis bloc heure + date : tous directement dans l'en-tête
        LinearLayout ligne = header;
        SourcesView sources = new SourcesView(this);
        sources.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ouvrirSources(); }
        });
        LinearLayout.LayoutParams sp0 = new LinearLayout.LayoutParams(dp(52), dp(52));
        sp0.setMarginEnd(dp(10));
        ligne.addView(sources, sp0);
        reseau = new ReseauView(this);
        reseau.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ouvrir(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS);
            }
        });
        ligne.addView(reseau, new LinearLayout.LayoutParams(dp(52), dp(52)));
        ReglagesView reglages = new ReglagesView(this);
        boutonReglages = reglages;
        reglages.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ouvrir(Settings.ACTION_SETTINGS); }
        });
        reglages.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { montrerReglages(); return true; }   // réglages Orba
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(52), dp(52));
        rp.setMarginStart(dp(10));
        rp.setMarginEnd(dp(18));
        ligne.addView(reglages, rp);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTypeface(interFin);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        time.setTextColor(TEXTE);
        time.setIncludeFontPadding(false);
        time.setSingleLine(true);
        time.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        time.setMinWidth(Math.round(time.getPaint().measureText("00:00")) + dp(4));   // place réservée : jamais coupée
        LinearLayout droite = new LinearLayout(this);
        droite.setOrientation(LinearLayout.VERTICAL);
        droite.setGravity(Gravity.END);
        droite.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat24Hour(getString(R.string.format_date));
        date.setFormat12Hour(getString(R.string.format_date));
        date.setTypeface(interFin);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, rtl ? 17 : 15);
        date.setTextColor(Views.doux());
        date.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        date.setPadding(0, dp(2), 0, 0);
        droite.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMarginEnd(dp(8));              // un peu plus loin du bord (bords d'image coupés par la télé)
        header.addView(droite, hp);

        LinearLayout.LayoutParams hdr = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdr.topMargin = dp(8);               // en-tête descendu d'environ 1 cm sur une 65 pouces
        column.addView(header, hdr);

        // ---- Zone qui défile : nom du favori, dock de favoris, titre de section, grille
        ScrollView scroll = new ScrollView(this);
        defilement = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(28));
        scroll.setClipChildren(false);
        scroll.setPadding(0, dp(4), 0, dp(24));
        scroll.setClipToPadding(true);
        scroll.setSmoothScrollingEnabled(true);

        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.setClipChildren(false);
        contenu.setClipToPadding(false);
        contenu.setPadding(dp(24), 0, dp(24), 0);   // la zone déborde de 24 dp : halo et zoom jamais rognés sur les bords
        scroll.addView(contenu, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Nom en grand de l'appli du dock sélectionnée
        nomFavori = new TextView(this);
        nomFavori.setTypeface(interFin);   // Inter fine, comme l'heure : discret
        nomFavori.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        if (!rtl) nomFavori.setLetterSpacing(0.01f);   // l'espacement détacherait les lettres arabes
        nomFavori.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        nomFavori.setTextColor(TEXTE);
        nomFavori.setSingleLine(true);
        nomFavori.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);   // nom trop long : il défile
        nomFavori.setMarqueeRepeatLimit(-1);
        nomFavori.setSelected(true);
        nomFavori.setIncludeFontPadding(false);
        nomFavori.setAlpha(0f);
        LinearLayout.LayoutParams nf = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nf.topMargin = dp(18);
        nf.setMarginStart(dp(14));
        nf.setMarginEnd(dp(14));
        contenu.addView(nomFavori, nf);

        // Dock : jusqu'à 4 favoris en grandes tuiles
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        contenu.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Titre de section : pastille colorée + capitales espacées de la même couleur (charte OrbaTV)
        LinearLayout titreSection = new LinearLayout(this);
        titreSection.setOrientation(LinearLayout.HORIZONTAL);
        titreSection.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = new TextView(this);
        section.setTypeface(moyen);
        section.setIncludeFontPadding(false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (Views.theme == Views.ORBA) {     // thème Orba : titre sobre d'origine
            section.setText(R.string.vos_applications);
            section.setTextSize(TypedValue.COMPLEX_UNIT_SP, rtl ? 19 : 17);
            section.setTextColor(TEXTE_DOUX);
            if (!rtl) section.setLetterSpacing(0.02f);
        } else {
            View pastilleSection = new View(this);
            pastilleSection.setBackground(new Views.Pastille());
            titreSection.addView(pastilleSection, new LinearLayout.LayoutParams(dp(5), dp(18)));
            section.setText(getString(R.string.vos_applications).toUpperCase(Locale.getDefault()));
            section.setTextSize(TypedValue.COMPLEX_UNIT_SP, rtl ? 18 : 14);   // l'arabe n'a pas de capitales
            section.setTextColor(Views.oled() ? Views.AMBRE_CLAIR : 0xFF5AA9FF);   // bleu clair de la charte, ambre clair en OLED
            if (!rtl) section.setLetterSpacing(0.16f);
            stp.setMarginStart(dp(10));
        }
        titreSection.addView(section, stp);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(16);
        sl.bottomMargin = dp(2);
        sl.setMarginStart(dp(16));
        contenu.addView(titreSection, sl);

        // ---- Grille
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        contenu.addView(rows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.setMarginStart(-dp(24));
        sp.setMarginEnd(-dp(24));
        column.addView(scroll, sp);
        root.addView(column);

        // Bandeau d'aide affiché seulement pendant un déplacement.
        aide = new TextView(this);
        aide.setTypeface(moyen);
        aide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        aide.setTextColor(TEXTE);
        aide.setText(R.string.aide_deplacement);
        aide.setPadding(dp(22), dp(11), dp(22), dp(11));
        aide.setBackground(Views.verre(this, 22));
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
        if (rouvrirReglages) {
            rouvrirReglages = false;
            handler.postDelayed(new Runnable() {
                @Override public void run() { if (!isFinishing()) montrerReglages(); }
            }, 250);
        }
    }

    /**
     * Appli au démarrage (Réglages Orba, désactivée par défaut) : au premier affichage d'Orba après un
     * démarrage complet de la télé, ouvre l'appli choisie, une seule fois. Aucune autorisation : l'heure
     * du démarrage se déduit de l'horloge (heure actuelle moins temps écoulé depuis le démarrage).
     */
    private void ouvrirAppliDemarrage() {
        final String pkg = prefs.getString("appli_demarrage", "");
        long demarrage = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long dernier = prefs.getLong("dernier_demarrage", 0);
        boolean nouveau = Math.abs(demarrage - dernier) > 120_000;   // marge : l'horloge peut se recaler
        if (!nouveau) return;
        prefs.edit().putLong("dernier_demarrage", demarrage).apply();
        // Seulement juste après l'allumage (pas après une mise à jour d'Orba des heures plus tard).
        if (pkg.isEmpty() || SystemClock.elapsedRealtime() > 10 * 60_000L) return;
        final App a = cache.get(pkg);
        if (a == null || a.intent == null) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() { if (hasWindowFocus()) launch(a); }
        }, 1500);
    }

    /** Choix de l'appli au démarrage : Orba seul, ou une appli installée. */
    private void choisirAppliDemarrage() {
        final List<App> liste = new ArrayList<>();
        for (App a : toutes) if (!SETTINGS_PKG.equals(a.pkg)) liste.add(a);
        String[] noms = new String[liste.size() + 1];
        noms[0] = getString(R.string.demarrage_aucune);
        for (int i = 0; i < liste.size(); i++) noms[i + 1] = liste.get(i).label;
        choisir(getString(R.string.au_demarrage_titre), noms, new Choix() {
            @Override public void choisi(int k) {
                prefs.edit().putString("appli_demarrage", k == 0 ? "" : liste.get(k - 1).pkg).apply();
                montrerReglages();
            }
        });
    }

    private String nomAppliDemarrage() {
        App a = cache.get(prefs.getString("appli_demarrage", ""));
        return a == null ? getString(R.string.demarrage_orba) : a.label;
    }

    @Override protected void onResume() {
        super.onResume();
        loadApps();
        ouvrirAppliDemarrage();
        ondes.start();
        handler.removeCallbacks(mover);
        handler.post(mover);
        handler.removeCallbacks(majReseau);
        handler.post(majReseau);
        relancerVeille();
    }

    @Override protected void onPause() {
        super.onPause();
        if (enDeplacement != null) finirDeplacement();
        ondes.stop();
        handler.removeCallbacks(mover);
        handler.removeCallbacks(majReseau);
        handler.removeCallbacks(lancerVeille);
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
        List<App> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Applis Android TV (LEANBACK_LAUNCHER) puis applis Android classiques / sideloadées (LAUNCHER)
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable banner = ri.activityInfo.loadBanner(pm);
                if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                a.isBanner = banner != null;
                a.image = banner != null ? banner : ri.loadIcon(pm);
                list.add(a);
            }
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(list, new Comparator<App>() {
            @Override public int compare(App x, App y) { return collator.compare(x.label, y.label); }
        });

        if (!seen.contains(SETTINGS_PKG)) {
            App s = new App();
            s.pkg = SETTINGS_PKG;
            s.label = getString(R.string.parametres);
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

        // Favoris du dock : choisis une première fois parmi les grandes applis de streaming installées.
        if (!prefs.contains("favoris") && !toutes.isEmpty()) {
            StringBuilder def = new StringBuilder();
            int n = 0;
            for (String pkg : FAVORIS_PAR_DEFAUT) {
                App a = cache.get(pkg);
                if (n < MAX_FAVORIS && a != null && toutes.contains(a) && !masquees.contains(pkg)) {
                    if (def.length() > 0) def.append('|');
                    def.append(pkg); n++;
                }
            }
            prefs.edit().putString("favoris", def.toString()).apply();
        }
        List<App> favs = new ArrayList<>();
        String f = prefs.getString("favoris", "");
        if (!f.isEmpty()) {
            for (String pkg : f.split("\\|")) {
                App a = cache.get(pkg);
                if (a != null && toutes.contains(a) && !masquees.contains(pkg) && !favs.contains(a)
                        && !SETTINGS_PKG.equals(pkg) && favs.size() < MAX_FAVORIS) favs.add(a);
            }
        }

        List<App> visibles = new ArrayList<>();
        for (String pkg : ordre) {
            App a = cache.get(pkg);
            if (a != null && toutes.contains(a) && !masquees.contains(pkg)) visibles.add(a);
        }
        for (App a : toutes) {   // nouvelles applis : à la fin, par ordre alphabétique
            if (!visibles.contains(a) && !masquees.contains(a.pkg)) visibles.add(a);
        }
        // Paramètres n'est plus une tuile : roue dentée en haut à droite. Les favoris sont dans le dock.
        for (int i = visibles.size() - 1; i >= 0; i--) {
            if (SETTINGS_PKG.equals(visibles.get(i).pkg) || favs.contains(visibles.get(i))) visibles.remove(i);
        }

        String sig = joindre(favs) + "|#|" + joindre(visibles) + "|";
        if (sig.equals(signature)) return;   // rien n'a changé : on garde la sélection
        signature = sig;
        favoris.clear();
        favoris.addAll(favs);
        apps.clear();
        apps.addAll(visibles);
        buildGrid();
    }

    private static String joindre(List<App> liste) {
        StringBuilder sb = new StringBuilder();
        for (App a : liste) { if (sb.length() > 0) sb.append('|'); sb.append(a.pkg); }
        return sb.toString();
    }

    private void memoriserOrdre() {
        prefs.edit().putString("ordre", joindre(apps)).putString("favoris", joindre(favoris)).apply();
    }

    /** Signature de l'affichage actuel (dock + grille), pour ne reconstruire que si elle change. */
    private String signatureActuelle() {
        return joindre(favoris) + "|#|" + joindre(apps) + "|";
    }

    /** Ajoute l'appli au dock (4 au maximum) ou l'en retire. */
    private void basculerFavori(App a) {
        List<App> liste = new ArrayList<>(favoris);
        if (liste.contains(a)) {
            liste.remove(a);
            Toast.makeText(this, getString(R.string.msg_favori_retire, Langue.nom(this, a.label)), Toast.LENGTH_SHORT).show();
        } else {
            if (liste.size() >= MAX_FAVORIS) {
                Toast.makeText(this, getString(R.string.msg_favoris_max, MAX_FAVORIS), Toast.LENGTH_LONG).show();
                return;
            }
            liste.add(a);
        }
        prefs.edit().putString("favoris", joindre(liste)).apply();
        focusPkg = a.pkg;
        appliquerOrdre();
    }

    // ------------------------------------------------------------------ menus

    interface Choix { void choisi(int index); }

    /** Fenêtre de choix aux couleurs d'Orba (bleu nuit, contour cyan sur l'option choisie). */
    private void choisir(String titre, final String[] options, final Choix action) { choisir(titre, options, action, 0); }

    /** Idem, avec l'option de départ (la valeur actuellement choisie) déjà sélectionnée à la télécommande. */
    private void choisir(String titre, final String[] options, final Choix action, int depart) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panneau = new LinearLayout(this);
        panneau.setOrientation(LinearLayout.VERTICAL);
        panneau.setPadding(dp(18), dp(24), dp(18), dp(14));
        // rognage laissé actif : la liste qui défile ne passe jamais sur le titre (le halo tient dans la marge des options)
        panneau.setBackground(Views.verre(this, 24));   // verre translucide d'OrbaTV
        panneau.setLayoutDirection(Langue.sens(this));

        TextView t = new TextView(this);
        t.setText(titre);
        t.setTypeface(gras);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, rtl ? 23 : 21);
        t.setTextColor(Color.WHITE);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        t.setSingleLine(true);   // (titre de fenêtre)
        t.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);   // titre trop long (nom d'appli) : il défile
        t.setMarqueeRepeatLimit(-1);
        t.setSelected(true);
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttp.setMarginStart(dp(8));
        ttp.setMarginEnd(dp(8));
        panneau.addView(t, ttp);

        View trait = new View(this);
        trait.setBackground(new Views.Pastille());   // filet dégradé et glossy sous le titre
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(44), dp(4));
        tp.topMargin = dp(10);
        tp.bottomMargin = dp(8);
        tp.setMarginStart(dp(8));
        panneau.addView(trait, tp);

        // Options : pilules glossy avec halo animé ; la marge du halo est incluse dans chaque option (jamais rogné)
        LinearLayout liste = new LinearLayout(this);
        liste.setOrientation(LinearLayout.VERTICAL);
        liste.setClipChildren(false);
        liste.setClipToPadding(false);
        TextView premier = null;
        float large = t.getPaint().measureText(titre) + dp(52);   // largeur utile : aucun libellé coupé, quelle que soit la langue
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            Views.Option o = new Views.Option(this, options[i], moyen, rtl ? 19 : 17, TEXTE);   // l'arabe paraît plus petit à taille égale
            o.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { d.dismiss(); action.choisi(idx); }
            });
            liste.addView(o, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            large = Math.max(large, o.getPaint().measureText(options[i]) + o.getPaddingLeft() + o.getPaddingRight() + dp(40));
            if (premier == null || i == depart) premier = o;
        }

        // Seule la liste défile : le titre reste visible, et la fenêtre ne dépasse jamais ~86 % de l'écran
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);   // la liste s'estompe là où elle continue
        sv.setFadingEdgeLength(dp(30));
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.addView(liste);
        panneau.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final int hauteurMax = Math.round(getResources().getDisplayMetrics().heightPixels * 0.86f);
        FrameLayout cadre = new FrameLayout(this) {
            @Override protected void onMeasure(int ws, int hs) {
                super.onMeasure(ws, MeasureSpec.makeMeasureSpec(hauteurMax, MeasureSpec.AT_MOST));
            }
        };
        cadre.addView(panneau, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(cadre);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.round(Math.max(dp(460), Math.min(dp(640), large))), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
        }
        d.show();
        if (premier != null) premier.requestFocus();
    }

    private String texteVeille(int minutes) {
        return minutes == 0 ? getString(R.string.veille_non) : getResources().getQuantityString(R.plurals.veille_apres, minutes, minutes);
    }

    /** Réglages Orba (appui long sur la roue dentée). Chaque choix est appliqué tout de suite. */
    private void montrerReglages() {
        final String[] fonds = {getString(R.string.fond_anime), getString(R.string.fond_fixe), getString(R.string.fond_noir)};
        final String[] themes = {"Orba", "Glossy", "OLED"};
        final String langue = Langue.choisie(this);
        final String[] langues = Langue.NOMS.clone();
        langues[0] = getString(R.string.langue_auto);
        int l = 0;
        for (int i = 0; i < langues.length; i++) if (Langue.CODES[i].equals(langue)) l = i;
        final int langueActuelle = l;
        final String[] options = {
                paire(R.string.theme, themes[Views.theme]),
                paire(R.string.langue, langues[langueActuelle]),
                paire(R.string.tuiles_par_ligne, String.format(Locale.getDefault(), "%d", colonnes)),
                paire(R.string.fond, fonds[fondMode]),
                paire(R.string.halo, getString(haloCyan ? R.string.halo_orba : R.string.halo_appli)),
                paire(R.string.veille, texteVeille(veilleMinutes)),
                getString(R.string.veille_voir),
                paire(R.string.au_demarrage, nomAppliDemarrage()),
                getString(R.string.applis_masquees),
                getString(R.string.parametres_tele)
        };
        choisir(getString(R.string.reglages_orba), options, new Choix() {
            @Override public void choisi(int i) {
                switch (i) {
                    case 0: choisirValeur(getString(R.string.theme), new String[]{getString(R.string.theme_orba),
                            getString(R.string.theme_glossy), getString(R.string.theme_oled)}, new Choix() {
                        @Override public void choisi(int k) {
                            if (k == Views.theme) { montrerReglages(); return; }
                            prefs.edit().putInt("theme", k).apply();
                            rouvrirReglages = true;   // l'écran est reconstruit avec le nouveau thème, puis les réglages se rouvrent
                            recreate();
                        } }, Views.theme); break;
                    case 1: choisirValeur(getString(R.string.langue), langues, new Choix() {
                        @Override public void choisi(int k) {
                            if (k == langueActuelle) { montrerReglages(); return; }
                            prefs.edit().putString(Langue.PREF, Langue.CODES[k]).apply();
                            rouvrirReglages = true;   // l'écran est reconstruit dans la langue choisie
                            recreate();
                        } }, langueActuelle); break;
                    case 2: choisirValeur(getString(R.string.tuiles_par_ligne), nombres(4, 7), new Choix() {
                        @Override public void choisi(int k) {
                            colonnes = 4 + k; prefs.edit().putInt("colonnes", colonnes).apply();
                            signature = ""; appliquerOrdre(); montrerReglages();
                        } }); break;
                    case 3: choisirValeur(getString(R.string.fond), fonds, new Choix() {
                        @Override public void choisi(int k) {
                            fondMode = k; prefs.edit().putInt("fond", k).apply();
                            ondes.setMode(Views.oled() ? 2 : k); montrerReglages();
                        } }); break;
                    case 4: choisirValeur(getString(R.string.halo), new String[]{Langue.cap(getString(R.string.halo_appli)),
                            Langue.cap(getString(R.string.halo_orba))}, new Choix() {
                        @Override public void choisi(int k) {
                            haloCyan = k == 1; prefs.edit().putBoolean("halo_cyan", haloCyan).apply();
                            signature = ""; appliquerOrdre(); montrerReglages();
                        } }); break;
                    case 5: final int[] minutes = {0, 5, 10, 20, 30};
                        String[] durees = new String[minutes.length];
                        for (int k = 0; k < durees.length; k++) durees[k] = Langue.cap(texteVeille(minutes[k]));
                        choisirValeur(getString(R.string.veille), durees, new Choix() {
                        @Override public void choisi(int k) {
                            veilleMinutes = minutes[k]; prefs.edit().putInt("veille_minutes", veilleMinutes).apply();
                            relancerVeille(); montrerReglages();
                        } }); break;
                    case 6: startActivity(new Intent(MainActivity.this, VeilleActivity.class)); break;
                    case 7: choisirAppliDemarrage(); break;
                    case 8: montrerMasquees(); break;
                    default: ouvrir(Settings.ACTION_SETTINGS);
                }
            }
        });
    }

    /** Les nombres de a à b, écrits dans les chiffres de la langue. */
    private static String[] nombres(int a, int b) {
        String[] r = new String[b - a + 1];
        for (int i = 0; i < r.length; i++) r[i] = String.format(Locale.getDefault(), "%d", a + i);
        return r;
    }
    private void choisirValeur(String titre, String[] valeurs, Choix action) { choisir(titre, valeurs, action); }
    private void choisirValeur(String titre, String[] valeurs, Choix action, int depart) { choisir(titre, valeurs, action, depart); }

    /** Ouvre la fiche système de l'appli (Forcer l'arrêt, Vider le cache, Désinstaller). */
    private void infosAppli(App a) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", a.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            ouvrir(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_SETTINGS);
        }
    }

    /** Vrai si l'appli peut être désinstallée : installée par l'utilisateur, pas une appli système. */
    private boolean desinstallable(App a) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(a.pkg, 0);
            return (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Désinstallation par la fenêtre de confirmation d'Android : rien n'est supprimé sans un « OK »
     * de l'utilisateur. Une fois l'appli partie, la grille se met à jour seule (packageReceiver).
     */
    private void desinstaller(App a) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, android.net.Uri.fromParts("package", a.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            infosAppli(a);   // pas de fenêtre de désinstallation sur cette télé : la fiche de l'appli propose le bouton
        }
    }

    /** Menu de l'appui long : déplacer, favoris, infos, désinstaller, masquer, réafficher. */
    private void menu(final App a) {
        final boolean protege = SETTINGS_PKG.equals(a.pkg);
        final List<String> choix = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        choix.add(getString(R.string.deplacer)); codes.add(0);
        if (!protege) {
            if (favoris.contains(a)) { choix.add(getString(R.string.favori_retirer)); codes.add(5); }
            else { choix.add(getString(R.string.favori_ajouter)); codes.add(5); }
        }
        if (!protege) { choix.add(getString(R.string.infos_appli)); codes.add(1); }
        if (!protege && desinstallable(a)) { choix.add(getString(R.string.desinstaller)); codes.add(6); }
        if (!protege) { choix.add(getString(R.string.masquer)); codes.add(2); }
        choix.add(getString(R.string.applis_masquees)); codes.add(3);
        choix.add(getString(R.string.reglages_orba_menu)); codes.add(4);
        choisir(a.label, choix.toArray(new String[0]), new Choix() {
            @Override public void choisi(int which) {
                switch (codes.get(which)) {
                    case 0: commencerDeplacement(a); break;
                    case 1: infosAppli(a); break;
                    case 2: masquer(a); break;
                    case 3: montrerMasquees(); break;
                    case 4: montrerReglages(); break;
                    case 5: basculerFavori(a); break;
                    case 6: desinstaller(a); break;
                }
            }
        });
    }

    private void masquer(App a) {
        Set<String> m = new HashSet<>(prefs.getStringSet("masquees", new HashSet<String>()));
        m.add(a.pkg);
        List<App> liste = favoris.contains(a) ? favoris : apps;
        int idx = liste.indexOf(a);
        List<App> favs = new ArrayList<>(favoris);
        favs.remove(a);
        prefs.edit().putStringSet("masquees", m).putString("favoris", joindre(favs)).apply();
        if (idx >= 0 && idx + 1 < liste.size()) focusPkg = liste.get(idx + 1).pkg;
        else if (idx > 0) focusPkg = liste.get(idx - 1).pkg;
        appliquerOrdre();
        Toast.makeText(this, getString(R.string.msg_masquee, Langue.nom(this, a.label)), Toast.LENGTH_LONG).show();
    }

    private void montrerMasquees() {
        Set<String> m = prefs.getStringSet("masquees", new HashSet<String>());
        final List<App> liste = new ArrayList<>();
        for (App a : toutes) if (m.contains(a.pkg) && !SETTINGS_PKG.equals(a.pkg)) liste.add(a);
        if (liste.isEmpty()) {
            Toast.makeText(this, R.string.msg_aucune_masquee, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] noms = new String[liste.size()];
        for (int i = 0; i < noms.length; i++) noms[i] = liste.get(i).label;
        choisir(getString(R.string.reafficher), noms, new Choix() {
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
        for (int k = 0; k < dock.getChildCount(); k++) dock.getChildAt(k).invalidate();
        for (int r = 0; r < rows.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) rows.getChildAt(r);
            for (int k = 0; k < row.getChildCount(); k++) row.getChildAt(k).invalidate();
        }
    }

    /** Pendant un déplacement, les flèches font glisser la tuile dans la grille. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        relancerVeille();   // toute touche repousse l'écran de veille
        int kc = e.getKeyCode();
        // Touche physique Sources / Input de la télécommande : ouvre directement le panneau Sources
        if (kc == KeyEvent.KEYCODE_TV_INPUT || kc == KeyEvent.KEYCODE_TV_ANTENNA_CABLE) {
            if (e.getAction() == KeyEvent.ACTION_UP) ouvrirSources();
            return true;
        }
        // Roue dentée : OK court = paramètres de la télé, OK maintenu 0,6 s = Réglages Orba.
        if ((kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER)
                && boutonReglages != null && (boutonReglages.isFocused() || okRoueEnfonce)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    okRoueEnfonce = true; okRoueLongTraite = false;
                    handler.postDelayed(appuiLongRoue, 600);
                } else if (e.getRepeatCount() >= 3 && !okRoueLongTraite) {   // télécommande qui répète la touche
                    handler.removeCallbacks(appuiLongRoue); appuiLongRoue.run();
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(appuiLongRoue);
                boolean longTraite = okRoueLongTraite;
                okRoueEnfonce = false; okRoueLongTraite = false;
                if (!longTraite) ouvrir(Settings.ACTION_SETTINGS);
            }
            return true;
        }
        if (enDeplacement == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(e);
        }
        boolean dansDock = favoris.contains(enDeplacement);   // un favori se déplace dans le dock seulement
        List<App> liste = dansDock ? favoris : apps;
        int i = liste.indexOf(enDeplacement);
        int j = i;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  j = rtl ? i + 1 : i - 1; break;   // arabe : la grille est inversée
            case KeyEvent.KEYCODE_DPAD_RIGHT: j = rtl ? i - 1 : i + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP:    j = dansDock ? i : i - colonnes; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  j = dansDock ? i : Math.min(i + colonnes, apps.size() - 1); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                finirDeplacement();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
        if (j >= 0 && j < liste.size() && j != i) {
            App moved = liste.remove(i);
            liste.add(j, moved);
            focusPkg = moved.pkg;
            signature = signatureActuelle();
            buildGrid();
        }
        return true;
    }

    private void buildGrid() {
        rows.removeAllViews();
        dock.removeAllViews();
        Tile first = null, cible = null;

        // Dock : les favoris en grandes tuiles (4 colonnes), sans nom dessous (il s'affiche en grand au-dessus).
        for (final App a : favoris) {
            Tile t = new Tile(this, a, true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launch(a); }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { menu(a); return true; }
            });
            dock.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (first == null) first = t;
            if (a.pkg.equals(focusPkg)) cible = t;
        }
        for (int k = favoris.size(); k > 0 && k < MAX_FAVORIS; k++) {
            dock.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        int vis = favoris.isEmpty() ? View.GONE : View.VISIBLE;
        dock.setVisibility(vis);
        nomFavori.setVisibility(vis);

        LinearLayout row = null;
        List<App> tuiles = new ArrayList<>(apps);
        tuiles.add(plus);   // tuile « + » toujours en dernier
        for (int i = 0; i < tuiles.size(); i++) {
            if (i % colonnes == 0) {
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
            for (int k = tuiles.size() % colonnes; k != 0 && k < colonnes; k++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        Tile focus = cible != null ? cible : first;
        if (focus != null) focus.requestFocus();
        if (focus != null && focus.dansDock && defilement != null) defilement.scrollTo(0, 0);   // le nom en grand reste visible
    }

    /** Affiche en grand le nom du favori choisi, et l'efface quand la sélection quitte le dock. */
    private void majNomFavori(final Tile t, boolean gain) {
        if (gain) {
            nomFavori.setText(t.app.label);
            nomFavori.animate().alpha(1f).setDuration(180).start();
            if (defilement != null) defilement.smoothScrollTo(0, 0);
        } else {
            handler.post(new Runnable() {
                @Override public void run() {
                    View v = getCurrentFocus();
                    if (!(v instanceof Tile) || !((Tile) v).dansDock) nomFavori.animate().alpha(0f).setDuration(180).start();
                }
            });
        }
    }

    /** Ouvre le premier écran de réglages disponible parmi ceux proposés. */
    /**
     * Sources de la télé (HDMI, antenne…). Sur les Philips : le panneau Sources d'origine.
     * Ailleurs : une liste des entrées de la télé dans une fenêtre Orba.
     */
    private void ouvrirSources() {
        try {
            startActivity(new Intent("org.droidtv.intent.action.SOURCE_LIST")
                    .setPackage("org.droidtv.channels")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception ignored) { }
        final List<android.media.tv.TvInputInfo> entrees = new ArrayList<>();
        android.media.tv.TvInputManager tim = null;
        try {
            tim = (android.media.tv.TvInputManager) getSystemService(TV_INPUT_SERVICE);
            for (android.media.tv.TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(this)) entrees.add(info);
            }
        } catch (Exception ignored) { }
        if (entrees.isEmpty()) {
            Toast.makeText(this, R.string.msg_aucune_source, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] noms = new String[entrees.size()];
        for (int i = 0; i < noms.length; i++) {
            android.media.tv.TvInputInfo info = entrees.get(i);
            CharSequence nom = info.loadCustomLabel(this);
            if (nom == null || nom.length() == 0) nom = info.loadLabel(this);
            boolean actif = false;
            try {
                actif = tim != null && tim.getInputState(info.getId()) == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED;
            } catch (Exception ignored) { }
            noms[i] = (actif ? "●  " : "") + String.valueOf(nom);
        }
        choisir(getString(R.string.sources), noms, new Choix() {
            @Override public void choisi(int which) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.media.tv.TvContract.buildChannelUriForPassthroughInput(entrees.get(which).getId()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, R.string.msg_source_impossible, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void ouvrir(String... actions) {
        for (String a : actions) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) { }
        }
        Toast.makeText(this, R.string.msg_parametres_impossible, Toast.LENGTH_SHORT).show();
    }

    private void launch(App a) {
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_ouvrir_impossible, Langue.nom(this, a.label)), Toast.LENGTH_SHORT).show();
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
        final boolean dansDock;             // tuile du dock de favoris : plus grande, nom affiché au-dessus du dock
        final int marge = dp(16);           // place réservée au halo autour de la tuile
        final int hautNom;                  // place du nom sous la tuile
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
        final Paint brillance = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint filet = new Paint(Paint.ANTI_ALIAS_FLAG);
        float focus = 0f;
        long debutDefil;
        ValueAnimator anim;

        Tile(Context c, App a) { this(c, a, false); }

        Tile(Context c, App a, boolean dock) {
            super(c);
            app = a;
            dansDock = dock;
            hautNom = dock ? dp(8) : dp(30);
            setFocusable(true);
            setClickable(true);
            setContentDescription(a.label);
            setOutlineProvider(null);       // pas d'ombre système quand on la passe au premier plan
            fond.setColor(Views.oled() && a.fond == TUILE ? 0xFF17171B : a.fond);
            lueur.setColor(haloCyan ? Views.accent() : a.halo);
            contour.setStyle(Paint.Style.STROKE);
            contour.setStrokeWidth(dp(2.5f));
            contour.setColor(Views.accent());
            voile.setColor(Views.fond());
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
            setPivotX(w / 2f);               // zoom centré, aussi pour la tuile choisie avant sa mise en place
            setPivotY(carte.centerY());
            // Halo « cyan Orba » : dégradé signature cyan → bleu → violet, comme OrbaTV
            if (haloCyan && Views.glossy()) lueur.setShader(Views.degrade(carte.left, carte.right));
        }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
            anim.setDuration(Views.theme == Views.ORBA ? 200 : 140);   // zoom vif de la charte (~140 ms)
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
            debutDefil = android.os.SystemClock.uptimeMillis();   // le nom trop long repart du début
            // Passer devant les voisines (et la ligne devant les autres lignes).
            setTranslationZ(gain ? 2f : 0f);
            View row = (View) getParent();
            if (row != null) row.setTranslationZ(gain ? 1f : 0f);
            if (row != null) row.setOutlineProvider(null);
            if (dansDock) majNomFavori(this, gain);
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
            // 1. Halo lumineux (couleur de l'appli, ou dégradé Orba) qui respire doucement tant que la tuile est choisie.
            if (focus > 0.01f) {
                if (Views.glossy()) {
                    float souffle = Views.souffle();
                    float ext = marge * (0.82f + 0.18f * souffle);
                    Views.halo(c, carte, rayon, ext, focus * (0.78f + 0.22f * souffle), lueur, tmp, 10, 24);
                } else {   // Orba et OLED : halo fixe (OLED un peu plus discret : pas de marquage de la dalle)
                    Views.halo(c, carte, rayon, marge, focus * (Views.oled() ? 0.7f : 1f), lueur, tmp, 10, 22);
                }
            }

            // 2. Tuile et logo d'origine.
            c.save();
            c.clipPath(clip);
            c.drawRect(carte, fond);
            if (app == plus) {
                float s = carte.height() * 0.18f;
                signe.setColor(focus > 0.5f ? Views.accent() : Views.doux());
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
            // 3. Les tuiles non choisies sont légèrement atténuées ; la tuile choisie reçoit le reflet glossy.
            voile.setAlpha(Math.round((1f - focus) * 80f));
            c.drawRect(carte, voile);
            if (focus > 0.01f && Views.theme != Views.ORBA) Views.reflet(c, carte, rayon, Math.round((Views.oled() ? 0x16 : 0x2C) * focus), brillance);
            c.restore();
            // Glossy : filet « verre » blanc → cyan ; OLED : filet ambre net ; Orba : aucun filet
            if (focus > 0.01f) {
                if (Views.glossy()) Views.filet(c, carte, rayon, dp(1.5f), Math.round(0xB0 * focus), Math.round(0x90 * focus), filet);
                else if (Views.oled()) Views.contour(c, carte, rayon, dp(2), Views.AMBRE, Math.round(255 * focus), filet);
            }

            // 4. Nom de l'appli sous la tuile choisie. Trop long : il défile (400 ms d'attente, 45 dp/s, sans fin).
            if (focus > 0.01f && !dansDock) {
                nom.setAlpha(Math.round(focus * 255));
                String txt = app.label;
                float max = getWidth() - dp(8), lw = nom.measureText(txt), y = carte.bottom + dp(24);
                if (lw <= max) {
                    c.drawText(txt, getWidth() / 2f, y, nom);
                } else {
                    float ecart = dp(48), cycle = lw + ecart;
                    long el = android.os.SystemClock.uptimeMillis() - debutDefil - 400;
                    float dx = el <= 0 ? 0f : (el / 1000f * dp(45)) % cycle;
                    float x0 = getWidth() / 2f - max / 2f;
                    c.save();
                    c.clipRect(x0, y - nom.getTextSize() * 1.3f, x0 + max, y + nom.getTextSize() * 0.5f);
                    nom.setTextAlign(Paint.Align.LEFT);
                    if (android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(txt, 0, txt.length())) {
                        c.drawText(txt, x0 + max - lw + dx, y, nom);   // nom écrit en arabe : il défile dans l'autre sens
                        c.drawText(txt, x0 + max - lw + dx - cycle, y, nom);
                    } else {
                        c.drawText(txt, x0 - dx, y, nom);
                        c.drawText(txt, x0 - dx + cycle, y, nom);
                    }
                    nom.setTextAlign(Paint.Align.CENTER);
                    c.restore();
                }
            }
            if (isFocused()) postInvalidateOnAnimation();   // halo qui respire et nom qui défile
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
            cercle.setColor(Views.accent());
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

        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG), brillance = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rond = new RectF(), tmp = new RectF();
        private final Path clip = new Path();

        @Override protected void onDraw(Canvas c) {
            // Halo dessiné hors du bouton : le parent ne le rogne pas (voir setClipChildren dans l'en-tête)
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f - dp(1.5f);
            if (focus > 0.01f && !Views.glossy()) {
                // Orba : disque bleu nuit cerclé de cyan ; OLED : disque sombre cerclé d'ambre (net, sans flou)
                disque.setShader(null);
                disque.setColor(Views.oled() ? 0xFF1C1C21 : 0xFF15245A);
                disque.setAlpha(Math.round(focus * 255));
                c.drawCircle(cx, cy, r, disque);
                cercle.setShader(null);
                cercle.setColor(Views.accent());
                cercle.setAlpha(Math.round(focus * 255));
                c.drawCircle(cx, cy, r, cercle);
            } else if (focus > 0.01f) {
                rond.set(cx - r, cy - r, cx + r, cy + r);
                float s = Views.souffle();
                halo.setShader(Views.degrade(rond.left, rond.right));
                Views.halo(c, rond, r, dp(7) * (0.8f + 0.2f * s), focus * (0.75f + 0.25f * s), halo, tmp, 6, 30);
                // disque de verre bleu nuit, reflet glossy, filet blanc → cyan
                disque.setShader(new LinearGradient(0, rond.top, 0, rond.bottom, 0xFF16397A, Views.PANEL_FOCUS, Shader.TileMode.CLAMP));
                disque.setAlpha(Math.round(focus * 255));
                c.drawCircle(cx, cy, r, disque);
                clip.reset();
                clip.addCircle(cx, cy, r, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                Views.reflet(c, rond, r, Math.round(0x40 * focus), brillance);
                c.restore();
                Views.filet(c, rond, r, dp(1.8f), Math.round(0xF0 * focus), Math.round(0xD0 * focus), cercle);
                if (isFocused()) postInvalidateOnAnimation();
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
            super(c, c.getString(R.string.parametres));
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
    /** Bouton « Sources » au trait fin (téléviseur avec antenne, d'après Lucide « tv », licence ISC). */
    final class SourcesView extends BoutonIcone {
        private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);

        SourcesView(Context c) {
            super(c, c.getString(R.string.sources_description));
            trait.setStyle(Paint.Style.STROKE);
            trait.setStrokeCap(Paint.Cap.ROUND);
            trait.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override void dessiner(Canvas c, float x, float y, float t) {
            float k = t / 24f;
            trait.setStrokeWidth(1.8f * k);
            trait.setColor(couleurIcone());
            // écran : rect x2 y7 w20 h15 rx2
            c.drawRoundRect(x + 2 * k, y + 7 * k, x + 22 * k, y + 22 * k, 2 * k, 2 * k, trait);
            // antenne : polyline 17,2 12,7 7,2
            Path p = new Path();
            p.moveTo(x + 17 * k, y + 2 * k);
            p.lineTo(x + 12 * k, y + 7 * k);
            p.lineTo(x + 7 * k, y + 2 * k);
            c.drawPath(p, trait);
        }
    }

    final class ReseauView extends BoutonIcone {
        private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private int type = 1;      // 0 = hors ligne, 1 = Wi-Fi, 2 = Ethernet
        private int niveau = 3;    // 0..3

        ReseauView(Context c) {
            super(c, c.getString(R.string.reseau_description));
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
            int[] force = {R.string.signal_0, R.string.signal_1, R.string.signal_2, R.string.signal_3};
            setContentDescription(type == 2 ? getString(R.string.reseau_ethernet)
                    : type == 1 ? getString(R.string.reseau_wifi, getString(force[niveau])) : getString(R.string.reseau_hors_ligne));
        }

        /** Couleur du Wi-Fi selon la force du signal : vert fort, jaune moyen, rouge faible ou hors ligne. */
        private int couleurSignal() {
            if (type == 2) return couleurIcone();          // câble : couleur normale
            if (type == 0) return SIGNAL_ROUGE;            // hors ligne
            return niveau >= 3 ? SIGNAL_VERT : niveau == 2 ? SIGNAL_JAUNE : SIGNAL_ROUGE;
        }

        @Override void dessiner(Canvas c, float x, float y, float t) {
            float k = t / 24f;
            int coul = couleurSignal();
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
        private int mode = 0;               // 0 = vagues animées, 1 = vagues fixes, 2 = noir pur (OLED)
        private float attenuation = 1f;     // < 1 : vagues plus discrètes (écran de veille)

        void setMode(int m) { mode = m; invalidate(); if (running && m != 0) removeCallbacks(tick); else if (running) post(tick); }
        void setAttenuation(float a) { attenuation = a; }

        // base (hauteur), amplitude, fréquence, vitesse, phase, opacité de chaque vague
        private static final float[][] VAGUES = {
                {0.86f, 0.050f, 0.9f, 0.10f, 0.3f, 45},
                {0.91f, 0.045f, 0.7f, -0.07f, 2.0f, 75},
                {0.96f, 0.030f, 1.2f, 0.05f, 4.0f, 105},
        };

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                invalidate();
                if (running && mode == 0) postDelayed(this, 40);   // ~25 images/s, mouvement lent et fluide
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
                int a = Math.round(VAGUES[i][5] * attenuation) << 24;
                vague[i].setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{a | (CYAN & 0xFFFFFF), a | (BLEU & 0xFFFFFF), a | (INDIGO & 0xFFFFFF), a | (VIOLET & 0xFFFFFF)},
                        new float[]{0f, 0.38f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            }
        }

        @Override protected void onDraw(Canvas c) {
            float t = mode == 0 ? (SystemClock.uptimeMillis() - t0) / 1000f : 0f;
            float W = getWidth(), H = getHeight();
            if (mode == 2) { c.drawColor(Views.oled() ? Views.OLED_FOND : Color.BLACK); return; }   // noir pur : pixels OLED éteints
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
                int a = Math.round(v[5] * attenuation);
                creteLueur.setColor(Color.argb(Math.min(255, a / 3), 0, 229, 255));
                c.drawPath(ligne, creteLueur);
                crete.setColor(Color.argb(Math.min(255, a + 90), 170, 240, 255));
                c.drawPath(ligne, crete);
            }
        }
    }
}
