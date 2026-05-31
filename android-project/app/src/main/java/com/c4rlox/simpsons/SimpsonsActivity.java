package com.c4rlox.simpsons;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.libsdl.app.R;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpsonsActivity extends SDLActivity {

    private GamepadOverlayView mOverlay;
    private RelativeLayout mSetupOverlay;
    private RelativeLayout mPickerOverlay;

    private static final int REQUEST_CODE_MANAGE_STORAGE = 9999;
    private static final String PREFS_NAME = "SimpsonsPrefs";
    private static final String KEY_GAME_DIR = "game_data_directory";

    private File mCurrentDir;
    private ArrayAdapter<String> mAdapter;
    private final List<File> mSubDirs = new ArrayList<>();
    private final List<String> mSubDirNames = new ArrayList<>();
    private TextView mPathText;
    private TextView mErrorText;
    private ListView mDirList;

    private static final int SIMPSONS_YELLOW = 0xFFFFD90F;
    private static final int SURFACE_DARK = 0xFF121216;
    private static final int SURFACE_CARD = 0xFF1E1E28;
    private static final int SURFACE_ELEVATED = 0xFF2A2A36;
    private static final int TEXT_PRIMARY = 0xFFF0F0F5;
    private static final int TEXT_SECONDARY = 0xFF9090A5;
    private static final int TEXT_ERROR = 0xFFFF6B6B;

    public static native float nativeGetFPS();
    public static native int nativeGetHudContext();
    public static native boolean nativeIsTitleScreen();
    public static native void nativeSetRumbleEnabled(boolean enabled);
    public static native boolean nativeIsRumbleEnabled();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOverlay = new GamepadOverlayView(this);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        mLayout.addView(mOverlay, lp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mOverlay != null) {
            mOverlay.handleActivityResult(requestCode, resultCode, data);
        }
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (hasStoragePermission()) {
                showPickerScreen();
            } else {
                Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPickerScreen();
            } else {
                Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void resumeNativeThread() {
        if (hasValidGameDataDirectory()) {
            runOnUiThread(() -> {
                removeOverlay(mSetupOverlay);
                removeOverlay(mPickerOverlay);
                mSetupOverlay = null;
                mPickerOverlay = null;
            });
            super.resumeNativeThread();
        } else {
            showSetupScreen();
        }
    }

    public String getGameDataPath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String path = prefs.getString(KEY_GAME_DIR, "");
        if (!path.isEmpty()) {
            File dir = new File(path);
            if (isValidGameDataDirectory(dir)) {
                return path.endsWith("/") ? path : path + "/";
            }
        }
        File defaultDir = getExternalFilesDir(null);
        if (defaultDir != null && isValidGameDataDirectory(defaultDir)) {
            return defaultDir.getAbsolutePath() + "/";
        }
        return "";
    }

    private boolean hasValidGameDataDirectory() {
        return !getGameDataPath().isEmpty();
    }

    private boolean isValidGameDataDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File scripts = new File(dir, "scripts.rcf");
        File art = new File(dir, "art.rcf");
        if (scripts.exists() || art.exists()) return true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".rcf")) return true;
            }
        }
        return false;
    }

    private void saveGameDataDirectory(String path) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(KEY_GAME_DIR, path).apply();
    }

    private void removeOverlay(RelativeLayout overlay) {
        if (overlay != null && overlay.getParent() != null) {
            mLayout.removeView(overlay);
        }
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedBg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void showSetupScreen() {
        runOnUiThread(() -> {
            removeOverlay(mSetupOverlay);
            mSetupOverlay = new RelativeLayout(this);
            mSetupOverlay.setBackgroundColor(SURFACE_DARK);

            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            center.setPadding(dp(32), dp(16), dp(32), dp(16));

            TextView title = new TextView(this);
            title.setText(getString(R.string.setup_title));
            title.setTextColor(SIMPSONS_YELLOW);
            title.setTextSize(26);
            title.setTypeface(null, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, dp(2));
            center.addView(title);

            TextView sub = new TextView(this);
            sub.setText(getString(R.string.setup_subtitle));
            sub.setTextColor(TEXT_SECONDARY);
            sub.setTextSize(14);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, 0, 0, dp(36));
            center.addView(sub);

            GradientDrawable cardBg = roundedBg(SURFACE_CARD, dp(20));
            cardBg.setStroke(dp(1), Color.argb(18, 255, 255, 255));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cardBg);
            card.setPadding(dp(28), dp(28), dp(28), dp(28));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                card.setElevation(dp(6));
            }

            TextView cardTitle = new TextView(this);
            cardTitle.setText(getString(R.string.welcome_title));
            cardTitle.setTextColor(TEXT_PRIMARY);
            cardTitle.setTextSize(20);
            cardTitle.setTypeface(null, Typeface.BOLD);
            cardTitle.setPadding(0, 0, 0, dp(10));
            card.addView(cardTitle);

            TextView cardDesc = new TextView(this);
            cardDesc.setText(getString(R.string.setup_description));
            cardDesc.setTextColor(TEXT_SECONDARY);
            cardDesc.setTextSize(14);
            cardDesc.setLineSpacing(dp(6), 1f);
            cardDesc.setPadding(0, 0, 0, dp(28));
            card.addView(cardDesc);

            Button btn = new Button(this, null, android.R.attr.borderlessButtonStyle);
            btn.setText(getString(R.string.select_game_folder));
            btn.setTextColor(SURFACE_DARK);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setTextSize(15);
            btn.setPadding(dp(28), dp(16), dp(28), dp(16));

            GradientDrawable btnBg = roundedBg(SIMPSONS_YELLOW, dp(30));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.graphics.drawable.RippleDrawable ripple =
                    new android.graphics.drawable.RippleDrawable(
                        ColorStateList.valueOf(Color.argb(60, 0, 0, 0)), btnBg, null);
                btn.setBackground(ripple);
            } else {
                btn.setBackground(btnBg);
            }

            btn.setOnClickListener(v -> requestStoragePermissionAndPickDirectory());
            card.addView(btn);

            center.addView(card);

            mSetupOverlay.addView(center, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mLayout.addView(mSetupOverlay);
        });
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermissionAndPickDirectory() {
        if (hasStoragePermission()) {
            showPickerScreen();
        } else {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.storage_access_title))
                .setMessage(getString(R.string.storage_access_message))
                .setPositiveButton(getString(R.string.settings_btn), (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_CODE_MANAGE_STORAGE);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        }
    }

    private void showPickerScreen() {
        mCurrentDir = new File("/storage/emulated/0");
        if (!mCurrentDir.exists() || !mCurrentDir.canRead()) {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        removeOverlay(mPickerOverlay);
        mPickerOverlay = new RelativeLayout(this);
        mPickerOverlay.setBackgroundColor(SURFACE_DARK);
        mPickerOverlay.setPadding(dp(16), dp(8), dp(16), dp(16));

        // ── Header ─────────────────────────────────────────────────
        RelativeLayout header = new RelativeLayout(this);
        header.setPadding(dp(8), dp(12), dp(8), dp(12));

        Button backBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        backBtn.setText("\u2190");
        backBtn.setTextColor(TEXT_PRIMARY);
        backBtn.setTextSize(22);
        backBtn.setTypeface(null, Typeface.NORMAL);
        backBtn.setBackground(roundedBg(Color.TRANSPARENT, 0));
        backBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        backBtn.setId(View.generateViewId());
        backBtn.setOnClickListener(v -> {
            removeOverlay(mPickerOverlay);
            mPickerOverlay = null;
        });
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(getString(R.string.select_folder));
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setId(View.generateViewId());

        RelativeLayout.LayoutParams titleLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        title.setLayoutParams(titleLp);
        header.addView(title);

        // Path breadcrumb
        mPathText = new TextView(this);
        mPathText.setTextColor(SIMPSONS_YELLOW);
        mPathText.setTextSize(12);
        mPathText.setSingleLine(true);
        mPathText.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mPathText.setSelected(true);
        mPathText.setPadding(dp(8), dp(6), dp(8), dp(12));

        RelativeLayout.LayoutParams pathLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathLp.addRule(RelativeLayout.BELOW, title.getId());
        pathLp.setMargins(dp(4), 0, dp(4), 0);
        mPathText.setLayoutParams(pathLp);
        header.addView(mPathText);

        mPickerOverlay.addView(header);

        // ── Error text ──────────────────────────────────────────────
        mErrorText = new TextView(this);
        mErrorText.setTextColor(TEXT_ERROR);
        mErrorText.setTextSize(13);
        mErrorText.setPadding(dp(12), dp(4), dp(12), dp(8));
        mErrorText.setVisibility(View.GONE);

        RelativeLayout.LayoutParams errLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errLp.addRule(RelativeLayout.BELOW, header.getId());
        mErrorText.setLayoutParams(errLp);
        mPickerOverlay.addView(mErrorText);

        // ── List container ──────────────────────────────────────────
        GradientDrawable listBg = roundedBg(SURFACE_CARD, dp(14));

        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setBackground(listBg);
        listWrap.setPadding(dp(4), dp(4), dp(4), dp(4));

        mDirList = new ListView(this);
        mDirList.setDivider(null);
        mDirList.setDividerHeight(0);
        mDirList.setBackgroundColor(Color.TRANSPARENT);
        mDirList.setClipToPadding(false);
        mDirList.setPadding(dp(4), dp(4), dp(4), dp(4));

        final int barId = View.generateViewId();

        RelativeLayout.LayoutParams listLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        listLp.addRule(RelativeLayout.BELOW, mErrorText.getId());
        listLp.addRule(RelativeLayout.ABOVE, barId);
        listWrap.addView(mDirList);

        mPickerOverlay.addView(listWrap, listLp);

        // ── Bottom bar ──────────────────────────────────────────────
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.END);
        bottomBar.setPadding(dp(4), dp(16), dp(4), dp(8));
        bottomBar.setId(barId);

        Button cancelBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        cancelBtn.setText(getString(R.string.cancel));
        cancelBtn.setTextColor(TEXT_PRIMARY);
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(null, Typeface.NORMAL);
        GradientDrawable cancelBg = roundedBg(Color.TRANSPARENT, dp(24));
        cancelBg.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(dp(20), dp(12), dp(20), dp(12));
        cancelBtn.setOnClickListener(v -> {
            removeOverlay(mPickerOverlay);
            mPickerOverlay = null;
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.setMarginEnd(dp(12));
        cancelBtn.setLayoutParams(cancelLp);
        bottomBar.addView(cancelBtn);

        Button selectBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        selectBtn.setText(getString(R.string.select_this_folder));
        selectBtn.setTextColor(SURFACE_DARK);
        selectBtn.setTypeface(null, Typeface.BOLD);
        selectBtn.setTextSize(14);
        selectBtn.setPadding(dp(24), dp(12), dp(24), dp(12));

        GradientDrawable selectBg = roundedBg(SIMPSONS_YELLOW, dp(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            selectBtn.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(60, 0, 0, 0)), selectBg, null));
        } else {
            selectBtn.setBackground(selectBg);
        }

        selectBtn.setOnClickListener(v -> {
            if (isValidGameDataDirectory(mCurrentDir)) {
                saveGameDataDirectory(mCurrentDir.getAbsolutePath());
                removeOverlay(mPickerOverlay);
                mPickerOverlay = null;
                Toast.makeText(SimpsonsActivity.this, getString(R.string.folder_configured), Toast.LENGTH_SHORT).show();
                resumeNativeThread();
            } else {
                mErrorText.setText(getString(R.string.no_rcf_files));
                mErrorText.setVisibility(View.VISIBLE);
            }
        });

        bottomBar.addView(selectBtn);

        RelativeLayout.LayoutParams barLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bottomBar.setLayoutParams(barLp);
        mPickerOverlay.addView(bottomBar);

        mLayout.addView(mPickerOverlay);

        refreshDirList();
    }

    private void refreshDirList() {
        mPathText.setText(mCurrentDir.getAbsolutePath());
        mSubDirs.clear();
        mSubDirNames.clear();

        File parent = mCurrentDir.getParentFile();
        if (parent != null && parent.exists() && parent.canRead()) {
            mSubDirs.add(parent);
            mSubDirNames.add("  \uD83D\uDCC1  ..");
        }

        List<File> dirs = new ArrayList<>();
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden() && f.canRead()) {
                    dirs.add(f);
                }
            }
        }
        Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File d : dirs) {
            mSubDirs.add(d);
            mSubDirNames.add("  \uD83D\uDCC2  " + d.getName());
        }

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSubDirNames) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                TextView v;
                if (convertView == null) {
                    v = new TextView(SimpsonsActivity.this);
                    v.setPadding(dp(16), dp(14), dp(16), dp(14));
                    v.setTextSize(15);
                    v.setBackground(roundedBg(Color.TRANSPARENT, dp(10)));
                } else {
                    v = (TextView) convertView;
                }

                v.setText(getItem(pos));
                v.setTextColor(TEXT_PRIMARY);
                v.setTypeface(null, Typeface.NORMAL);

                if (getItem(pos).endsWith("..")) {
                    v.setTextColor(TEXT_SECONDARY);
                    v.setTypeface(null, Typeface.ITALIC);
                } else if (pos > 0 && pos < mSubDirs.size() &&
                           isValidGameDataDirectory(mSubDirs.get(pos))) {
                    v.setTextColor(SIMPSONS_YELLOW);
                }

                return v;
            }
        };
        mDirList.setAdapter(mAdapter);

        mDirList.setOnItemClickListener((_parent, view, pos, id) -> {
            mErrorText.setVisibility(View.GONE);
            String name = mSubDirNames.get(pos);
            if (name.endsWith("..")) {
                File p = mCurrentDir.getParentFile();
                if (p != null && p.canRead()) {
                    mCurrentDir = p;
                    refreshDirList();
                }
            } else {
                mCurrentDir = mSubDirs.get(pos);
                refreshDirList();
            }
        });
    }

    // Required by AlertDialog showInvalidDirectoryAlert compatibility
    private GradientDrawable getRoundedButtonDrawable(int color) {
        return roundedBg(color, dp(16));
    }

    private int dpToPx(int dp) {
        return dp(dp);
    }
}
