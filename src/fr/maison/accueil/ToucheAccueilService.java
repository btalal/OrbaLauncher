package fr.maison.accueil;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fait d'Orba l'écran d'accueil de la télé (Google TV et Android TV).
 *
 *  - Touche Accueil, appui court : ouvre Orba.
 *  - Touche Accueil, appui long  : ouvre l'accueil d'origine (Google TV / Android TV)
 *    et le laisse tranquille jusqu'à ce qu'on ouvre une autre appli ou qu'on éteigne la télé.
 *  - Quand l'accueil d'origine s'affiche tout seul (allumage, démarrage, sortie d'une
 *    appli), il est remplacé par Orba.
 *
 * Ce que le service voit : les touches de la télécommande (seule la touche Accueil
 * est utilisée) et le NOM de l'appli affichée. Il ne lit pas le contenu de l'écran
 * et n'enregistre rien.
 */
public class ToucheAccueilService extends AccessibilityService {

    private static final long APPUI_LONG_MS = 500;
    private static final long ANTI_BOUCLE_MS = 4000;

    // Launchers d'origine connus sur Google TV, Android TV et Xiaomi TV (PatchWall)
    private static final String[] LAUNCHERS_ORIGINE = {
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.mitv.tvhome"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long debutAppui = 0;
    private long derniereRedirection = 0;
    private boolean accueilOrigineVoulu = false; // l'utilisateur a demandé l'accueil d'origine (appui long)
    private String pkgAffiche = "";              // dernier package affiché

    private final BroadcastReceiver ecran = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                accueilOrigineVoulu = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                // Au réveil, si c'est l'accueil d'origine qui est devant, on le remplace.
                handler.postDelayed(new Runnable() {
                    @Override public void run() { if (estLauncherOrigine(pkgAffiche)) remplacer(); }
                }, 1500);
            }
        }
    };

    private static boolean estLauncherOrigine(String pkg) {
        if (pkg == null) return false;
        for (String s : LAUNCHERS_ORIGINE) if (s.equals(pkg)) return true;
        return false;
    }

    @Override
    protected void onServiceConnected() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(ecran, f);
        // Après l'allumage, l'accueil d'origine est déjà à l'écran quand le service démarre : aucun changement
        // de fenêtre ne viendra le signaler. Juste après un démarrage, on affiche donc Orba directement.
        if (SystemClock.elapsedRealtime() < 3 * 60_000L) {
            handler.postDelayed(new Runnable() {
                @Override public void run() { if (!accueilOrigineVoulu) remplacer(); }
            }, 1500);
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(ecran); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_HOME) return false; // on ne touche à rien d'autre

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) debutAppui = event.getEventTime();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            long duree = event.getEventTime() - debutAppui;
            if (duree >= APPUI_LONG_MS) {
                accueilOrigineVoulu = true;
                ouvrirAccueilOrigine();
            } else {
                accueilOrigineVoulu = false;
                ouvrirOrba();
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

        if (estLauncherOrigine(pkg)) {
            if (!cls.startsWith(pkg) && !cls.contains("Home") && !cls.contains("Main") && !cls.contains("Launcher")) return;
            pkgAffiche = pkg;
            if (!accueilOrigineVoulu) remplacer();
            return;
        }
        // Ignorer les surcouches système (volume, notifications...) : elles ne changent pas l'appli devant.
        if (pkg.equals("com.android.systemui") || pkg.equals("org.droidtv.tvsystemui")
                || pkg.equals("android") || !cls.contains(".")) return;
        pkgAffiche = pkg;
        if (!pkg.equals(getPackageName())) accueilOrigineVoulu = false;  // une autre appli a été ouverte
    }

    /** Remplace l'accueil d'origine par Orba, au plus une fois toutes les 4 s. */
    private void remplacer() {
        long now = SystemClock.uptimeMillis();
        if (now - derniereRedirection < ANTI_BOUCLE_MS) return;
        derniereRedirection = now;
        ouvrirOrba();
    }

    private void ouvrirOrba() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch (Exception e) { /* on laisse l'accueil d'origine */ }
    }

    private void ouvrirAccueilOrigine() {
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(homeIntent, 0)) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (!getPackageName().equals(p) && estLauncherOrigine(p)) {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setComponent(new ComponentName(p, ri.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception ignored) { }
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(LAUNCHERS_ORIGINE[0], LAUNCHERS_ORIGINE[0] + ".home.HomeActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    @Override public void onInterrupt() { }
}
