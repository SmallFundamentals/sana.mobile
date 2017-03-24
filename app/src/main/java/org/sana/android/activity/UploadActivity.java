package org.sana.android.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.IntegerRes;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.sana.R;
import org.sana.net.Response;

/**
 * Created by Errin on 23/03/2017.
 */

public class UploadActivity extends Activity {

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            handleBroadcast(context, intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uploading_process);
        IntentFilter filter = buildFilter();
        LocalBroadcastManager.getInstance(
                getApplicationContext()).registerReceiver(mReceiver, filter);

    }

    protected void handleBroadcast(final Context context, Intent intent){

        //set onClick actions for buttons
        Button cancelButton = (Button) findViewById(R.id.uploading_process_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                LocalBroadcastManager.getInstance(
                        getApplicationContext()).unregisterReceiver(mReceiver);
                Context ctx = v.getContext();
                ((Activity) ctx).finish();

            }
        });

        if(intent.getAction().equals("Upload_Slicing")){
            int fileSize = intent.getIntExtra("fileSize", 0);
            TextView fileSizeView = (TextView) findViewById(R.id.uploading_process_info_size);
            fileSizeView.setText(Integer.toString(fileSize));

            String fileName = intent.getStringExtra("fileName");
            TextView fileNameView = (TextView) findViewById(R.id.uploading_process_info_filename);
            fileNameView.setText(fileName);

            String dateTime = intent.getStringExtra("date");
            TextView dateView = (TextView) findViewById(R.id.uploading_process_info_doc);
            dateView.setText(dateTime);
        } else if(intent.getAction().equals("Progress_Updating")) {
            int percentage = intent.getIntExtra("percentage", 0);
            TextView percentageView = (TextView) findViewById(R.id.uploading_process_percentage);
            if(percentage == 100) {
                Button doneButton = (Button) findViewById(R.id.uploading_process_done);
                doneButton.setEnabled(true);
                doneButton.setTextColor(Color.WHITE);

                //set onClick actions for buttons
                doneButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        LocalBroadcastManager.getInstance(
                                getApplicationContext()).unregisterReceiver(mReceiver);
                        Context ctx = v.getContext();
                        ((Activity) ctx).finish();

                    }
                });
            }
            percentageView.setText(Integer.toString(percentage));
        }

    }

    public IntentFilter buildFilter(){
        IntentFilter filter = new IntentFilter(Response.RESPONSE);
        filter.addAction("Upload_Slicing");
        filter.addAction("Progress_Updating");
        return filter;
    }

}
