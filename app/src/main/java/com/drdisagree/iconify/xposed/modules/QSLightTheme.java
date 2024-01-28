package com.drdisagree.iconify.xposed.modules;

/* Modified from AOSPMods
 * https://github.com/siavash79/AOSPMods/blob/canary/app/src/main/java/sh/siava/AOSPMods/systemui/QSThemeManager.java
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/].
 */

import static android.service.quicksettings.Tile.STATE_UNAVAILABLE;
import static com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE;
import static com.drdisagree.iconify.common.Preferences.DUALTONE_QSPANEL;
import static com.drdisagree.iconify.common.Preferences.LIGHT_QSPANEL;
import static com.drdisagree.iconify.common.Preferences.QS_TEXT_ALWAYS_WHITE;
import static com.drdisagree.iconify.common.Preferences.QS_TEXT_FOLLOW_ACCENT;
import static com.drdisagree.iconify.config.XPrefs.Xprefs;
import static com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.getColorAttr;
import static com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.getColorAttrDefaultColor;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.findMethodExactIfExists;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.drdisagree.iconify.xposed.ModPack;
import com.drdisagree.iconify.xposed.modules.utils.Helpers;
import com.drdisagree.iconify.xposed.utils.SystemUtil;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressWarnings("RedundantThrows")
public class QSLightTheme extends ModPack {

    public static final int STATE_ACTIVE = 2;
    private static final String TAG = "Iconify - " + QSLightTheme.class.getSimpleName() + ": ";
    private static boolean lightQSHeaderEnabled = false;
    private static boolean dualToneQSEnabled = false;
    private Object mBehindColors;
    private boolean isDark;
    private Integer colorActive = null;
    private Integer colorInactive = null;
    private Object mClockViewQSHeader = null;
    private Object unlockedScrimState;
    private boolean qsTextAlwaysWhite = false;
    private boolean qsTextFollowAccent = false;

    public QSLightTheme(Context context) {
        super(context);

        isDark = SystemUtil.isDarkMode();
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        lightQSHeaderEnabled = Xprefs.getBoolean(LIGHT_QSPANEL, false);
        dualToneQSEnabled = lightQSHeaderEnabled && Xprefs.getBoolean(DUALTONE_QSPANEL, false);

        qsTextAlwaysWhite = Xprefs.getBoolean(QS_TEXT_ALWAYS_WHITE, false);
        qsTextFollowAccent = Xprefs.getBoolean(QS_TEXT_FOLLOW_ACCENT, false);

        applyOverlays(true);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        setLightQsA12Plus(loadPackageParam);
        setLightQsA12(loadPackageParam);
    }

    private void setLightQsA12Plus(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (Build.VERSION.SDK_INT < 33) return;

        Class<?> QSTileViewImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.tileimpl.QSTileViewImpl", loadPackageParam.classLoader);
        Class<?> FragmentHostManagerClass = findClass(SYSTEMUI_PACKAGE + ".fragments.FragmentHostManager", loadPackageParam.classLoader);
        Class<?> ScrimControllerClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.ScrimController", loadPackageParam.classLoader);
        Class<?> GradientColorsClass = findClass("com.android.internal.colorextraction.ColorExtractor$GradientColors", loadPackageParam.classLoader);
        Class<?> QSPanelControllerClass = findClass(SYSTEMUI_PACKAGE + ".qs.QSPanelController", loadPackageParam.classLoader);
        Class<?> InterestingConfigChangesClass = findClass("com.android.settingslib.applications.InterestingConfigChanges", loadPackageParam.classLoader);
        Class<?> ScrimStateEnum = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.ScrimState", loadPackageParam.classLoader);
        Class<?> QSIconViewImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.tileimpl.QSIconViewImpl", loadPackageParam.classLoader);
        Class<?> CentralSurfacesImplClass = findClassIfExists(SYSTEMUI_PACKAGE + ".statusbar.phone.CentralSurfacesImpl", loadPackageParam.classLoader);
        Class<?> ClockClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.policy.Clock", loadPackageParam.classLoader);
        Class<?> QuickStatusBarHeaderClass = findClass(SYSTEMUI_PACKAGE + ".qs.QuickStatusBarHeader", loadPackageParam.classLoader);

        try {
            Class<?> BatteryStatusChipClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.BatteryStatusChip", loadPackageParam.classLoader);

            //background color of android 14's charging chip. Fix for light QS theme situation
            hookAllMethods(BatteryStatusChipClass, "updateResources", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (lightQSHeaderEnabled && !isDark) {
                        ((LinearLayout) getObjectField(param.thisObject, "roundedContainer")).getBackground().setTint(colorActive);

                        int colorPrimary = getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimaryInverse);
                        int textColorSecondary = getColorAttrDefaultColor(mContext, android.R.attr.textColorSecondaryInverse);
                        callMethod(getObjectField(param.thisObject, "batteryMeterView"), "updateColors", colorPrimary, textColorSecondary, colorPrimary);
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        unlockedScrimState = Arrays.stream(ScrimStateEnum.getEnumConstants()).filter(c -> c.toString().equals("UNLOCKED")).findFirst().get();

        hookAllMethods(unlockedScrimState.getClass(), "prepare", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled) return;

                setObjectField(unlockedScrimState, "mBehindTint", Color.TRANSPARENT);
            }
        });

        hookAllConstructors(QSPanelControllerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                calculateColors();
            }
        });

        try { //13QPR1
            Class<?> QSFgsManagerFooterClass = findClass(SYSTEMUI_PACKAGE + ".qs.QSFgsManagerFooter", loadPackageParam.classLoader);
            Class<?> FooterActionsControllerClass = findClass(SYSTEMUI_PACKAGE + ".qs.FooterActionsController", loadPackageParam.classLoader);

            hookAllConstructors(QSFgsManagerFooterClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isDark && lightQSHeaderEnabled) {
                        try {
                            ((View) getObjectField(param.thisObject, "mNumberContainer")).getBackground().setTint(colorInactive);
                        } catch (Throwable throwable) {
                            log(TAG + throwable);
                        }
                    }
                }
            });

            hookAllConstructors(FooterActionsControllerClass, new XC_MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isDark && lightQSHeaderEnabled) {
                        try {
                            Resources res = mContext.getResources();
                            ViewGroup view = (ViewGroup) param.args[0];

                            view.findViewById(res.getIdentifier("multi_user_switch", "id", mContext.getPackageName())).getBackground().setTint(colorInactive);

                            View settings_button_container = view.findViewById(res.getIdentifier("settings_button_container", "id", mContext.getPackageName()));
                            settings_button_container.getBackground().setTint(colorInactive);
                        } catch (Throwable throwable) {
                            log(TAG + throwable);
                        }
                    }
                }
            });

            //White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            hookAllMethods(QuickStatusBarHeaderClass, "onFinishInflate", new XC_MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View thisView = (View) param.thisObject;
                    Resources res = mContext.getResources();

                    try {
                        mClockViewQSHeader = getObjectField(param.thisObject, "mClockView");
                    } catch (Throwable ignored) {
                        mClockViewQSHeader = thisView.findViewById(res.getIdentifier("clock", "id", mContext.getPackageName()));
                    }

                    if (lightQSHeaderEnabled && !isDark && mClockViewQSHeader != null) {
                        try {
                            ((TextView) mClockViewQSHeader).setTextColor(Color.BLACK);
                        } catch (Throwable throwable) {
                            log(TAG + throwable);
                        }
                    }
                }
            });

            //White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            hookAllMethods(ClockClass, "onColorsChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    boolean isLight = isDark; // reverse logic
                    if (lightQSHeaderEnabled && isLight && mClockViewQSHeader != null) {
                        try {
                            ((TextView) mClockViewQSHeader).setTextColor(Color.BLACK);
                        } catch (Throwable throwable) {
                            log(TAG + throwable);
                        }
                    }
                }
            });
        } catch (Throwable throwable) { //13QPR2&3
            //13QPR3
            Class<?> ShadeHeaderControllerClass = findClassIfExists(SYSTEMUI_PACKAGE + ".shade.ShadeHeaderController", loadPackageParam.classLoader);
            //13QPR2
            if (ShadeHeaderControllerClass == null)
                ShadeHeaderControllerClass = findClass(SYSTEMUI_PACKAGE + ".shade.LargeScreenShadeHeaderController", loadPackageParam.classLoader);
            Class<?> QSContainerImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.QSContainerImpl", loadPackageParam.classLoader);

            hookAllMethods(ShadeHeaderControllerClass, "onInit", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        View mView = (View) getObjectField(param.thisObject, "mView");
                        Object iconManager = getObjectField(param.thisObject, "iconManager");
                        Object batteryIcon = getObjectField(param.thisObject, "batteryIcon");
                        Object configurationControllerListener = getObjectField(param.thisObject, "configurationControllerListener");

                        hookAllMethods(configurationControllerListener.getClass(), "onConfigChanged", new XC_MethodHook() {
                            @SuppressLint("DiscouragedApi")
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                setHeaderComponentsColor(mView, iconManager, batteryIcon);
                            }
                        });

                        setHeaderComponentsColor(mView, iconManager, batteryIcon);
                    } catch (Throwable throwable) {
                        log(TAG + throwable);
                    }
                }
            });

            hookAllMethods(QSContainerImplClass, "updateResources", new XC_MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isDark && lightQSHeaderEnabled) {
                        try {
                            Resources res = mContext.getResources();
                            ViewGroup view = (ViewGroup) param.thisObject;

                            View settings_button_container = view.findViewById(res.getIdentifier("settings_button_container", "id", mContext.getPackageName()));
                            settings_button_container.getBackground().setTint(colorInactive);

                            ImageView settings_icon = settings_button_container.findViewById(res.getIdentifier("icon", "id", mContext.getPackageName()));
                            settings_icon.setImageTintList(ColorStateList.valueOf(Color.BLACK));

                            View pm_button_container = view.findViewById(res.getIdentifier("pm_lite", "id", mContext.getPackageName()));
                            ImageView pm_icon = pm_button_container.findViewById(res.getIdentifier("icon", "id", mContext.getPackageName()));
                            pm_icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                        } catch (Throwable throwable) {
                            log(TAG + throwable);
                        }
                    }
                }
            });
        }

        hookAllMethods(QSIconViewImplClass, "updateIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (lightQSHeaderEnabled && !isDark && getIntField(param.args[1], "state") == STATE_ACTIVE) {
                    try {
                        ((ImageView) param.args[0]).setImageTintList(ColorStateList.valueOf(colorInactive));
                    } catch (Throwable throwable) {
                        log(TAG + throwable);
                    }
                }
            }
        });

        hookAllMethods(QSIconViewImplClass, "setIcon", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (lightQSHeaderEnabled && !isDark) {
                    try {
                        if (param.args[0] instanceof ImageView && getIntField(param.args[1], "state") == STATE_ACTIVE) {
                            setObjectField(param.thisObject, "mTint", colorInactive);
                        }
                    } catch (Throwable throwable) {
                        log(TAG + throwable);
                    }
                }
            }
        });

        if (CentralSurfacesImplClass != null) {
            hookAllConstructors(CentralSurfacesImplClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyOverlays(true);
                }
            });

            hookAllMethods(CentralSurfacesImplClass, "updateTheme", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    applyOverlays(false);
                }
            });
        }

        hookAllConstructors(QSTileViewImplClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled || isDark) return;

                try {
                    if (!qsTextAlwaysWhite && !qsTextFollowAccent) {
                        setObjectField(param.thisObject, "colorLabelActive", Color.WHITE);
                        setObjectField(param.thisObject, "colorSecondaryLabelActive", 0x80FFFFFF);
                    }
                    setObjectField(param.thisObject, "colorLabelInactive", Color.BLACK);
                    setObjectField(param.thisObject, "colorSecondaryLabelInactive", 0x80000000);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(QSIconViewImplClass, "getIconColorForState", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                boolean isActiveState = false;
                boolean isDisabledState;

                try {
                    isDisabledState = (boolean) getObjectField(param.args[1], "disabledByPolicy") ||
                            (int) getObjectField(param.args[1], "state") == STATE_UNAVAILABLE;
                } catch (Throwable throwable) {
                    isDisabledState = (int) getObjectField(param.args[1], "state") == STATE_UNAVAILABLE;
                }

                try {
                    isActiveState = (int) getObjectField(param.args[1], "state") == STATE_ACTIVE;
                } catch (Throwable throwable) {
                    try {
                        isActiveState = (int) param.args[1] == STATE_ACTIVE;
                    } catch (Throwable throwable1) {
                        try {
                            isActiveState = (boolean) param.args[1];
                        } catch (Throwable throwable2) {
                            log(TAG + throwable2);
                        }
                    }
                }

                if (!isDark && lightQSHeaderEnabled) {
                    if (isDisabledState) {
                        param.setResult(0x80000000);
                    } else {
                        if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                            param.setResult(Color.WHITE);
                        } else if (!isActiveState) {
                            param.setResult(Color.BLACK);
                        }
                    }
                }
            }
        });

        try {
            hookAllMethods(QSIconViewImplClass, "updateIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    boolean isActiveState = false;
                    boolean isDisabledState;

                    try {
                        isDisabledState = (boolean) getObjectField(param.args[1], "disabledByPolicy") ||
                                (int) getObjectField(param.args[1], "state") == STATE_UNAVAILABLE;
                    } catch (Throwable throwable) {
                        isDisabledState = (int) getObjectField(param.args[1], "state") == STATE_UNAVAILABLE;
                    }

                    try {
                        isActiveState = (int) getObjectField(param.args[1], "state") == STATE_ACTIVE;
                    } catch (Throwable throwable) {
                        try {
                            isActiveState = (int) param.args[1] == STATE_ACTIVE;
                        } catch (Throwable throwable1) {
                            try {
                                isActiveState = (boolean) param.args[1];
                            } catch (Throwable throwable2) {
                                log(TAG + throwable2);
                            }
                        }
                    }

                    if (!isDark && lightQSHeaderEnabled) {
                        ImageView mIcon = (ImageView) param.args[0];
                        if (isDisabledState) {
                            param.setResult(0x80000000);
                        } else {
                            if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                                mIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                            } else if (!isActiveState) {
                                mIcon.setImageTintList(ColorStateList.valueOf(Color.BLACK));
                            }
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            mBehindColors = GradientColorsClass.getDeclaredConstructor().newInstance();
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }

        hookAllMethods(ScrimControllerClass, "updateScrims", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!dualToneQSEnabled) return;

                try {
                    Object mScrimBehind = getObjectField(param.thisObject, "mScrimBehind");
                    boolean mBlankScreen = (boolean) getObjectField(param.thisObject, "mBlankScreen");
                    float alpha = getFloatField(mScrimBehind, "mViewAlpha");
                    boolean animateBehindScrim = alpha != 0 && !mBlankScreen;

                    callMethod(mScrimBehind, "setColors", mBehindColors, animateBehindScrim);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(ScrimControllerClass, "updateThemeColors", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                calculateColors();

                if (!dualToneQSEnabled) return;

                try {
                    @SuppressLint("DiscouragedApi") ColorStateList states = getColorAttr(mContext.getResources().getIdentifier("android:attr/colorSurfaceHeader", "attr", mContext.getPackageName()), mContext);

                    int surfaceBackground = states.getDefaultColor();

                    @SuppressLint("DiscouragedApi") ColorStateList accentStates = getColorAttr(mContext.getResources().getIdentifier("colorAccent", "attr", "android"), mContext);
                    int accent = accentStates.getDefaultColor();

                    callMethod(mBehindColors, "setMainColor", surfaceBackground);
                    callMethod(mBehindColors, "setSecondaryColor", accent);

                    double contrast = ColorUtils.calculateContrast((int) callMethod(mBehindColors, "getMainColor"), Color.WHITE);

                    callMethod(mBehindColors, "setSupportsDarkText", contrast > 4.5);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(ScrimControllerClass, "applyState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled) return;

                try {
                    boolean mClipsQsScrim = (boolean) getObjectField(param.thisObject, "mClipsQsScrim");
                    if (mClipsQsScrim) {
                        setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        try {
            Object[] constants = ScrimStateEnum.getEnumConstants();
            for (Object constant : constants) {
                String enumVal = constant.toString();
                switch (enumVal) {
                    case "KEYGUARD" ->
                            hookAllMethods(constant.getClass(), "prepare", new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    boolean mClipQsScrim = (boolean) getObjectField(param.thisObject, "mClipQsScrim");
                                    if (mClipQsScrim) {
                                        Object mScrimBehind = getObjectField(param.thisObject, "mScrimBehind");
                                        int mTintColor = getIntField(mScrimBehind, "mTintColor");
                                        if (mTintColor != Color.TRANSPARENT) {
                                            setObjectField(mScrimBehind, "mTintColor", Color.TRANSPARENT);
                                            callMethod(mScrimBehind, "updateColorWithTint", false);
                                        }

                                        callMethod(mScrimBehind, "setViewAlpha", 1f);
                                    }
                                }
                            });
                    case "BOUNCER" ->
                            hookAllMethods(constant.getClass(), "prepare", new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);
                                }
                            });
                    case "SHADE_LOCKED" -> {
                        hookAllMethods(constant.getClass(), "prepare", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (!lightQSHeaderEnabled) return;

                                setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);
                                boolean mClipQsScrim = (boolean) getObjectField(param.thisObject, "mClipQsScrim");
                                if (mClipQsScrim) {
                                    Object mScrimBehind = getObjectField(param.thisObject, "mScrimBehind");
                                    int mTintColor = getIntField(mScrimBehind, "mTintColor");
                                    if (mTintColor != Color.TRANSPARENT) {
                                        setObjectField(mScrimBehind, "mTintColor", Color.TRANSPARENT);
                                        callMethod(mScrimBehind, "updateColorWithTint", false);
                                    }

                                    callMethod(mScrimBehind, "setViewAlpha", 1f);
                                }
                            }
                        });
                        hookAllMethods(constant.getClass(), "getBehindTint", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (!lightQSHeaderEnabled) return;

                                param.setResult(Color.TRANSPARENT);
                            }
                        });
                    }
                    case "UNLOCKED" ->
                            hookAllMethods(constant.getClass(), "prepare", new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);

                                    Object mScrimBehind = getObjectField(param.thisObject, "mScrimBehind");
                                    int mTintColor = getIntField(mScrimBehind, "mTintColor");
                                    if (mTintColor != Color.TRANSPARENT) {
                                        setObjectField(mScrimBehind, "mTintColor", Color.TRANSPARENT);
                                        callMethod(mScrimBehind, "updateColorWithTint", false);
                                    }
                                    callMethod(mScrimBehind, "setViewAlpha", 1f);
                                }
                            });
                }
            }
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }

        hookAllConstructors(FragmentHostManagerClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    setObjectField(param.thisObject, "mConfigChanges", InterestingConfigChangesClass.getDeclaredConstructor(int.class).newInstance(0x40000000 | 0x0004 | 0x0100 | 0x80000000 | 0x0200));
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });
    }

    private void setLightQsA12(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (Build.VERSION.SDK_INT >= 33) return;

        Class<?> UtilsClass = findClass("com.android.settingslib.Utils", loadPackageParam.classLoader);
        Class<?> OngoingPrivacyChipClass = findClass(SYSTEMUI_PACKAGE + ".privacy.OngoingPrivacyChip", loadPackageParam.classLoader);
        Class<?> FragmentHostManagerClass = findClass(SYSTEMUI_PACKAGE + ".fragments.FragmentHostManager", loadPackageParam.classLoader);
        Class<?> ScrimControllerClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.ScrimController", loadPackageParam.classLoader);
        Class<?> GradientColorsClass = findClass("com.android.internal.colorextraction.ColorExtractor.GradientColors", loadPackageParam.classLoader);
        Class<?> StatusbarClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.StatusBar", loadPackageParam.classLoader);
        Class<?> InterestingConfigChangesClass = findClass("com.android.settingslib.applications.InterestingConfigChanges", loadPackageParam.classLoader);

        Method applyStateMethod = findMethodExactIfExists(ScrimControllerClass, "applyStateToAlpha");
        if (applyStateMethod == null) {
            applyStateMethod = findMethodExact(ScrimControllerClass, "applyState");
        }

        try {
            mBehindColors = GradientColorsClass.getDeclaredConstructor().newInstance();
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }
        hookAllMethods(ScrimControllerClass, "onUiModeChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    mBehindColors = GradientColorsClass.getDeclaredConstructor().newInstance();
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(ScrimControllerClass, "updateScrims", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!dualToneQSEnabled) return;

                try {
                    Object mScrimBehind = getObjectField(param.thisObject, "mScrimBehind");
                    boolean mBlankScreen = (boolean) getObjectField(param.thisObject, "mBlankScreen");
                    float alpha = (float) callMethod(mScrimBehind, "getViewAlpha");
                    boolean animateBehindScrim = alpha != 0 && !mBlankScreen;

                    callMethod(mScrimBehind, "setColors", mBehindColors, animateBehindScrim);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(ScrimControllerClass, "updateThemeColors", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!dualToneQSEnabled) return;

                try {
                    @SuppressLint("DiscouragedApi") ColorStateList states = (ColorStateList) callStaticMethod(UtilsClass, "getColorAttr", mContext, mContext.getResources().getIdentifier("android:attr/colorSurfaceHeader", "attr", mContext.getPackageName()));
                    int surfaceBackground = states.getDefaultColor();

                    ColorStateList accentStates = (ColorStateList) callStaticMethod(UtilsClass, "getColorAccent", mContext);
                    int accent = accentStates.getDefaultColor();

                    callMethod(mBehindColors, "setMainColor", surfaceBackground);
                    callMethod(mBehindColors, "setSecondaryColor", accent);

                    double contrast = ColorUtils.calculateContrast((int) callMethod(mBehindColors, "getMainColor"), Color.WHITE);

                    callMethod(mBehindColors, "setSupportsDarkText", contrast > 4.5);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });


        findAndHookMethod(OngoingPrivacyChipClass, "updateResources", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled) return;

                try {
                    Resources res = mContext.getResources();
                    @SuppressLint("DiscouragedApi") int iconColor = mContext.getColor(res.getIdentifier("android:color/system_neutral1_900", "color", mContext.getPackageName()));
                    setObjectField(param.thisObject, "iconColor", iconColor);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookMethod(applyStateMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled) return;

                try {
                    boolean mClipsQsScrim = (boolean) getObjectField(param.thisObject, "mClipsQsScrim");
                    if (mClipsQsScrim) {
                        setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        try {
            Class<?> ScrimStateEnum = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.ScrimState", loadPackageParam.classLoader);

            Object[] constants = ScrimStateEnum.getEnumConstants();
            for (Object constant : constants) {
                String enumVal = constant.toString();
                switch (enumVal) {
                    case "KEYGUARD" ->
                            findAndHookMethod(constant.getClass(), "prepare", ScrimStateEnum, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    boolean mClipQsScrim = (boolean) getObjectField(param.thisObject, "mClipQsScrim");
                                    if (mClipQsScrim) {
                                        callMethod(param.thisObject, "updateScrimColor", getObjectField(param.thisObject, "mScrimBehind"), 1f, Color.TRANSPARENT);
                                    }
                                }
                            });
                    case "BOUNCER" ->
                            findAndHookMethod(constant.getClass(), "prepare", ScrimStateEnum, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);
                                }
                            });
                    case "SHADE_LOCKED" -> {
                        hookAllMethods(constant.getClass(), "prepare", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (!lightQSHeaderEnabled) return;

                                setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);

                                boolean mClipQsScrim = (boolean) getObjectField(param.thisObject, "mClipQsScrim");
                                if (mClipQsScrim) {
                                    callMethod(param.thisObject, "updateScrimColor", getObjectField(param.thisObject, "mScrimBehind"), 1f, Color.TRANSPARENT);
                                }
                            }
                        });
                        hookAllMethods(constant.getClass(), "getBehindTint", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (!lightQSHeaderEnabled) return;
                                param.setResult(Color.TRANSPARENT);
                            }
                        });
                    }
                    case "UNLOCKED" ->
                            findAndHookMethod(constant.getClass(), "prepare", ScrimStateEnum, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (!lightQSHeaderEnabled) return;

                                    setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT);

                                    callMethod(param.thisObject, "updateScrimColor", getObjectField(param.thisObject, "mScrimBehind"), 1f, Color.TRANSPARENT);
                                }
                            });
                }
            }
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }

        hookAllConstructors(FragmentHostManagerClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!lightQSHeaderEnabled) return;

                try {
                    setObjectField(param.thisObject, "mConfigChanges", InterestingConfigChangesClass.getDeclaredConstructor(int.class).newInstance(0x40000000 | 0x0004 | 0x0100 | 0x80000000 | 0x0200));
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllConstructors(StatusbarClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    hookAllMethods(getObjectField(param.thisObject, "mOnColorsChangedListener").getClass(), "onColorsChanged", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            applyOverlays(true);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void applyOverlays(boolean force) {
        boolean isCurrentlyDark = SystemUtil.isDarkMode();

        if (isCurrentlyDark == isDark && !force) return;
        isDark = isCurrentlyDark;

        String QS_LIGHT_THEME_OVERLAY = "IconifyComponentQSLT.overlay";
        String QS_DUAL_TONE_OVERLAY = "IconifyComponentQSDT.overlay";

        calculateColors();

        Helpers.disableOverlays(QS_LIGHT_THEME_OVERLAY, QS_DUAL_TONE_OVERLAY);

        try {
            Thread.sleep(50);
        } catch (Throwable ignored) {
        }

        if (lightQSHeaderEnabled) {
            if (!isCurrentlyDark)
                Helpers.enableOverlay(QS_LIGHT_THEME_OVERLAY);

            if (dualToneQSEnabled)
                Helpers.enableOverlay(QS_DUAL_TONE_OVERLAY);
        }
    }

    @SuppressLint("DiscouragedApi")
    private void calculateColors() {
        if (!lightQSHeaderEnabled || Build.VERSION.SDK_INT < 33) return;

        try {
            if (unlockedScrimState != null) {
                setObjectField(unlockedScrimState, "mBehindTint", Color.TRANSPARENT);
            }

            if (!isDark) {
                Resources res = mContext.getResources();
                colorActive = mContext.getColor(android.R.color.system_accent1_600);
                colorInactive = res.getColor(res.getIdentifier("android:color/system_neutral1_10", "color", mContext.getPackageName()), mContext.getTheme());
            }
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }
    }

    @SuppressLint("DiscouragedApi")
    private void setHeaderComponentsColor(View mView, Object iconManager, Object batteryIcon) {
        if (!lightQSHeaderEnabled) return;

        int textColorPrimary = getColorAttrDefaultColor(android.R.attr.textColorPrimary, mContext);
        int textColorSecondary = getColorAttrDefaultColor(android.R.attr.textColorSecondary, mContext);

        try {
            ((TextView) mView.findViewById(mContext.getResources().getIdentifier("clock", "id", mContext.getPackageName()))).setTextColor(textColorPrimary);
            ((TextView) mView.findViewById(mContext.getResources().getIdentifier("date", "id", mContext.getPackageName()))).setTextColor(textColorPrimary);
        } catch (Throwable ignored) {
        }

        try {
            callMethod(iconManager, "setTint", textColorPrimary);

            for (int i = 1; i <= 3; i++) {
                String id = String.format("carrier%s", i);

                try {
                    ((TextView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mCarrierText")).setTextColor(textColorPrimary);
                    ((ImageView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mMobileSignal")).setImageTintList(ColorStateList.valueOf(textColorPrimary));
                    ((ImageView) getObjectField(mView.findViewById(mContext.getResources().getIdentifier(id, "id", mContext.getPackageName())), "mMobileRoaming")).setImageTintList(ColorStateList.valueOf(textColorPrimary));
                } catch (Throwable ignored) {
                }
            }

            callMethod(batteryIcon, "updateColors", textColorPrimary, textColorSecondary, textColorPrimary);
        } catch (Throwable throwable) {
            log(TAG + throwable);
        }
    }
}