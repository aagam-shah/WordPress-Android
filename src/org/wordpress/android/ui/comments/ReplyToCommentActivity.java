package org.wordpress.android.ui.comments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.wordpress.android.R;



public class ReplyToCommentActivity extends Activity {
    String accountName, postID = "", comment;
    int commentID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.reply);
        setTitle(getResources().getText(R.string.reply_to_comment));

        Bundle extras = getIntent().getExtras();
        if(extras !=null)
        {
         accountName = extras.getString("accountName");
         commentID = extras.getInt("commentID");
         postID = extras.getString("postID");
         comment = extras.getString("comment");
        }

        if (comment != null) {
            EditText replyTextET = (EditText)findViewById(R.id.replyText);
            replyTextET.setText(comment);
        }

        final Button cancelButton = (Button) findViewById(R.id.cancel);
        final Button okButton = (Button) findViewById(R.id.ok);

        okButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {

                EditText replyTextET = (EditText)findViewById(R.id.replyText);
                String replyText = replyTextET.getText().toString();

                if (replyText.equals("")){
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ReplyToCommentActivity.this);
                      dialogBuilder.setTitle(getResources().getText(R.string.reply_required));
                      dialogBuilder.setMessage(getResources().getText(R.string.reply_please_enter));
                      dialogBuilder.setPositiveButton("OK",  new
                              DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog, int whichButton) {
                          // Just close the window.
                      }
                  });
                      dialogBuilder.setCancelable(true);
                     dialogBuilder.create().show();
                }
                else{

                Bundle bundle = new Bundle();

                bundle.putString("replyText", replyText);
                bundle.putInt("commentID", commentID);
                bundle.putString("postID", postID);

                Intent mIntent = new Intent();
                mIntent.putExtras(bundle);
                setResult(RESULT_OK, mIntent);
                finish();
                }

            }
        });

        cancelButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {

                 Bundle bundle = new Bundle();

                 bundle.putString("replyText", "CANCEL");
                 Intent mIntent = new Intent();
                 mIntent.putExtras(bundle);
                 setResult(RESULT_OK, mIntent);
                 finish();
            }
        });

    }


}
