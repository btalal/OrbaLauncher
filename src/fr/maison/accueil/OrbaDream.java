package fr.maison.accueil;

import android.service.dreams.DreamService;
import android.view.View;

/**
 * Le même écran de veille, déclaré comme économiseur d'écran Android : la télé peut
 * l'utiliser partout (Paramètres > Système > Économiseur d'écran, si la marque le propose).
 */
public class OrbaDream extends DreamService {
    private View racine;

    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Langue.appliquer(base)); }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        racine = Veille.creer(this);
        setContentView(racine);
    }

    @Override public void onDreamingStarted() { super.onDreamingStarted(); Veille.demarrer(racine); }
    @Override public void onDreamingStopped() { super.onDreamingStopped(); Veille.arreter(racine); }
}
