package com.ryanmitchener.batchdownloadtestbed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.ryanmitchener.batchdownload.BatchDownload;
import com.ryanmitchener.batchdownload.BatchDownload.Request;

import java.io.File;
import java.util.ArrayList;

public class ActivityMain extends Activity {
    BatchDownload bd;
    Callback callback = new Callback();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Test request download folder
        File testFolder = new File(this.getFilesDir() + "/test/");
        testFolder.mkdir();

        final ArrayList<Request> requests = new ArrayList<BatchDownload.Request>();
        requests.add(new Request("http://info.sonicretro.org/images/9/9f/ASR_Big.png", "TEST", testFolder));
        requests.add(new Request("https://robertsspaceindustries.com/media/qz07ylp60wayer/source/JumpPoint_02-06_Jun_14_The-Next-Great-Jump-Point.pdf"));
        requests.add(new Request("http://upload.wikimedia.org/wikipedia/commons/8/8b/Webdings-big.png"));
        requests.add(new Request("http://www.cert.at/static/downloads/certatlogo/logo_big_whitebackground.jpg"));
        requests.add(new Request("http://img4.wikia.nocookie.net/__cb20120119153317/fantendo/images/7/7a/MP9_Big_Bob-Omb.png"));
        requests.add(new Request("http://img4.wikia.nocookie.net/__cb20130118022025/ben10/images/6/6d/Big_Chill_Save_Last_Dance_1.PNG"));
        requests.add(new Request("http://img1.wikia.nocookie.net/__cb20120922200332/ben10/images/8/87/Ultimate_Big_Chill_UA_4.PNG"));
        requests.add(new Request("http://vnmedia.ign.com/witchervault.ign.com/wiki/7/79/GhortaruisLG.png"));
        requests.add(new Request("https://robertsspaceindustries.com/media/q7i1qzak8bo1fr/source/JumpPoint_02-05_May_14_Racing-To-Get-Done-1.pdf"));
        requests.add(new Request("https://robertsspaceindustries.com/media/hynpxrt7s225tr/source/JumpPoint_02-04_Apr_14_Heads-Up.pdf"));

        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(BatchDownload.ACTION_CALCULATING);
        broadcastFilter.addAction(BatchDownload.ACTION_ERROR);
        broadcastFilter.addAction(BatchDownload.ACTION_PROGRESS);
        broadcastFilter.addAction(BatchDownload.ACTION_FILE_DOWNLOADED);
        broadcastFilter.addAction(BatchDownload.ACTION_COMPLETE);
        LocalBroadcastManager.getInstance(this).registerReceiver(callback, broadcastFilter);

        bd = BatchDownload.getInstance(this);
//        bd.add(requests);

        findViewById(R.id.add_url).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                System.out.println("" + bd.isRunning());
                bd.add(requests.get(0));
                requests.remove(0);
            }
        });

        findViewById(R.id.open_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ActivityMain.this, ActivityTest.class));
            }
        });

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bd.cancel();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callback);
    }


    // Callback Receiver
    private class Callback extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView text = (TextView) findViewById(R.id.message);
            if (intent.getAction().equals(BatchDownload.ACTION_CALCULATING)) {
                text.setText("Calculating size...");
//                System.out.println("Calculating...");
            } else if (intent.getAction().equals(BatchDownload.ACTION_ERROR)) {
//                System.out.println("Error: " + intent.getStringExtra(BatchDownload.EXTRA_ERROR_URL));
            } else if (intent.getAction().equals(BatchDownload.ACTION_CANCELLED)) {
//                System.out.println("Cancelled");
            } else if (intent.getAction().equals(BatchDownload.ACTION_PROGRESS)) {
                Bundle extras = intent.getExtras();
                String errors = "" + extras.getInt(BatchDownload.EXTRA_ERROR_COUNT);
//                int percent = Math.round(((float) extras.getLong(BatchDownload.EXTRA_BYTES_DOWNLOADED, 0) / extras.getLong(BatchDownload.EXTRA_TOTAL_SIZE, 0)) * 100);
                int percent = (int) ((extras.getLong(BatchDownload.EXTRA_BYTES_DOWNLOADED, 0) * 100) / extras.getLong(BatchDownload.EXTRA_TOTAL_BYTES, 0));
                String kilobytes = (extras.getLong(BatchDownload.EXTRA_TOTAL_BYTES) / 1000) + "KB";
                text.setText("" + percent + "%: " + kilobytes + ": Remaining: " + extras.getInt(BatchDownload.EXTRA_FILES_REMAINING));
            } else if (intent.getAction().equals(BatchDownload.ACTION_FILE_DOWNLOADED)) {
//                System.out.println(intent.getStringExtra(BatchDownload.EXTRA_FILENAME) + " : " + intent.getStringExtra(BatchDownload.EXTRA_FILEPATH));
            } else if (intent.getAction().equals(BatchDownload.ACTION_COMPLETE)) {
//                System.out.println("Complete!");
                text.setText("Download Complete. " + intent.getIntExtra(BatchDownload.EXTRA_ERROR_COUNT, 0) + " errors." + " Remaining: " + intent.getIntExtra(BatchDownload.EXTRA_FILES_REMAINING, 0));
            }
        }
    }
}