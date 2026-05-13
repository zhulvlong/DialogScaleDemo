package com.demo.dialogscale;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public class DialogScaleDemoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(0xFF2C3E50);

        Button btnShowDialog = new Button(this);
        btnShowDialog.setText("打开可缩放 Dialog");
        btnShowDialog.setTextSize(18);
        btnShowDialog.setPadding(40, 20, 40, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layout.addView(btnShowDialog, params);

        setContentView(layout);

        btnShowDialog.setOnClickListener(v -> {
            ScaleableDialog dialog = new ScaleableDialog(this);
            dialog.show();
        });
    }
}
