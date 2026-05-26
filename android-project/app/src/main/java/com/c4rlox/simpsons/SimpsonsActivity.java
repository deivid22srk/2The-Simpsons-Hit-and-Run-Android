package com.c4rlox.simpsons;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpsonsActivity extends SDLActivity {

    private GamepadOverlayView mOverlay;
    private RelativeLayout mSetupOverlay;

    private static final int REQUEST_CODE_MANAGE_STORAGE = 9999;
    private static final String PREFS_NAME = "SimpsonsPrefs";
    private static final String KEY_GAME_DIR = "game_data_directory";

    private static final int COLOR_BG = 0xFF0D0D12;
    private static final int COLOR_BG_CARD = 0xFF16161E;
    private static final int COLOR_BG_CARD_LIGHT = 0xFF1E1E28;
    private static final int COLOR_ACCENT = 0xFFFFD90F;
    private static final int COLOR_ACCENT_DARK = 0xFFE6C200;
    private static final int COLOR_SUCCESS = 0xFF4CAF50;
    private static final int COLOR_ERROR = 0xFFE53935;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFB0B0C0;
    private static final int COLOR_TEXT_DIM = 0xFF707080;
    private static final int COLOR_DIVIDER = 0xFF2A2A38;
    private static final int COLOR_FOLDER_ICON = 0xFF5C6BC0;
    private static final int COLOR_BACK_ICON = 0xFF78909C;

    private File mCurrentDir;
    private AlertDialog mPickDialog;
    private ArrayAdapter<String> mAdapter;
    private final List<File> mSubDirs = new ArrayList<>();
    private final List<String> mSubDirNames = new ArrayList<>();
    private TextView mPathTextView;
    private TextView mStatusIndicator;

    public static native float nativeGetFPS();
    public static native int nativeGetHudContext();
    public static native boolean nativeIsTitleScreen();

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
                showDirectoryPickerDialog();
            } else {
                Toast.makeText(this, "Permissão de armazenamento não concedida.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showDirectoryPickerDialog();
            } else {
                Toast.makeText(this, "Permissão de armazenamento não concedida.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void resumeNativeThread() {
        if (hasValidGameDataDirectory()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mSetupOverlay != null) {
                        mLayout.removeView(mSetupOverlay);
                        mSetupOverlay = null;
                    }
                }
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
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        File scripts = new File(dir, "scripts.rcf");
        File art = new File(dir, "art.rcf");
        if (scripts.exists() || art.exists()) {
            return true;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".rcf")) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countRcfFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".rcf")) {
                    count++;
                }
            }
        }
        return count;
    }

    private void saveGameDataDirectory(String path) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_GAME_DIR, path).apply();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private GradientDrawable createRoundedRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable createRoundedRectStroke(int bgColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable createGradientDrawable(int[] colors, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        return drawable;
    }

    private View createDivider(int color, int heightDp, int marginVDp) {
        View divider = new View(this);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(marginVDp);
        params.bottomMargin = dp(marginVDp);
        divider.setLayoutParams(params);
        return divider;
    }

    private void showSetupScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSetupOverlay != null) {
                    mLayout.removeView(mSetupOverlay);
                }

                mSetupOverlay = new RelativeLayout(SimpsonsActivity.this);
                GradientDrawable bgGradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF12121A, COLOR_BG, 0xFF0A0A10});
                mSetupOverlay.setBackground(bgGradient);

                ScrollView scrollView = new ScrollView(SimpsonsActivity.this);
                scrollView.setFillViewport(true);
                scrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

                LinearLayout outerContainer = new LinearLayout(SimpsonsActivity.this);
                outerContainer.setOrientation(LinearLayout.VERTICAL);
                outerContainer.setGravity(Gravity.CENTER_HORIZONTAL);
                outerContainer.setPadding(dp(24), dp(48), dp(24), dp(48));

                LinearLayout card = new LinearLayout(SimpsonsActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setBackground(createRoundedRect(COLOR_BG_CARD, 20));
                card.setPadding(dp(32), dp(40), dp(32), dp(40));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    dp(520), ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.gravity = Gravity.CENTER;

                FrameLayout iconContainer = new FrameLayout(SimpsonsActivity.this);
                iconContainer.setBackground(createGradientDrawable(
                    new int[]{0xFF2D2D44, 0xFF1A1A2E},
                    GradientDrawable.Orientation.TOP_BOTTOM));
                GradientDrawable iconBg = (GradientDrawable) iconContainer.getBackground();
                iconBg.setShape(GradientDrawable.OVAL);
                int iconSize = dp(80);
                FrameLayout.LayoutParams iconContainerParams = new FrameLayout.LayoutParams(iconSize, iconSize);
                iconContainerParams.gravity = Gravity.CENTER_HORIZONTAL;
                iconContainer.setLayoutParams(iconContainerParams);

                TextView iconText = new TextView(SimpsonsActivity.this);
                iconText.setText("S");
                iconText.setTextColor(COLOR_ACCENT);
                iconText.setTextSize(36);
                iconText.setTypeface(Typeface.DEFAULT_BOLD);
                iconText.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams iconTextParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                iconText.setLayoutParams(iconTextParams);
                iconContainer.addView(iconText);

                LinearLayout.LayoutParams iconMarginParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                iconMarginParams.bottomMargin = dp(20);
                iconMarginParams.gravity = Gravity.CENTER_HORIZONTAL;
                card.addView(iconContainer, iconMarginParams);

                TextView title = new TextView(SimpsonsActivity.this);
                title.setText("THE SIMPSONS");
                title.setTextColor(COLOR_TEXT_PRIMARY);
                title.setTextSize(28);
                title.setTypeface(null, Typeface.BOLD);
                title.setGravity(Gravity.CENTER);
                title.setLetterSpacing(0.08f);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                card.addView(title, titleParams);

                TextView subtitle = new TextView(SimpsonsActivity.this);
                subtitle.setText("HIT & RUN");
                subtitle.setTextColor(COLOR_ACCENT);
                subtitle.setTextSize(16);
                subtitle.setTypeface(null, Typeface.BOLD);
                subtitle.setGravity(Gravity.CENTER);
                subtitle.setLetterSpacing(0.2f);
                LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                subtitleParams.topMargin = dp(2);
                subtitleParams.bottomMargin = dp(24);
                card.addView(subtitle, subtitleParams);

                card.addView(createDivider(COLOR_DIVIDER, 1, 4));

                TextView desc = new TextView(SimpsonsActivity.this);
                desc.setText("Para jogar, selecione a pasta que contém os arquivos do jogo (.rcf)");
                desc.setTextColor(COLOR_TEXT_SECONDARY);
                desc.setTextSize(15);
                desc.setGravity(Gravity.CENTER);
                desc.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                descParams.topMargin = dp(20);
                descParams.bottomMargin = dp(28);
                card.addView(desc, descParams);

                LinearLayout stepsContainer = new LinearLayout(SimpsonsActivity.this);
                stepsContainer.setOrientation(LinearLayout.VERTICAL);
                stepsContainer.setBackground(createRoundedRect(COLOR_BG_CARD_LIGHT, 12));
                stepsContainer.setPadding(dp(20), dp(16), dp(20), dp(16));
                LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                stepsParams.bottomMargin = dp(28);
                stepsContainer.setLayoutParams(stepsParams);

                String[] steps = {
                    "1.  Toque no botão abaixo para abrir o seletor",
                    "2.  Navegue até a pasta dos arquivos do jogo",
                    "3.  Confirme a seleção para iniciar o jogo"
                };

                for (String stepText : steps) {
                    TextView step = new TextView(SimpsonsActivity.this);
                    step.setText(stepText);
                    step.setTextColor(COLOR_TEXT_DIM);
                    step.setTextSize(13);
                    step.setPadding(0, dp(4), 0, dp(4));
                    stepsContainer.addView(step);
                }
                card.addView(stepsContainer);

                Button btnSelect = new Button(SimpsonsActivity.this);
                btnSelect.setText("SELECIONAR PASTA DO JOGO");
                btnSelect.setTextColor(COLOR_BG);
                btnSelect.setTypeface(null, Typeface.BOLD);
                btnSelect.setTextSize(16);
                btnSelect.setLetterSpacing(0.04f);
                btnSelect.setAllCaps(false);

                GradientDrawable btnBg = createRoundedRect(COLOR_ACCENT, 14);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    RippleDrawable ripple = new RippleDrawable(
                        ColorStateList.valueOf(0x40FFFFFF), btnBg, null);
                    btnSelect.setBackground(ripple);
                } else {
                    btnSelect.setBackground(btnBg);
                }

                btnSelect.setPadding(dp(24), dp(16), dp(24), dp(16));
                btnSelect.setMinimumHeight(dp(56));
                btnSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestStoragePermissionAndPickDirectory();
                    }
                });
                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                card.addView(btnSelect, btnParams);

                String savedPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_GAME_DIR, "");
                if (!savedPath.isEmpty()) {
                    TextView currentDirLabel = new TextView(SimpsonsActivity.this);
                    currentDirLabel.setText("Pasta atual: " + savedPath);
                    currentDirLabel.setTextColor(COLOR_TEXT_DIM);
                    currentDirLabel.setTextSize(11);
                    currentDirLabel.setGravity(Gravity.CENTER);
                    currentDirLabel.setSingleLine(true);
                    currentDirLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    currentDirLabel.setPadding(0, dp(16), 0, 0);
                    card.addView(currentDirLabel);
                }

                outerContainer.addView(card, cardParams);

                TextView footer = new TextView(SimpsonsActivity.this);
                footer.setText("v1.0  •  Port Android  •  C4rlox");
                footer.setTextColor(COLOR_TEXT_DIM);
                footer.setTextSize(11);
                footer.setGravity(Gravity.CENTER);
                footer.setPadding(0, dp(24), 0, 0);
                footer.setLetterSpacing(0.05f);
                outerContainer.addView(footer);

                scrollView.addView(outerContainer);

                RelativeLayout.LayoutParams scrollParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
                mSetupOverlay.addView(scrollView, scrollParams);

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                );
                mLayout.addView(mSetupOverlay, lp);
            }
        });
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }
    }

    private void requestStoragePermissionAndPickDirectory() {
        if (hasStoragePermission()) {
            showDirectoryPickerDialog();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            LinearLayout permLayout = new LinearLayout(this);
            permLayout.setOrientation(LinearLayout.VERTICAL);
            permLayout.setBackgroundColor(COLOR_BG_CARD);
            permLayout.setPadding(dp(28), dp(28), dp(28), dp(28));

            TextView permTitle = new TextView(this);
            permTitle.setText("Acesso ao Armazenamento");
            permTitle.setTextColor(COLOR_TEXT_PRIMARY);
            permTitle.setTextSize(20);
            permTitle.setTypeface(null, Typeface.BOLD);
            permTitle.setPadding(0, 0, 0, dp(12));
            permLayout.addView(permTitle);

            TextView permDesc = new TextView(this);
            permDesc.setText("Para selecionar qualquer pasta do aparelho, o jogo precisa de acesso para gerenciar os arquivos no Android.\n\nVocê será redirecionado para as configurações. Por favor, ative a opção.");
            permDesc.setTextColor(COLOR_TEXT_SECONDARY);
            permDesc.setTextSize(14);
            permDesc.setLineSpacing(dp(3), 1f);
            permLayout.addView(permDesc);

            builder.setView(permLayout);
            builder.setPositiveButton("Configurar", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            }, REQUEST_CODE_MANAGE_STORAGE);
                        }
                    }
                }
            });
            builder.setNegativeButton("Cancelar", null);
            builder.show();
        }
    }

    private void showDirectoryPickerDialog() {
        mCurrentDir = new File("/storage/emulated/0");
        if (!mCurrentDir.exists() || !mCurrentDir.canRead()) {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_BG);
        rootLayout.setPadding(0, 0, 0, 0);

        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setBackground(createGradientDrawable(
            new int[]{0xFF1A1A28, COLOR_BG_CARD},
            GradientDrawable.Orientation.LEFT_RIGHT));
        headerBar.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView headerIcon = new TextView(this);
        headerIcon.setText("\uD83D\uDCC2");
        headerIcon.setTextSize(20);
        headerIcon.setPadding(0, 0, dp(12), 0);
        headerBar.addView(headerIcon);

        LinearLayout headerTextContainer = new LinearLayout(this);
        headerTextContainer.setOrientation(LinearLayout.VERTICAL);
        headerTextContainer.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Selecionar Pasta do Jogo");
        headerTitle.setTextColor(COLOR_TEXT_PRIMARY);
        headerTitle.setTextSize(17);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTextContainer.addView(headerTitle);

        TextView headerSub = new TextView(this);
        headerSub.setText("Navegue e escolha a pasta com arquivos .rcf");
        headerSub.setTextColor(COLOR_TEXT_DIM);
        headerSub.setTextSize(12);
        headerSub.setPadding(0, dp(2), 0, 0);
        headerTextContainer.addView(headerSub);

        headerBar.addView(headerTextContainer);
        rootLayout.addView(headerBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(createDivider(COLOR_DIVIDER, 1, 0));

        LinearLayout pathBar = new LinearLayout(this);
        pathBar.setOrientation(LinearLayout.HORIZONTAL);
        pathBar.setGravity(Gravity.CENTER_VERTICAL);
        pathBar.setBackgroundColor(COLOR_BG_CARD_LIGHT);
        pathBar.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView pathIcon = new TextView(this);
        pathIcon.setText("\uD83D\uDCCD");
        pathIcon.setTextSize(13);
        pathIcon.setPadding(0, 0, dp(8), 0);
        pathBar.addView(pathIcon);

        mPathTextView = new TextView(this);
        mPathTextView.setTextColor(COLOR_ACCENT);
        mPathTextView.setTextSize(12);
        mPathTextView.setTypeface(Typeface.MONOSPACE);
        mPathTextView.setSingleLine(true);
        mPathTextView.setEllipsize(TextUtils.TruncateAt.START);
        mPathTextView.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pathBar.addView(mPathTextView);

        rootLayout.addView(pathBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mStatusIndicator = new TextView(this);
        mStatusIndicator.setTextSize(12);
        mStatusIndicator.setTypeface(null, Typeface.BOLD);
        mStatusIndicator.setGravity(Gravity.CENTER_VERTICAL);
        mStatusIndicator.setPadding(dp(16), dp(8), dp(16), dp(8));
        mStatusIndicator.setBackground(createRoundedRect(0xFF1A1A24, 0));
        rootLayout.addView(mStatusIndicator, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(createDivider(COLOR_DIVIDER, 1, 0));

        ListView listView = new ListView(this);
        listView.setBackgroundColor(COLOR_BG);
        listView.setDivider(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00000000, COLOR_DIVIDER, COLOR_DIVIDER, COLOR_DIVIDER, 0x00000000}));
        listView.setDividerHeight(dp(1));
        listView.setSelector(createRoundedRect(0x20FFD90F, 0));
        listView.setPadding(0, dp(4), 0, dp(4));
        listView.setClipToPadding(false);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        rootLayout.addView(listView);

        rootLayout.addView(createDivider(COLOR_DIVIDER, 1, 0));

        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        buttonsLayout.setBackgroundColor(COLOR_BG_CARD);
        buttonsLayout.setPadding(dp(16), dp(12), dp(16), dp(12));

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancelar");
        btnCancel.setTextColor(COLOR_TEXT_SECONDARY);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextSize(14);
        btnCancel.setAllCaps(false);
        btnCancel.setBackground(createRoundedRectStroke(COLOR_BG_CARD, COLOR_DIVIDER, 1, 10));
        btnCancel.setPadding(dp(20), dp(10), dp(20), dp(10));
        btnCancel.setMinimumHeight(dp(44));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.setMargins(0, 0, dp(12), 0);
        btnCancel.setLayoutParams(cancelParams);
        buttonsLayout.addView(btnCancel);

        Button btnSelect = new Button(this);
        btnSelect.setText("Selecionar Esta Pasta");
        btnSelect.setTextColor(COLOR_BG);
        btnSelect.setTypeface(null, Typeface.BOLD);
        btnSelect.setTextSize(14);
        btnSelect.setAllCaps(false);

        GradientDrawable selectBtnBg = createRoundedRect(COLOR_ACCENT, 10);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            RippleDrawable selectRipple = new RippleDrawable(
                ColorStateList.valueOf(0x40FFFFFF), selectBtnBg, null);
            btnSelect.setBackground(selectRipple);
        } else {
            btnSelect.setBackground(selectBtnBg);
        }
        btnSelect.setPadding(dp(20), dp(10), dp(20), dp(10));
        btnSelect.setMinimumHeight(dp(44));
        buttonsLayout.addView(btnSelect);

        rootLayout.addView(buttonsLayout, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        builder.setView(rootLayout);
        mPickDialog = builder.create();

        updateDirectoryList(listView);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0 && mCurrentDir.getParentFile() != null) {
                    File parentDir = mCurrentDir.getParentFile();
                    if (parentDir != null && parentDir.canRead()) {
                        mCurrentDir = parentDir;
                        updateDirectoryList(listView);
                    }
                } else {
                    int actualPos = mCurrentDir.getParentFile() != null ? position : position;
                    if (mCurrentDir.getParentFile() != null && position == 0) return;
                    int dirIndex = mCurrentDir.getParentFile() != null ? position - 1 : position;
                    if (dirIndex >= 0 && dirIndex < mSubDirs.size()) {
                        File nextDir = mSubDirs.get(dirIndex);
                        mCurrentDir = nextDir;
                        updateDirectoryList(listView);
                    }
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPickDialog.dismiss();
            }
        });

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isValidGameDataDirectory(mCurrentDir)) {
                    saveGameDataDirectory(mCurrentDir.getAbsolutePath());
                    mPickDialog.dismiss();
                    Toast.makeText(SimpsonsActivity.this, "Pasta configurada com sucesso!", Toast.LENGTH_SHORT).show();
                    resumeNativeThread();
                } else {
                    showInvalidDirectoryAlert(mCurrentDir);
                }
            }
        });

        mPickDialog.show();

        if (mPickDialog.getWindow() != null) {
            mPickDialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                (int)(getResources().getDisplayMetrics().heightPixels * 0.85f));
            mPickDialog.getWindow().setBackgroundDrawable(createRoundedRect(COLOR_BG, 16));
        }
    }

    private void updateDirectoryList(ListView listView) {
        mPathTextView.setText(mCurrentDir.getAbsolutePath());
        mSubDirs.clear();
        mSubDirNames.clear();

        int rcfCount = countRcfFiles(mCurrentDir);
        if (rcfCount > 0) {
            mStatusIndicator.setText("\u2705  Pasta válida — " + rcfCount + " arquivo(s) .rcf encontrado(s)");
            mStatusIndicator.setTextColor(COLOR_SUCCESS);
            mStatusIndicator.setBackground(createRoundedRect(0xFF1B2E1C, 0));
        } else {
            mStatusIndicator.setText("\u26A0\uFE0F  Sem arquivos .rcf nesta pasta — continue navegando");
            mStatusIndicator.setTextColor(0xFFFFB74D);
            mStatusIndicator.setBackground(createRoundedRect(0xFF2E261B, 0));
        }

        boolean hasParent = false;
        File parentDir = mCurrentDir.getParentFile();
        if (parentDir != null && parentDir.exists() && parentDir.canRead()) {
            hasParent = true;
            mSubDirs.add(parentDir);
            mSubDirNames.add("__BACK__");
        }

        File[] dirs = mCurrentDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && !file.isHidden() && file.canRead();
            }
        });

        if (dirs != null) {
            List<File> dirList = new ArrayList<>();
            for (File d : dirs) {
                dirList.add(d);
            }
            Collections.sort(dirList, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            for (File d : dirList) {
                mSubDirs.add(d);
                mSubDirNames.add(d.getName());
            }
        }

        final boolean finalHasParent = hasParent;
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSubDirNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setBackgroundColor(Color.TRANSPARENT);

                boolean isBack = finalHasParent && position == 0;

                TextView iconView = new TextView(getContext());
                iconView.setTextSize(18);
                iconView.setPadding(0, 0, dp(14), 0);
                iconView.setGravity(Gravity.CENTER);

                if (isBack) {
                    iconView.setText("\u2B06\uFE0F");
                } else {
                    iconView.setText("\uD83D\uDCD1");
                }
                row.addView(iconView);

                LinearLayout textContainer = new LinearLayout(getContext());
                textContainer.setOrientation(LinearLayout.VERTICAL);
                textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView nameView = new TextView(getContext());
                if (isBack) {
                    nameView.setText("Pasta anterior");
                    nameView.setTextColor(COLOR_BACK_ICON);
                    nameView.setTypeface(null, Typeface.ITALIC);
                    nameView.setTextSize(14);
                } else {
                    String name = getItem(position);
                    nameView.setText(name);
                    nameView.setTextColor(COLOR_TEXT_PRIMARY);
                    nameView.setTypeface(null, Typeface.NORMAL);
                    nameView.setTextSize(15);
                }
                textContainer.addView(nameView);

                if (!isBack) {
                    File dir = mSubDirs.get(position);
                    int subRcf = countRcfFiles(dir);
                    if (subRcf > 0) {
                        TextView badge = new TextView(getContext());
                        badge.setText(subRcf + " .rcf");
                        badge.setTextColor(COLOR_SUCCESS);
                        badge.setTextSize(10);
                        badge.setTypeface(null, Typeface.BOLD);
                        badge.setBackground(createRoundedRect(0xFF1B2E1C, 6));
                        badge.setPadding(dp(6), dp(2), dp(6), dp(2));

                        FrameLayout badgeContainer = new FrameLayout(getContext());
                        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        badgeLp.topMargin = dp(3);
                        badge.setLayoutParams(badgeLp);
                        badgeContainer.addView(badge);
                        textContainer.addView(badgeContainer);
                    }
                }

                row.addView(textContainer);

                if (!isBack) {
                    TextView arrow = new TextView(getContext());
                    arrow.setText("\u276F");
                    arrow.setTextColor(COLOR_TEXT_DIM);
                    arrow.setTextSize(16);
                    arrow.setGravity(Gravity.CENTER);
                    row.addView(arrow);
                }

                return row;
            }
        };
        listView.setAdapter(mAdapter);
    }

    private void showInvalidDirectoryAlert(File dir) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout errorLayout = new LinearLayout(this);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setBackgroundColor(COLOR_BG_CARD);
        errorLayout.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView errorTitle = new TextView(this);
        errorTitle.setText("\u26A0\uFE0F  Pasta Inválida");
        errorTitle.setTextColor(COLOR_ERROR);
        errorTitle.setTextSize(20);
        errorTitle.setTypeface(null, Typeface.BOLD);
        errorTitle.setPadding(0, 0, 0, dp(12));
        errorLayout.addView(errorTitle);

        TextView errorPath = new TextView(this);
        errorPath.setText(dir.getAbsolutePath());
        errorPath.setTextColor(COLOR_ACCENT);
        errorPath.setTextSize(12);
        errorPath.setTypeface(Typeface.MONOSPACE);
        errorPath.setBackground(createRoundedRect(COLOR_BG_CARD_LIGHT, 8));
        errorPath.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathParams.bottomMargin = dp(16);
        errorLayout.addView(errorPath, pathParams);

        TextView errorDesc = new TextView(this);
        errorDesc.setText("Esta pasta não contém arquivos .rcf do jogo (ex: scripts.rcf, art.rcf).\n\nCertifique-se de selecionar a pasta correta.");
        errorDesc.setTextColor(COLOR_TEXT_SECONDARY);
        errorDesc.setTextSize(14);
        errorDesc.setLineSpacing(dp(3), 1f);
        errorLayout.addView(errorDesc);

        builder.setView(errorLayout);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
