package local.tabhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Домашний экран №1 (планшет): фото природы на фоне, часы/приветствие, крупные
 * иконки приложений, и кнопка-замок, которая по PIN открывает расчищенный
 * рабочий стол HyperOS напрямую (без смены лаунчера по умолчанию - этот экран
 * остаётся главным, Home всегда возвращает сюда).
 */
public class HomeActivity extends Activity {

    // applicationId, иконка, подпись - вот и всё, что нужно изменить, чтобы поменять набор приложений
    private static final String[][] APPS = {
            {"Иви", "ru.ivi.client"},
            {"Кинопоиск", "ru.kinopoisk"},
            {"Chrome", "com.android.chrome"},
    };

    private static final long WALLPAPER_MS = 20L * 60L * 1000L;
    private static final String PREFS = "tabhome";
    private static final String KEY_PIN = "pin";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String[] wallpapers = new String[0];
    private int wallIndex = -1;
    private ImageView wallBack, wallFront;
    private boolean firstWall = true;
    private TextView greeting, clock, date;

    private final Runnable rotate = new Runnable() {
        @Override public void run() { nextWallpaper(); }
    };
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, tf);
        updateClock();
        if (wallIndex < 0) nextWallpaper(); else schedule(WALLPAPER_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotate);
        try { unregisterReceiver(tick); } catch (RuntimeException ignored) { }
    }

    @Override
    public void onBackPressed() {
        // на главном экране Back ничего не делает
    }

    // ---------------------------------------------------------------- интерфейс

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        wallBack = wallLayer();
        wallFront = wallLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));
        try { wallpapers = getAssets().list("wallpapers"); } catch (java.io.IOException ignored) { }

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x33000000, 0x22000000, 0x66000000, 0xCC000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // ---- заголовок (приветствие, часы, дата) ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        hlp.setMargins(dp(40), dp(32), 0, 0);
        root.addView(header, hlp);

        greeting = text(16, "sans-serif-medium", 0xCCFFFFFF);
        header.addView(greeting);
        clock = text(52, "sans-serif-light", Color.WHITE);
        header.addView(clock);
        date = text(16, "sans-serif", 0xCCFFFFFF);
        header.addView(date);

        // ---- кнопка-замок (выход в HyperOS) ----
        TextView lock = text(22, "sans-serif-medium", Color.WHITE);
        lock.setText("\u2699");
        lock.setGravity(Gravity.CENTER);
        lock.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable lockBg = new GradientDrawable();
        lockBg.setShape(GradientDrawable.OVAL);
        lockBg.setColor(0x33FFFFFF);
        lock.setBackground(lockBg);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        llp.setMargins(0, dp(28), dp(32), 0);
        root.addView(lock, llp);
        lock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onLockTapped(); }
        });

        // ---- док с иконками приложений ----
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(28));
        band.setColor(0x26FFFFFF);
        band.setStroke(dp(1), 0x1AFFFFFF);
        dock.setBackground(band);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.setMargins(0, 0, 0, dp(48));
        root.addView(dock, dlp);

        for (final String[] app : APPS) {
            View card = buildAppCard(app[0], app[1]);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(160), dp(160));
            clp.setMargins(dp(14), 0, dp(14), 0);
            dock.addView(card, clp);
        }
        return root;
    }

    private View buildAppCard(final String label, final String pkg) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0x33FFFFFF);
        card.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(appIcon(pkg));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ilp.bottomMargin = dp(10);
        card.addView(icon, ilp);

        TextView t = text(13, "sans-serif-medium", Color.WHITE);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        card.addView(t);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchApp(pkg, label); }
        });
        return card;
    }

    private Drawable appIcon(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); }
        catch (PackageManager.NameNotFoundException e) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0x55FFFFFF);
            return d;
        }
    }

    private void launchApp(String pkg, String label) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, label + " не установлен(о). Установите из Play Маркета на экране HyperOS.", Toast.LENGTH_LONG).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (RuntimeException e) {
            Toast.makeText(this, "Не удалось открыть " + label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- PIN и выход в HyperOS

    private void onLockTapped() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = p.getString(KEY_PIN, null);
        if (saved == null) { askSetPin(); } else { askEnterPin(saved); }
    }

    private void askSetPin() {
        final EditText field = pinField();
        new AlertDialog.Builder(this)
                .setTitle("Задайте PIN (4 цифры)")
                .setMessage("Этот PIN будет нужен для выхода на рабочий стол HyperOS.")
                .setView(field)
                .setPositiveButton("Сохранить", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String v = field.getText().toString();
                        if (v.length() != 4) {
                            Toast.makeText(HomeActivity.this, "Нужно ровно 4 цифры", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PIN, v).apply();
                        Toast.makeText(HomeActivity.this, "PIN сохранён", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void askEnterPin(final String saved) {
        final EditText field = pinField();
        new AlertDialog.Builder(this)
                .setTitle("Введите PIN")
                .setView(field)
                .setPositiveButton("Войти", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (saved.equals(field.getText().toString())) {
                            openHyperOs();
                        } else {
                            Toast.makeText(HomeActivity.this, "Неверный PIN", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private EditText pinField() {
        EditText f = new EditText(this);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        f.setGravity(Gravity.CENTER);
        f.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        return f;
    }

    /** Находит и запускает родной рабочий стол HyperOS (не меняя лаунчер по умолчанию). */
    private void openHyperOs() {
        PackageManager pm = getPackageManager();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> candidates = pm.queryIntentActivities(homeIntent, 0);
        for (ResolveInfo ri : candidates) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (!getPackageName().equals(ai.packageName)) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.setClassName(ai.packageName, ri.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); return; } catch (RuntimeException ignored) { }
            }
        }
        Toast.makeText(this, "Не нашёл рабочий стол HyperOS на устройстве.", Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- часы

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "ДОБРОЙ НОЧИ" : h < 12 ? "ДОБРОЕ УТРО" : h < 18 ? "ДОБРЫЙ ДЕНЬ" : "ДОБРЫЙ ВЕЧЕР");
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.getTime()));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                .format(now.getTime()).toUpperCase(Locale.getDefault()));
    }

    // ---------------------------------------------------------------- обои

    private ImageView wallLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private void nextWallpaper() {
        handler.removeCallbacks(rotate);
        if (wallpapers.length == 0) return;
        if (wallIndex < 0) wallIndex = getPreferences(MODE_PRIVATE).getInt("wallpaper", -1);
        wallIndex = (wallIndex + 1) % wallpapers.length;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", wallIndex).apply();
        final String name = "wallpapers/" + wallpapers[wallIndex];
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bm = null;
                try {
                    java.io.InputStream in = getAssets().open(name);
                    try {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inPreferredConfig = Bitmap.Config.RGB_565;
                        bm = BitmapFactory.decodeStream(in, null, o);
                    } finally { in.close(); }
                } catch (java.io.IOException ignored) { }
                final Bitmap result = bm;
                runOnUiThread(new Runnable() {
                    @Override public void run() { showWallpaper(result); }
                });
            }
        }).start();
        schedule(WALLPAPER_MS);
    }

    private void showWallpaper(Bitmap bm) {
        if (bm == null || isDestroyed()) return;
        ImageView old = wallFront;
        wallFront = wallBack;
        wallBack = old;
        ViewGroup root = (ViewGroup) wallFront.getParent();
        root.removeView(wallFront);
        root.addView(wallFront, 1, new FrameLayout.LayoutParams(-1, -1));
        wallFront.setImageBitmap(bm);
        wallFront.setAlpha(0f);
        wallFront.animate().alpha(1f).setDuration(firstWall ? 500 : 1800).withEndAction(new Runnable() {
            @Override public void run() { wallBack.setImageDrawable(null); }
        }).start();
        firstWall = false;
    }

    private void schedule(long delay) {
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, Math.max(1000, delay));
    }

    // ---------------------------------------------------------------- вспомогательное

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
}
