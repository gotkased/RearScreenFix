package com.rearscreenfix;

import android.util.Log;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * RearScreenFix — LSPosed modul za Xiaomi popsicle (EliteGaming HyperOS 3.0)
 *
 * Problem (iz jadx analize RearScreenResOperationHelper.invokeSuspend):
 *
 *   Redoslijed apply operacije:
 *   1. writeConfigToFile          ✅ uspijeva
 *   2. py.g(rightPath, dest, 511) ❌ pada jer .mra datoteka ne postoji lokalno
 *      → b3 = 0 → return false   ← OVDJE PUCA
 *   3. py.g(resLocalPath, ...)    ← nikad se ne izvrši (MTZ kopiranje teme)
 *   4. gc3c.k(... "etc" ...)      ← nikad se ne izvrši (ETC kopiranje)
 *   5. RearScreenCenterManager    ← nikad se ne izvrši (notifikacija SubScreenCentera)
 *   6. return true
 *
 * Rješenje:
 *   Hookamo py.g(String, String, int) — generičku file copy metodu.
 *   Kada se poziva za .mra rights datoteku koja ne postoji lokalno,
 *   vraćamo true umjesto false.
 *   Time b3 = 1, svi daljnji koraci se izvršavaju normalno,
 *   i SubScreenCenter dobiva notifikaciju o novoj temi.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "RearScreenFix";
    private static final String TARGET_PACKAGE = "com.android.thememanager";

    // Generička file copy metoda unutar ThemeManagera (pronađena u jadx)
    private static final String COPY_CLASS = "com.android.thememanager.util.py";
    private static final String COPY_METHOD = "g";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        Log.i(TAG, "=== RearScreenFix: ThemeManager detektiran, postavljam hook... ===");

        try {
            XposedHelpers.findAndHookMethod(
                COPY_CLASS,
                lpparam.classLoader,
                COPY_METHOD,
                String.class,   // srcPath
                String.class,   // destPath
                int.class,      // mode (511 = 0777)
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String srcPath = (String) param.args[0];
                        if (srcPath == null) return;

                        // Ciljamo samo .mra rights datoteke za rear screen
                        // koje ne postoje lokalno (tema nije kupljena/licencirana)
                        if (srcPath.endsWith(".mra") && srcPath.contains("rearscreen")) {
                            File srcFile = new File(srcPath);
                            if (!srcFile.exists()) {
                                Log.i(TAG, "✅ Rights .mra datoteka ne postoji: " + srcPath);
                                Log.i(TAG, "✅ Preskačemo copy, vraćamo true → MTZ/ETC/MRM kopiranje će se nastaviti normalno");
                                param.setResult(true);
                            }
                        }
                    }
                }
            );

            Log.i(TAG, "✅ Hook uspješno postavljen na: " + COPY_CLASS + "." + COPY_METHOD);

        } catch (XposedHelpers.ClassNotFoundError e) {
            Log.e(TAG, "❌ Klasa nije pronađena: " + COPY_CLASS);
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "❌ Metoda nije pronađena: " + COPY_METHOD + " — " + e.getMessage());
        } catch (Throwable t) {
            Log.e(TAG, "❌ Neočekivana greška: " + t.getMessage());
        }
    }
}
