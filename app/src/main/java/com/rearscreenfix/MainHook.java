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
 * HOOK 1 — com.android.thememanager (PID ThemeManagera)
 * -------------------------------------------------------
 * Problem: RearScreenResOperationHelper.invokeSuspend() poziva py.g() za
 * kopiranje .mra DRM rights datoteke. Datoteka ne postoji lokalno →
 * py.g() vraća false → b3=0 → return false → MTZ/ETC/MRM kopiranje
 * i SubScreenCenter notifikacija se nikad ne izvršavaju.
 *
 * Fix: Hookamo py.g(String,String,int). Kad src .mra ne postoji,
 * vraćamo true + kreiramo praznu stub datoteku na destPath.
 * Time b3=1 i svi daljnji koraci se izvršavaju.
 *
 * HOOK 2 — com.xiaomi.subscreencenter (PID SubScreenCentera)
 * -----------------------------------------------------------
 * Problem: SubScreenWidgetManager neovisno validira svaki widget koji
 * prima od ThemeManagera. Provjerava postoji li .mra rights datoteka na
 * disku (File.exists()). Budući da datoteka ne postoji, svi widgeti su
 * označeni "invalid" → SubScreenCenter se vraća na preset teme →
 * vizualna promjena teme se ne dogodi.
 *
 * Napomena: ThemeManager ima "same-path bypass" — ako je rightPath==destPath,
 * py.g() se preskače i nikad ne zove (Hook 1 se ne okida). Stub datoteka
 * ipak nije na disku → SubScreenCenter validation pada.
 *
 * Fix: Hookamo java.io.File.exists() u SubScreenCenter procesu.
 * Za rearscreen .mra datoteke uvijek vraćamo true.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "RearScreenFix";
    private static final String THEME_MANAGER_PKG     = "com.android.thememanager";
    private static final String SUB_SCREEN_CENTER_PKG = "com.xiaomi.subscreencenter";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (THEME_MANAGER_PKG.equals(lpparam.packageName)) {
            hookThemeManager(lpparam);
        } else if (SUB_SCREEN_CENTER_PKG.equals(lpparam.packageName)) {
            hookSubScreenCenter(lpparam);
        }
    }

    // ─── HOOK 1: ThemeManager — py.g() rights copy bypass ───────────────────

    private void hookThemeManager(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "=== RearScreenFix: ThemeManager detektiran, postavljam hook... ===");
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.thememanager.util.py",
                lpparam.classLoader,
                "g",
                String.class,   // srcPath
                String.class,   // destPath
                int.class,      // mode (511 = 0777)
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String srcPath  = (String) param.args[0];
                        String destPath = (String) param.args[1];
                        if (srcPath == null) return;

                        if (srcPath.endsWith(".mra") && srcPath.contains("rearscreen")) {
                            File srcFile = new File(srcPath);
                            if (!srcFile.exists()) {
                                Log.i(TAG, "✅ [ThemeManager] .mra ne postoji: " + srcPath);

                                // Kreiraj praznu stub datoteku na destPath kako bi
                                // SubScreenCenter File.exists() provjera prošla
                                if (destPath != null) {
                                    try {
                                        File destFile = new File(destPath);
                                        destFile.getParentFile().mkdirs();
                                        if (!destFile.exists()) {
                                            destFile.createNewFile();
                                            Log.i(TAG, "✅ [ThemeManager] Kreirana stub .mra datoteka: " + destPath);
                                        }
                                    } catch (Exception e) {
                                        Log.w(TAG, "⚠️ [ThemeManager] Ne mogu kreirati stub datoteku: " + e.getMessage());
                                    }
                                }

                                param.setResult(true);
                            }
                        }
                    }
                }
            );
            Log.i(TAG, "✅ Hook 1 postavljen: com.android.thememanager.util.py.g");

        } catch (XposedHelpers.ClassNotFoundError e) {
            Log.e(TAG, "❌ Klasa nije pronađena: com.android.thememanager.util.py");
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "❌ Metoda nije pronađena: py.g — " + e.getMessage());
        } catch (Throwable t) {
            Log.e(TAG, "❌ Hook 1 greška: " + t.getMessage());
        }
    }

    // ─── HOOK 2: SubScreenCenter — File.exists() rights validation bypass ───

    private void hookSubScreenCenter(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "=== RearScreenFix: SubScreenCenter detektiran, postavljam hook... ===");
        try {
            XposedHelpers.findAndHookMethod(
                File.class,
                "exists",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Presrećemo samo ako je result false (datoteka ne postoji)
                        if (Boolean.TRUE.equals(param.getResult())) return;

                        String path = ((File) param.thisObject).getAbsolutePath();
                        if (path != null && path.endsWith(".mra") && path.contains("rearscreen")) {
                            Log.i(TAG, "✅ [SubScreenCenter] Bypass File.exists() za rights: " + path);
                            param.setResult(true);
                        }
                    }
                }
            );
            Log.i(TAG, "✅ Hook 2 postavljen: File.exists() u SubScreenCenter");

        } catch (Throwable t) {
            Log.e(TAG, "❌ Hook 2 greška: " + t.getMessage());
        }
    }
}
