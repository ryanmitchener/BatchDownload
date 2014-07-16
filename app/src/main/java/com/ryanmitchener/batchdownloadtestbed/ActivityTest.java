package com.ryanmitchener.batchdownloadtestbed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.TextView;

import com.ryanmitchener.batchdownload.BatchDownload;

/**
 * Created by Ryan on 7/15/14.
 */
public class ActivityTest extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(BatchDownload.ACTION_CALCULATING);
        broadcastFilter.addAction(BatchDownload.ACTION_ERROR);
        broadcastFilter.addAction(BatchDownload.ACTION_PROGRESS);
        broadcastFilter.addAction(BatchDownload.ACTION_FILE_DOWNLOADED);
        broadcastFilter.addAction(BatchDownload.ACTION_COMPLETE);
        LocalBroadcastManager.getInstance(this).registerReceiver(new Callback(), broadcastFilter);
    }


    // Callback Receiver
    private class Callback extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView text = (TextView) findViewById(R.id.test_message);
            if (intent.getAction().equals(BatchDownload.ACTION_CALCULATING)) {
                text.setText("Calculating size...");
//                System.out.println("Calculating...");
            } else if (intent.getAction().equals(BatchDownload.ACTION_CANCELLED)) {
//                System.out.println("Cancelled");
            } else if (intent.getAction().equals(BatchDownload.ACTION_PROGRESS)) {
//                System.out.println("New Activity");
                Bundle extras = intent.getExtras();
                String errors = "" + extras.getInt(BatchDownload.EXTRA_ERROR_COUNT);
                int percent = (int) ((extras.getLong(BatchDownload.EXTRA_BYTES_DOWNLOADED, 0) * 100) / extras.getLong(BatchDownload.EXTRA_TOTAL_BYTES, 0));
                String kilobytes = (extras.getLong(BatchDownload.EXTRA_TOTAL_BYTES) / 1000) + "KB";
                text.setText("" + percent + "%: " + kilobytes);
            } else if (intent.getAction().equals(BatchDownload.ACTION_COMPLETE)) {
                text.setText("Download Complete. " + intent.getIntExtra(BatchDownload.EXTRA_ERROR_COUNT, 0) + " errors.");
            }
        }
    }
}
