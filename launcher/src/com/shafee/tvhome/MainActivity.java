package com.shafee.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

/**
 * Apple TV style home screen: clock top-left, pills top-right, the focused dock app name over an
 * ambient glow, a dock of pinned apps, and every other app in a grid that scrolls up on Down.
 * Plain framework views only (no AndroidX), no network, no background work.
 */
public class MainActivity extends Activity {

    private static final String PREF_DOCK = "dock_apps";

    private final List<String> dockPkgs = new ArrayList<>();
    private final Set<String> hidden = new HashSet<>();
    private String liveTvPkg, inputPickerPkg;
    private int cols;

    static final class App {
        String pkg, label;
        ComponentName component;
        Drawable art;
        boolean isBanner;
        int color;
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();
    private final Map<String, App> allApps = new HashMap<>();

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private FrameLayout hero;
    private LinearLayout grid;
    private View glow;
    private ImageView wallBack, wallFront;
    private View gridShade;
    private static final String[] UNSPLASH_NATURE_IDS = {
        // --- Birds (all verified wide landscape) ---
        "photo-1618331835717-801e976710b2", // Bald eagle perched
        "photo-1549608276-5786777e6587", // Eagle in soaring flight
        "photo-1480044965905-02098d419e96", // Coastal bird over water
        "photo-1444464666168-49d633b86797", // Songbird on branch
        "photo-1522926193341-e9ffd686c60f", // Seabird soaring coast
        "photo-1555685812-4b943f1cb0eb", // Toucan in rainforest
        "photo-1520808663317-647b476a81b9", // Atlantic puffin on cliff
        "photo-1470114716159-e389f8712fda", // Kingfisher perched branch
        "photo-1516641396056-0ce60a85d49f", // Flamingos on lake sunrise

        // --- Wildlife (all verified wide landscape) ---
        "photo-1534567153574-2b12153a87f0", // Grizzly bear in river
        "photo-1564349683136-77e08dba1ef7", // Giant panda in bamboo forest
        "photo-1474511320723-9a56873867b5", // Red fox in autumn woods
        "photo-1504173010664-32509aeebb62", // Wolf in misty wilderness
        "photo-1589656966895-2f33e7653819", // Polar bear on Arctic ice
        "photo-1575550959106-5a7defe28b56", // Leopard on acacia branch
        "photo-1517825738774-7de9363ef735", // Brown bear cubs in river
        "photo-1548767797-d8c844163c4c", // Cheetah running savannah
        "photo-1437622368342-7a3d73a34c8f", // Sea turtle swimming coral reef

        // --- Mountains & Alpine (all verified wide landscape) ---
        "photo-1464822759023-fed622ff2c3b", // Mountain ridge sunset
        "photo-1519681393784-d120267933ba", // Starry snowy peaks
        "photo-1470770841072-f978cf4d019e", // Norwegian fjord mountain lake
        "photo-1426604966848-d7adac402bff", // Alpine lake reflection
        "photo-1497436072909-60f360e1d4b1", // Mountain lake mirror reflection
        "photo-1506744038136-46273834b3fb", // Yosemite Valley iconic river
        "photo-1507525428034-b723cf961d3e", // Ocean beach sunset
        "photo-1501785888041-af3ef285b470", // Coastal sunset vista

        // --- Forests, Rivers & Valleys (all verified wide landscape) ---
        "photo-1470071459604-3b5ec3a7fe05", // Misty pine forest landscape
        "photo-1441974231531-c6227db76b6e", // Sunlit woodland trees
        "photo-1447752875215-b2761acb3c5d", // Autumn river reflections
        "photo-1473448912268-2022ce9509d8", // Pine trees in morning mist
        "photo-1470240731273-7821a6eeb6bd", // Golden autumn forest path
        "photo-1418065460487-3e41a6c84dc5", // Redwood forest in mist
        "photo-1510784722466-f2aa9c52fff6", // Wildflower valley sunset
        "photo-1472214103451-9374bd1c798e", // Green rolling hills valley
        "photo-1469474968028-56623f02e42e", // Mountain pass and path
        "photo-1465146344425-f00d5f5c8f07", // Spring meadow blooms
        "photo-1491555103944-7c647fd857e6", // Ocean cliffs and breakers
        "photo-1469854523086-cc02fe5d8800"  // Valley road sunset
    };
    private int wallIndex = -1;
    private long wallChangedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateWallpaper = this::nextWallpaper;
    static final long WALLPAPER_INTERVAL_MS = 10 * 60 * 1000;
    private TextView greeting, clock, ampm, date, weather, title, wifiPill, appsLabel, dockCustomBtn;
    private int glowColor = 0xFF3A3A3A;
    private ValueAnimator glowAnim;
    private int lastFocusedId = View.NO_ID;
    private int lastDockFocusedId = View.NO_ID;
    private String lastDockFocusedPkg = null;
    private boolean firstShow = true;
    private int packageSeq;
    private PopupWindow inputsPopup, dockPopup;
    private long lastWeatherUpdate = 0;
    private static final long WEATHER_REFRESH_INTERVAL_MS = 20 * 60 * 1000;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateNetwork(); updateWeather(false); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildApps(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String[] defaults = getResources().getStringArray(R.array.dock_apps);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String saved = prefs.getString(PREF_DOCK, null);
        if (saved != null && !saved.isEmpty()) {
            for (String s : saved.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) dockPkgs.add(t);
            }
        } else {
            dockPkgs.addAll(Arrays.asList(defaults));
        }
        hidden.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        liveTvPkg = getString(R.string.live_tv_package);
        inputPickerPkg = getString(R.string.input_picker_package);
        cols = getResources().getInteger(R.integer.columns);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        buildApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        tf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(tick, tf);
        updateClock();
        updateNetwork();
        updateWeather(true);
        focusDock();
        if (getPackageManager().getChangedPackages(packageSeq) != null) buildApps();
        if (firstShow) { firstShow = false; playEntrance(); }
        if (wallIndex < 0 || SystemClock.elapsedRealtime() - wallChangedAt >= WALLPAPER_INTERVAL_MS) nextWallpaper();
        else scheduleWallpaper(WALLPAPER_INTERVAL_MS - (SystemClock.elapsedRealtime() - wallChangedAt));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            View current = getCurrentFocus();
            if (current == null || (!dockCards.contains(current) && !gridCards.contains(current))) {
                focusDock();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotateWallpaper);
        unregisterReceiver(tick);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (dockPopup != null) dockPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (dockPopup != null) dockPopup.dismiss();
        if (intent != null && "com.shafee.tvhome.ACTION_SHOW_TILE_MENU".equals(intent.getAction())) {
            if (!dockCards.isEmpty()) {
                View tile = dockCards.get(0);
                App app = (App) tile.getTag();
                handler.postDelayed(() -> showTileMenu(tile, app, true), 400);
                return;
            }
        }
        focusDock();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        View current = getCurrentFocus();
        boolean inDock = current != null && dockCards.contains(current);
        if (!inDock && !dockCards.isEmpty()) {
            focusDock();
            return;
        }
    }

    private void focusDock() {
        View target = null;
        if (lastDockFocusedPkg != null) {
            for (View c : dockCards) {
                if (c.getTag() instanceof App && lastDockFocusedPkg.equals(((App) c.getTag()).pkg)) {
                    target = c;
                    break;
                }
            }
        }
        if (target == null && lastDockFocusedId != View.NO_ID) {
            View v = findViewById(lastDockFocusedId);
            if (v != null && dockCards.contains(v)) target = v;
        }
        if (target == null && !dockCards.isEmpty()) {
            target = dockCards.get(0);
        }
        if (target != null) {
            final View fTarget = target;
            fTarget.requestFocus();
            fTarget.post(() -> {
                fTarget.requestFocus();
                for (View dc : dockCards) {
                    float targetAlpha = (dc == fTarget) ? 1.0f : 0.65f;
                    dc.animate().alpha(targetAlpha).setDuration(180).start();
                }
            });
            scroll.smoothScrollTo(0, 0);
        }
    }

    private void resetDockDimming() {
        if (dockCards == null) return;
        for (View dc : dockCards) {
            dc.animate().alpha(1f).setDuration(180).start();
        }
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        // Wallpaper: two stacked layers so a new photo can crossfade in over the old one.
        wallBack = wallpaperLayer();
        wallFront = wallpaperLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x40000000, 0x26000000, 0x8C000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0xB3000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));

        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        setGlow(glowColor);

        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        content.addView(hero, new LinearLayout.LayoutParams(-1, screenH));

        hero.addView(buildHeader(), lp(-1, -2, Gravity.TOP, dp(48), dp(36), dp(48), 0));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        hero.addView(bottom, lp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(30)));

        title = text(34, "sans-serif-medium", Color.WHITE);
        title.setShadowLayer(dp(18), 0, 0, 0x66000000);
        title.setSingleLine(true);
        bottom.addView(title, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(dp(8), 0, 0, dp(14));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        dockRow.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable band = new GradientDrawable();
        band.setColor(0x1FFFFFFF);
        band.setStroke(dp(1), 0x26FFFFFF);
        band.setCornerRadius(dp(5));
        dockRow.setBackground(band);
        bottom.addView(dockRow, new LinearLayout.LayoutParams(-1, -2));

        // Customize Dock button below the dock bar
        dockCustomBtn = addDockButton();
        LinearLayout.LayoutParams btnLp = llp(-2, -2, 0, dp(8), 0, 0);
        btnLp.gravity = Gravity.END;
        bottom.addView(dockCustomBtn, btnLp);

        appsLabel = text(11, "sans-serif-medium", 0x99FFFFFF);
        appsLabel.setText("ALL APPS");
        appsLabel.setLetterSpacing(0.22f);
        content.addView(appsLabel, llp(-2, -2, dp(68), dp(8), 0, dp(18)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(68), 0, dp(68), dp(160)));
        return root;
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);
        header.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(20), dp(10));

        GradientDrawable glass = new GradientDrawable();
        glass.setColor(0x3812141A);
        glass.setStroke(dp(1), 0x26FFFFFF);
        glass.setCornerRadius(dp(14));
        panel.setBackground(glass);

        // Apple SF-style Clock
        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.HORIZONTAL);
        clockCol.setGravity(Gravity.BOTTOM);

        clock = text(48, "sans-serif-medium", Color.WHITE);
        clock.setIncludeFontPadding(false);
        clock.setLetterSpacing(-0.02f);
        clockCol.addView(clock);

        ampm = text(11, "sans-serif-medium", 0x99FFFFFF);
        ampm.setLetterSpacing(0.04f);
        clockCol.addView(ampm, llp(-2, -2, dp(5), 0, 0, dp(5)));

        panel.addView(clockCol, new LinearLayout.LayoutParams(-2, -2));

        // Glass divider line
        View divider = new View(this);
        divider.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(36));
        divLp.setMargins(dp(16), 0, dp(16), 0);
        panel.addView(divider, divLp);

        // Details column: Greeting, Date, Weather
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);

        greeting = text(9, "sans-serif-medium", 0x88FFFFFF);
        greeting.setLetterSpacing(0.18f);
        greeting.setSingleLine(true);
        details.addView(greeting);

        date = text(12, "sans-serif-medium", 0xE6FFFFFF);
        date.setSingleLine(true);
        details.addView(date, llp(-2, -2, 0, dp(1), 0, 0));

        weather = text(11, "sans-serif", 0xB3FFFFFF);
        weather.setSingleLine(true);
        details.addView(weather, llp(-2, -2, 0, dp(2), 0, 0));

        for (TextView t : new TextView[]{greeting, clock, ampm, date, weather}) {
            t.setShadowLayer(dp(8), 0, dp(1), 0x66000000);
        }
        panel.addView(details, new LinearLayout.LayoutParams(-2, -2));
        header.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);

        // Inputs: chevron-down
        TextView inputs = pill("Inputs", 0, R.drawable.ic_chevron_down);
        inputs.setOnClickListener(v -> showInputs(v));

        // WiFi: signal bars
        wifiPill = pill("Wi-Fi", R.drawable.ic_wifi, 0);
        wifiPill.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        // Settings: gear
        TextView settings = pill("Settings", R.drawable.ic_settings, 0);
        settings.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));

        for (TextView p : new TextView[]{inputs, wifiPill, settings}) {
            right.addView(p, llp(-2, dp(30), dp(10), 0, 0, 0));
            pills.add(p);
        }
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return header;
    }

    private TextView pill(String label, int startIconRes, int endIconRes) {
        TextView p = text(11, "sans-serif-medium", 0xE6FFFFFF);
        p.setText(label);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(14), 0, dp(14), 0);
        p.setFocusable(true);
        p.setId(View.generateViewId());

        Drawable start = startIconRes != 0 ? getDrawable(startIconRes).mutate() : null;
        Drawable end = endIconRes != 0 ? getDrawable(endIconRes).mutate() : null;
        int iconSize = dp(14);
        if (start != null) {
            start.setBounds(0, 0, iconSize, iconSize);
            start.setTint(0xE6FFFFFF);
        }
        if (end != null) {
            end.setBounds(0, 0, iconSize, iconSize);
            end.setTint(0xE6FFFFFF);
        }
        p.setCompoundDrawables(start, null, end, null);
        p.setCompoundDrawablePadding(dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setStroke(dp(1), 0x33FFFFFF);
        bg.setCornerRadius(dp(4));
        p.setBackground(bg);
        p.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x66000000);
            int textColor = has ? 0xFF111111 : 0xE6FFFFFF;
            ((TextView) v).setTextColor(textColor);
            for (Drawable d : ((TextView) v).getCompoundDrawables()) {
                if (d != null) d.mutate().setTint(textColor);
            }
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(160).start();
            if (has) {
                lastFocusedId = v.getId();
                scroll.smoothScrollTo(0, 0);
                fadeGlow(1f);
                resetDockDimming();
            }
        });
        return p;
    }

    /** Small button below the dock to add/remove apps */
    private TextView addDockButton() {
        TextView btn = text(10, "sans-serif-medium", 0x80FFFFFF);
        btn.setText("\uFF0B  Customize Dock");
        btn.setLetterSpacing(0.12f);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(1), 0x26FFFFFF);
        bg.setCornerRadius(dp(4));
        btn.setBackground(bg);
        btn.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x26FFFFFF : Color.TRANSPARENT);
            btn.setTextColor(has ? 0xE6FFFFFF : 0x80FFFFFF);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(160).start();
            if (has) {
                lastFocusedId = v.getId();
                resetDockDimming();
            }
        });
        btn.setOnClickListener(v -> showAddToDock(v));
        return btn;
    }

    // ---------------------------------------------------------------- apps

    private void buildApps() {
        PackageManager pm = getPackageManager();
        android.content.pm.ChangedPackages changed = pm.getChangedPackages(packageSeq);
        if (changed != null) packageSeq = changed.getSequenceNumber();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        allApps.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (hidden.contains(ai.packageName) || allApps.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.component = new ComponentName(ai.packageName, ai.name);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            a.isBanner = banner != null;
            a.art = banner != null ? banner : ri.loadIcon(pm);
            a.color = sampleColor(a.pkg, a.art);
            allApps.put(a.pkg, a);
        }

        List<App> dock = new ArrayList<>();
        for (String p : dockPkgs) if (allApps.containsKey(p)) dock.add(allApps.get(p));

        Map<String, App> restMap = new HashMap<>(allApps);
        for (String p : dockPkgs) restMap.remove(p);
        List<App> rest = new ArrayList<>(restMap.values());
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));

        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        int initialDockIdx = 0;
        if (lastDockFocusedPkg != null) {
            for (int i = 0; i < dock.size(); i++) {
                if (lastDockFocusedPkg.equals(dock.get(i).pkg)) {
                    initialDockIdx = i;
                    break;
                }
            }
        }

        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i == 0 ? 0 : dp(10), 0, i == dock.size() - 1 ? 0 : dp(10), 0);
            dockRow.addView(tile, p);
        }

        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, llp(-1, -2, 0, 0, 0, dp(26)));
            }
            View tile = tile(rest.get(i), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % cols == 0 ? 0 : dp(10), 0, i % cols == cols - 1 ? 0 : dp(10), 0);
            row.addView(tile, p);
        }
        if (row != null) for (int i = rest.size() % cols; i != 0 && i < cols; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(10), 0, i == cols - 1 ? 0 : dp(10), 0);
            row.addView(new View(this), p);
        }
        appsLabel.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
        wireFocus();

        if (!dock.isEmpty()) {
            App initialApp = dock.get(initialDockIdx);
            title.setText(initialApp.label);
            setGlow(initialApp.color);
        }
        focusDock();
    }

    /** A 16:9 card with the app's banner; the name shows under it while focused. */
    private View tile(App app, boolean inDock) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(5));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(app.isBanner ? 0xFF1C1C1E : darken(app.color, 0.45f));
        bg.setCornerRadius(dp(5));
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            img.setPadding(dp(22), dp(14), dp(22), dp(14));
        }

        GradientDrawable ring = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40FFFFFF, 0x14FFFFFF});
        ring.setStroke(dp(2.5f), Color.WHITE);
        ring.setCornerRadius(dp(5));
        View shine = new View(this);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        final View pill;
        if (inDock) {
            pill = new View(this);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(Color.WHITE);
            pillBg.setCornerRadius(dp(1.5f));
            pill.setBackground(pillBg);
            pill.setAlpha(0f);
            pill.setScaleX(0.4f);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(dp(22), dp(3));
            pillLp.gravity = Gravity.CENTER_HORIZONTAL;
            pillLp.topMargin = dp(8);
            box.addView(pill, pillLp);
        } else {
            pill = null;
        }

        TextView label = text(11, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        if (!inDock) box.addView(label, llp(-1, -2, 0, dp(10), 0, 0));

        card.setOnClickListener(v -> launch(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));

        // Long-press opens dock management menu
        card.setOnLongClickListener(v -> {
            showTileMenu(v, app, inDock);
            return true;
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                showTileMenu(v, app, inDock);
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        event.startTracking();
                    } else if (event.isLongPress() || event.getRepeatCount() == 1) {
                        v.performLongClick();
                        return true;
                    }
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? (inDock ? 1.16f : 1.06f) : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(10) : 0)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(180).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            if (inDock) {
                if (pill != null) {
                    pill.animate().alpha(has ? 1f : 0f)
                            .scaleX(has ? 1f : 0.4f)
                            .translationY(has ? dp(4) : 0)
                            .setInterpolator(new DecelerateInterpolator())
                            .setDuration(180).start();
                }
                if (has) {
                    for (View dc : dockCards) {
                        float targetAlpha = (dc == v) ? 1.0f : 0.65f;
                        dc.animate().alpha(targetAlpha).setDuration(180).start();
                    }
                }
            } else {
                label.animate().alpha(has ? 1f : 0f).translationY(has ? dp(2) : 0).setDuration(150).start();
                if (has) {
                    resetDockDimming();
                }
            }
            if (!has) return;
            lastFocusedId = v.getId();
            if (inDock) {
                lastDockFocusedId = v.getId();
                lastDockFocusedPkg = app.pkg;
                title.setText(app.label);
                title.animate().alpha(1f).setDuration(130).start();
                animateGlow(app.color);
                fadeGlow(1f);
                scroll.smoothScrollTo(0, 0);
            } else {
                fadeGlow(0.35f);
                title.animate().alpha(0f).setDuration(130).start();
                scrollToRow(box);
            }
        });

        (inDock ? dockCards : gridCards).add(card);
        return box;
    }

    /** Explicit D-pad targets: one row per press, ends of rows and of the top bar don't wrap. */
    private void wireFocus() {
        int d = dockCards.size(), g = gridCards.size();
        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(d > 0 ? dockCards.get(Math.min(d - 1, d / 2)).getId() : (dockCustomBtn != null ? dockCustomBtn.getId() : p.getId()));
        }
        int topId = pills.isEmpty() ? View.NO_ID : pills.get(0).getId();
        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(topId != View.NO_ID ? topId : c.getId());
            c.setNextFocusLeftId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            if (i == d - 1) {
                c.setNextFocusRightId(dockCustomBtn != null ? dockCustomBtn.getId() : c.getId());
                c.setNextFocusDownId(dockCustomBtn != null ? dockCustomBtn.getId() : (g > 0 ? gridCards.get(Math.min(i, g - 1)).getId() : c.getId()));
            } else {
                c.setNextFocusRightId(dockCards.get(i + 1).getId());
                c.setNextFocusDownId(g > 0 ? gridCards.get(Math.min(i, g - 1)).getId() : (dockCustomBtn != null ? dockCustomBtn.getId() : c.getId()));
            }
        }
        if (dockCustomBtn != null) {
            View lastDock = d > 0 ? dockCards.get(d - 1) : null;
            dockCustomBtn.setNextFocusUpId(lastDock != null ? lastDock.getId() : (topId != View.NO_ID ? topId : dockCustomBtn.getId()));
            dockCustomBtn.setNextFocusLeftId(lastDock != null ? lastDock.getId() : dockCustomBtn.getId());
            dockCustomBtn.setNextFocusRightId(dockCustomBtn.getId());
            dockCustomBtn.setNextFocusDownId(g > 0 ? gridCards.get(Math.min(g - 1, cols - 1)).getId() : dockCustomBtn.getId());
        }
        int lastRow = g == 0 ? 0 : (g - 1) / cols;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / cols, col = j % cols;
            if (r == 0) {
                if (dockCustomBtn != null && (col == cols - 1 || j == g - 1)) {
                    c.setNextFocusUpId(dockCustomBtn.getId());
                } else if (d > 0) {
                    c.setNextFocusUpId(dockCards.get(Math.min(col, d - 1)).getId());
                } else if (dockCustomBtn != null) {
                    c.setNextFocusUpId(dockCustomBtn.getId());
                } else {
                    c.setNextFocusUpId(topId != View.NO_ID ? topId : c.getId());
                }
            } else {
                c.setNextFocusUpId(gridCards.get(j - cols).getId());
            }
            c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId()
                    : r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusLeftId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusRightId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    private void scrollToRow(View tileBox) {
        View row = (View) tileBox.getParent();
        int y = grid.getTop() + row.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        View band = (View) dockRow.getParent();
        int target = grid.indexOfChild(row) == 0
                ? hero.getTop() + band.getTop() + dockRow.getTop() - dp(22)
                : y - (int) (screenH * 0.42f);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(target, max)));
    }

    // ---------------------------------------------------------------- dock management

    /** Long-press context menu for dock management */
    private void showTileMenu(View anchor, App app, boolean inDock) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(5));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD0121318);
        bg.setStroke(dp(1), 0x33FFFFFF);
        bg.setCornerRadius(dp(5));
        card.setBackground(bg);

        // Header: App icon + Title + Subtitle
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(14));

        ImageView iconView = new ImageView(this);
        if (app != null && app.art != null) iconView.setImageDrawable(app.art);
        boolean isBanner = app != null && app.isBanner;
        iconView.setScaleType(isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        iconView.setClipToOutline(true);
        iconView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(4));
            }
        });
        int iconW = isBanner ? dp(48) : dp(32);
        int iconH = isBanner ? dp(27) : dp(32);
        head.addView(iconView, new LinearLayout.LayoutParams(iconW, iconH));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);

        TextView titleView = text(15, "sans-serif-medium", Color.WHITE);
        titleView.setText(app != null ? app.label : "");
        titleView.setSingleLine(true);
        titleCol.addView(titleView);

        TextView subView = text(10, "sans-serif-medium", 0x80FFFFFF);
        subView.setText(inDock ? "DOCK ACTIONS" : "APP OPTIONS");
        subView.setLetterSpacing(0.14f);
        titleCol.addView(subView, llp(-2, -2, 0, dp(2), 0, 0));

        head.addView(titleCol, new LinearLayout.LayoutParams(-1, -2));
        card.addView(head);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(0x26FFFFFF);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(0, dp(14), 0, 0);
        card.addView(options);

        PopupWindow pw = new PopupWindow(card, dp(340), -2, true);
        List<View> actionRows = new ArrayList<>();

        if (inDock && app != null) {
            int idx = dockPkgs.indexOf(app.pkg);
            if (idx > 0) {
                actionRows.add(actionButton("\u2190  Move Left", false, () -> {
                    dockPkgs.remove(idx);
                    dockPkgs.add(idx - 1, app.pkg);
                    saveDock();
                    buildApps();
                }));
            }
            if (idx >= 0 && idx < dockPkgs.size() - 1) {
                actionRows.add(actionButton("\u2192  Move Right", false, () -> {
                    dockPkgs.remove(idx);
                    dockPkgs.add(idx + 1, app.pkg);
                    saveDock();
                    buildApps();
                }));
            }
            actionRows.add(actionButton("\u2715  Remove from Dock", true, () -> {
                dockPkgs.remove(app.pkg);
                saveDock();
                buildApps();
                Toast.makeText(this, app.label + " removed from dock", Toast.LENGTH_SHORT).show();
            }));
        } else if (app != null) {
            actionRows.add(actionButton("\uFF0B  Add to Dock", false, () -> {
                if (!dockPkgs.contains(app.pkg)) {
                    dockPkgs.add(app.pkg);
                    saveDock();
                    buildApps();
                    Toast.makeText(this, app.label + " added to dock", Toast.LENGTH_SHORT).show();
                }
            }));
        }

        for (int i = 0; i < actionRows.size(); i++) {
            View r = actionRows.get(i);
            options.addView(r, llp(-1, dp(42), 0, i == 0 ? 0 : dp(8), 0, 0));
            r.setOnClickListener(v -> {
                pw.dismiss();
                ((Runnable) v.getTag()).run();
            });
        }

        for (int i = 0; i < actionRows.size(); i++) {
            View r = actionRows.get(i);
            r.setNextFocusUpId(actionRows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(actionRows.get(Math.min(actionRows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }

        card.setElevation(dp(20));

        final String anchorPkg = app != null ? app.pkg : null;
        final View openingAnchor = anchor;

        pw.setFocusable(true);
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setElevation(0);
        pw.setOnDismissListener(() -> {
            if (inDock && anchorPkg != null) {
                for (View c : dockCards) {
                    if (c.getTag() instanceof App && anchorPkg.equals(((App) c.getTag()).pkg)) {
                        c.requestFocus();
                        return;
                    }
                }
            }
            if (openingAnchor != null && openingAnchor.isAttachedToWindow()) {
                openingAnchor.requestFocus();
            } else if (!dockCards.isEmpty()) {
                dockCards.get(0).requestFocus();
            }
        });
        pw.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        dimBehind(pw, 0.48f);
        card.post(() -> { if (!actionRows.isEmpty()) actionRows.get(0).requestFocus(); });
    }

    private View actionButton(String label, boolean isDestructive, Runnable action) {
        int normalTextColor = isDestructive ? 0xCCFF9999 : Color.WHITE;
        TextView btn = text(13, "sans-serif-medium", normalTextColor);
        btn.setText(label);
        btn.setTag(action);
        btn.setId(View.generateViewId());
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(dp(16), 0, dp(16), 0);
        btn.setFocusable(true);
        btn.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isDestructive ? 0x1AFF5555 : 0x1AFFFFFF);
        bg.setStroke(dp(1), isDestructive ? 0x26FF5555 : 0x1FFFFFFF);
        bg.setCornerRadius(dp(4));
        btn.setBackground(bg);

        btn.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? (isDestructive ? 0xDDFF4B4B : 0xF2FFFFFF) : (isDestructive ? 0x1AFF5555 : 0x1AFFFFFF));
            bg.setStroke(dp(1), has ? 0xFFFFFFFF : (isDestructive ? 0x26FF5555 : 0x1FFFFFFF));
            btn.setTextColor(has ? (isDestructive ? Color.WHITE : 0xFF111111) : normalTextColor);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(160).start();
        });
        return btn;
    }

    /** Popup to customize the dock: add apps, remove apps, or reset to defaults */
    private void showAddToDock(View anchor) {
        final View openingAnchor = anchor != null ? anchor : dockCustomBtn;
        List<App> available = new ArrayList<>();
        for (Map.Entry<String, App> e : allApps.entrySet()) {
            if (!dockPkgs.contains(e.getKey())) available.add(e.getValue());
        }
        Collections.sort(available, (x, y) -> x.label.compareToIgnoreCase(y.label));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(5));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD0121318);
        bg.setStroke(dp(1), 0x33FFFFFF);
        bg.setCornerRadius(dp(5));
        card.setBackground(bg);
        card.setElevation(dp(20));

        // Header
        TextView header = text(16, "sans-serif-medium", Color.WHITE);
        header.setText("Customize Dock");
        card.addView(header);

        TextView sub = text(11, "sans-serif", 0x88FFFFFF);
        sub.setText("Manage pinned apps displayed on your home dock");
        card.addView(sub, llp(-2, -2, 0, dp(2), 0, dp(14)));

        View divider = new View(this);
        divider.setBackgroundColor(0x26FFFFFF);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, dp(10));

        List<View> rows = new ArrayList<>();

        if (!dockPkgs.isEmpty()) {
            TextView removeSection = text(10, "sans-serif-medium", 0x80FFFFFF);
            removeSection.setText("PINNED APPS (" + dockPkgs.size() + ")");
            removeSection.setLetterSpacing(0.16f);
            removeSection.setPadding(dp(4), dp(8), dp(4), dp(6));
            list.addView(removeSection);

            for (String pkg : new ArrayList<>(dockPkgs)) {
                App app = allApps.get(pkg);
                if (app == null) {
                    app = new App();
                    app.pkg = pkg;
                    app.label = pkg;
                }
                final String finalPkg = pkg;
                final String finalLabel = app.label;
                View r = dockItemRow(app, true, () -> {
                    dockPkgs.remove(finalPkg);
                    saveDock();
                    buildApps();
                    Toast.makeText(this, finalLabel + " removed from dock", Toast.LENGTH_SHORT).show();
                    if (dockPopup != null) dockPopup.dismiss();
                    showAddToDock(openingAnchor);
                });
                rows.add(r);
                list.addView(r, llp(-1, dp(48), 0, 0, 0, dp(6)));
            }
        }

        if (!available.isEmpty()) {
            TextView addSection = text(10, "sans-serif-medium", 0x80FFFFFF);
            addSection.setText("AVAILABLE APPS (" + available.size() + ")");
            addSection.setLetterSpacing(0.16f);
            addSection.setPadding(dp(4), dp(12), dp(4), dp(6));
            list.addView(addSection);

            for (App app : available) {
                View r = dockItemRow(app, false, () -> {
                    dockPkgs.add(app.pkg);
                    saveDock();
                    buildApps();
                    Toast.makeText(this, app.label + " added to dock", Toast.LENGTH_SHORT).show();
                    if (dockPopup != null) dockPopup.dismiss();
                    showAddToDock(openingAnchor);
                });
                rows.add(r);
                list.addView(r, llp(-1, dp(48), 0, 0, 0, dp(6)));
            }
        }

        TextView resetSection = text(10, "sans-serif-medium", 0x80FFFFFF);
        resetSection.setText("DEFAULTS");
        resetSection.setLetterSpacing(0.16f);
        resetSection.setPadding(dp(4), dp(12), dp(4), dp(6));
        list.addView(resetSection);

        View resetRow = actionButton("\u21BA  Reset to Default Dock", false, () -> {
            dockPkgs.clear();
            dockPkgs.addAll(Arrays.asList(getResources().getStringArray(R.array.dock_apps)));
            saveDock();
            buildApps();
            Toast.makeText(this, "Dock reset to defaults", Toast.LENGTH_SHORT).show();
            if (dockPopup != null) dockPopup.dismiss();
            showAddToDock(openingAnchor);
        });
        rows.add(resetRow);
        list.addView(resetRow, llp(-1, dp(42), 0, 0, 0, dp(4)));

        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        sv.addView(list);
        int maxScrollH = Math.min(dp(440), (int) (getResources().getDisplayMetrics().heightPixels * 0.52f));
        card.addView(sv, new LinearLayout.LayoutParams(-1, maxScrollH));

        PopupWindow pw = new PopupWindow(card, dp(500), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setFocusable(true);
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setElevation(0);
        pw.setOnDismissListener(() -> {
            dockPopup = null;
            if (openingAnchor != null && openingAnchor.isAttachedToWindow()) {
                openingAnchor.requestFocus();
            } else if (dockCustomBtn != null && dockCustomBtn.isAttachedToWindow()) {
                dockCustomBtn.requestFocus();
            }
        });
        dockPopup = pw;
        pw.showAtLocation(openingAnchor != null ? openingAnchor : card, Gravity.CENTER, 0, 0);
        dimBehind(pw, 0.48f);
        card.post(() -> { if (!rows.isEmpty()) rows.get(0).requestFocus(); });
    }

    private void dimBehind(PopupWindow pw, float amount) {
        try {
            View root = pw.getContentView().getRootView();
            if (root == null) return;
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            if (p == null) return;
            p.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            p.dimAmount = amount;
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.updateViewLayout(root, p);
        } catch (Throwable ignored) { }
    }

    private View dockItemRow(App app, boolean isRemove, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        row.setFocusable(true);
        row.setClickable(true);
        row.setId(View.generateViewId());
        row.setTag(action);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14FFFFFF);
        bg.setStroke(dp(1), 0x1FFFFFFF);
        bg.setCornerRadius(dp(4));
        row.setBackground(bg);

        // App Icon / Thumbnail
        ImageView icon = new ImageView(this);
        if (app != null && app.art != null) {
            icon.setImageDrawable(app.art);
        }
        boolean isBanner = app != null && app.isBanner;
        icon.setScaleType(isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(4));
            }
        });
        int iconW = isBanner ? dp(44) : dp(28);
        int iconH = isBanner ? dp(26) : dp(28);
        row.addView(icon, new LinearLayout.LayoutParams(iconW, iconH));

        // App Label
        TextView label = text(13, "sans-serif-medium", Color.WHITE);
        label.setText(app != null ? app.label : "");
        label.setSingleLine(true);
        label.setPadding(dp(12), 0, dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        // Action badge on the right (understated design)
        int badgeTextColor = isRemove ? 0xCCFF9999 : 0xCCA6E3A1;
        TextView badge = text(11, "sans-serif-medium", badgeTextColor);
        badge.setText(isRemove ? "Remove" : "+ Add");
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(isRemove ? 0x1AFF5555 : 0x1A33DD66);
        badgeBg.setStroke(dp(1), isRemove ? 0x26FF5555 : 0x2633DD66);
        badgeBg.setCornerRadius(dp(4));
        badge.setBackground(badgeBg);
        row.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x14FFFFFF);
            bg.setStroke(dp(1), has ? 0xFFFFFFFF : 0x1FFFFFFF);
            label.setTextColor(has ? 0xFF111111 : Color.WHITE);
            badge.setTextColor(has ? 0xFF111111 : badgeTextColor);
            badgeBg.setColor(has ? 0x26000000 : (isRemove ? 0x1AFF5555 : 0x1A33DD66));
            badgeBg.setStroke(dp(1), has ? 0x33000000 : (isRemove ? 0x26FF5555 : 0x2633DD66));
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(160).start();
        });

        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void saveDock() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dockPkgs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(dockPkgs.get(i));
        }
        getPreferences(MODE_PRIVATE).edit().putString(PREF_DOCK, sb.toString()).apply();
    }

    private TextView menuRow(String label, Runnable action) {
        TextView r = text(13, "sans-serif", Color.WHITE);
        r.setText(label);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(4));
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : Color.TRANSPARENT);
            r.setTextColor(has ? 0xFF111111 : Color.WHITE);
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(160).start();
        });
        return r;
    }

    // ---------------------------------------------------------------- inputs

    private void showInputs(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.setClipToOutline(true);
        list.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(5));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD0121318);
        bg.setStroke(dp(1), 0x33FFFFFF);
        bg.setCornerRadius(dp(5));
        list.setBackground(bg);
        list.setElevation(dp(16));

        PopupWindow pw = new PopupWindow(list, dp(200), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
            if (t.isPassthroughInput() && !t.isHidden(this)) inputs.add(t);
        }
        Collections.sort(inputs, (x, y) -> inputLabel(x).compareToIgnoreCase(inputLabel(y)));
        for (TvInputInfo t : inputs) {
            rows.add(menuRow(inputLabel(t), () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!launch(i)) openPackage(inputPickerPkg);
            }));
        }
        if (isInstalled(liveTvPkg)) rows.add(menuRow("Live TV", () -> openPackage(liveTvPkg)));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg)) rows.add(menuRow("All sources\u2026", () -> openPackage(inputPickerPkg)));
        if (rows.isEmpty()) rows.add(menuRow("Settings", () -> launch(new Intent(Settings.ACTION_SETTINGS))));

        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) r.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setElevation(0);
        pw.setOnDismissListener(() -> {
            inputsPopup = null;
            if (anchor != null && anchor.isAttachedToWindow()) anchor.requestFocus();
        });
        inputsPopup = pw;
        pw.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "GOOD NIGHT" : h < 12 ? "GOOD MORNING" : h < 17 ? "GOOD AFTERNOON" : "GOOD EVENING");
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.getTime()));
    }

    private void setPillStartIcon(TextView p, int iconRes) {
        Drawable start = iconRes != 0 ? getDrawable(iconRes).mutate() : null;
        if (start != null) {
            int iconSize = dp(14);
            start.setBounds(0, 0, iconSize, iconSize);
            int textColor = p.hasFocus() ? 0xFF111111 : 0xE6FFFFFF;
            start.setTint(textColor);
        }
        Drawable[] d = p.getCompoundDrawables();
        p.setCompoundDrawables(start, d[1], d[2], d[3]);
    }

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        String label = "Offline";
        int iconRes = R.drawable.ic_globe_off;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            label = "Ethernet";
                            iconRes = R.drawable.ic_ethernet;
                        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            iconRes = R.drawable.ic_wifi;
                            label = "Wi-Fi";
                            try {
                                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                                if (wm != null && wm.getConnectionInfo() != null) {
                                    String s = wm.getConnectionInfo().getSSID();
                                    if (s != null && !s.contains("unknown ssid")) label = s.replace("\"", "");
                                }
                            } catch (Throwable ignored) { }
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
        wifiPill.setText(label);
        setPillStartIcon(wifiPill, iconRes);
    }

    // ---------------------------------------------------------------- glow

    private void setGlow(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.5f, 0.78f);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        g.setColors(new int[]{withAlpha(color, 0.5f), withAlpha(color, 0.14f), Color.TRANSPARENT});
        glow.setBackground(g);
    }

    private void animateGlow(int to) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void fadeGlow(float alpha) {
        glow.animate().alpha(alpha).setDuration(300).start();
        gridShade.animate().alpha(alpha < 1f ? 1f : 0f).setDuration(300).start();
    }

    // ---------------------------------------------------------------- wallpaper

    private ImageView wallpaperLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private int[] getTargetWallpaperResolution() {
        int w = 1920;
        int h = 1080;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                DisplayMetrics realDm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(realDm);
                if (realDm.widthPixels > 0 && realDm.heightPixels > 0) {
                    w = realDm.widthPixels;
                    h = realDm.heightPixels;
                }
            } else {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    w = dm.widthPixels;
                    h = dm.heightPixels;
                }
            }
        } catch (Throwable ignored) {}
        // Guarantee at least 1080p full HD so lower-density TV panels never get downscaled images
        if (w < 1920) {
            float aspect = (float) w / Math.max(1, h);
            w = 1920;
            h = Math.round(1920f / (aspect > 0 ? aspect : (16f / 9f)));
        }
        return new int[]{w, h};
    }

    /** Download or load a random nature photo from Unsplash off the main thread, then crossfade it in. */
    private void nextWallpaper() {
        handler.removeCallbacks(rotateWallpaper);
        wallChangedAt = SystemClock.elapsedRealtime();

        int nextIdx = new Random().nextInt(UNSPLASH_NATURE_IDS.length);
        if (nextIdx == wallIndex && UNSPLASH_NATURE_IDS.length > 1) {
            nextIdx = (nextIdx + 1) % UNSPLASH_NATURE_IDS.length;
        }
        wallIndex = nextIdx;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", wallIndex).apply();

        final String photoId = UNSPLASH_NATURE_IDS[wallIndex];
        int[] res = getTargetWallpaperResolution();
        final String photoUrl = "https://images.unsplash.com/" + photoId + "?w=" + res[0] + "&h=" + res[1] + "&fit=crop&q=80";

        new Thread(() -> {
            File cacheFile = new File(getCacheDir(), "wallpaper_cache.jpg");
            Bitmap bm = downloadBitmap(photoUrl, cacheFile);

            if (bm == null && cacheFile.exists()) {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    bm = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), o);
                    android.util.Log.i("TVHome", "Loaded fallback wallpaper from disk cache");
                } catch (Throwable t) {
                    android.util.Log.e("TVHome", "Failed to decode cached wallpaper: " + t.getMessage());
                }
            }

            final Bitmap result = bm;
            handler.post(() -> showWallpaper(result));
        }).start();

        scheduleWallpaper(WALLPAPER_INTERVAL_MS);
    }

    private static Bitmap downloadBitmap(String urlStr, File cacheFile) {
        try {
            android.util.Log.i("TVHome", "Downloading wallpaper from " + urlStr);
            URL u = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                android.util.Log.e("TVHome", "Wallpaper HTTP " + code + " for " + urlStr);
                return null;
            }
            byte[] data;
            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                data = out.toByteArray();
            }
            android.util.Log.i("TVHome", "Wallpaper downloaded: " + (data != null ? data.length : 0) + " bytes");
            if (data == null || data.length == 0) return null;

            if (cacheFile != null) {
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(data);
                    fos.flush();
                } catch (Throwable t) {
                    android.util.Log.w("TVHome", "Failed to write wallpaper cache: " + t.getMessage());
                }
            }

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            android.util.Log.i("TVHome", "Wallpaper decoded: " + (bm != null ? (bm.getWidth() + "x" + bm.getHeight()) : "null"));
            return bm;
        } catch (Throwable e) {
            android.util.Log.e("TVHome", "downloadBitmap error: " + e.getMessage(), e);
            return null;
        }
    }

    private static String httpGet(String urlStr) {
        try {
            URL u = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            int code = conn.getResponseCode();
            if (code != 200) {
                android.util.Log.e("TVHome", "httpGet HTTP " + code + " for " + urlStr);
                return null;
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 8192);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Throwable e) {
            android.util.Log.e("TVHome", "httpGet error for " + urlStr + ": " + e.getMessage(), e);
            return null;
        }
    }

    private void updateWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        android.util.Log.i("TVHome", "updateWeather called with force=" + force + ", last=" + lastWeatherUpdate + ", now=" + now);
        if (!force && now - lastWeatherUpdate < WEATHER_REFRESH_INTERVAL_MS) {
            android.util.Log.i("TVHome", "updateWeather skipped due to rate limit");
            return;
        }
        lastWeatherUpdate = now;

        new Thread(() -> {
            try {
                android.util.Log.i("TVHome", "Updating weather...");
                double lat = Double.NaN;
                double lon = Double.NaN;
                String city = "";

                // 1. Try GPS / Network location
                try {
                    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                    if (lm != null) {
                        for (String p : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
                            try {
                                if (lm.isProviderEnabled(p)) {
                                    Location loc = lm.getLastKnownLocation(p);
                                    if (loc != null) {
                                        lat = loc.getLatitude();
                                        lon = loc.getLongitude();
                                        android.util.Log.i("TVHome", "Location from " + p + ": " + lat + ", " + lon);
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) { }
                        }
                    }
                } catch (Throwable ignored) { }

                // 2. Fallback to device timezone city via Open-Meteo Geocoding
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    String tzId = TimeZone.getDefault().getID();
                    android.util.Log.i("TVHome", "Timezone ID: " + tzId);
                    if (tzId != null && tzId.contains("/")) {
                        city = tzId.substring(tzId.lastIndexOf('/') + 1).replace('_', ' ');
                    } else if (tzId != null) {
                        city = tzId;
                    }
                    if (!city.isEmpty()) {
                        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                                + URLEncoder.encode(city, "UTF-8") + "&count=1";
                        android.util.Log.i("TVHome", "Geocoding: " + geoUrl);
                        String geoJson = httpGet(geoUrl);
                        if (geoJson != null) {
                            JSONObject obj = new JSONObject(geoJson);
                            JSONArray results = obj.optJSONArray("results");
                            if (results != null && results.length() > 0) {
                                JSONObject first = results.getJSONObject(0);
                                lat = first.optDouble("latitude", Double.NaN);
                                lon = first.optDouble("longitude", Double.NaN);
                                if (first.has("name")) city = first.getString("name");
                                android.util.Log.i("TVHome", "Resolved city: " + city + " (" + lat + ", " + lon + ")");
                            }
                        }
                    }
                }

                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    android.util.Log.e("TVHome", "Failed to resolve lat/lon for weather");
                    return;
                }

                // 3. Fetch current weather from Open-Meteo
                String weatherUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code",
                        lat, lon);
                android.util.Log.i("TVHome", "Weather URL: " + weatherUrl);
                String weatherJson = httpGet(weatherUrl);
                if (weatherJson == null) {
                    android.util.Log.e("TVHome", "Weather JSON response is null");
                    return;
                }

                JSONObject root = new JSONObject(weatherJson);
                JSONObject current = root.getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");

                String condition = weatherCodeToString(code);
                String label = Math.round(temp) + "\u00B0C  \u2022  " + condition + (city.isEmpty() ? "" : "  \u2022  " + city);
                android.util.Log.i("TVHome", "Weather label: " + label);

                handler.post(() -> {
                    if (weather != null) {
                        weather.setText(label);
                        weather.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Throwable t) {
                android.util.Log.e("TVHome", "updateWeather exception: " + t.getMessage(), t);
            }
        }).start();
    }

    private static String weatherCodeToString(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 71: case 73: case 75: return "Snow";
            case 80: case 81: case 82: return "Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Clear";
        }
    }

    private boolean firstWallpaper = true;

    private void showWallpaper(Bitmap bm) {
        if (bm == null || isFinishing() || isDestroyed()) return;
        wallBack.animate().cancel();
        wallFront.animate().cancel();

        // Load new image into wallBack (the hidden layer).
        wallBack.setImageBitmap(bm);
        wallBack.setAlpha(0f);

        // Bring wallBack to the front in z-order so it fades in on top of wallFront.
        ViewGroup root = (ViewGroup) wallBack.getParent();
        if (root != null) {
            root.removeView(wallBack);
            root.addView(wallBack, 1, new FrameLayout.LayoutParams(-1, -1));
        }

        long dur = firstWallpaper ? 800L : 2000L;
        firstWallpaper = false;

        wallBack.animate().alpha(1f).setDuration(dur).withEndAction(() -> {
            // Swap roles: old front is now the hidden back layer.
            Drawable oldDraw = wallFront.getDrawable();
            wallFront.setImageDrawable(null);
            wallFront.setAlpha(0f);
            if (oldDraw instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap oldBmp = ((android.graphics.drawable.BitmapDrawable) oldDraw).getBitmap();
                if (oldBmp != null && !oldBmp.isRecycled() && oldBmp != bm) {
                    oldBmp.recycle();
                }
            }
            ImageView tmp = wallFront;
            wallFront = wallBack;
            wallBack = tmp;
        }).start();
    }

    private void scheduleWallpaper(long delay) {
        handler.removeCallbacks(rotateWallpaper);
        handler.postDelayed(rotateWallpaper, Math.max(1000, delay));
    }

    /** Average the banner's vivid pixels, then push saturation so the glow reads as a colour. */
    private int sampleColor(String pkg, Drawable d) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        int color = 0xFF3A3A3A;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.75f); hsv[2] = 0.85f; }
                else { hsv[1] = 0f; hsv[2] = 0.45f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        colorCache.put(pkg, color);
        return color;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        View header = hero.getChildAt(0);
        if (header != null) {
            header.setAlpha(1f);
            header.setTranslationY(0);
        }
        title.setAlpha(1f);
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View t = dockRow.getChildAt(i);
            t.setAlpha(1f);
            t.setTranslationY(0);
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean launch(Intent i) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof App) {
                App app = (App) focused.getTag();
                boolean inDock = dockCards.contains(focused);
                showTileMenu(focused, app, inDock);
                return true;
            }
            if (!pills.isEmpty()) {
                showInputs(pills.get(0));
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** 16:9 container. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** ScrollView that never auto-scrolls to the focused child; the activity scrolls it by row. */
    static final class HomeScrollView extends android.widget.ScrollView {
        HomeScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }
}
