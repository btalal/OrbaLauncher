package fr.maison.accueil;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Écran de veille lancé par Orba après un temps d'inactivité. N'importe quelle touche le ferme. */
public class VeilleActivity extends Activity {
    private View racine;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        racine = Veille.creer(this);
        setContentView(racine);
    }

    @Override protected void onResume() { super.onResume(); Veille.demarrer(racine); }
    @Override protected void onPause()  { super.onPause();  Veille.arreter(racine); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // Volume et coupure du son passent sans réveiller : on peut monter le son pendant la veille.
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) { finish(); return true; }
}
