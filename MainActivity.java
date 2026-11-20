package com.iptv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.*;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    WebView w;
    WebChromeClient.CustomViewCallback cb;
    ValueCallback<Uri[]> fc;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        w = new WebView(this);
        setContentView(w);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        w.addJavascriptInterface(new Bridge(), "AndroidBridge");
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View v, CustomViewCallback c) {
                cb = c; setContentView(v);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() {
                getWindow().getDecorView().setSystemUiVisibility(0);
                setContentView(w);
                if (cb != null) cb.onCustomViewHidden();
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
                if (fc != null) fc.onReceiveValue(null);
                fc = c;
                try { startActivityForResult(p.createIntent(), 1); } catch (Exception e) { fc = null; return false; }
                return true;
            }
            @Override public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("موافق", (d, i) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m)
                    .setPositiveButton("موافق", (d, i) -> r.confirm())
                    .setNegativeButton("إلغاء", (d, i) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override public boolean onJsPrompt(WebView v, String u, String m, String def, JsPromptResult r) {
                EditText e = new EditText(MainActivity.this); e.setText(def);
                new AlertDialog.Builder(MainActivity.this).setMessage(m).setView(e)
                    .setPositiveButton("موافق", (d, i) -> r.confirm(e.getText().toString()))
                    .setNegativeButton("إلغاء", (d, i) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
        });
        w.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && fc != null) {
            fc.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fc = null;
        } else if (req == 2 && res == RESULT_OK && data != null) {
            String u = data.getStringExtra("url");
            if (u != null) w.evaluateJavascript("window.onBrowserUrl&&window.onBrowserUrl(" + JSONObject.quote(u) + ")", null);
        }
    }

    class Bridge {
        /* ---------- أدوات عامة ---------- */
        void js(String code) { runOnUiThread(() -> w.evaluateJavascript(code, null)); }
        String read(InputStream is) throws Exception {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close(); return sb.toString();
        }
        String http(String u) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(3000); c.setReadTimeout(3000);
            return read(c.getInputStream());
        }
        String tag(String x, String t) {
            Matcher m = Pattern.compile("<" + t + ">(.*?)</" + t + ">", Pattern.DOTALL).matcher(x);
            return m.find() ? m.group(1).trim() : "";
        }
        String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
        String soap(String ctl, String svc, String act, String body) throws Exception {
            String ns = "urn:schemas-upnp-org:service:" + svc + ":1";
            String x = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + act + " xmlns:u=\"" + ns + "\"><InstanceID>0</InstanceID>" + body + "</u:" + act + "></s:Body></s:Envelope>";
            HttpURLConnection c = (HttpURLConnection) new URL(ctl).openConnection();
            c.setConnectTimeout(4000); c.setReadTimeout(7000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPAction", "\"" + ns + "#" + act + "\"");
            OutputStream o = c.getOutputStream(); o.write(x.getBytes("UTF-8")); o.close();
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code);
            return read(c.getInputStream());
        }
        int sec(String t) {
            try {
                String[] a = t.split("[:.]");
                if (a.length < 3) return 0;
                return Integer.parseInt(a[0].trim()) * 3600 + Integer.parseInt(a[1].trim()) * 60 + Integer.parseInt(a[2].trim());
            } catch (Exception e) { return 0; }
        }
        String hms(int s) { return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60); }

        Set<String> ssdp(String[] sts, int ms) {
            Set<String> locs = new HashSet<>();
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock ml = wm.createMulticastLock("ssdp");
            ml.setReferenceCounted(false); ml.acquire();
            try {
                DatagramSocket s = new DatagramSocket();
                s.setSoTimeout(700);
                InetAddress g = InetAddress.getByName("239.255.255.250");
                for (int k = 0; k < 2; k++) for (String st : sts) {
                    byte[] b = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + st + "\r\n\r\n").getBytes();
                    s.send(new DatagramPacket(b, b.length, g, 1900));
                }
                Pattern lp = Pattern.compile("(?i)location:\\s*(\\S+)");
                long end = System.currentTimeMillis() + ms;
                while (System.currentTimeMillis() < end) {
                    try {
                        DatagramPacket p = new DatagramPacket(new byte[4096], 4096);
                        s.receive(p);
                        Matcher m = lp.matcher(new String(p.getData(), 0, p.getLength()));
                        if (m.find()) locs.add(m.group(1));
                    } catch (SocketTimeoutException e) { }
                }
                s.close();
            } catch (Exception e) { }
            finally { ml.release(); }
            return locs;
        }

        /* ---------- DLNA ---------- */
        @JavascriptInterface public void dlnaScan() {
            new Thread(() -> {
                JSONArray arr = new JSONArray();
                Set<String> locs = ssdp(new String[]{"urn:schemas-upnp-org:service:AVTransport:1", "urn:schemas-upnp-org:device:MediaRenderer:1"}, 4000);
                for (String l : locs) {
                    try {
                        String x = http(l);
                        Matcher c = Pattern.compile("AVTransport:1</serviceType>.*?<controlURL>(.*?)</controlURL>", Pattern.DOTALL).matcher(x);
                        if (!c.find()) continue;
                        JSONObject o = new JSONObject();
                        o.put("name", tag(x, "friendlyName").isEmpty() ? l : tag(x, "friendlyName"));
                        o.put("ctl", new URL(new URL(l), c.group(1).trim()).toString());
                        Matcher r = Pattern.compile("RenderingControl:1</serviceType>.*?<controlURL>(.*?)</controlURL>", Pattern.DOTALL).matcher(x);
                        o.put("rc", r.find() ? new URL(new URL(l), r.group(1).trim()).toString() : "");
                        arr.put(o);
                    } catch (Exception e) { }
                }
                js("window.onDlnaDevices(" + arr.toString() + ")");
            }).start();
        }

        @JavascriptInterface public void dlnaPlay(String ctl, String url, String title) {
            new Thread(() -> {
                try {
                    String low = url.toLowerCase(), pi = "http-get:*:video/mp4:*", cls = "object.item.videoItem";
                    if (low.contains(".m3u8")) pi = "http-get:*:application/vnd.apple.mpegurl:*";
                    else if (low.endsWith(".ts")) pi = "http-get:*:video/mpeg:*";
                    else if (low.endsWith(".mp3") || low.contains("radio") || low.contains("listen")) { pi = "http-get:*:audio/mpeg:*"; cls = "object.item.audioItem"; }
                    String didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>" + esc(title) + "</dc:title><upnp:class>" + cls + "</upnp:class><res protocolInfo=\"" + pi + "\">" + esc(url) + "</res></item></DIDL-Lite>";
                    try { soap(ctl, "AVTransport", "Stop", ""); } catch (Exception e) { }
                    soap(ctl, "AVTransport", "SetAVTransportURI", "<CurrentURI>" + esc(url) + "</CurrentURI><CurrentURIMetaData>" + esc(didl) + "</CurrentURIMetaData>");
                    soap(ctl, "AVTransport", "Play", "<Speed>1</Speed>");
                    js("window.onDlnaResult(true," + JSONObject.quote("تم الإرسال إلى التلفزيون") + ")");
                } catch (Exception e) {
                    js("window.onDlnaResult(false," + JSONObject.quote("فشل الإرسال: " + e.getMessage()) + ")");
                }
            }).start();
        }

        @JavascriptInterface public void dlnaCmd(String ctl, String cmd) {
            new Thread(() -> {
                try { soap(ctl, "AVTransport", cmd, cmd.equals("Play") ? "<Speed>1</Speed>" : ""); }
                catch (Exception e) { js("window.onDlnaResult(false," + JSONObject.quote("تعذر تنفيذ الأمر") + ")"); }
            }).start();
        }

        @JavascriptInterface public void dlnaSeek(String ctl, int secs) {
            new Thread(() -> {
                try { soap(ctl, "AVTransport", "Seek", "<Unit>REL_TIME</Unit><Target>" + hms(secs) + "</Target>"); }
                catch (Exception e) { js("window.onDlnaResult(false," + JSONObject.quote("الجهاز لا يدعم التقديم/التأخير لهذا المحتوى") + ")"); }
            }).start();
        }

        @JavascriptInterface public void dlnaPos(String ctl) {
            new Thread(() -> {
                try {
                    String r = soap(ctl, "AVTransport", "GetPositionInfo", "");
                    js("window.onDlnaPos&&window.onDlnaPos(" + sec(tag(r, "RelTime")) + "," + sec(tag(r, "TrackDuration")) + ")");
                } catch (Exception e) { }
            }).start();
        }

        @JavascriptInterface public void dlnaVolGet(String rc) {
            new Thread(() -> {
                try {
                    String r = soap(rc, "RenderingControl", "GetVolume", "<Channel>Master</Channel>");
                    js("window.onDlnaVol&&window.onDlnaVol(" + Integer.parseInt(tag(r, "CurrentVolume")) + ")");
                } catch (Exception e) { }
            }).start();
        }

        @JavascriptInterface public void dlnaVol(String rc, int v) {
            new Thread(() -> {
                try { soap(rc, "RenderingControl", "SetVolume", "<Channel>Master</Channel><DesiredVolume>" + v + "</DesiredVolume>"); }
                catch (Exception e) { }
            }).start();
        }

        @JavascriptInterface public void dlnaMute(String rc, boolean m) {
            new Thread(() -> {
                try { soap(rc, "RenderingControl", "SetMute", "<Channel>Master</Channel><DesiredMute>" + (m ? 1 : 0) + "</DesiredMute>"); }
                catch (Exception e) { }
            }).start();
        }

        /* ---------- البحث عن أجهزة للريموت ---------- */
        @JavascriptInterface public void remoteScan() {
            new Thread(() -> {
                JSONArray arr = new JSONArray();
                Set<String> locs = ssdp(new String[]{"ssdp:all", "roku:ecp"}, 4000);
                Map<String, Boolean> seen = new HashMap<>();
                for (String l : locs) {
                    try {
                        URL u = new URL(l);
                        String ip = u.getHost();
                        if (seen.containsKey(ip)) continue;
                        String x = http(l);
                        String name = tag(x, "friendlyName"), man = tag(x, "manufacturer").toLowerCase(Locale.ROOT);
                        String type = "";
                        if (man.contains("roku") || u.getPort() == 8060) type = "roku";
                        else if (man.contains("samsung")) type = "samsung";
                        else if (man.contains("xbmc") || man.contains("kodi")) type = "kodi";
                        if (name.isEmpty()) name = ip;
                        seen.put(ip, true);
                        JSONObject o = new JSONObject();
                        o.put("name", name); o.put("ip", ip); o.put("type", type);
                        arr.put(o);
                    } catch (Exception e) { }
                }
                js("window.onRemoteDevices(" + arr.toString() + ")");
            }).start();
        }

        /* ---------- نوافذ الفيديو أونلاين ---------- */
        @JavascriptInterface public void openBrowser(String url) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, BrowserActivity.class);
                i.putExtra("url", url);
                startActivityForResult(i, 2);
            });
        }
        @JavascriptInterface public void shareText(String t) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, t);
                startActivity(Intent.createChooser(i, "مشاركة"));
            });
        }
        @JavascriptInterface public void openExternal(String url) {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "لا يوجد تطبيق مناسب", Toast.LENGTH_SHORT).show(); }
            });
        }

        /* ---------- حافظة وحفظ ملفات ---------- */
        @JavascriptInterface public void copy(String t) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("iptv", t));
        }
        @JavascriptInterface public void save(String name, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                OutputStream o;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    o = getContentResolver().openOutputStream(u);
                } else {
                    File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    o = new FileOutputStream(f);
                }
                o.write(data); o.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم الحفظ في التنزيلات: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر الحفظ", Toast.LENGTH_LONG).show());
            }
        }
    }
}
