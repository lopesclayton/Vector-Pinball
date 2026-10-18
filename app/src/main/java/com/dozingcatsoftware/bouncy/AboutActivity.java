package com.dozingcatsoftware.bouncy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.widget.Toast;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.TextView;

public class AboutActivity extends Activity {
    private static final int CONFIRM_EXTERNAL_LINK = 7101;
    private static final String PRIVACY_URL = "https://s3.sa-east-1.amazonaws.com/lupper.com.br/politica-privacidade-jogos.html";
    private String pendingExternalUrl;
    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.about);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.getWindow().setNavigationBarColor(Color.BLACK);
        }

        // Get text to display by replacing "[TABLE_RULES]" with the contents of string resource
        // with ID table[level]_rules.
        String baseText = getString(R.string.about_text);
        String tableRulesText = null;
        try {
            String fieldName = "table" + getIntent().getIntExtra("level", 1) + "_rules";
            int tableRulesID = (Integer) R.string.class.getField(fieldName).get(null);
            tableRulesText = getString(tableRulesID);
        }
        catch (Exception ex) {
            tableRulesText = null;
        }
        if (tableRulesText == null) tableRulesText = "";
        String displayText = getString(R.string.app_name) + "\n"
                + getString(R.string.wigo_privacy_policy) + "\n"
                + PRIVACY_URL + "\n\n"
                + "Wigo Pinball is an independent adaptation of Vector Pinball.\n"
                + "Support: contato@lupper.com.br\n"
                + "Modified source: https://github.com/lopesclayton/Vector-Pinball\n\n"
                + baseText.replace("[TABLE_RULES]", tableRulesText);

        TextView tv = findViewById(R.id.aboutTextView);
        setGuardedText(tv, displayText);
        // Padding based on screen.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int padding = Math.min(metrics.widthPixels, metrics.heightPixels) / 25;
        tv.setPadding(padding, padding, padding, padding);

        // Use larger text on physically larger screens.
        float widthInches = metrics.widthPixels / metrics.xdpi;
        float heightInches = metrics.heightPixels / metrics.ydpi;
        float minInches = Math.min(widthInches, heightInches);
        float fontSize = minInches > 4 ? 18f : (minInches > 3.5 ? 16f : 14f);
        tv.setTextSize(fontSize);
        // Uncomment to see the pixel dimensions and computed physical size.
        // tv.setText(metrics.widthPixels + ":" + metrics.heightPixels + ":" + widthInches + ":" + heightInches + "\n" + tv.getText());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            tv.setOnApplyWindowInsetsListener(this::applyWindowInsets);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    WindowInsets applyWindowInsets(View v, WindowInsets windowInsets) {
        TextView tv = findViewById(R.id.aboutTextView);
        // We don't want text to start in the display cutout, but we do want it to scroll
        // into that area, so we add empty lines at the beginning and end of the text content.
        Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        float lineHeight = tv.getLineHeight();
        String prefix = newlinesForPaddingPixels(insets.top - tv.getPaddingTop(), lineHeight);
        String suffix = newlinesForPaddingPixels(insets.bottom - tv.getPaddingBottom(), lineHeight);
        // android.util.Log.i("About", "top: " + insets.top + ", bottom: " + insets.bottom + ", lineHeight: " + lineHeight + ", top padding: " + tv.getPaddingTop() + ", bottom padding: " + tv.getPaddingBottom());
        setGuardedText(tv, prefix + tv.getText() + suffix);

        return WindowInsets.CONSUMED;
    }

    private void setGuardedText(TextView view, CharSequence text) {
        // Disable layout auto-linking: it could recreate unguarded spans after inset updates.
        view.setAutoLinkMask(0);
        SpannableString linked = new SpannableString(text.toString());
        Linkify.addLinks(linked, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        for (URLSpan original : linked.getSpans(0, linked.length(), URLSpan.class)) {
            int start = linked.getSpanStart(original);
            int end = linked.getSpanEnd(original);
            int flags = linked.getSpanFlags(original);
            final String url = original.getURL();
            linked.removeSpan(original);
            linked.setSpan(new URLSpan(url) {
                @Override public void onClick(View widget) {
                    if (PRIVACY_URL.equals(url)) {
                        openExternalUrl(url);
                    } else {
                        confirmExternalUrl(url);
                    }
                }
            }, start, end, flags);
        }
        view.setText(linked);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void confirmExternalUrl(String url) {
        // Authorization is single-use and never restored across activity recreation.
        if (pendingExternalUrl != null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showLinkBlocked();
            return;
        }
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            showLinkBlocked();
            return;
        }
        Intent credential = keyguard.createConfirmDeviceCredentialIntent(
                "Parental control / Controle parental",
                "A parent must unlock this device to open an external link.");
        if (credential == null) {
            showLinkBlocked();
            return;
        }
        pendingExternalUrl = url;
        try {
            startActivityForResult(credential, CONFIRM_EXTERNAL_LINK);
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            pendingExternalUrl = null;
            showLinkBlocked();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_EXTERNAL_LINK) {
            String url = pendingExternalUrl;
            pendingExternalUrl = null;
            if (resultCode == RESULT_OK && url != null) openExternalUrl(url);
        }
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, "No app available to open this link.", Toast.LENGTH_LONG).show();
        }
    }

    private void showLinkBlocked() {
        Toast.makeText(this, "External links require a parent-controlled Android screen lock. / "
                + "Links externos exigem bloqueio de tela Android controlado pelo responsável.",
                Toast.LENGTH_LONG).show();
    }

    private static String newlinesForPaddingPixels(int px, float lineHeight) {
        if (lineHeight <= 0) {
            return "";
        }
        int numNewlines = Math.min(5, (int) Math.ceil(px / lineHeight));
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < numNewlines; i++) {
            s.append('\n');
        }
        return s.toString();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Return to main activity when "P" is pressed, to avoid accidentally quitting by hitting
        // "back" multiple times. See https://github.com/dozingcat/Vector-Pinball/issues/103.
        if (keyCode == KeyEvent.KEYCODE_P) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public static Intent startForLevel(Context context, int level) {
        Intent aboutIntent = new Intent(context, AboutActivity.class);
        aboutIntent.putExtra("level", level);
        context.startActivity(aboutIntent);
        return aboutIntent;
    }
}
