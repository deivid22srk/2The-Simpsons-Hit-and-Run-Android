package com.c4rlox.simpsons;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
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

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpsonsActivity extends SDLActivity {

    private GamepadOverlayView mOverlay;
    private RelativeLayout mSetupOverlay;

    private static final int REQUEST_CODE_MANAGE_STORAGE = 9999;
    private static final String PREFS_NAME = "SimpsonsPrefs";
    private static final String KEY_GAME_DIR = "game_data_directory";

    private File mCurrentDir;
    private AlertDialog mPickDialog;
    private ArrayAdapter<String> mAdapter;
    private final List<File> mSubDirs = new ArrayList<>();
    private final List<String> mSubDirNames = new ArrayList<>();
    private TextView mPathTextView;
    private TextView mErrorTextView;

    private int mDynamicPrimary = 0xFFD90F;
    private int mDynamicOnPrimary = 0xFF121216;
    private int mDynamicSurface = 0xFF1A1A24;
    private int mDynamicSurfaceVariant = 0xFF252533;
    private int mDynamicOnSurface = 0xFFE8E8F0;
    private int mDynamicOnSurfaceDim = 0xFF9090A0;
    private int mDynamicError = 0xFFE57373;

    public static native float nativeGetFPS();
    public static native int nativeGetHudContext();
    public static native boolean nativeIsTitleScreen();
    public static native void nativeSetRumbleEnabled(boolean enabled);
    public static native boolean nativeIsRumbleEnabled();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        extractDynamicColors();

        mOverlay = new GamepadOverlayView(this);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );

        mLayout.addView(mOverlay, lp);
    }

    private void extractDynamicColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                int[] attrs = {
                    android.R.attr.colorPrimary,
                    android.R.attr.colorOnPrimary,
                    android.R.attr.colorSurface,
                    android.R.attr.colorOnSurface
                };
                TypedArray ta = getTheme().obtainStyledAttributes(attrs);
                mDynamicPrimary = ta.getColor(0, mDynamicPrimary);
                mDynamicOnPrimary = ta.getColor(1, mDynamicOnPrimary);
                mDynamicSurface = ta.getColor(2, mDynamicSurface);
                mDynamicOnSurface = ta.getColor(3, mDynamicOnSurface);
                ta.recycle();
            } catch (Exception ignored) {}
        }
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

    private void showSetupScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSetupOverlay != null) {
                    mLayout.removeView(mSetupOverlay);
                }

                mSetupOverlay = new RelativeLayout(SimpsonsActivity.this);
                mSetupOverlay.setBackgroundColor(mDynamicSurface);

                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                );

                LinearLayout centerLayout = new LinearLayout(SimpsonsActivity.this);
                centerLayout.setOrientation(LinearLayout.VERTICAL);
                centerLayout.setGravity(Gravity.CENTER);
                centerLayout.setPadding(
                    dpToPx(32), dpToPx(16),
                    dpToPx(32), dpToPx(16)
                );

                TextView title = new TextView(SimpsonsActivity.this);
                title.setText("The Simpsons Hit & Run");
                title.setTextColor(mDynamicPrimary);
                title.setTextSize(26);
                title.setTypeface(null, Typeface.BOLD);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, 0, 0, dpToPx(4));
                centerLayout.addView(title);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    title.setShadowLayer(12f, 0f, 2f, Color.argb(60, 0, 0, 0));
                }

                TextView subtitle = new TextView(SimpsonsActivity.this);
                subtitle.setText("Android Port");
                subtitle.setTextColor(mDynamicOnSurfaceDim);
                subtitle.setTextSize(14);
                subtitle.setTypeface(null, Typeface.NORMAL);
                subtitle.setGravity(Gravity.CENTER);
                subtitle.setPadding(0, 0, 0, dpToPx(32));
                centerLayout.addView(subtitle);

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(mDynamicSurfaceVariant);
                cardBg.setCornerRadius(dpToPx(16));
                cardBg.setStroke(dpToPx(1), Color.argb(20, 255, 255, 255));

                LinearLayout card = new LinearLayout(SimpsonsActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(cardBg);
                card.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
                card.setElevation(dpToPx(4));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    card.setElevation(dpToPx(4));
                }

                RelativeLayout.LayoutParams cardParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                cardParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                cardParams.setMargins(dpToPx(16), 0, dpToPx(16), 0);

                TextView cardTitle = new TextView(SimpsonsActivity.this);
                cardTitle.setText("Bem-vindo!");
                cardTitle.setTextColor(mDynamicOnSurface);
                cardTitle.setTextSize(18);
                cardTitle.setTypeface(null, Typeface.BOLD);
                cardTitle.setPadding(0, 0, 0, dpToPx(8));
                card.addView(cardTitle);

                TextView cardDesc = new TextView(SimpsonsActivity.this);
                cardDesc.setText("Selecione a pasta onde estão os arquivos do jogo (.rcf) para começar a jogar.");
                cardDesc.setTextColor(mDynamicOnSurfaceDim);
                cardDesc.setTextSize(14);
                cardDesc.setLineSpacing(dpToPx(4), 1f);
                cardDesc.setPadding(0, 0, 0, dpToPx(24));
                card.addView(cardDesc);

                Button btnSelect = createMaterialButton(
                    "Selecionar Pasta do Jogo",
                    mDynamicPrimary,
                    mDynamicOnPrimary
                );
                btnSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestStoragePermissionAndPickDirectory();
                    }
                });
                card.addView(btnSelect);

                centerLayout.addView(card);

                mSetupOverlay.addView(centerLayout, lp);
                mLayout.addView(mSetupOverlay, lp);
            }
        });
    }

    private Button createMaterialButton(String text, int bgColor, int textColor) {
        Button btn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextSize(15);
        btn.setPadding(dpToPx(24), dpToPx(14), dpToPx(24), dpToPx(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dpToPx(28));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                Color.argb(30, 0, 0, 0),
                bg,
                null
            );
            btn.setBackground(ripple);
        } else {
            btn.setBackground(bg);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setElevation(dpToPx(2));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(params);

        return btn;
    }

    private Button createOutlineButton(String text) {
        Button btn = new Button(this, null, android.R.attr.borderlessButtonStyle);
        btn.setText(text);
        btn.setTextColor(mDynamicOnSurface);
        btn.setTypeface(null, Typeface.NORMAL);
        btn.setTextSize(14);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dpToPx(28));
        bg.setStroke(dpToPx(1), Color.argb(40, 255, 255, 255));
        btn.setBackground(bg);
        btn.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));

        return btn;
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
        layout.setBackgroundColor(mDynamicSurface);
        layout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(mDynamicSurface);
        dialogBg.setCornerRadius(dpToPx(28));

        RelativeLayout headerLayout = new RelativeLayout(this);
        headerLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(12));

        TextView titleView = new TextView(this);
        titleView.setText("Selecionar Pasta do Jogo");
        titleView.setTextColor(mDynamicOnSurface);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setId(View.generateViewId());
        headerLayout.addView(titleView);

        // Path breadcrumb
        mPathTextView = new TextView(this);
        mPathTextView.setTextColor(mDynamicPrimary);
        mPathTextView.setTextSize(12);
        mPathTextView.setTypeface(null, Typeface.NORMAL);
        mPathTextView.setPadding(0, dpToPx(4), 0, dpToPx(12));
        mPathTextView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        mPathTextView.setSingleLine(true);
        mPathTextView.setSelected(true);
        headerLayout.addView(mPathTextView);

        RelativeLayout.LayoutParams titleParams = (RelativeLayout.LayoutParams) mPathTextView.getLayoutParams();
        if (titleParams != null) {
            titleParams.addRule(RelativeLayout.BELOW, titleView.getId());
        } else {
            titleParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.addRule(RelativeLayout.BELOW, titleView.getId());
            mPathTextView.setLayoutParams(titleParams);
        }

        layout.addView(headerLayout);

        // Error text
        mErrorTextView = new TextView(this);
        mErrorTextView.setTextColor(mDynamicError);
        mErrorTextView.setTextSize(12);
        mErrorTextView.setPadding(dpToPx(20), 0, dpToPx(20), dpToPx(8));
        mErrorTextView.setVisibility(View.GONE);
        layout.addView(mErrorTextView);

        GradientDrawable listBg = new GradientDrawable();
        listBg.setColor(mDynamicSurfaceVariant);
        listBg.setCornerRadius(dpToPx(12));

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setBackground(listBg);
        listContainer.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        ListView listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(Color.TRANSPARENT);
        listView.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        listView.setClipToPadding(false);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listView.setLayoutParams(listParams);
        listContainer.addView(listView);

        layout.addView(listContainer);

        // Buttons
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.END);
        buttonsLayout.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(12));

        Button btnCancel = createOutlineButton("Cancelar");
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelParams.setMargins(0, 0, dpToPx(8), 0);
        btnCancel.setLayoutParams(cancelParams);
        buttonsLayout.addView(btnCancel);

        Button btnSelect = createMaterialButton(
            "Selecionar Esta Pasta",
            mDynamicPrimary,
            mDynamicOnPrimary
        );
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnSelect.setLayoutParams(selectParams);
        buttonsLayout.addView(btnSelect);

        layout.addView(buttonsLayout);

        builder.setView(layout);
        mPickDialog = builder.create();

        if (mPickDialog.getWindow() != null) {
            mPickDialog.getWindow().setBackgroundDrawable(dialogBg);
        }

        updateDirectoryList(listView);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mErrorTextView.setVisibility(View.GONE);
                String selectedName = mSubDirNames.get(position);
                if (selectedName.endsWith("..")) {
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
                    mErrorTextView.setText("Está pasta não contém arquivos .rcf do jogo.");
                    mErrorTextView.setVisibility(View.VISIBLE);
                }
            }
        });

        mPickDialog.show();
    }

    private void updateDirectoryList(ListView listView) {
        mPathTextView.setText(mCurrentDir.getAbsolutePath());
        mSubDirs.clear();
        mSubDirNames.clear();

        File parentDir = mCurrentDir.getParentFile();
        if (parentDir != null && parentDir.exists() && parentDir.canRead()) {
            mSubDirs.add(parentDir);
            mSubDirNames.add("  \uD83D\uDCC1  ..");
        }

        List<File> dirList = new ArrayList<>();
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden() && f.canRead()) {
                    dirList.add(f);
                }
            }
        }
        Collections.sort(dirList, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

        for (File d : dirList) {
            mSubDirs.add(d);
            String icon = "  \uD83D\uDCC2  ";
            mSubDirNames.add(icon + d.getName());
        }

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mSubDirNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view;
                if (convertView == null) {
                    view = new TextView(SimpsonsActivity.this);
                    view.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
                    view.setTextSize(15);
                    view.setTypeface(null, Typeface.NORMAL);

                    GradientDrawable itemBg = new GradientDrawable();
                    itemBg.setColor(Color.TRANSPARENT);
                    itemBg.setCornerRadius(dpToPx(10));
                    view.setBackground(itemBg);
                    view.setTag(itemBg);
                } else {
                    view = (TextView) convertView;
                }

                view.setText(getItem(position));
                view.setTextColor(mDynamicOnSurface);
                view.setElevation(0f);

                if (getItem(position).endsWith("..")) {
                    view.setTypeface(null, Typeface.ITALIC);
                    view.setTextColor(mDynamicOnSurfaceDim);
                } else {
                    view.setTypeface(null, Typeface.NORMAL);
                    if (position > 0 && position < mSubDirs.size() && isValidGameDataDirectory(mSubDirs.get(position))) {
                        view.setTextColor(mDynamicPrimary);
                    }
                }

                return view;
            }
        };
        listView.setAdapter(mAdapter);
    }

    private GradientDrawable getRoundedButtonDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(16));
        return drawable;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        );
    }
}
