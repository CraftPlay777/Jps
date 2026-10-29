package com.CraftPlay777.jps;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/** Muestra la política de privacidad o la pantalla "Acerca de". */
public class InfoActivity extends Activity {

    static final String EXTRA_PAGE = "page";
    static final String PAGE_PRIVACY = "privacy";
    static final String PAGE_ABOUT = "about";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        boolean privacy = PAGE_PRIVACY.equals(getIntent().getStringExtra(EXTRA_PAGE));
        Ui.setupHeader(this, privacy ? R.string.privacy_title : R.string.about_title);

        String html;
        if (privacy) {
            html = getString(R.string.privacy_html);
        } else {
            html = getString(R.string.about_html, versionName());
        }

        TextView text = findViewById(R.id.info_text);
        text.setText(Ui.html(html));
        text.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "1.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }
}
