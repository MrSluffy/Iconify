package com.drdisagree.iconify.ui.activity;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
import static com.drdisagree.iconify.common.References.HEADER_CLOCK_QSTOPMARGIN;
import static com.drdisagree.iconify.common.References.HEADER_CLOCK_SIDEMARGIN;
import static com.drdisagree.iconify.common.References.HEADER_CLOCK_STYLE;
import static com.drdisagree.iconify.common.References.HEADER_CLOCK_SWITCH;
import static com.drdisagree.iconify.common.References.HEADER_CLOCK_TOPMARGIN;
import static com.drdisagree.iconify.common.References.HEADER_IMAGE_ALPHA;
import static com.drdisagree.iconify.common.References.HEADER_IMAGE_HEIGHT;
import static com.drdisagree.iconify.common.References.HEADER_IMAGE_SWITCH;
import static com.drdisagree.iconify.common.References.HIDE_STATUS_ICONS_SWITCH;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.drdisagree.iconify.Iconify;
import com.drdisagree.iconify.R;
import com.drdisagree.iconify.common.References;
import com.drdisagree.iconify.config.RemotePrefs;
import com.drdisagree.iconify.utils.SystemUtil;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Experimental extends AppCompatActivity {

    private static final int PICKFILE_RESULT_CODE = 100;
    List<String> accurate_sh = Shell.cmd("settings get secure monet_engine_accurate_shades").exec().getOut();
    int shade = initialize_shade();
    private Button enable_header_image;

    private static String getRealPathFromURI(Uri uri) {
        File file = null;
        try {
            @SuppressLint("Recycle") Cursor returnCursor = Iconify.getAppContext().getContentResolver().query(uri, null, null, null, null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            String name = returnCursor.getString(nameIndex);
            file = new File(Iconify.getAppContext().getFilesDir(), name);
            @SuppressLint("Recycle") InputStream inputStream = Iconify.getAppContext().getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            int read = 0;
            int maxBufferSize = 1024 * 1024;
            int bytesAvailable = inputStream.available();
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            final byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return file.getPath();
    }

    @SuppressLint({"SetTextI18n", "CommitPrefEdits"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experimental);

        // Header
        CollapsingToolbarLayout collapsing_toolbar = findViewById(R.id.collapsing_toolbar);
        collapsing_toolbar.setTitle(getResources().getString(R.string.activity_title_experimental));
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Accurate Shades
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch enable_accurate_shades = findViewById(R.id.enable_accurate_shades);
        enable_accurate_shades.setChecked(shade != 0);
        enable_accurate_shades.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Shell.cmd("settings put secure monet_engine_accurate_shades 1").exec();
            } else {
                Shell.cmd("settings put secure monet_engine_accurate_shades 0").exec();
            }
        });

        // Header image picker
        Button pick_header_image = findViewById(R.id.pick_header_image);
        pick_header_image.setOnClickListener(v -> {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent();
                intent.setAction(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", Iconify.getAppContext().getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            } else {
                browseHeaderImage();
            }
        });

        Button disable_header_image = findViewById(R.id.disable_header_image);
        disable_header_image.setVisibility(RemotePrefs.getBoolean(HEADER_IMAGE_SWITCH, false) ? View.VISIBLE : View.GONE);

        enable_header_image = findViewById(R.id.enable_header_image);
        enable_header_image.setOnClickListener(v -> {
            RemotePrefs.putBoolean(HEADER_IMAGE_SWITCH, true);
            new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            enable_header_image.setVisibility(View.GONE);
            disable_header_image.setVisibility(View.VISIBLE);
        });

        disable_header_image.setOnClickListener(v -> {
            RemotePrefs.putBoolean(HEADER_IMAGE_SWITCH, false);
            new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            disable_header_image.setVisibility(View.GONE);
        });

        // Image height
        SeekBar image_height_seekbar = findViewById(R.id.image_height_seekbar);
        image_height_seekbar.setPadding(0, 0, 0, 0);
        TextView image_height_output = findViewById(R.id.image_height_output);
        image_height_output.setText(getResources().getString(R.string.opt_selected) + ' ' + RemotePrefs.getInt(HEADER_IMAGE_HEIGHT, 108) + "dp");
        image_height_seekbar.setProgress(RemotePrefs.getInt(HEADER_IMAGE_HEIGHT, 108));
        final int[] imageHeight = {RemotePrefs.getInt(HEADER_IMAGE_HEIGHT, 108)};
        image_height_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                imageHeight[0] = progress;
                image_height_output.setText(getResources().getString(R.string.opt_selected) + ' ' + progress + "dp");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RemotePrefs.putInt(HEADER_IMAGE_HEIGHT, imageHeight[0]);
                new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            }
        });

        // Image alpha
        SeekBar image_alpha_seekbar = findViewById(R.id.image_alpha_seekbar);
        image_alpha_seekbar.setPadding(0, 0, 0, 0);
        TextView image_alpha_output = findViewById(R.id.image_alpha_output);
        image_alpha_output.setText(getResources().getString(R.string.opt_selected) + ' ' + RemotePrefs.getInt(HEADER_IMAGE_ALPHA, 100) + "%");
        image_alpha_seekbar.setProgress(RemotePrefs.getInt(HEADER_IMAGE_ALPHA, 100));
        final int[] imageAlpha = {RemotePrefs.getInt(HEADER_IMAGE_ALPHA, 100)};
        image_alpha_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                imageAlpha[0] = progress;
                image_alpha_output.setText(getResources().getString(R.string.opt_selected) + ' ' + progress + "%");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RemotePrefs.putInt(HEADER_IMAGE_ALPHA, imageAlpha[0]);
                new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            }
        });

        // Hide status icons
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch hide_status_icons = findViewById(R.id.hide_status_icons);
        hide_status_icons.setChecked(RemotePrefs.getBoolean(HIDE_STATUS_ICONS_SWITCH, false));
        hide_status_icons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RemotePrefs.putBoolean(HIDE_STATUS_ICONS_SWITCH, isChecked);
            new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
        });

        // Custom header clock
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch enable_header_clock = findViewById(R.id.enable_header_clock);
        enable_header_clock.setChecked(RemotePrefs.getBoolean(HEADER_CLOCK_SWITCH, false));
        enable_header_clock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            RemotePrefs.putBoolean(HEADER_CLOCK_SWITCH, isChecked);
            new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
        });

        // Header clock style
        final Spinner header_clock_style = findViewById(R.id.header_clock_style);
        List<String> hcclock_styles = new ArrayList<>();
        hcclock_styles.add("Style 1");
        hcclock_styles.add("Style 2");

        ArrayAdapter<String> hcclock_styles_adapter = new ArrayAdapter<>(this, R.layout.simple_spinner_item, hcclock_styles);
        hcclock_styles_adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        header_clock_style.setAdapter(hcclock_styles_adapter);

        header_clock_style.setSelection(RemotePrefs.getInt(HEADER_CLOCK_STYLE, 0));
        header_clock_style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                RemotePrefs.putInt(HEADER_CLOCK_STYLE, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Header clock side margin
        SeekBar header_clock_side_margin_seekbar = findViewById(R.id.header_clock_side_margin_seekbar);
        header_clock_side_margin_seekbar.setPadding(0, 0, 0, 0);
        TextView header_clock_side_margin_output = findViewById(R.id.header_clock_side_margin_output);
        header_clock_side_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + RemotePrefs.getInt(HEADER_CLOCK_SIDEMARGIN, 0) + "dp");
        header_clock_side_margin_seekbar.setProgress(RemotePrefs.getInt(HEADER_CLOCK_SIDEMARGIN, 0));
        final int[] sideMargin = {RemotePrefs.getInt(HEADER_CLOCK_SIDEMARGIN, 0)};
        header_clock_side_margin_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sideMargin[0] = progress;
                header_clock_side_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + progress + "dp");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RemotePrefs.putInt(HEADER_CLOCK_SIDEMARGIN, sideMargin[0]);
                new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            }
        });

        // Header clock top margin
        SeekBar header_clock_top_margin_seekbar = findViewById(R.id.header_clock_top_margin_seekbar);
        header_clock_top_margin_seekbar.setPadding(0, 0, 0, 0);
        TextView header_clock_top_margin_output = findViewById(R.id.header_clock_top_margin_output);
        header_clock_top_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + RemotePrefs.getInt(HEADER_CLOCK_TOPMARGIN, 8) + "dp");
        header_clock_top_margin_seekbar.setProgress(RemotePrefs.getInt(HEADER_CLOCK_TOPMARGIN, 8));
        final int[] topMargin = {RemotePrefs.getInt(HEADER_CLOCK_TOPMARGIN, 8)};
        header_clock_top_margin_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                topMargin[0] = progress;
                header_clock_top_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + progress + "dp");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RemotePrefs.putInt(HEADER_CLOCK_TOPMARGIN, topMargin[0]);
                new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            }
        });

        // QS panel top margin
        SeekBar header_clock_qs_top_margin_seekbar = findViewById(R.id.header_clock_qs_top_margin_seekbar);
        header_clock_qs_top_margin_seekbar.setPadding(0, 0, 0, 0);
        TextView header_clock_qs_top_margin_output = findViewById(R.id.header_clock_qs_top_margin_output);
        header_clock_qs_top_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + RemotePrefs.getInt(HEADER_CLOCK_QSTOPMARGIN, 0) + "dp");
        header_clock_qs_top_margin_seekbar.setProgress(RemotePrefs.getInt(HEADER_CLOCK_QSTOPMARGIN, 0));
        final int[] qsTopMargin = {RemotePrefs.getInt(HEADER_CLOCK_QSTOPMARGIN, 0)};
        header_clock_qs_top_margin_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                qsTopMargin[0] = progress;
                header_clock_qs_top_margin_output.setText(getResources().getString(R.string.opt_selected) + ' ' + progress + "dp");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RemotePrefs.putInt(HEADER_CLOCK_QSTOPMARGIN, qsTopMargin[0]);
                new Handler().postDelayed(SystemUtil::restartSystemUI, 200);
            }
        });
    }

    public void browseHeaderImage() {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("image/*");
        startActivityForResult(Intent.createChooser(chooseFile, "Choose Header Image"), PICKFILE_RESULT_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null)
            return;

        if (requestCode == PICKFILE_RESULT_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String source = getRealPathFromURI(uri);
            if (source == null) {
                Toast.makeText(Iconify.getAppContext(), getResources().getString(R.string.toast_rename_file), Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d("Header image source:", source);

            String destination = References.RESOURCE_TEMP_DIR + "/header_image.png";
            Log.d("Header image destination:", destination);

            Shell.cmd("mkdir -p " + References.RESOURCE_TEMP_DIR).exec();

            if (Shell.cmd("cp " + source + ' ' + destination).exec().isSuccess())
                enable_header_image.setVisibility(View.VISIBLE);
            else
                Toast.makeText(Iconify.getAppContext(), getResources().getString(R.string.toast_rename_file), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(Iconify.getAppContext(), getResources().getString(R.string.toast_error), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private int initialize_shade() {
        int shade = 1;
        try {
            shade = Integer.parseInt(accurate_sh.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return shade;
    }
}