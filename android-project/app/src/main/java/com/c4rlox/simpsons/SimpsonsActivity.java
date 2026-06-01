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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.libsdl.app.R;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private static final int SIMPSONS_GREEN = 0xFF00B894;

    // Save manager
    private static final int NUM_SAVE_SLOTS = 4;
    private static final String[] SAVE_FILE_NAMES = {"Save1", "Save2", "Save3", "Save4"};
    private static final int REQUEST_CODE_EXPORT_SAVE = 2001;
    private static final int REQUEST_CODE_IMPORT_SAVE = 2002;
    private RelativeLayout mSaveMgrOverlay;
    private int mSelectedSlotForExport = -1;
    private int mSelectedSlotForImport = -1;

    public static native float nativeGetFPS();

    public static native int nativeGetHudContext();

    public static native boolean nativeIsTitleScreen();

    public static native void nativeSetRumbleEnabled(boolean enabled);

    public static native void nativeSetFPSCap(int fps);

    public static native void nativeSetLsfgEnabled(boolean enabled);
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

        // Show fork notice dialog
        String title, message, btnText;
        String lang = Locale.getDefault().getLanguage();
        if ("pt".equals(lang)) {
            title = "Aviso de Fork";
            message = "Olá, eu criei este fork usando IA. Este projeto existe apenas porque achei o trabalho do Carlox33 muito bom. Todo o crédito pelo port vai para o projeto original: https://github.com/Carlox33/The-Simpsons-Hit-and-Run-Android Meu objetivo não é ganhar fama ou me passar pelo desenvolvedor original; apenas quis explorar e aprender com o projeto.";
            btnText = "Entendido";
        } else if ("es".equals(lang)) {
            title = "Aviso de Fork";
            message = "Hola, creé este fork usando IA. Este proyecto solo existe porque consideré que el trabajo de Carlox33 es muy bueno. Todo el crédito por el port va para el proyecto original: https://github.com/Carlox33/The-Simpsons-Hit-and-Run-Android Mi objetivo no es ganar fama ni hacerme pasar por el desarrollador original; solo quería explorar y aprender con el proyecto.";
            btnText = "Entendido";
        } else {
            title = "Fork Notice";
            message = "Hello, I created this fork using AI. This project only exists because I thought Carlox33's work was very good. All credit for the port goes to the original project: https://github.com/Carlox33/The-Simpsons-Hit-and-Run-Android My goal is not to gain fame or impersonate the original developer; I just wanted to explore and learn from the project.";
            btnText = "Understood";
        }

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(btnText, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .setCancelable(false)
            .show();
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
        } else if (requestCode == REQUEST_CODE_EXPORT_SAVE && resultCode == RESULT_OK && data != null) {
            handleSaveExportResult(data.getData());
        } else if (requestCode == REQUEST_CODE_IMPORT_SAVE && resultCode == RESULT_OK && data != null) {
            handleSaveImportResult(data.getData());
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
                removeOverlay(mSaveMgrOverlay);
                mSetupOverlay = null;
                mPickerOverlay = null;
                mSaveMgrOverlay = null;
            });
            super.resumeNativeThread();
        } else {
            showSetupScreen();
        }
    }

    public void showSaveManager() {
        runOnUiThread(this::buildSaveManagerScreen);
    }

    private String getSaveDirectory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String path = prefs.getString(KEY_GAME_DIR, "");
        if (!path.isEmpty()) {
            return path.endsWith("/") ? path : path + "/";
        }
        File defaultDir = getExternalFilesDir(null);
        if (defaultDir != null) {
            return defaultDir.getAbsolutePath() + "/";
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    }

    private static class SaveSlotInfo {
        boolean exists;
        long size;
        long lastModified;
        String slotName;
        int slotIndex;
    }

    private List<SaveSlotInfo> getSaveSlots() {
        String dir = getSaveDirectory();
        List<SaveSlotInfo> slots = new ArrayList<>();
        for (int i = 0; i < NUM_SAVE_SLOTS; i++) {
            SaveSlotInfo info = new SaveSlotInfo();
            info.slotIndex = i;
            info.slotName = SAVE_FILE_NAMES[i];
            File f = new File(dir, SAVE_FILE_NAMES[i]);
            info.exists = f.exists() && f.isFile();
            if (info.exists) {
                info.size = f.length();
                info.lastModified = f.lastModified();
            }
            slots.add(info);
        }
        return slots;
    }

    private void buildSaveManagerScreen() {
        removeOverlay(mSaveMgrOverlay);
        mSaveMgrOverlay = new RelativeLayout(this);
        mSaveMgrOverlay.setBackgroundColor(SURFACE_DARK);
        mSaveMgrOverlay.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));

        // Header
        RelativeLayout header = new RelativeLayout(this);
        header.setPadding(dp(4), dp(4), dp(4), dp(12));

        Button backBtn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        backBtn.setText("\u2190");
        backBtn.setTextColor(TEXT_PRIMARY);
        backBtn.setTextSize(22);
        backBtn.setTypeface(null, Typeface.NORMAL);
        backBtn.setBackground(roundedBg(Color.TRANSPARENT, 0));
        backBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        backBtn.setId(View.generateViewId());
        backBtn.setOnClickListener(v -> {
            removeOverlay(mSaveMgrOverlay);
            mSaveMgrOverlay = null;
        });
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(getString(R.string.save_manager_title));
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        RelativeLayout.LayoutParams titleLp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        title.setLayoutParams(titleLp);
        header.addView(title);
        root.addView(header);

        // Scrollable slot list
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(0, dp(8), 0, 0);

        LinearLayout slotsContainer = new LinearLayout(this);
        slotsContainer.setOrientation(LinearLayout.VERTICAL);

        List<SaveSlotInfo> slots = getSaveSlots();
        boolean anyExists = false;

        for (int i = 0; i < slots.size(); i++) {
            SaveSlotInfo info = slots.get(i);
            if (info.exists) anyExists = true;

            GradientDrawable cardBg = roundedBg(SURFACE_CARD, dp(14));
            cardBg.setStroke(dp(1), Color.argb(18, 255, 255, 255));

            LinearLayout slotCard = new LinearLayout(this);
            slotCard.setOrientation(LinearLayout.VERTICAL);
            slotCard.setBackground(cardBg);
            slotCard.setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, dp(10));
            slotCard.setLayoutParams(cardLp);

            // Slot title row
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(0, 0, 0, dp(6));

            TextView slotLabel = new TextView(this);
            slotLabel.setText(String.format("%s %d", getString(R.string.slot_prefix), i + 1));
            slotLabel.setTextColor(TEXT_PRIMARY);
            slotLabel.setTextSize(16);
            slotLabel.setTypeface(null, Typeface.BOLD);
            titleRow.addView(slotLabel);

            TextView statusLabel = new TextView(this);
            if (info.exists) {
                statusLabel.setText(getString(R.string.save_occupied));
                statusLabel.setTextColor(SIMPSONS_GREEN);
            } else {
                statusLabel.setText(getString(R.string.save_empty));
                statusLabel.setTextColor(TEXT_SECONDARY);
            }
            statusLabel.setTextSize(13);
            statusLabel.setPadding(dp(12), 0, 0, 0);
            titleRow.addView(statusLabel);
            slotCard.addView(titleRow);

            if (info.exists) {
                // Size and date
                TextView detailText = new TextView(this);
                String sizeStr = formatFileSize(info.size);
                String dateStr = formatDate(info.lastModified);
                detailText.setText(String.format("%s | %s", sizeStr, dateStr));
                detailText.setTextColor(TEXT_SECONDARY);
                detailText.setTextSize(12);
                detailText.setPadding(0, 0, 0, dp(10));
                slotCard.addView(detailText);

                // Action buttons row
                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setGravity(Gravity.END);

                Button exportBtn = makeSmallActionButton(getString(R.string.export_save));
                exportBtn.setOnClickListener(v -> exportSave(info.slotIndex));
                actionRow.addView(exportBtn);

                Button importBtn = makeSmallActionButton(getString(R.string.import_save));
                importBtn.setOnClickListener(v -> importSave(info.slotIndex));
                importBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
                LinearLayout.LayoutParams importLp = (LinearLayout.LayoutParams) importBtn.getLayoutParams();
                if (importLp != null) importLp.setMarginEnd(dp(8));
                actionRow.addView(importBtn);

                Button deleteBtn = makeSmallActionButton(getString(R.string.delete_save));
                deleteBtn.setTextColor(TEXT_ERROR);
                deleteBtn.setOnClickListener(v -> confirmDeleteSave(info.slotIndex));
                actionRow.addView(deleteBtn);

                slotCard.addView(actionRow);
            } else {
                // Empty slot - import button only
                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRow.setGravity(Gravity.END);

                Button importBtn = makeSmallActionButton(getString(R.string.import_save));
                importBtn.setOnClickListener(v -> importSave(info.slotIndex));
                actionRow.addView(importBtn);
                slotCard.addView(actionRow);
            }

            slotsContainer.addView(slotCard);
        }

        if (!anyExists) {
            TextView emptyText = new TextView(this);
            emptyText.setText(getString(R.string.no_saves_found));
            emptyText.setTextColor(TEXT_SECONDARY);
            emptyText.setTextSize(14);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dp(40), 0, dp(40));
            slotsContainer.addView(emptyText);
        }

        scroll.addView(slotsContainer);
        root.addView(scroll);

        mSaveMgrOverlay.addView(root, new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLayout.addView(mSaveMgrOverlay);
    }

    private Button makeSmallActionButton(String text) {
        Button btn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btn.setText(text);
        btn.setTextColor(TEXT_PRIMARY);
        btn.setTextSize(12);
        btn.setTypeface(null, Typeface.NORMAL);
        GradientDrawable bg = roundedBg(Color.TRANSPARENT, dp(16));
        bg.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        btn.setBackground(bg);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        btn.setLayoutParams(lp);
        return btn;
    }

    private void exportSave(int slotIndex) {
        if (!hasStoragePermission()) {
            Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
            return;
        }
        mSelectedSlotForExport = slotIndex;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, SAVE_FILE_NAMES[slotIndex]);
        startActivityForResult(intent, REQUEST_CODE_EXPORT_SAVE);
    }

    private void importSave(int slotIndex) {
        if (!hasStoragePermission()) {
            Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_SHORT).show();
            return;
        }
        mSelectedSlotForImport = slotIndex;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_SAVE);
    }

    private void handleSaveExportResult(Uri uri) {
        if (mSelectedSlotForExport < 0) return;
        try {
            String dir = getSaveDirectory();
            File src = new File(dir, SAVE_FILE_NAMES[mSelectedSlotForExport]);
            if (!src.exists()) {
                Toast.makeText(this, getString(R.string.no_saves_found), Toast.LENGTH_SHORT).show();
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                FileInputStream is = new FileInputStream(src);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                is.close();
                os.close();
                Toast.makeText(this, getString(R.string.save_exported), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.invalid_save_file), Toast.LENGTH_SHORT).show();
        }
        mSelectedSlotForExport = -1;
    }

    private void handleSaveImportResult(Uri uri) {
        if (mSelectedSlotForImport < 0) return;
        try {
            String dir = getSaveDirectory();
            File dst = new File(dir, SAVE_FILE_NAMES[mSelectedSlotForImport]);
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                FileOutputStream os = new FileOutputStream(dst);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                os.close();
                is.close();
                Toast.makeText(this, getString(R.string.save_imported), Toast.LENGTH_SHORT).show();
                buildSaveManagerScreen();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.invalid_save_file), Toast.LENGTH_SHORT).show();
        }
        mSelectedSlotForImport = -1;
    }

    private void confirmDeleteSave(int slotIndex) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message))
            .setPositiveButton(getString(R.string.delete_save), (dialog, which) -> doDeleteSave(slotIndex))
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void doDeleteSave(int slotIndex) {
        String dir = getSaveDirectory();
        File f = new File(dir, SAVE_FILE_NAMES[slotIndex]);
        if (f.exists() && f.delete()) {
            Toast.makeText(this, getString(R.string.save_deleted), Toast.LENGTH_SHORT).show();
            buildSaveManagerScreen();
        } else {
            Toast.makeText(this, getString(R.string.invalid_save_file), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
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
