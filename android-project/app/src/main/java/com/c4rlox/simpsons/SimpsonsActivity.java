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

    // Constants for Directory Picker and Permissions
    private static final int REQUEST_CODE_MANAGE_STORAGE = 9999;
    private static final String PREFS_NAME = "SimpsonsPrefs";
    private static final String KEY_GAME_DIR = "game_data_directory";

    // Directory Picker State variables
    private File mCurrentDir;
    private AlertDialog mPickDialog;
    private ArrayAdapter<String> mAdapter;
    private final List<File> mSubDirs = new ArrayList<>();
    private final List<String> mSubDirNames = new ArrayList<>();
    private TextView mPathTextView;

    // ── Native bridge: real-time FPS from C++ game loop ──────────────
    // Called by GamepadOverlayView each frame to query smoothed FPS.
    public static native float nativeGetFPS();
    public static native int nativeGetHudContext();
    public static native boolean nativeIsTitleScreen();
    public static native void nativeSetRumbleEnabled(boolean enabled);
    public static native boolean nativeIsRumbleEnabled();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // O overlay desenha o gamepad virtual e encaminha toques ao jogo
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

    // ── Directory configuration logic ────────────────────────────────

    /**
     * Called by C++ game core via JNI to determine the game files path.
     */
    public String getGameDataPath() {
        // 1. Check SharedPreferences for custom folder
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String path = prefs.getString(KEY_GAME_DIR, "");
        if (!path.isEmpty()) {
            File dir = new File(path);
            if (isValidGameDataDirectory(dir)) {
                return path.endsWith("/") ? path : path + "/";
            }
        }

        // 2. Check default directory (backward compatibility)
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

        // Verify if it contains game RCF packages (e.g. scripts.rcf or art.rcf or dialog*.rcf)
        File scripts = new File(dir, "scripts.rcf");
        File art = new File(dir, "art.rcf");
        if (scripts.exists() || art.exists()) {
            return true;
        }

        // Case insensitive fallback check
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

    // ── Setup UI and Picker Dialog ──────────────────────────────────

    private void showSetupScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSetupOverlay != null) {
                    mLayout.removeView(mSetupOverlay);
                }

                mSetupOverlay = new RelativeLayout(SimpsonsActivity.this);
                mSetupOverlay.setBackgroundColor(Color.parseColor("#121216"));

                LinearLayout centerLayout = new LinearLayout(SimpsonsActivity.this);
                centerLayout.setOrientation(LinearLayout.VERTICAL);
                centerLayout.setGravity(Gravity.CENTER);

                TextView title = new TextView(SimpsonsActivity.this);
                title.setText("The Simpsons Hit & Run");
                title.setTextColor(Color.parseColor("#FFD90F"));
                title.setTextSize(24);
                title.setTypeface(null, Typeface.BOLD);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, 0, 0, 16);
                centerLayout.addView(title);

                TextView desc = new TextView(SimpsonsActivity.this);
                desc.setText("Selecione a pasta onde estão os arquivos do jogo (.rcf) para iniciar.");
                desc.setTextColor(Color.parseColor("#A0A0B0"));
                desc.setTextSize(16);
                desc.setGravity(Gravity.CENTER);
                desc.setPadding(32, 0, 32, 48);
                centerLayout.addView(desc);

                Button btnSelect = new Button(SimpsonsActivity.this);
                btnSelect.setText("Selecionar Pasta do Jogo");
                btnSelect.setTextColor(Color.parseColor("#121216"));
                btnSelect.setTypeface(null, Typeface.BOLD);
                btnSelect.setTextSize(18);
                btnSelect.setBackground(getRoundedButtonDrawable(Color.parseColor("#FFD90F")));
                btnSelect.setPadding(48, 24, 48, 24);
                btnSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestStoragePermissionAndPickDirectory();
                    }
                });
                centerLayout.addView(btnSelect);

                RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                mSetupOverlay.addView(centerLayout, centerParams);

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

    private void showDirectoryPickerDialog() {
        mCurrentDir = new File("/storage/emulated/0");
        if (!mCurrentDir.exists() || !mCurrentDir.canRead()) {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1E1E24"));
        layout.setPadding(32, 32, 32, 32);

        TextView titleView = new TextView(this);
        titleView.setText("Selecionar Pasta do Jogo");
        titleView.setTextColor(Color.parseColor("#FFD90F"));
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 24);
        layout.addView(titleView);

        mPathTextView = new TextView(this);
        mPathTextView.setTextColor(Color.parseColor("#E0E0E0"));
        mPathTextView.setTextSize(14);
        mPathTextView.setPadding(0, 0, 0, 16);
        layout.addView(mPathTextView);

        ListView listView = new ListView(this);
        listView.setBackgroundColor(Color.parseColor("#2A2A32"));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        layout.addView(listView);

        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.END);
        buttonsLayout.setPadding(0, 24, 0, 0);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancelar");
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setBackground(getRoundedButtonDrawable(Color.parseColor("#424250")));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.setMargins(0, 0, 16, 0);
        btnCancel.setLayoutParams(cancelParams);
        buttonsLayout.addView(btnCancel);

        Button btnSelect = new Button(this);
        btnSelect.setText("Selecionar Esta Pasta");
        btnSelect.setTextColor(Color.parseColor("#1E1E24"));
        btnSelect.setTypeface(null, Typeface.BOLD);
        btnSelect.setBackground(getRoundedButtonDrawable(Color.parseColor("#FFD90F")));
        buttonsLayout.addView(btnSelect);

        layout.addView(buttonsLayout);

        builder.setView(layout);
        mPickDialog = builder.create();

        updateDirectoryList(listView);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedName = mSubDirNames.get(position);
                if (selectedName.equals("📁 .. [Ir para pasta anterior]")) {
                    File parentDir = mCurrentDir.getParentFile();
                    if (parentDir != null && parentDir.canRead()) {
                        mCurrentDir = parentDir;
                        updateDirectoryList(listView);
                    }
                } else {
                    File nextDir = mSubDirs.get(position);
                    mCurrentDir = nextDir;
                    updateDirectoryList(listView);
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
    }

    private void updateDirectoryList(ListView listView) {
        mPathTextView.setText("Caminho atual:\n" + mCurrentDir.getAbsolutePath());
        mSubDirs.clear();
        mSubDirNames.clear();

        File parentDir = mCurrentDir.getParentFile();
        if (parentDir != null && parentDir.exists() && parentDir.canRead()) {
            mSubDirs.add(parentDir);
            mSubDirNames.add("📁 .. [Ir para pasta anterior]");
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
                mSubDirNames.add("📁 " + d.getName());
            }
        }

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSubDirNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(16);
                view.setPadding(24, 24, 24, 24);
                return view;
            }
        };
        listView.setAdapter(mAdapter);
    }

    private void showInvalidDirectoryAlert(File dir) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pasta Inválida")
               .setMessage("A pasta selecionada:\n" + dir.getAbsolutePath() + "\n\nnão contém arquivos .rcf do jogo (ex: scripts.rcf, art.rcf).\n\nCertifique-se de selecionar a pasta que contém estes arquivos.")
               .setPositiveButton("OK", null)
               .show();
    }

    private GradientDrawable getRoundedButtonDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(16);
        return drawable;
    }
}
