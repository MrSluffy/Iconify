package com.drdisagree.iconify.ui;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.drdisagree.iconify.R;
import com.drdisagree.iconify.utils.OverlayUtils;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.List;

public class QsShapes extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qs_shapes);

        // Header
        CollapsingToolbarLayout collapsing_toolbar = findViewById(R.id.collapsing_toolbar);
        collapsing_toolbar.setTitle("QS Panel Tiles");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        List<String> enabledOverlays = OverlayUtils.getEnabledOverlayList();

        Switch label_white = findViewById(R.id.label_white);
        Switch label_systemInverse = findViewById(R.id.label_systemInverse);
        Switch label_systemInverseV2 = findViewById(R.id.label_systemInverseV2);

        if (OverlayUtils.isOverlayEnabled(enabledOverlays, "IconifyComponentQST1.overlay"))
            label_white.setChecked(true);
        else
            label_white.setChecked(false);

        label_white.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    label_systemInverse.setChecked(false);
                    label_systemInverseV2.setChecked(false);
                    OverlayUtils.enableOverlay(enabledOverlays, "IconifyComponentQST1.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST2.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST3.overlay");
                } else {
                    OverlayUtils.disableOverlay("IconifyComponentQST1.overlay");
                }
            }
        });

        if (OverlayUtils.isOverlayEnabled(enabledOverlays, "IconifyComponentQST2.overlay"))
            label_systemInverse.setChecked(true);
        else
            label_systemInverse.setChecked(false);

        label_systemInverse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    label_white.setChecked(false);
                    label_systemInverseV2.setChecked(false);
                    OverlayUtils.enableOverlay(enabledOverlays, "IconifyComponentQST2.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST1.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST3.overlay");
                } else {
                    OverlayUtils.disableOverlay("IconifyComponentQST2.overlay");
                }
            }
        });

        if (OverlayUtils.isOverlayEnabled(enabledOverlays, "IconifyComponentQST3.overlay"))
            label_systemInverseV2.setChecked(true);
        else
            label_systemInverseV2.setChecked(false);

        label_systemInverseV2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    label_white.setChecked(false);
                    label_systemInverse.setChecked(false);
                    OverlayUtils.enableOverlay(enabledOverlays, "IconifyComponentQST3.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST1.overlay");
                    OverlayUtils.disableOverlay("IconifyComponentQST2.overlay");
                } else {
                    OverlayUtils.disableOverlay("IconifyComponentQST3.overlay");
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}