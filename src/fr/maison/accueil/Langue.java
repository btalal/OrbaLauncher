package fr.maison.accueil;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.BidiFormatter;
import android.view.View;
import java.util.Locale;

/**
 * Langue d'Orba : automatique (celle de la télé) ou choisie dans Réglages Orba > Langue.
 * Textes dans res/values*, arabe affiché de droite à gauche. Une langue non traduite retombe sur l'anglais.
 */
final class Langue {
    static final String PREF = "langue", AUTO = "auto";
    static final String[] CODES = {AUTO, "fr", "en", "es", "it", "ar"};
    /** Chaque langue est écrite dans sa propre langue, pour s'y retrouver même après une erreur de choix. */
    static final String[] NOMS = {null, "Français", "English", "Español", "Italiano", "العربية"};

    private static boolean forcee;      // une langue a été imposée dans ce processus

    private Langue() { }

    static String choisie(Context c) {
        String l = c.getSharedPreferences("accueil", Context.MODE_PRIVATE).getString(PREF, AUTO);
        for (String k : CODES) if (k.equals(l)) return l;
        return AUTO;
    }

    /** À appeler dans attachBaseContext : contexte dans la langue choisie. */
    static Context appliquer(Context base) {
        String l = choisie(base);
        if (AUTO.equals(l)) {
            // Retour à la langue de la télé (sinon la langue imposée avant resterait active : dates, chiffres arabes…)
            if (forcee) Locale.setDefault(Resources.getSystem().getConfiguration().getLocales().get(0));
            forcee = false;
            return base;
        }
        forcee = true;
        Locale loc = new Locale(l);
        Locale.setDefault(loc);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(loc);
        c.setLayoutDirection(loc);
        return base.createConfigurationContext(c);
    }

    /** Vrai si Orba s'affiche en arabe (d'après la langue réellement affichée, pas celle de la télé). */
    static boolean rtl(Context c) { return "ar".equals(c.getString(R.string.code_langue)); }

    static int sens(Context c) { return rtl(c) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR; }

    /** Nom d'appli glissé dans une phrase : « CANAL+ » ne doit pas devenir « +CANAL » en arabe. */
    static String nom(Context c, String s) { return rtl(c) ? BidiFormatter.getInstance(true).unicodeWrap(s) : s; }

    /** Première lettre en capitale (sans effet en arabe). */
    static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }
}
