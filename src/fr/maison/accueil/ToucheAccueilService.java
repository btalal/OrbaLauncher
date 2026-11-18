package fr.maison.accueil;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fait d'« Accueil simple » l'écran d'accueil de la télé.
 *
 *  - Touche Accueil, appui court : ouvre Accueil simple.
 *  - Touche Accueil, appui long  : ouvre l'accueil Google TV (et le laisse tranquille
 *    jusqu'à ce qu'on ouvre une autre appli ou qu'on éteigne la télé).
 *  - Quand l'accueil Google s'affiche tout seul (allumage, démarrage, sortie d'une
 *    appli), il est remplacé par Accueil simple.
 *
 * Ce que le service voit : les touches de la télécommande (seule la touche Accueil
 * est utilisée) et le NOM de l'appli affichée. Il ne lit pas le contenu de l'écran
 * et n'enregistre rien.
 */
public class ToucheAccueilService extends AccessibilityService {

    private static final long APPUI_LONG_MS = 500;
    private static final long ANTI_BOUCLE_MS = 4000;
    private static final String GOOGLE_PKG = "com.google.android.apps.tv.launcherx";
    private static final String GOOGLE_HOME = GOOGLE_PKG + ".home.HomeActivity";
    private static final ComponentName ACCUEIL_GOOGLE = new ComponentName(GOOGLE_PKG, GOOGLE_HOME);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long debutAppui = 0;
    private long derniereRedirection = 0;
    private boolean googleVoulu = false;     // l'utilisateur a demandé l'accueil Google (appui long)
    private String classeAffichee = "";      // dernière activité affichée (nom seulement)

    private final BroadcastReceiver ecran = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                googleVoulu = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                // Au réveil, si c'est l'accueil Google qui est devant, on le remplace.
                handler.postDelayed(new Runnable() {
                    @Override public void run() { if (GOOGLE_HOME.equals(classeAffichee)) remplacer(); }
                }, 1500);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(ecran, f);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(ecran); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false; // on ne touche à rien d'autre

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) debutAppui = event.getEventTime();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            long duree = event.getEventTime() - debutAppui;
            if (duree >= APPUI_LONG_MS) {
                googleVoulu = true;
                ouvrirAccueilGoogle();
            } else {
                googleVoulu = false;
                ouvrirAccueilSimple();
            }
            return true;
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(e.getPackageName());
        String cls = String.valueOf(e.getClassName());

        if (GOOGLE_PKG.equals(pkg)) {
            if (!cls.startsWith(GOOGLE_PKG)) return;   // fenêtres internes sans nom d'activité
            classeAffichee = cls;
            if (GOOGLE_HOME.equals(cls) && !googleVoulu) remplacer();
            return;
        }
        // Ignorer les surcouches système (volume, notifications...) : elles ne changent pas l'appli devant.
        if (pkg.equals("com.android.systemui") || pkg.equals("org.droidtv.tvsystemui")
                || pkg.equals("android") || !cls.contains(".")) return;
        classeAffichee = cls;
        if (!pkg.equals(getPackageName())) googleVoulu = false;  // une autre appli a été ouverte
    }

    /** Remplace l'accueil Google par Accueil simple, au plus une fois toutes les 4 s. */
    private void remplacer() {
        long now = SystemClock.uptimeMillis();
        if (now - derniereRedirection < ANTI_BOUCLE_MS) return;
        derniereRedirection = now;
        ouvrirAccueilSimple();
    }

    private void ouvrirAccueilSimple() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch (Exception e) { /* on laisse l'accueil Google */ }
    }

    private void ouvrirAccueilGoogle() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(ACCUEIL_GOOGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    @Override public void onInterrupt() { }
}
