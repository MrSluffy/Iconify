package com.drdisagree.iconify.xposed.mods;

import static com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE;
import static com.drdisagree.iconify.common.Preferences.FLUID_NOTIF_TRANSPARENCY;
import static com.drdisagree.iconify.common.Preferences.FLUID_POWERMENU_TRANSPARENCY;
import static com.drdisagree.iconify.common.Preferences.FLUID_QSPANEL;
import static com.drdisagree.iconify.config.XPrefs.Xprefs;
import static com.drdisagree.iconify.xposed.HookRes.modRes;
import static com.drdisagree.iconify.xposed.HookRes.resparams;
import static com.drdisagree.iconify.xposed.utils.SettingsLibUtils.getColorAttr;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.drdisagree.iconify.R;
import com.drdisagree.iconify.xposed.HookEntry;
import com.drdisagree.iconify.xposed.ModPack;
import com.drdisagree.iconify.xposed.utils.RoundedCornerProgressDrawable;
import com.drdisagree.iconify.xposed.utils.SystemUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedApi")
public class QSFluidTheme extends ModPack {

    public static final String listenPackage = SYSTEMUI_PACKAGE;
    private static final String TAG = "Iconify - " + QSFluidTheme.class.getSimpleName() + ": ";
    private static final int STATE_UNAVAILABLE = 0;
    private static final int STATE_INACTIVE = 1;
    private static final int STATE_ACTIVE = 2;
    private static final float ACTIVE_ALPHA = 0.2f;
    private static final float INACTIVE_ALPHA = ACTIVE_ALPHA + 0.2f;
    private static final float UNAVAILABLE_ALPHA = INACTIVE_ALPHA - 0.1f;
    private static boolean fluidQsThemeEnabled = false;
    private static boolean fluidNotifEnabled = false;
    private static boolean fluidPowerMenuEnabled = false;
    final Integer[] colorAccent = {mContext.getResources().getColor(mContext.getResources().getIdentifier("android:color/system_accent1_300", "color", listenPackage), mContext.getTheme())};
    final Integer[] colorActiveAlpha = {Color.argb((int) (ACTIVE_ALPHA * 255), Color.red(colorAccent[0]), Color.green(colorAccent[0]), Color.blue(colorAccent[0]))};
    final Integer[] colorInactiveAlpha = {null};
    private boolean wasDark = SystemUtil.isDarkMode();
    private XC_MethodHook.MethodHookParam QSTileViewImplParam = null;

    public QSFluidTheme(Context context) {
        super(context);
        if (!listensTo(context.getPackageName())) return;

        wasDark = SystemUtil.isDarkMode();
    }

    @SuppressWarnings("SameParameterValue")
    private static float dpToPx(Context context, int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        fluidQsThemeEnabled = Xprefs.getBoolean(FLUID_QSPANEL, false);
        fluidNotifEnabled = Xprefs.getBoolean(FLUID_NOTIF_TRANSPARENCY, false);
        fluidPowerMenuEnabled = Xprefs.getBoolean(FLUID_POWERMENU_TRANSPARENCY, false);

        initResources();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(listenPackage)) return;

        Class<?> QsPanelClass = findClass(SYSTEMUI_PACKAGE + ".qs.QSPanel", lpparam.classLoader);
        Class<?> QSTileViewImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.tileimpl.QSTileViewImpl", lpparam.classLoader);
        Class<?> QSIconViewImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.tileimpl.QSIconViewImpl", lpparam.classLoader);
        Class<?> FooterViewClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.notification.row.FooterView", lpparam.classLoader);
        Class<?> CentralSurfacesImplClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.phone.CentralSurfacesImpl", lpparam.classLoader);
        Class<?> NotificationExpandButtonClass = findClass("com.android.internal.widget.NotificationExpandButton", lpparam.classLoader);
        Class<?> BrightnessSliderViewClass = findClass(SYSTEMUI_PACKAGE + ".settings.brightness.BrightnessSliderView", lpparam.classLoader);
        Class<?> BrightnessControllerClass = findClass(SYSTEMUI_PACKAGE + ".settings.brightness.BrightnessController", lpparam.classLoader);
        Class<?> BrightnessMirrorControllerClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.policy.BrightnessMirrorController", lpparam.classLoader);
        Class<?> BrightnessSliderControllerClass = findClass(SYSTEMUI_PACKAGE + ".settings.brightness.BrightnessSliderController", lpparam.classLoader);
        Class<?> ActivatableNotificationViewClass = findClass(SYSTEMUI_PACKAGE + ".statusbar.notification.row.ActivatableNotificationView", lpparam.classLoader);

        // Initialize resources and colors
        hookAllMethods(QSTileViewImplClass, "init", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                initResources();
            }
        });

        hookAllConstructors(CentralSurfacesImplClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                initResources();
            }
        });

        hookAllMethods(CentralSurfacesImplClass, "updateTheme", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                initResources();
            }
        });

        // QS tile color
        hookAllMethods(QSTileViewImplClass, "getBackgroundColorForState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                try {
                    if ((int) param.args[0] == STATE_ACTIVE) {
                        param.setResult(changeAlpha(colorAccent[0], ACTIVE_ALPHA));
                    } else {
                        Integer colorInactive = (Integer) param.getResult();

                        if (colorInactive != null) {
                            colorInactiveAlpha[0] = changeAlpha(colorInactive, INACTIVE_ALPHA);

                            if ((int) param.args[0] == STATE_INACTIVE) {
                                param.setResult(changeAlpha(colorInactive, INACTIVE_ALPHA));
                            } else if ((int) param.args[0] == STATE_UNAVAILABLE) {
                                param.setResult(changeAlpha(colorInactive, UNAVAILABLE_ALPHA));
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        // QS icon color
        hookAllMethods(QSIconViewImplClass, "getIconColorForState", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    if ((int) getObjectField(param.args[1], "state") == STATE_ACTIVE) {
                        param.setResult(colorAccent[0]);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(QSIconViewImplClass, "updateIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    if (param.args[0] instanceof ImageView && getIntField(param.args[1], "state") == STATE_ACTIVE) {
                        ((ImageView) param.args[0]).setImageTintList(ColorStateList.valueOf(colorAccent[0]));
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(QSIconViewImplClass, "setIcon", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    if (param.args[0] instanceof ImageView && getIntField(param.args[1], "state") == STATE_ACTIVE) {
                        setObjectField(param.thisObject, "mTint", colorAccent[0]);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        try {
            Class<?> QSContainerImplClass = findClass(SYSTEMUI_PACKAGE + ".qs.QSContainerImpl", lpparam.classLoader);

            hookAllMethods(QSContainerImplClass, "updateResources", new XC_MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!fluidQsThemeEnabled) return;

                    try {
                        Resources res = mContext.getResources();
                        ViewGroup view = ((ViewGroup) param.thisObject).findViewById(res.getIdentifier("qs_footer_actions", "id", mContext.getPackageName()));

                        setAlphaForBackgroundDrawables(view, INACTIVE_ALPHA);

                        ViewGroup pm_button_container = view.findViewById(res.getIdentifier("pm_lite", "id", mContext.getPackageName()));
                        pm_button_container.getBackground().setAlpha((int) (ACTIVE_ALPHA * 255));
                        ((ImageView) pm_button_container.getChildAt(0)).setColorFilter(colorAccent[0], PorterDuff.Mode.SRC_IN);
                    } catch (Throwable throwable) {
                        log(TAG + throwable);
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // Brightness slider and auto brightness color
        hookAllMethods(BrightnessSliderViewClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    SeekBar mSlider = (SeekBar) getObjectField(param.thisObject, "mSlider");
                    mSlider.setProgressDrawable(createBrightnessBackgroundDrawable(mContext));

                    LayerDrawable progress = (LayerDrawable) mSlider.getProgressDrawable();
                    DrawableWrapper progressSlider = (DrawableWrapper) progress.findDrawableByLayerId(android.R.id.progress);

                    try {
                        LayerDrawable actualProgressSlider = (LayerDrawable) progressSlider.getDrawable();
                        Drawable mBrightnessIcon = actualProgressSlider.findDrawableByLayerId(mContext.getResources().getIdentifier("slider_icon", "id", mContext.getPackageName()));
                        mBrightnessIcon.setTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                        mBrightnessIcon.setAlpha(0);
                    } catch (Throwable ignored) {
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(BrightnessControllerClass, "updateIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setImageTintList(ColorStateList.valueOf(colorAccent[0]));
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setBackgroundTintList(ColorStateList.valueOf(colorActiveAlpha[0]));
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllConstructors(BrightnessSliderControllerClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setImageTintList(ColorStateList.valueOf(colorAccent[0]));
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setBackgroundTintList(ColorStateList.valueOf(colorActiveAlpha[0]));
                } catch (Throwable throwable) {
                    try {
                        ((ImageView) getObjectField(param.thisObject, "mIconView")).setImageTintList(ColorStateList.valueOf(colorAccent[0]));
                        ((ImageView) getObjectField(param.thisObject, "mIconView")).setBackgroundTintList(ColorStateList.valueOf(colorActiveAlpha[0]));
                    } catch (Throwable throwable1) {
                        log(TAG + throwable1);
                    }
                }
            }
        });

        hookAllMethods(BrightnessMirrorControllerClass, "updateIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setImageTintList(ColorStateList.valueOf(colorAccent[0]));
                    ((ImageView) getObjectField(param.thisObject, "mIcon")).setBackgroundTintList(ColorStateList.valueOf(colorActiveAlpha[0]));
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(BrightnessMirrorControllerClass, "updateResources", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    FrameLayout mBrightnessMirror = (FrameLayout) getObjectField(param.thisObject, "mBrightnessMirror");
                    mBrightnessMirror.getBackground().setAlpha((int) (INACTIVE_ALPHA * 255));
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(QsPanelClass, "updateResources", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled) return;

                try {
                    ((View) getObjectField(param.thisObject, "mAutoBrightnessView")).getBackground().setTint(colorActiveAlpha[0]);
                } catch (Throwable ignored) {
                }
            }
        });

        // QS tile primary label color
        hookAllMethods(QSTileViewImplClass, "getLabelColorForState", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                try {
                    if ((int) param.args[0] == STATE_ACTIVE) {
                        param.setResult(colorAccent[0]);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        // QS tile secondary label color
        hookAllMethods(QSTileViewImplClass, "getSecondaryLabelColorForState", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                try {
                    if ((int) param.args[0] == STATE_ACTIVE) {
                        param.setResult(colorAccent[0]);
                    }
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        // For LineageOS based roms
        hookAllConstructors(QSTileViewImplClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                try {
                    setObjectField(param.thisObject, "colorActive", changeAlpha(colorAccent[0], ACTIVE_ALPHA));
                    setObjectField(param.thisObject, "colorInactive", changeAlpha((Integer) getObjectField(param.thisObject, "colorInactive"), INACTIVE_ALPHA));
                    setObjectField(param.thisObject, "colorUnavailable", changeAlpha((Integer) getObjectField(param.thisObject, "colorInactive"), UNAVAILABLE_ALPHA));
                    setObjectField(param.thisObject, "colorLabelActive", colorAccent[0]);
                    setObjectField(param.thisObject, "colorSecondaryLabelActive", colorAccent[0]);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        hookAllMethods(QSTileViewImplClass, "updateResources", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                QSTileViewImplParam = param;

                if (!fluidQsThemeEnabled) return;

                initResources();

                try {
                    setObjectField(param.thisObject, "colorActive", changeAlpha(colorAccent[0], ACTIVE_ALPHA));
                    setObjectField(param.thisObject, "colorInactive", changeAlpha((Integer) getObjectField(param.thisObject, "colorInactive"), INACTIVE_ALPHA));
                    setObjectField(param.thisObject, "colorUnavailable", changeAlpha((Integer) getObjectField(param.thisObject, "colorInactive"), UNAVAILABLE_ALPHA));
                    setObjectField(param.thisObject, "colorLabelActive", colorAccent[0]);
                    setObjectField(param.thisObject, "colorSecondaryLabelActive", colorAccent[0]);
                } catch (Throwable throwable) {
                    log(TAG + throwable);
                }
            }
        });

        // Notifications
        hookAllMethods(ActivatableNotificationViewClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return;

                View mBackgroundNormal = (View) getObjectField(param.thisObject, "mBackgroundNormal");

                if (mBackgroundNormal != null) {
                    mBackgroundNormal.setAlpha(INACTIVE_ALPHA);
                }
            }
        });

        // Notification expand/collapse pill
        hookAllMethods(NotificationExpandButtonClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return;

                View mPillView = (View) getObjectField(param.thisObject, "mPillView");

                if (mPillView != null) {
                    mPillView.getBackground().setAlpha((int) (INACTIVE_ALPHA * 255));
                }
            }
        });

        // Notification footer buttons
        hookAllMethods(FooterViewClass, "updateColors", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return;

                Button mManageButton = (Button) getObjectField(param.thisObject, "mManageButton");
                Button mClearAllButton = (Button) getObjectField(param.thisObject, "mClearAllButton");

                if (mManageButton != null) {
                    mManageButton.getBackground().setAlpha((int) (INACTIVE_ALPHA * 255));
                }

                if (mClearAllButton != null) {
                    mClearAllButton.getBackground().setAlpha((int) (INACTIVE_ALPHA * 255));
                }
            }
        });
    }

    private void initColors() {
        colorAccent[0] = mContext.getResources().getColor(mContext.getResources().getIdentifier("android:color/system_accent1_300", "color", mContext.getPackageName()), mContext.getTheme());
        colorActiveAlpha[0] = Color.argb((int) (ACTIVE_ALPHA * 255), Color.red(colorAccent[0]), Color.green(colorAccent[0]), Color.blue(colorAccent[0]));
        if (QSTileViewImplParam != null) {
            colorInactiveAlpha[0] = changeAlpha((Integer) getObjectField(QSTileViewImplParam.thisObject, "colorInactive"), INACTIVE_ALPHA);
        } else {
            colorInactiveAlpha[0] = colorInactiveAlpha[0] == null ? (wasDark ? Color.parseColor("#0FFFFFFF") : Color.parseColor("#59FFFFFF")) : colorInactiveAlpha[0];
        }
    }

    private void initResources() {
        boolean isDark = SystemUtil.isDarkMode();

        if (isDark != wasDark) {
            wasDark = isDark;
        }

        initColors();

        // Replace drawables to match QS style
        XC_InitPackageResources.InitPackageResourcesParam ourResparam = resparams.get(SYSTEMUI_PACKAGE);

        if (ourResparam == null || !fluidQsThemeEnabled) return;

        int px2dp2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, mContext.getResources().getDisplayMetrics());
        int px2dp4 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, mContext.getResources().getDisplayMetrics());
        int notifCornerRadius = mContext.getResources().getDimensionPixelSize(mContext.getResources().getIdentifier("notification_corner_radius", "dimen", mContext.getPackageName()));

        try {
            ourResparam.res.setReplacement(mContext.getPackageName(), "drawable", "qs_footer_actions_background", new XResources.DrawableLoader() {
                @Override
                public Drawable newDrawable(XResources res, int id) {
                    return new ColorDrawable(Color.TRANSPARENT);
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            ourResparam.res.setReplacement(mContext.getPackageName(), "drawable", "qs_footer_action_chip_background", new XResources.DrawableLoader() {
                @Override
                public Drawable newDrawable(XResources res, int id) {
                    GradientDrawable gradientDrawable = new GradientDrawable();
                    gradientDrawable.setShape(GradientDrawable.RECTANGLE);
                    gradientDrawable.setColor(colorInactiveAlpha[0]);
                    gradientDrawable.setCornerRadius(notifCornerRadius);
                    return new InsetDrawable(gradientDrawable, 0, px2dp2, 0, px2dp2);
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            ourResparam.res.setReplacement(mContext.getPackageName(), "drawable", "qs_security_footer_background", new XResources.DrawableLoader() {
                @Override
                public Drawable newDrawable(XResources res, int id) {
                    GradientDrawable gradientDrawable = new GradientDrawable();
                    gradientDrawable.setShape(GradientDrawable.RECTANGLE);
                    gradientDrawable.setColor(changeAlpha(colorInactiveAlpha[0], 1f));
                    gradientDrawable.setCornerRadius(notifCornerRadius);
                    return new InsetDrawable(gradientDrawable, 0, px2dp4, 0, px2dp4);
                }
            });
        } catch (Throwable ignored) {
        }

        if (fluidPowerMenuEnabled) {
            try {
                int color = mContext.getResources().getColor(mContext.getResources().getIdentifier("global_actions_lite_background", "color", mContext.getPackageName()), mContext.getTheme());
                ourResparam.res.setReplacement(mContext.getPackageName(), "drawable", "global_actions_lite_background", new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
                        gradientDrawable.setColor(changeAlpha(color, INACTIVE_ALPHA));
                        gradientDrawable.setCornerRadius(notifCornerRadius);
                        return gradientDrawable;
                    }
                });
            } catch (Throwable ignored) {
            }

            try {
                int color = mContext.getResources().getColor(mContext.getResources().getIdentifier("global_actions_lite_button_background", "color", mContext.getPackageName()), mContext.getTheme());
                ourResparam.res.setReplacement(mContext.getPackageName(), "drawable", "global_actions_lite_button", new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        GradientDrawable gradientDrawable = new GradientDrawable();
                        gradientDrawable.setShape(GradientDrawable.OVAL);
                        gradientDrawable.setColor(changeAlpha(color, INACTIVE_ALPHA));
                        return gradientDrawable;
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private int changeAlpha(int color, float alpha) {
        return changeAlpha(color, (int) (alpha * 255));
    }

    private int changeAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(alpha, 255));

        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        return Color.argb(alpha, red, green, blue);
    }

    private LayerDrawable createBrightnessBackgroundDrawable(Context context) {
        Resources res = context.getResources();
        int cornerRadius = context.getResources().getDimensionPixelSize(res.getIdentifier("rounded_slider_corner_radius", "dimen", context.getPackageName()));
        int height = context.getResources().getDimensionPixelSize(res.getIdentifier("rounded_slider_height", "dimen", context.getPackageName()));
        int startPadding = (int) dpToPx(context, 15);
        int endPadding = (int) dpToPx(context, 15);

        // Create the background shape
        float[] radiusF = new float[8];
        for (int i = 0; i < 8; i++) {
            radiusF[i] = cornerRadius;
        }
        ShapeDrawable backgroundShape = new ShapeDrawable(new RoundRectShape(radiusF, null, null));
        backgroundShape.setIntrinsicHeight(height);

        @SuppressLint("DiscouragedApi") ColorStateList states = getColorAttr(mContext.getResources().getIdentifier("attr/offStateColor", "attr", mContext.getPackageName()), mContext);
        if (states != null) {
            backgroundShape.getPaint().setColor(changeAlpha(states.getDefaultColor(), INACTIVE_ALPHA));
        } else {
            backgroundShape.getPaint().setColor(colorInactiveAlpha[0]);
        }

        // Create the progress drawable
        RoundedCornerProgressDrawable progressDrawable = null;
        try {
            progressDrawable = new RoundedCornerProgressDrawable(AppCompatResources.getDrawable(context, res.getIdentifier("brightness_progress_full_drawable", "drawable", context.getPackageName())));
            progressDrawable.setAlpha((int) (ACTIVE_ALPHA * 255));
        } catch (Throwable ignored) {
        }

        // Create the start and end drawables
        Drawable startDrawable = ResourcesCompat.getDrawable(modRes, R.drawable.ic_brightness_low, context.getTheme());
        Drawable endDrawable = ResourcesCompat.getDrawable(modRes, R.drawable.ic_brightness_full, context.getTheme());
        if (startDrawable != null && endDrawable != null) {
            startDrawable.setTint(colorAccent[0]);
            endDrawable.setTint(colorAccent[0]);
        }

        // Create the layer drawable
        Drawable[] layers = {backgroundShape, progressDrawable, startDrawable, endDrawable};
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        layerDrawable.setId(0, android.R.id.background);
        layerDrawable.setId(1, android.R.id.progress);
        layerDrawable.setLayerGravity(2, Gravity.START | Gravity.CENTER_VERTICAL);
        layerDrawable.setLayerGravity(3, Gravity.END | Gravity.CENTER_VERTICAL);
        layerDrawable.setLayerInsetStart(2, startPadding);
        layerDrawable.setLayerInsetEnd(3, endPadding);

        return layerDrawable;
    }

    private void setAlphaForBackgroundDrawables(View view, float alpha) {
        setAlphaForBackgroundDrawables(view, (int) (alpha * 255));
    }

    private void setAlphaForBackgroundDrawables(View view, int alpha) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = viewGroup.getChildAt(i);
                setAlphaForBackgroundDrawablesRecursive(child, alpha);
            }
        }
    }

    private void setAlphaForBackgroundDrawablesRecursive(View view, int alpha) {
        Drawable backgroundDrawable = view.getBackground();
        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha(alpha);
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            int childCount = viewGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = viewGroup.getChildAt(i);
                setAlphaForBackgroundDrawablesRecursive(child, alpha);
            }
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName) && !HookEntry.isChildProcess;
    }
}