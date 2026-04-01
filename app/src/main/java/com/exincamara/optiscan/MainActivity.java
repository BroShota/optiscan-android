package com.exincamara.optiscan;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "optiscan_prefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final int PERM_REQUEST_CODE = 100;

    private WebView webView;
    private View errorView;
    private TextView tvErrorUrl;
    private SharedPreferences prefs;
    private String currentIp;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentIp = prefs.getString(KEY_SERVER_IP, null);

        setupToolbar();
        setupWebView();
        setupErrorView();
        requestCameraPermission();

        if (currentIp == null || currentIp.isEmpty()) {
            showIpConfigDialog(false); // false = no se puede cancelar (primera vez)
        } else {
            loadServer();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ─── Toolbar ─────────────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Logo en la toolbar: long-press abre config de IP
        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.logo);
        logoView.setPadding(24, 12, 24, 12);
        logoView.setOnLongClickListener(v -> {
            showIpConfigDialog(true);
            return true;
        });

        Toolbar.LayoutParams lp = new Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        toolbar.addView(logoView, lp);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.menu_config)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showIpConfigDialog(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── WebView ──────────────────────────────────────────────────────────────

    private void setupWebView() {
        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Conceder permisos de cámara/audio al sitio web automáticamente.
                // El sistema Android ya los pidió al usuario al instalar/primer uso.
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorScreen();
                }
            }
        });
    }

    private void loadServer() {
        if (currentIp == null || currentIp.isEmpty()) return;
        String url = "http://" + currentIp + ":5000";
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    // ─── Pantalla de error ────────────────────────────────────────────────────

    private void setupErrorView() {
        errorView = findViewById(R.id.error_view);
        tvErrorUrl = findViewById(R.id.tv_error_url);

        Button btnRetry = findViewById(R.id.btn_retry);
        btnRetry.setOnClickListener(v -> {
            if (currentIp != null) loadServer();
        });

        Button btnConfigIp = findViewById(R.id.btn_config_ip);
        btnConfigIp.setOnClickListener(v -> showIpConfigDialog(true));

        // Long-press en el logo de la pantalla de error también abre config
        ImageView logoError = findViewById(R.id.logo_img);
        logoError.setOnLongClickListener(v -> {
            showIpConfigDialog(true);
            return true;
        });
    }

    private void showErrorScreen() {
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        if (currentIp != null) {
            tvErrorUrl.setText("http://" + currentIp + ":5000");
        }
    }

    // ─── Diálogo de configuración de IP ──────────────────────────────────────

    private void showIpConfigDialog(boolean cancellable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title);

        final EditText input = new EditText(this);
        input.setHint(R.string.dialog_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                           android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if (currentIp != null) {
            input.setText(currentIp);
            input.setSelection(currentIp.length()); // cursor al final
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(48, 16, 48, 0);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton(R.string.btn_save, (dialog, which) -> {
            String newIp = input.getText().toString().trim();
            if (!newIp.isEmpty()) {
                currentIp = newIp;
                prefs.edit().putString(KEY_SERVER_IP, currentIp).apply();
                loadServer();
            }
        });

        if (cancellable) {
            builder.setNegativeButton(R.string.btn_cancel, null);
        }

        builder.setCancelable(cancellable);
        builder.show();
    }

    // ─── Permisos de cámara (sistema Android) ────────────────────────────────

    private void requestCameraPermission() {
        String[] perms = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        };
        boolean needRequest = false;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, perms, PERM_REQUEST_CODE);
        }
    }
}
