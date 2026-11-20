package com.iptv.player;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public class BrowserActivity extends Activity {
    WebView w;
    FrameLayout root;
    View custom;
    WebChromeClient.CustomViewCallback cb;

    Button mk(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t); b.setTextColor(0xFFFFFFFF); b.setTextSize(17);
        b.setBackgroundColor(0x00000000); b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(l);
        return b;
    }

    @Override protected void onCreate(Bundle sv) {
        super.onCreate(sv);
        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0xFF0B1A4A);
        w = new WebView(this);
        String url = getIntent().getStringExtra("url");

        View.OnClickListener[] ls = new View.OnClickListener[]{
            v -> { if (w.canGoBack()) w.goBack(); },
            v -> { if (w.canGoForward()) w.goForward(); },
            v -> w.reload(),
            v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", w.getUrl()));
                Toast.makeText(this, "تم نسخ الرابط", Toast.LENGTH_SHORT).show();
            },
            v -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, w.getUrl());
                startActivity(Intent.createChooser(i, "مشاركة"));
            },
            v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(w.getUrl()))); }
                catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق مناسب", Toast.LENGTH_SHORT).show(); }
            },
            v -> {
                Intent r = new Intent(); r.putExtra("url", w.getUrl());
                setResult(RESULT_OK, r); finish();
            },
            v -> finish()
        };
        String[] names = {"◀", "▶", "⟳", "🔗", "↗", "📲", "📺", "✕"};
        for (int i = 0; i < names.length; i++)
            bar.addView(mk(names[i], ls[i]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        float d = getResources().getDisplayMetrics().density;
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * d)));
        col.addView(w, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col);
        setContentView(root);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View v, CustomViewCallback c) {
                custom = v; cb = c;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() {
                if (custom != null) root.removeView(custom);
                custom = null;
                getWindow().getDecorView().setSystemUiVisibility(0);
                if (cb != null) cb.onCustomViewHidden();
            }
        });
        if (url != null) w.loadUrl(url);
    }

    @Override public void onBackPressed() {
        if (custom != null) {
            root.removeView(custom); custom = null;
            getWindow().getDecorView().setSystemUiVisibility(0);
            if (cb != null) cb.onCustomViewHidden();
        }
        else if (w.canGoBack()) w.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() { w.destroy(); super.onDestroy(); }
}
