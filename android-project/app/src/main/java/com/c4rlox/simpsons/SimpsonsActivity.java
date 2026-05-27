package com.c4rlox.simpsons;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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

/**
 * Simpsons Hit & Run — Activity principal.
 *
 * Adiciona um GamepadOverlayView sobre a SDLSurface para desenhar
 * o HUD de controle touch (D-Pad, botões A/B/X/Y, sticks, L1/R1).
 * Os toques são encaminhados à SDLSurface para processamento pelo
 * InputManager C++.
 */
public class SimpsonsActivity extends SDLActivity {

    private GamepadOverlayView mOverlay;
    private RelativeLayout mSetupOverlay;

    private static final int REQUEST_CODE_MANAGE_STORAGE = 9999;
    private static final String PREFS_NAME = "SimpsonsPrefs";
    private static final String KEY_GAME_DIR = "game_data_directory";

    private static final int COLOR_ACCENT = 0xFFFFD90F;
    private static final int COLOR_ACCENT_DARK = 0xFFE6C20E;
    private static final int COLOR_BG_DARK = 0xFF0F0F14;
    private static final int COLOR_BG_CARD = 0xFF1A1A22;
    private static final int COLOR_BG_CARD_LIGHT = 0xFF24242E;
    private static final int COLOR_BG_LIST = 0xFF15151C;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFB0B0C0;
    private static final int COLOR_TEXT_MUTED = 0xFF6B6B80;
    private static final int COLOR_SUCCESS = 0xFF4ADE80;
    private static final int COLOR_SUCCESS_BG = 0xFF122A1E;
    private static final int COLOR_WARN_BG = 0xFF2A2514;
    private static final int COLOR_WARN = 0xFFF5C151;

    private File mCurrentDir;
    private AlertDialog mPickDialog;
    private ArrayAdapter<String> mAdapter;
    private final List<File> mSubDirs = new ArrayList<>();
    private final List<String> mSubDirNames = new ArrayList<>();
    private TextView mPathTextView;
    private LinearLayout mBreadcrumbLayout;
    private LinearLayout mValidityBanner;
    private TextView mValidityIcon;
    private TextView mValidityText;
    private Button mBtnSelectFolder;

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

    private void saveGameDataDirectory(String path) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_GAME_DIR, path).apply();
    }

    // ── Setup UI ────────────────────────────────────────────────────

    private void showSetupScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSetupOverlay != null) {
                    mLayout.removeView(mSetupOverlay);
                }

                mSetupOverlay = new RelativeLayout(SimpsonsActivity.this);
                mSetupOverlay.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF14141C, 0xFF0A0A10}
                ));

                ScrollView scroll = new ScrollView(SimpsonsActivity.this);
                scroll.setFillViewport(true);
                scroll.setScrollBarSize(0);

                LinearLayout root = new LinearLayout(SimpsonsActivity.this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER_HORIZONTAL);
                int hPad = dpToPx(32);
                root.setPadding(hPad, dpToPx(48), hPad, dpToPx(32));

                root.addView(buildLogoBadge());
                root.addView(buildTitleBlock());
                root.addView(buildStepsBlock());
                root.addView(buildPrimaryActionButton());
                root.addView(buildHintFooter());

                scroll.addView(root);

                RelativeLayout.LayoutParams scrollParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                );
                mSetupOverlay.addView(scroll, scrollParams);

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                );
                mLayout.addView(mSetupOverlay, lp);
            }
        });
    }

    private View buildLogoBadge() {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(createRoundedRect(0xFF1F1F2A, dpToPx(24)));
        int pad = dpToPx(20);
        badge.setPadding(pad, pad, pad, pad);

        TextView icon = new TextView(this);
        icon.setText("\uD83D\uDE97");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        badge.addView(icon);

        TextView tag = new TextView(this);
        tag.setText("HIT & RUN");
        tag.setTextColor(COLOR_ACCENT);
        tag.setTextSize(12);
        tag.setTypeface(null, Typeface.BOLD);
        tag.setLetterSpacing(0.25f);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, dpToPx(8), 0, 0);
        badge.addView(tag);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            dpToPx(140), dpToPx(140));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(24);
        badge.setLayoutParams(lp);
        return badge;
    }

    private View buildTitleBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("The Simpsons");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        block.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Hit & Run");
        subtitle.setTextColor(COLOR_ACCENT);
        subtitle.setTextSize(30);
        subtitle.setTypeface(null, Typeface.BOLD);
        subtitle.setGravity(Gravity.CENTER);
        block.addView(subtitle);

        TextView desc = new TextView(this);
        desc.setText("Para iniciar sua aventura em Springfield, localize a pasta onde estão os arquivos do jogo.");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(15);
        desc.setLineSpacing(0, 1.35f);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(dpToPx(8), dpToPx(18), dpToPx(8), dpToPx(8));
        block.addView(desc);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(28);
        block.setLayoutParams(lp);
        return block;
    }

    private View buildStepsBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        block.addView(buildStepCard("1", "\uD83D\uDD10", "Permitir acesso",
            "Libere o acesso aos arquivos do aparelho para navegar pelas pastas."));
        block.addView(buildStepCard("2", "\uD83D\uDCC2", "Escolher a pasta",
            "Navegue até o diretório que contém os arquivos .rcf do jogo."));
        block.addView(buildStepCard("3", "\uD83C\uDFAE", "Começar a jogar",
            "O jogo validará os arquivos e iniciará automaticamente."));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(32);
        block.setLayoutParams(lp);
        return block;
    }

    private View buildStepCard(String number, String icon, String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(createRoundedRect(COLOR_BG_CARD, dpToPx(16)));
        int pad = dpToPx(16);
        card.setPadding(pad, pad, pad, pad);

        TextView numberView = new TextView(this);
        numberView.setText(number);
        numberView.setTextColor(COLOR_BG_DARK);
        numberView.setTextSize(16);
        numberView.setTypeface(null, Typeface.BOLD);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(createCircleDrawable(COLOR_ACCENT, dpToPx(36)));
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        numLp.setMarginEnd(dpToPx(14));
        numberView.setLayoutParams(numLp);
        card.addView(numberView);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(18);
        iconView.setPadding(0, 0, dpToPx(8), 0);
        titleRow.addView(iconView);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleRow.addView(titleView);

        textBlock.addView(titleRow);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(COLOR_TEXT_SECONDARY);
        descView.setTextSize(13);
        descView.setLineSpacing(0, 1.3f);
        descView.setPadding(0, dpToPx(4), 0, 0);
        textBlock.addView(descView);

        card.addView(textBlock);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(10);
        card.setLayoutParams(lp);
        return card;
    }

    private View buildPrimaryActionButton() {
        Button btn = new Button(this);
        btn.setText("\uD83D\uDCC1  SELECIONAR PASTA DO JOGO");
        btn.setTextColor(COLOR_BG_DARK);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextSize(15);
        btn.setLetterSpacing(0.05f);
        btn.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{COLOR_ACCENT, COLOR_ACCENT_DARK}
        ));
        btn.setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePermissionAndPickDirectory();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(20);
        btn.setLayoutParams(lp);
        return btn;
    }

    private View buildHintFooter() {
        LinearLayout hint = new LinearLayout(this);
        hint.setOrientation(LinearLayout.HORIZONTAL);
        hint.setGravity(Gravity.CENTER);
        hint.setBackground(createStrokedRoundedRect(0x14FFD90F, 0x33FFD90F, 1, 12));
        int pad = dpToPx(14);
        hint.setPadding(pad, pad, pad, pad);

        TextView icon = new TextView(this);
        icon.setText("\uD83D\uDCA1");
        icon.setTextSize(16);
        icon.setPadding(0, 0, dpToPx(10), 0);
        hint.addView(icon);

        TextView text = new TextView(this);
        text.setText("Os arquivos do jogo geralmente ficam em ");
        text.setTextColor(COLOR_TEXT_SECONDARY);
        text.setTextSize(13);
        hint.addView(text);

        TextView path = new TextView(this);
        path.setText("Download");
        path.setTextColor(COLOR_ACCENT);
        path.setTypeface(null, Typeface.BOLD);
        path.setTextSize(13);
        hint.addView(path);

        TextView text2 = new TextView(this);
        text2.setText(" ou ");
        text2.setTextColor(COLOR_TEXT_SECONDARY);
        text2.setTextSize(13);
        hint.addView(text2);

        TextView path2 = new TextView(this);
        path2.setText("Documents");
        path2.setTextColor(COLOR_ACCENT);
        path2.setTypeface(null, Typeface.BOLD);
        path2.setTextSize(13);
        hint.addView(path2);

        return hint;
    }

    // ── Permissions ─────────────────────────────────────────────────

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
            builder.setTitle("Acesso ao Armazenamento")
                   .setMessage("Para selecionar qualquer pasta do aparelho, o jogo precisa de acesso para gerenciar os arquivos no Android.\n\nVocê será redirecionado para as configurações. Por favor, ative a opção.")
                   .setPositiveButton("Configurar", new DialogInterface.OnClickListener() {
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
                   })
                   .setNegativeButton("Cancelar", null)
                   .show();
        }
    }

    // ── Picker Dialog ───────────────────────────────────────────────

    private void showDirectoryPickerDialog() {
        mCurrentDir = new File("/storage/emulated/0");
        if (!mCurrentDir.exists() || !mCurrentDir.canRead()) {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF13131A, 0xFF0B0B10}
        ));

        root.addView(buildPickerTopBar());
        root.addView(buildBreadcrumbBar());
        root.addView(buildValidityBanner());
        root.addView(buildShortcutsBar());
        root.addView(buildDivider());
        root.addView(buildFolderList(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildDivider());
        root.addView(buildPickerBottomBar());

        builder.setView(root);
        mPickDialog = builder.create();
        if (mPickDialog.getWindow() != null) {
            mPickDialog.getWindow().setBackgroundDrawable(
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF13131A, 0xFF0B0B10}));
            mPickDialog.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        }

        mPickDialog.show();
        refreshPickerState();
    }

    private View buildPickerTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF18181F);
        int pad = dpToPx(16);
        bar.setPadding(pad, pad, pad, pad);

        Button btnUp = new Button(this);
        btnUp.setText("\u2190");
        btnUp.setTextColor(COLOR_TEXT_PRIMARY);
        btnUp.setTextSize(20);
        btnUp.setBackground(createRoundedRect(COLOR_BG_CARD_LIGHT, dpToPx(10)));
        btnUp.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44));
        upLp.setMarginEnd(dpToPx(12));
        btnUp.setLayoutParams(upLp);
        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File parent = mCurrentDir.getParentFile();
                if (parent != null && parent.canRead()) {
                    mCurrentDir = parent;
                    refreshPickerState();
                }
            }
        });
        bar.addView(btnUp);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Pasta do Jogo");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        titleBlock.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Navegue até a pasta com os arquivos .rcf");
        sub.setTextColor(COLOR_TEXT_MUTED);
        sub.setTextSize(12);
        titleBlock.addView(sub);

        bar.addView(titleBlock);

        return bar;
    }

    private View buildBreadcrumbBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFF111117);
        scroll.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));

        mBreadcrumbLayout = new LinearLayout(this);
        mBreadcrumbLayout.setOrientation(LinearLayout.HORIZONTAL);
        mBreadcrumbLayout.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(mBreadcrumbLayout);

        return scroll;
    }

    private View buildValidityBanner() {
        mValidityBanner = new LinearLayout(this);
        mValidityBanner.setOrientation(LinearLayout.HORIZONTAL);
        mValidityBanner.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dpToPx(14);
        mValidityBanner.setPadding(dpToPx(16), pad, dpToPx(16), pad);

        mValidityIcon = new TextView(this);
        mValidityIcon.setTextSize(18);
        mValidityIcon.setPadding(0, 0, dpToPx(12), 0);
        mValidityBanner.addView(mValidityIcon);

        mValidityText = new TextView(this);
        mValidityText.setTextSize(14);
        mValidityText.setTypeface(null, Typeface.BOLD);
        mValidityText.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mValidityBanner.addView(mValidityText);

        return mValidityBanner;
    }

    private View buildShortcutsBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFF13131A);
        scroll.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(buildShortcutChip("\uD83D\uDCF1", "Interno", "/storage/emulated/0"));
        row.addView(buildShortcutChip("\uD83D\uDCC1", "Android", "/storage/emulated/0/Android"));
        row.addView(buildShortcutChip("\u2B07\uFE0F", "Download", "/storage/emulated/0/Download"));
        row.addView(buildShortcutChip("\uD83D\uDCC4", "Documents", "/storage/emulated/0/Documents"));
        row.addView(buildShortcutChip("\uD83C\uDFAE", "Games", "/storage/emulated/0/Games"));

        scroll.addView(row);
        return scroll;
    }

    private View buildShortcutChip(String icon, String label, final String path) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(createStrokedRoundedRect(COLOR_BG_CARD, 0x33FFFFFF, 1, 20));
        int padH = dpToPx(14);
        int padV = dpToPx(8);
        chip.setPadding(padH, padV, padH, padV);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(14);
        iconView.setPadding(0, 0, dpToPx(6), 0);
        chip.addView(iconView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT_PRIMARY);
        labelView.setTextSize(13);
        labelView.setTypeface(null, Typeface.BOLD);
        chip.addView(labelView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dpToPx(8), 0);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File target = new File(path);
                if (target.exists() && target.canRead()) {
                    mCurrentDir = target;
                    refreshPickerState();
                } else {
                    Toast.makeText(SimpsonsActivity.this,
                        "Pasta não disponível: " + path, Toast.LENGTH_SHORT).show();
                }
            }
        });

        return chip;
    }

    private View buildDivider() {
        View div = new View(this);
        div.setBackgroundColor(0x22FFFFFF);
        return div;
    }

    private View buildFolderList() {
        ListView listView = new ListView(this);
        listView.setBackgroundColor(COLOR_BG_LIST);
        listView.setDivider(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00FFFFFF, 0x11FFFFFF, 0x00FFFFFF}
        ));
        listView.setDividerHeight(1);
        listView.setPadding(0, dpToPx(4), 0, dpToPx(4));
        listView.setClipToPadding(false);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= mSubDirs.size()) return;
                File next = mSubDirs.get(position);
                if (next != null && next.canRead()) {
                    mCurrentDir = next;
                    refreshPickerState();
                }
            }
        });

        return listView;
    }

    private View buildPickerBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bar.setBackgroundColor(0xFF15151C);
        int pad = dpToPx(16);
        bar.setPadding(pad, pad, pad, pad);

        mPathTextView = new TextView(this);
        mPathTextView.setTextColor(COLOR_TEXT_MUTED);
        mPathTextView.setTextSize(11);
        mPathTextView.setSingleLine(true);
        mPathTextView.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(mPathTextView);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancelar");
        btnCancel.setTextColor(COLOR_TEXT_PRIMARY);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setTextSize(13);
        btnCancel.setBackground(createRoundedRect(COLOR_BG_CARD_LIGHT, dpToPx(10)));
        btnCancel.setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(12));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.setMarginEnd(dpToPx(10));
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPickDialog.dismiss();
            }
        });
        bar.addView(btnCancel);

        mBtnSelectFolder = new Button(this);
        mBtnSelectFolder.setText("Usar esta pasta");
        mBtnSelectFolder.setTextColor(COLOR_BG_DARK);
        mBtnSelectFolder.setTypeface(null, Typeface.BOLD);
        mBtnSelectFolder.setTextSize(13);
        mBtnSelectFolder.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{COLOR_ACCENT, COLOR_ACCENT_DARK}
        ));
        mBtnSelectFolder.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(12));
        mBtnSelectFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isValidGameDataDirectory(mCurrentDir)) {
                    saveGameDataDirectory(mCurrentDir.getAbsolutePath());
                    mPickDialog.dismiss();
                    Toast.makeText(SimpsonsActivity.this,
                        "Pasta configurada com sucesso!", Toast.LENGTH_SHORT).show();
                    resumeNativeThread();
                } else {
                    showInvalidDirectoryAlert(mCurrentDir);
                }
            }
        });
        bar.addView(mBtnSelectFolder);

        return bar;
    }

    private void refreshPickerState() {
        if (mPathTextView != null) {
            mPathTextView.setText(mCurrentDir.getAbsolutePath());
        }
        refreshBreadcrumb();
        refreshValidityBanner();

        ViewGroup listParent = null;
        if (mPickDialog != null && mPickDialog.getWindow() != null) {
            View root = mPickDialog.getWindow().getDecorView();
            List<ListView> found = new ArrayList<>();
            collectListViews(root, found);
            if (!found.isEmpty()) {
                listParent = found.get(0);
                updateDirectoryList(found.get(0));
            }
        }
    }

    private void collectListViews(View view, List<ListView> out) {
        if (view instanceof ListView) {
            out.add((ListView) view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectListViews(vg.getChildAt(i), out);
            }
        }
    }

    private void refreshBreadcrumb() {
        if (mBreadcrumbLayout == null) return;
        mBreadcrumbLayout.removeAllViews();

        String absPath = mCurrentDir.getAbsolutePath();
        String[] parts = absPath.split("/");

        mBreadcrumbLayout.addView(buildBreadcrumbChip("\uD83C\uDFE0", "Raiz", new File("/")));

        StringBuilder accum = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            accum.append("/").append(p);
            final File target = new File(accum.toString());
            mBreadcrumbLayout.addView(buildBreadcrumbSeparator());
            mBreadcrumbLayout.addView(buildBreadcrumbChip(null, p, target));
        }
    }

    private View buildBreadcrumbChip(String icon, String label, final File target) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(createRoundedRect(0xFF1E1E28, dpToPx(8)));
        int padH = dpToPx(10);
        int padV = dpToPx(4);
        chip.setPadding(padH, padV, padH, padV);

        if (icon != null) {
            TextView iconView = new TextView(this);
            iconView.setText(icon);
            iconView.setTextSize(12);
            iconView.setPadding(0, 0, dpToPx(4), 0);
            chip.addView(iconView);
        }

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(COLOR_TEXT_PRIMARY);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        chip.addView(tv);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (target.exists() && target.canRead()) {
                    mCurrentDir = target;
                    refreshPickerState();
                }
            }
        });

        return chip;
    }

    private View buildBreadcrumbSeparator() {
        TextView sep = new TextView(this);
        sep.setText("\u203A");
        sep.setTextColor(COLOR_TEXT_MUTED);
        sep.setTextSize(16);
        sep.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        return sep;
    }

    private void refreshValidityBanner() {
        if (mValidityBanner == null) return;

        boolean valid = isValidGameDataDirectory(mCurrentDir);
        int rcfCount = countRcfFiles(mCurrentDir);
        int subDirCount = countReadableSubDirs(mCurrentDir);

        if (valid) {
            mValidityBanner.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF16301F, 0xFF0F2418}
            ));
            mValidityIcon.setText("\u2705");
            mValidityText.setTextColor(COLOR_SUCCESS);
            mValidityText.setText("Pasta válida — " + rcfCount + " arquivo(s) .rcf encontrado(s)");
        } else {
            mValidityBanner.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF2A2414, 0xFF1E1A0F}
            ));
            mValidityIcon.setText("\u26A0\uFE0F");
            mValidityText.setTextColor(COLOR_WARN);
            if (subDirCount > 0) {
                mValidityText.setText("Nenhum .rcf aqui — " + subDirCount + " subpasta(s) disponível(eis)");
            } else {
                mValidityText.setText("Nenhum .rcf aqui — navegue para outra pasta");
            }
        }
    }

    private int countRcfFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".rcf")) {
                count++;
            }
        }
        return count;
    }

    private int countReadableSubDirs(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() && !f.isHidden() && f.canRead();
            }
        });
        return files == null ? 0 : files.length;
    }

    private void updateDirectoryList(ListView listView) {
        mSubDirs.clear();
        mSubDirNames.clear();

        File parentDir = mCurrentDir.getParentFile();
        boolean hasParent = parentDir != null && parentDir.exists() && parentDir.canRead();
        if (hasParent) {
            mSubDirs.add(parentDir);
            mSubDirNames.add("..parent..");
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

        if (mSubDirs.isEmpty()) {
            mSubDirs.add(mCurrentDir);
            mSubDirNames.add("..empty..");
        }

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSubDirNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                String token = mSubDirNames.get(position);

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.TRANSPARENT);
                int padH = dpToPx(16);
                int padV = dpToPx(14);
                row.setPadding(padH, padV, padH, padV);

                TextView icon = new TextView(getContext());
                icon.setTextSize(22);
                icon.setPadding(0, 0, dpToPx(14), 0);

                LinearLayout textBlock = new LinearLayout(getContext());
                textBlock.setOrientation(LinearLayout.VERTICAL);
                textBlock.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView name = new TextView(getContext());
                name.setTextSize(15);
                name.setTypeface(null, Typeface.BOLD);

                TextView meta = new TextView(getContext());
                meta.setTextSize(11);
                meta.setPadding(0, dpToPx(2), 0, 0);

                File file = (position < mSubDirs.size()) ? mSubDirs.get(position) : null;

                if ("..parent..".equals(token)) {
                    icon.setText("\u2B06\uFE0F");
                    name.setText("Voltar para pasta anterior");
                    name.setTextColor(COLOR_ACCENT);
                    meta.setText(file != null ? file.getAbsolutePath() : "");
                    meta.setTextColor(COLOR_TEXT_MUTED);
                    row.setBackground(createRoundedRect(0x22FFD90F, dpToPx(10)));
                } else if ("..empty..".equals(token)) {
                    icon.setText("\uD83D\uDCC2");
                    name.setText("Esta pasta está vazia");
                    name.setTextColor(COLOR_TEXT_MUTED);
                    meta.setText("Use o botão \u2190 para voltar");
                    meta.setTextColor(COLOR_TEXT_MUTED);
                } else {
                    icon.setText("\uD83D\uDCC1");
                    name.setText(token);
                    name.setTextColor(COLOR_TEXT_PRIMARY);

                    int subCount = countReadableSubDirs(file);
                    int rcfCount = countRcfFiles(file);
                    String metaStr = subCount + " subpasta" + (subCount == 1 ? "" : "s");
                    if (rcfCount > 0) {
                        metaStr += "  \u2022  " + rcfCount + " .rcf";
                        meta.setTextColor(COLOR_SUCCESS);
                    } else {
                        meta.setTextColor(COLOR_TEXT_MUTED);
                    }
                    meta.setText(metaStr);
                }

                textBlock.addView(name);
                textBlock.addView(meta);
                row.addView(icon);
                row.addView(textBlock);

                TextView arrow = new TextView(getContext());
                if ("..empty..".equals(token)) {
                    arrow.setText("");
                } else {
                    arrow.setText("\u203A");
                    arrow.setTextColor(COLOR_TEXT_MUTED);
                    arrow.setTextSize(22);
                }
                row.addView(arrow);

                return row;
            }
        };
        listView.setAdapter(mAdapter);
    }

    private void showInvalidDirectoryAlert(File dir) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF18181F);
        int pad = dpToPx(24);
        panel.setPadding(pad, pad, pad, pad);

        TextView icon = new TextView(this);
        icon.setText("\u26A0\uFE0F");
        icon.setTextSize(40);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(0, 0, 0, dpToPx(12));
        panel.addView(icon);

        TextView title = new TextView(this);
        title.setText("Pasta inválida");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView path = new TextView(this);
        path.setText(dir.getAbsolutePath());
        path.setTextColor(COLOR_ACCENT);
        path.setTextSize(12);
        path.setGravity(Gravity.CENTER);
        path.setPadding(0, dpToPx(12), 0, dpToPx(12));
        panel.addView(path);

        TextView msg = new TextView(this);
        msg.setText("Esta pasta não contém arquivos .rcf do jogo (ex: scripts.rcf, art.rcf).\n\nCertifique-se de escolher a pasta que contém esses arquivos.");
        msg.setTextColor(COLOR_TEXT_SECONDARY);
        msg.setTextSize(14);
        msg.setLineSpacing(0, 1.35f);
        msg.setGravity(Gravity.CENTER);
        panel.addView(msg);

        builder.setView(panel);
        builder.setPositiveButton("Entendi", null);
        builder.show();
    }

    // ── Drawing helpers ─────────────────────────────────────────────

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private GradientDrawable createRoundedRect(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private GradientDrawable createStrokedRoundedRect(int fillColor, int strokeColor, int strokeWidthPx, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setStroke(strokeWidthPx, strokeColor);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private GradientDrawable createCircleDrawable(int color, int sizePx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setSize(sizePx, sizePx);
        return drawable;
    }

    private GradientDrawable getRoundedButtonDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(16);
        return drawable;
    }
}
