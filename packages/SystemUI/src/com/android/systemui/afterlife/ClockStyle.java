package com.android.systemui.afterlife;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.tuner.TunerService;

import com.android.internal.util.infinity.InfinityUtils;

import com.android.systemui.charging.BatteryProgressImageView;
import com.android.systemui.volume.VolumeProgressImageView;
import com.android.systemui.util.MemoryProgressImageView;

public class ClockStyle extends RelativeLayout implements TunerService.Tunable {

    private static final int[] CLOCK_VIEW_IDS = {
            0,
            R.id.keyguard_clock_style_oos,
            R.id.keyguard_clock_style_ios,
            R.id.keyguard_clock_style_cos,
            R.id.keyguard_clock_style_custom,
            R.id.keyguard_clock_style_custom1,
            R.id.keyguard_clock_style_custom2,
            R.id.keyguard_clock_style_custom3,
            R.id.keyguard_clock_style_miui,
            R.id.keyguard_clock_style_ide,
            R.id.keyguard_clock_style_fluid,
            R.id.keyguard_clock_style_hyper,
            R.id.keyguard_clock_style_dual,
            R.id.keyguard_clock_style_stylish,
            R.id.keyguard_clock_style_sidebar,
            R.id.keyguard_clock_style_minimal,
            R.id.keyguard_clock_style_minimal2,
            R.id.keyguard_clock_style_minimal3
    };

    private static final int DEFAULT_STYLE = 0;
    private static final String CLOCK_STYLE_KEY = "clock_style";
    private static final String TOGGLE_LAYOUT_KEY = "toggle_layout_visibility";
    private static final String LOCKSCREEN_CLOCK_COLORED_KEY = "lockscreen_clock_colored";
    private static final String CLOCK_MARGIN_TOP_KEY = "clock_margin_top";
    private static final String CLOCK_LUMINANCE_KEY = "clock_luminance";

    private static final String CLOCK_STYLE = "system:" + CLOCK_STYLE_KEY;
    private static final String TOGGLE_LAYOUT = "system:" + TOGGLE_LAYOUT_KEY;
    private static final String LOCKSCREEN_CLOCK_COLORED = "system:" + LOCKSCREEN_CLOCK_COLORED_KEY;
    private static final String CLOCK_MARGIN_TOP = "system:" + CLOCK_MARGIN_TOP_KEY;
    private static final String CLOCK_LUMINANCE = "system:" + CLOCK_LUMINANCE_KEY;

    private Context mContext;
    private View[] clockViews;
    private int mClockStyle;

    private View toggleableLayout;
    private boolean isToggleableLayoutVisible;
    private boolean isLockscreenClockColored;
    private int mClockMarginTop;

    private static final long UPDATE_INTERVAL_MILLIS = 15 * 1000;
    private final Handler mHandler;
    private Runnable updateRunnable;
    private long lastUpdateTimeMillis = 0;
    
    private int mClockLuminance;

    private SharedPreferences sharedPreferences;

    public ClockStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE, TOGGLE_LAYOUT, LOCKSCREEN_CLOCK_COLORED, CLOCK_MARGIN_TOP, CLOCK_LUMINANCE);

        sharedPreferences = context.getSharedPreferences("ClockStylePreferences", Context.MODE_PRIVATE);
        isToggleableLayoutVisible = sharedPreferences.getBoolean(TOGGLE_LAYOUT_KEY, false);
        mClockMarginTop = sharedPreferences.getInt(CLOCK_MARGIN_TOP_KEY, 0);
        mClockLuminance = sharedPreferences.getInt(CLOCK_LUMINANCE_KEY, 80);

        updateRunnable = this::onTimeChanged;
    }
    
    private int adjustColorLuminance(int color, int luminancePercentage) {
        float[] hsl = new float[3];
        rgbToHSL(color, hsl);
        
        hsl[2] = luminancePercentage / 100.0f;

        return HSLToRGB(hsl);
    }

    private void rgbToHSL(int color, float[] hsl) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float lightness = (max + min) / 2.0f;

        float saturation;
        if (delta == 0) {
            saturation = 0;
        } else {
            saturation = delta / (1.0f - Math.abs(2.0f * lightness - 1.0f));
        }

        float hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6.0f;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0f;
        } else {
            hue = (r - g) / delta + 4.0f;
        }

        hue = (hue * 60.0f) % 360.0f;
        if (hue < 0) {
            hue += 360.0f;
        }

        hsl[0] = hue;
        hsl[1] = saturation;
        hsl[2] = lightness;
    }

    private int HSLToRGB(float[] hsl) {
        float hue = hsl[0];
        float saturation = hsl[1];
        float lightness = hsl[2];

        float c = (1.0f - Math.abs(2.0f * lightness - 1.0f)) * saturation;
        float x = c * (1.0f - Math.abs((hue / 60.0f) % 2.0f - 1.0f));
        float m = lightness - c / 2.0f;

        float r, g, b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        int red = (int) ((r + m) * 255);
        int green = (int) ((g + m) * 255);
        int blue = (int) ((b + m) * 255);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }    

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHandler.post(() -> {
            clockViews = new View[CLOCK_VIEW_IDS.length];
            for (int i = 0; i < CLOCK_VIEW_IDS.length; i++) {
                if (CLOCK_VIEW_IDS[i] != 0) {
                    clockViews[i] = findViewById(CLOCK_VIEW_IDS[i]);
                }
            }
            toggleableLayout = findViewById(R.id.toggleable_layout);
            updateClockView();
            toggleableLayout.setVisibility(isToggleableLayoutVisible ? View.VISIBLE : View.GONE);
        });
    }

    public void onTimeChanged() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastUpdateTimeMillis >= UPDATE_INTERVAL_MILLIS) {
            lastUpdateTimeMillis = currentTimeMillis;
            updateClockViews();
        }
    }

    private void updateClockViews() {
        if (clockViews != null) {
            for (View clockView : clockViews) {
                updateTextClockViews(clockView);
            }
        }
    }

    private void updateTextClockViews(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childView = viewGroup.getChildAt(i);
                updateTextClockViews(childView);
                if (childView instanceof TextClock) {
                    ((TextClock) childView).refreshTime();
                }
            }
        }
    }

    private void updateClockView() {
        if (clockViews != null) {
            mHandler.post(() -> {
                for (int i = 0; i < clockViews.length; i++) {
                    if (clockViews[i] != null) {
                        int visibility = (i == mClockStyle) ? View.VISIBLE : View.GONE;
                        if (clockViews[i].getVisibility() != visibility) {
                            clockViews[i].setVisibility(visibility);
                        }
                        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) clockViews[i].getLayoutParams();
                        layoutParams.topMargin = mClockMarginTop;
                        clockViews[i].setLayoutParams(layoutParams);
                    }
                }
                if (isLockscreenClockColored) {
                    int clockColor = getResources().getColor(android.R.color.system_accent1_500);
                    int luminanceAdjustedColor = adjustColorLuminance(clockColor, mClockLuminance);
                    setClockColor(luminanceAdjustedColor);                    
                } else {
                    // Do not set any color
                    // keep the default defined in XML
                }                
            });
        }
    }
    
    public void setClockColor(int color) {
        for (View clockView : clockViews) {
            if (clockView != null) {
                applyColorToViews(clockView, color);
            }
        }
    }   

    private void applyColorToViews(View view, int color) {
        if (view.getId() == R.id.weather_image ||
            view.getId() == R.id.bottom_bar ||
            view.getId() == R.id.lock_image ||
            view.getId() == R.id.user_layout ||
            view.getId() == R.id.volume_layout ||
            view.getId() == R.id.battery_layout ||
            view.getId() == R.id.bar_section ||
            view.getId() == R.id.timeCircle1 ||
            view.getId() == R.id.timeCircle2 ||
            view.getId() == R.id.timeCircle3) {
            return;
        }
        
        if (view instanceof TextClock && view.getId() == R.id.text_day) {
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childView = viewGroup.getChildAt(i);
                applyColorToViews(childView, color);
            }
        } else if (view instanceof TextClock) { 
            TextClock textClock = (TextClock) view;
            if (textClock.getCurrentTextColor() != color) {
                textClock.setTextColor(color);
            }
        } else if (view instanceof TextView) {
             ((TextView) view).setTextColor(color);
        } else if (view instanceof BatteryProgressImageView) {
            BatteryProgressImageView batterybarView = (BatteryProgressImageView) view;
            batterybarView.setImageTintList(ColorStateList.valueOf(color));
        } else if (view instanceof VolumeProgressImageView) {
            VolumeProgressImageView volumeView = (VolumeProgressImageView) view;
            volumeView.setImageTintList(ColorStateList.valueOf(color));
        } else if (view instanceof MemoryProgressImageView) {
            MemoryProgressImageView ramView = (MemoryProgressImageView) view;
            ramView.setImageTintList(ColorStateList.valueOf(color));
        } else if (view instanceof View) {
            view.setBackgroundColor(color);
        }
        updateCustomViewsColor(isLockscreenClockColored);
    }

    private void updateCustomViewsColor(boolean isColored) {
        int baseColor = isColored ? getResources().getColor(android.R.color.system_accent1_500) : getResources().getColor(android.R.color.white);
        int adjustedColor = isColored ? adjustColorLuminance(baseColor, mClockLuminance) : baseColor;
        
        View batterybarView = findViewById(R.id.battery_progressbar);
        if (batterybarView instanceof BatteryProgressImageView) {
            ((BatteryProgressImageView) batterybarView).setImageTintList(ColorStateList.valueOf(adjustedColor));
        }

        View volumeView = findViewById(R.id.volume_progress);
        if (volumeView instanceof VolumeProgressImageView) {
            ((VolumeProgressImageView) volumeView).setImageTintList(ColorStateList.valueOf(adjustedColor));
        }

        View ramView = findViewById(R.id.ram_usage_info);
        if (ramView instanceof MemoryProgressImageView) {
            ((MemoryProgressImageView) ramView).setImageTintList(ColorStateList.valueOf(adjustedColor));
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CLOCK_STYLE:
                mClockStyle = TunerService.parseInteger(newValue, 0);
                handlePackageStateChange(mClockStyle);
                updateClockView();

                View sliceView = findViewById(R.id.keyguard_slice_view);
                if (sliceView != null) {
                    sliceView.setVisibility(mClockStyle != 0 ? View.GONE : View.VISIBLE);
                }
                break;
            case TOGGLE_LAYOUT:
                isToggleableLayoutVisible = TunerService.parseIntegerSwitch(newValue, false);
                if (toggleableLayout != null) {
                    toggleableLayout.setVisibility(isToggleableLayoutVisible ? View.VISIBLE : View.GONE);
                }
                if (sharedPreferences != null) {
                    sharedPreferences.edit().putBoolean(TOGGLE_LAYOUT_KEY, isToggleableLayoutVisible).apply();
                }
                break;
            case LOCKSCREEN_CLOCK_COLORED:
                isLockscreenClockColored = TunerService.parseIntegerSwitch(newValue, false);
                updateClockView();
                break;
            case CLOCK_MARGIN_TOP:
                mClockMarginTop = TunerService.parseInteger(newValue, 0);
                if (sharedPreferences != null) {
                    sharedPreferences.edit().putInt(CLOCK_MARGIN_TOP_KEY, mClockMarginTop).apply();
                }
                updateClockView();
                break;
            case CLOCK_LUMINANCE:
    		mClockLuminance = TunerService.parseInteger(newValue, 80);
    		if (sharedPreferences != null) {
    		    sharedPreferences.edit().putInt(CLOCK_LUMINANCE_KEY, mClockLuminance).apply();
                }
                updateClockView();
                break;
            default:
                break;
        }
    }
    
    private void handlePackageStateChange(int clockStyle) {
        if (clockStyle != 0) {
            Settings.Secure.putIntForUser(mContext.getContentResolver(), 
                Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE, 0, UserHandle.USER_CURRENT);
            InfinityUtils.disablePackages(new String[]{
                    "com.android.systemui.clocks.bignum",
                    "com.android.systemui.clocks.calligraphy",
                    "com.android.systemui.clocks.flex",
                    "com.android.systemui.clocks.growth",
                    "com.android.systemui.clocks.handwritten",
                    "com.android.systemui.clocks.inflate",
                    "com.android.systemui.clocks.metro",
                    "com.android.systemui.clocks.numoverlap",
                    "com.android.systemui.clocks.weather"
            }, mContext);
        } else {
            InfinityUtils.enablePackages(new String[]{
                    "com.android.systemui.clocks.bignum",
                    "com.android.systemui.clocks.calligraphy",
                    "com.android.systemui.clocks.flex",
                    "com.android.systemui.clocks.growth",
                    "com.android.systemui.clocks.handwritten",
                    "com.android.systemui.clocks.inflate",
                    "com.android.systemui.clocks.metro",
                    "com.android.systemui.clocks.numoverlap",
                    "com.android.systemui.clocks.weather"
            }, mContext);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(updateRunnable);
        Dependency.get(TunerService.class).removeTunable(this);
    }
}
