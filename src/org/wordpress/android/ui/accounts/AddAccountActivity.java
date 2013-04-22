package org.wordpress.android.ui.accounts;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.conn.HttpHostConnectException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlrpc.android.ApiHelper;
import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;
import org.xmlrpc.android.XMLRPCFault;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.util.AlertUtil;
import org.wordpress.android.util.EscapeUtils;

public class AddAccountActivity extends Activity implements OnClickListener {

    private static final String URL_WORDPRESS = "http://wordpress.com";

    private XMLRPCClient client;
    private String blogURL, xmlrpcURL;
    private ProgressDialog pd;
    private String httpuser = "";
    private String httppassword = "";
    private boolean wpcom = false;
    private int blogCtr = 0;
    private ArrayList<CharSequence> aBlogNames = new ArrayList<CharSequence>();
    private boolean isCustomURL = false;
    private ConnectivityManager mSystemService;
    private EditText mUrlEdit;
    private EditText mUsernameEdit;
    private EditText mPasswordEdit;
    private Button mSettingsButton;
    private Button mCancelButton;
    private Button mSaveButton;
    private Button mSignUpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_account);
        mSettingsButton = (Button) findViewById(R.id.settingsButton);
        mCancelButton = (Button) findViewById(R.id.cancel);
        mSaveButton = (Button) findViewById(R.id.save);
        mSignUpButton = (Button) findViewById(R.id.wordpressdotcom);
        mUrlEdit = (EditText) findViewById(R.id.url);
        mUsernameEdit = (EditText) findViewById(R.id.username);
        mPasswordEdit = (EditText) findViewById(R.id.password);
        
        ((TextView) findViewById(R.id.l_section1)).setText(getResources().getString(R.string.account_details).toUpperCase());

        mSystemService = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            wpcom = extras.getBoolean("wpcom", false);
            String username = extras.getString("username");
            if (username != null) {
                mUsernameEdit.setText(username);
            }
        }

        if (wpcom) {
            ((EditText) findViewById(R.id.url)).setVisibility(View.GONE);
        } else {
            ImageView logo = (ImageView) findViewById(R.id.wpcomLogo);
            logo.setImageDrawable(getResources().getDrawable(R.drawable.wplogo));
        }

        if (wpcom)
            mSettingsButton.setVisibility(View.GONE);
        else
            mSettingsButton.setOnClickListener(this);

        mSaveButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);
        mSignUpButton.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == R.id.settingsButton) {
            if (resultCode == RESULT_OK) {
                Bundle extras = data.getExtras();
                httpuser = extras.getString("httpuser");
                httppassword = extras.getString("httppassword");
            }
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void configureAccount() {

        if (wpcom) {
            blogURL = URL_WORDPRESS;
        } else {
            blogURL = mUrlEdit.getText().toString().trim();
        }
        final String username = mUsernameEdit.getText().toString().trim();
        final String password = mPasswordEdit.getText().toString().trim();

        if (blogURL.equals("") || username.equals("") || password.equals("")) {
            pd.dismiss();
            AlertUtil.showAlert(AddAccountActivity.this, R.string.required_fields, R.string.url_username_password_required);
            return;
        }

        // add http to the beginning of the URL if needed
        if (!(blogURL.toLowerCase().startsWith("http://")) && !(blogURL.toLowerCase().startsWith("https://"))) {
            blogURL = "http://" + blogURL; // default to http
        }

        if (!URLUtil.isValidUrl(blogURL)) {
            pd.dismiss();
            AlertUtil.showAlert(AddAccountActivity.this, R.string.invalid_url, R.string.invalid_url_message);
            return;
        }

        // attempt to get the XMLRPC URL via RSD
        String rsdUrl = getRSDMetaTagHrefRegEx(blogURL);
        if (rsdUrl == null) {
            rsdUrl = getRSDMetaTagHref(blogURL);
        }

        if (rsdUrl != null) {
            xmlrpcURL = ApiHelper.getXMLRPCUrl(rsdUrl);
            if (xmlrpcURL == null)
                xmlrpcURL = rsdUrl.replace("?rsd", "");
        } else {
            isCustomURL = false;
            // try the user entered path
            try {
                client = new XMLRPCClient(blogURL, httpuser, httppassword);
                try {
                    client.call("system.listMethods");
                    xmlrpcURL = blogURL;
                    isCustomURL = true;
                } catch (XMLRPCException e) {
                    // guess the xmlrpc path
                    String guessURL = blogURL;
                    if (guessURL.substring(guessURL.length() - 1, guessURL.length()).equals("/")) {
                        guessURL = guessURL.substring(0, guessURL.length() - 1);
                    }
                    guessURL += "/xmlrpc.php";
                    client = new XMLRPCClient(guessURL, httpuser, httppassword);
                    try {
                        client.call("system.listMethods");
                        xmlrpcURL = guessURL;
                    } catch (XMLRPCException ex) {
                    }
                }
            } catch (Exception e) {
            }
        }

        if (xmlrpcURL == null) {
            pd.dismiss();
            AlertUtil.showAlert(AddAccountActivity.this, R.string.error, R.string.no_site_error);
        } else {
            // verify settings
            client = new XMLRPCClient(xmlrpcURL, httpuser, httppassword);

            XMLRPCMethod method = new XMLRPCMethod("wp.getUsersBlogs", new XMLRPCMethodCallback() {

                public void callFinished(Object[] result) {

                    final String[] blogNames = new String[result.length];
                    final String[] urls = new String[result.length];
                    final String[] homeURLs = new String[result.length];
                    final int[] blogIds = new int[result.length];
                    final boolean[] wpcoms = new boolean[result.length];
                    final String[] wpVersions = new String[result.length];
                    Map<Object, Object> contentHash = new HashMap<Object, Object>();
                    blogCtr = 0;
                    // loop this!
                    for (int ctr = 0; ctr < result.length; ctr++) {
                        contentHash = (Map<Object, Object>) result[ctr];
                        // check if this blog is already set up
                        boolean match = false;
                        String matchBlogName = contentHash.get("blogName").toString();
                        if (matchBlogName.length() == 0) {
                            matchBlogName = contentHash.get("url").toString();
                        }
                        match = WordPress.wpDB.checkMatch(matchBlogName, contentHash.get("xmlrpc").toString(), username);
                        if (!match) {
                            blogNames[blogCtr] = matchBlogName;
                            if (isCustomURL)
                                urls[blogCtr] = blogURL;
                            else
                                urls[blogCtr] = contentHash.get("xmlrpc").toString();
                            homeURLs[blogCtr] = contentHash.get("url").toString();
                            blogIds[blogCtr] = Integer.parseInt(contentHash.get("blogid").toString());
                            String blogURL = urls[blogCtr];

                            aBlogNames.add(EscapeUtils.unescapeHtml(blogNames[blogCtr]));

                            boolean wpcomFlag = false;
                            // check for wordpress.com
                            if (blogURL.toLowerCase().contains("wordpress.com")) {
                                wpcomFlag = true;
                            }
                            wpcoms[blogCtr] = wpcomFlag;

                            // attempt to get the software version
                            String wpVersion = "";
                            if (!wpcomFlag) {
                                Map<String, String> hPost = new HashMap<String, String>();
                                hPost.put("software_version", "software_version");
                                Object[] vParams = { 1, username, password, hPost };
                                Object versionResult = new Object();
                                try {
                                    versionResult = (Object) client.call("wp.getOptions", vParams);
                                } catch (XMLRPCException e) {
                                }

                                if (versionResult != null) {
                                    try {
                                        contentHash = (Map<Object, Object>) versionResult;
                                        Map<?, ?> sv = (Map<?, ?>) contentHash.get("software_version");
                                        wpVersion = sv.get("value").toString();
                                    } catch (Exception e) {
                                    }
                                }
                            } else {
                                wpVersion = "3.4";
                            }

                            wpVersions[blogCtr] = wpVersion;

                            blogCtr++;
                        }
                    } // end loop
                    pd.dismiss();
                    if (blogCtr == 0) {
                        String additionalText = "";
                        if (result.length > 0) {
                            additionalText = getString(R.string.additional);
                        }
                        AlertUtil.showAlert(AddAccountActivity.this, R.string.no_blogs_found,
                                String.format(getString(R.string.no_blogs_message), additionalText), getString(R.string.ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.dismiss();
                                    }
                                });
                    } else {
                        // take them to the blog selection screen if
                        // there's more than one blog
                        if (blogCtr > 1) {

                            LayoutInflater inflater = (LayoutInflater) AddAccountActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            final ListView lv = (ListView) inflater.inflate(R.layout.select_blogs_list, null);
                            lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                            lv.setItemsCanFocus(false);

                            ArrayAdapter<CharSequence> blogs = new ArrayAdapter<CharSequence>(AddAccountActivity.this, R.layout.blogs_row,
                                    aBlogNames);

                            lv.setAdapter(blogs);

                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AddAccountActivity.this);
                            dialogBuilder.setTitle(R.string.select_blogs);
                            dialogBuilder.setView(lv);
                            dialogBuilder.setNegativeButton(R.string.add_selected, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    SparseBooleanArray selectedItems = lv.getCheckedItemPositions();

                                    for (int i = 0; i < selectedItems.size(); i++) {
                                        if (selectedItems.get(selectedItems.keyAt(i)) == true) {
                                            int rowID = selectedItems.keyAt(i);
                                            long blogID = -1;
                                            blogID = WordPress.wpDB.addAccount(urls[rowID], homeURLs[rowID], blogNames[rowID], username, password, httpuser,
                                                    httppassword, "Above Text", false, false, "500", 5, false, blogIds[rowID],
                                                    wpcoms[rowID], wpVersions[rowID]);
                                            //Set the first blog in the list to the currentBlog
                                            if (i == 0) {
                                                if (blogID >= 0) {
                                                    WordPress.setCurrentBlog((int) blogID);
                                                }
                                            }
                                        }
                                    }

                                    setResult(RESULT_OK);
                                    finish();

                                }
                            });
                            dialogBuilder.setPositiveButton(R.string.add_all, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {

                                    for (int i = 0; i < blogCtr; i++) {
                                        long blogID = -1;
                                        blogID = WordPress.wpDB.addAccount(urls[i], homeURLs[i], blogNames[i], username, password, httpuser, httppassword,
                                                "Above Text", false, false, "500", 5, false, blogIds[i], wpcoms[i], wpVersions[i]);
                                        //Set the first blog in the list to the currentBlog
                                        if (i == 0) {
                                            if (blogID >= 0) {
                                                WordPress.setCurrentBlog((int) blogID);
                                            }
                                        }
                                    }

                                    setResult(RESULT_OK);
                                    finish();
                                }
                            });
                            dialogBuilder.setCancelable(true);
                            AlertDialog ad = dialogBuilder.create();
                            ad.setInverseBackgroundForced(true);
                            ad.show();

                            final Button addSelected = ad.getButton(AlertDialog.BUTTON_NEGATIVE);
                            addSelected.setEnabled(false);

                            lv.setOnItemClickListener(new OnItemClickListener() {
                                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                                    SparseBooleanArray selectedItems = lv.getCheckedItemPositions();
                                    boolean isChecked = false;
                                    for (int i = 0; i < selectedItems.size(); i++) {
                                        if (selectedItems.get(selectedItems.keyAt(i)) == true) {
                                            isChecked = true;
                                        }
                                    }
                                    if (!isChecked) {
                                        addSelected.setEnabled(false);
                                    } else {
                                        addSelected.setEnabled(true);
                                    }
                                }
                            });

                        } else {
                            long blogID = WordPress.wpDB.addAccount(urls[0], homeURLs[0], blogNames[0], username, password, httpuser, httppassword, "Above Text",
                                    false, false, "500", 5, false, blogIds[0], wpcoms[0], wpVersions[0]);
                            if (blogID >= 0) {
                                WordPress.setCurrentBlog((int) blogID);
                            }
                            setResult(RESULT_OK);
                            finish();
                        }
                    }
                }
            });
            Object[] params = { username, password };

            method.call(params);
        }
    }

    interface XMLRPCMethodCallback {
        void callFinished(Object[] result);
    }

    class XMLRPCMethod extends Thread {
        private String method;
        private Object[] params;
        private Handler handler;
        private XMLRPCMethodCallback callBack;

        public XMLRPCMethod(String method, XMLRPCMethodCallback callBack) {
            this.method = method;
            this.callBack = callBack;

            handler = new Handler();

        }

        public void call() {
            call(null);
        }

        public void call(Object[] params) {
            this.params = params;
            start();
        }

        @Override
        public void run() {
            try {
                final Object[] result;
                result = (Object[]) client.call(method, params);
                handler.post(new Runnable() {
                    public void run() {
                        callBack.callFinished(result);
                    }
                });
            } catch (final XMLRPCFault e) {
                handler.post(new Runnable() {
                    public void run() {
                        // e.printStackTrace();
                        pd.dismiss();
                        String message = e.getMessage();
                        if (message.contains("code 403")) {
                            // invalid login
                            Thread shake = new Thread() {
                                public void run() {
                                    Animation shake = AnimationUtils.loadAnimation(AddAccountActivity.this, R.anim.shake);
                                    findViewById(R.id.section1).startAnimation(shake);
                                    Toast.makeText(AddAccountActivity.this, getString(R.string.invalid_login), Toast.LENGTH_SHORT).show();
                                }
                            };
                            runOnUiThread(shake);
                        } else {
                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AddAccountActivity.this);
                            dialogBuilder.setTitle(getString(R.string.connection_error));
                            if (message.contains("404"))
                                message = getString(R.string.xmlrpc_error);
                            dialogBuilder.setMessage(message);
                            dialogBuilder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            });
                            dialogBuilder.setCancelable(true);
                            dialogBuilder.create().show();
                        }
                    }
                });

            } catch (final XMLRPCException e) {

                handler.post(new Runnable() {
                    public void run() {
                        Throwable couse = e.getCause();
                        e.printStackTrace();
                        pd.dismiss();
                        String message = e.getMessage();
                        if (couse instanceof HttpHostConnectException) {

                        } else {
                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(AddAccountActivity.this);
                            dialogBuilder.setTitle(getString(R.string.connection_error));
                            if (message.contains("404"))
                                message = getString(R.string.xmlrpc_error);
                            dialogBuilder.setMessage(message);
                            dialogBuilder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            });
                            dialogBuilder.setCancelable(true);
                            dialogBuilder.create().show();
                        }
                        e.printStackTrace();

                    }
                });
            }
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // ignore orientation change
        super.onConfigurationChanged(newConfig);
    }

    private static final Pattern rsdLink = Pattern.compile(
            "<link\\s*?rel=\"EditURI\"\\s*?type=\"application/rsd\\+xml\"\\s*?title=\"RSD\"\\s*?href=\"(.*?)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private String getRSDMetaTagHrefRegEx(String urlString) {
        String html = ApiHelper.getResponse(urlString);
        if (html != null) {
            Matcher matcher = rsdLink.matcher(html);
            if (matcher.find()) {
                String href = matcher.group(1);
                return href;
            }
        }
        return null;
    }

    private String getRSDMetaTagHref(String urlString) {
        // get the html code
        InputStream in = ApiHelper.getResponseStream(urlString);

        // parse the html and get the attribute for xmlrpc endpoint
        if (in != null) {
            XmlPullParser parser = Xml.newPullParser();
            try {
                // auto-detect the encoding from the stream
                parser.setInput(in, null);
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String name = null;
                    String rel = "";
                    String type = "";
                    String href = "";
                    switch (eventType) {
                    case XmlPullParser.START_TAG:
                        name = parser.getName();
                        if (name.equalsIgnoreCase("link")) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attrName = parser.getAttributeName(i);
                                String attrValue = parser.getAttributeValue(i);
                                if (attrName.equals("rel")) {
                                    rel = attrValue;
                                } else if (attrName.equals("type"))
                                    type = attrValue;
                                else if (attrName.equals("href"))
                                    href = attrValue;
                            }

                            if (rel.equals("EditURI") && type.equals("application/rsd+xml")) {
                                return href;
                            }
                            // currentMessage.setLink(parser.nextText());
                        }
                        break;
                    }
                    eventType = parser.next();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

        }
        return null; // never found the rsd tag
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.save) {
            if (mSystemService.getActiveNetworkInfo() == null) {
                AlertUtil.showAlert(AddAccountActivity.this, R.string.no_network_title, R.string.no_network_message);
            } else {
                pd = ProgressDialog.show(AddAccountActivity.this, getString(R.string.account_setup), getString(R.string.attempting_configure),
                        true, false);

                Thread action = new Thread() {
                    public void run() {
                        Looper.prepare();
                        configureAccount();
                        Looper.loop();
                    }
                };
                action.start();
            }
        } else if (id == R.id.cancel) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (id == R.id.settingsButton) {
            Intent settings = new Intent(AddAccountActivity.this, AddAcountSettingsActivity.class);
            settings.putExtra("httpuser", httpuser);
            settings.putExtra("httppassword", httppassword);
            startActivityForResult(settings, R.id.settingsButton);
        } else if (id == R.id.wordpressdotcom) {
            startActivity(new Intent(AddAccountActivity.this, SignupActivity.class));
        }
    }
}
