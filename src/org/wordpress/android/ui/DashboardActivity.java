
package org.wordpress.android.ui;

import android.os.Bundle;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;

/**
 * Activity to view the WordPress admin dashboard (/wp-admin) for the current blog.
 */
public class DashboardActivity extends AuthenticatedWebViewActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createMenuDrawer(this.findViewById(R.id.webview_wrapper));
        
        this.setTitle(getResources().getText(R.string.wp_admin));

        // configure webview
        mWebView.setWebChromeClient(new WordPressWebChromeClient(this));
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setPluginsEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);

        loadDashboard();
    }

    public void loadDashboard() {
     // load dashboard
        String dashboardUrl;
        if (mBlog.getUrl().lastIndexOf("/") != -1) {
            dashboardUrl = mBlog.getUrl().substring(0, mBlog.getUrl().lastIndexOf("/"))
                    + "/wp-admin";
        } else {
            dashboardUrl = mBlog.getUrl().replace("xmlrpc.php", "wp-admin");
        }
        loadAuthenticatedUrl(dashboardUrl);
    }

    @Override
    public void onBlogChanged() {
        super.onBlogChanged();
        mBlog = WordPress.currentBlog;
        loadDashboard();
    }
}
