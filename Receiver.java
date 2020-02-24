package com.smart.httpwww.smartfit.Base.feature.mainOrder;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.smart.httpwww.smartfit.Base.modelWrapper.DefaultGitsResponse;
import com.smart.httpwww.smartfit.Base.network.NetworkCallback;
import com.smart.httpwww.smartfit.Base.network.NetworkClient;
import com.smart.httpwww.smartfit.Base.network.NetworkStores;
import com.smart.httpwww.smartfit.Base.utils.FixValue;
import com.smart.httpwww.smartfit.Base.utils.Functionality;
import com.smart.httpwww.smartfit.Base.utils.Preference;
import com.smart.httpwww.smartfit.Base.utils.ResendCSVService;
import com.smart.httpwww.smartfit.R;
import org.threeten.bp.LocalTime;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static com.smart.httpwww.smartfit.Base.utils.FixValue.INTENT_CSV;

/**
 * The type Receiver.
 */
public class Receiver extends BroadcastReceiver {

    private static final String TAG = "Receiver";
    /**
     * The Composite subscription.
     */
    CompositeSubscription compositeSubscription;
    private List<String> filesString;
    private List<File> csvFiles;
    private List<File> csvFilesNotValidMetaDate;
    private CountDownTimer timer;
    private static String pathEfact = "/storage/emulated/0/PMMP_sawber_upload/";
    private static String pathLogSawber = "/storage/emulated/0/sawber/log/";
    private static String pathSawber = "/storage/emulated/0/sawber/processed/";
    private static String NOTIFICATION_CHANNEL_ID1 = "sawber_no_csv";
    private static Integer NOTIFICATION_CHANNEL_ID1i = 1;
    private static String NOTIFICATION_CHANNEL_ID2 = "sawber_failed_csv";
    private static Integer NOTIFICATION_CHANNEL_ID2i = 2;
    private static String NOTIFICATION_CHANNEL_ID3 = "sawber_uploaded_csv";
    private static Integer NOTIFICATION_CHANNEL_ID3i = 3;

    /**
     * Triggered from alarm manager, time set for check and send csv from local storage
     *
     * @param context
     * @param intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        preCheckCSVs(context);
    }

    private BroadcastReceiver configuredResendCSVReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            preCheckCSVs(context);

        }
    };

    /**
     * Set schedule send csv file from external storage with three time, 12:00, 15:00 17:00
     *
     * @param ctx the Context
     */
    public void setSchedule1(Context ctx){

        AndroidThreeTen.init(ctx);

        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        Calendar calendar3 = Calendar.getInstance();

        calendar1.set(Calendar.HOUR_OF_DAY, 12);
        calendar1.set(Calendar.MINUTE, 0);
        calendar1.set(Calendar.SECOND, 0);

        calendar2.set(Calendar.HOUR_OF_DAY, 15);
        calendar2.set(Calendar.MINUTE, 0);
        calendar2.set(Calendar.SECOND, 0);

        calendar3.set(Calendar.HOUR_OF_DAY, 20);
        calendar3.set(Calendar.MINUTE, 17);
        calendar3.set(Calendar.SECOND, 0);

        Intent intent = new Intent(ctx, Receiver.class);
        PendingIntent pendingIntent1 = PendingIntent.getBroadcast(ctx, FixValue.ID_SCHEDULE1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent2 = PendingIntent.getBroadcast(ctx, FixValue.ID_SCHEDULE2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent3 = PendingIntent.getBroadcast(ctx, FixValue.ID_SCHEDULE3, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar1.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent1);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar2.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent2);
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar3.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent3);
        }

    }

    /**
     * Add subscribe.
     *
     * @param observable the observable
     * @param subscriber the subscriber
     */
    protected void addSubscribe(Observable observable, Subscriber subscriber) {
        if (compositeSubscription == null) {
            compositeSubscription = new CompositeSubscription();
        }
        compositeSubscription.add(observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber));
    }

    /**
     * validation time process and user type.
     *
     * @param context the context
     */
    public void preCheckCSVs(Context context) {
        AndroidThreeTen.init(context);

        if  (LocalTime.now().isBefore( LocalTime.parse( "11:59" )) ||
                LocalTime.now().isAfter( LocalTime.parse( "21:05" )) ||
                "driver".equalsIgnoreCase(Functionality.getStringFromSharedPref(context, Preference.prefTypeUser))) {
            // if process before 11:59 or after 21:05 or driver stop service
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(configuredResendCSVReceiver);
            } catch (Exception e) {
                Log.d("context", e.getMessage());
            }

            try {
                context.stopService(new Intent(context, ResendCSVService.class));
                ResendCSVService.Companion.cancelTimer();
            } catch (Exception e) {
                Log.d("context", e.getMessage());
            }

            if (timer!=null) {
                timer.cancel();
            }
        } else {
            checkCSVs(context);
        }
    }

    /**
     * Check csv file.
     *
     * @param context the context
     */
    public void checkCSVs(Context context) {

        if (filesString!=null && !filesString.isEmpty()) {
            filesString.clear();
        }

        // check file in external storage with specified path and file name start with efact
        // if any gets complete path
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try (Stream<Path> walk = Files.walk(Paths.get(pathEfact))) {

                filesString = walk.filter(p -> p.getFileName().toString().startsWith("efact"))
                        .map(x -> x.toString()).collect(Collectors.toList());

            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
        } else {

            File f = new File(pathEfact);
            File[] allSubFiles = f.listFiles();
            filesString = new ArrayList<>();
            for (File file : allSubFiles) {
                if (!file.isDirectory() && file.getName().startsWith("efact")) {

                    filesString.add(pathEfact + file.getName());

                }

            }
        }

        csvFiles = new ArrayList<>();

        // if any file in path efact create file
        if (filesString!=null && !filesString.isEmpty()) {

            for (int i = 0; i < filesString.size(); i++) {

                File csvFile = new File(filesString.get(i));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {

                        Path p = Paths.get(csvFile.getAbsolutePath());
                        BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                        String metaDate = attr.lastModifiedTime().toString();
                        String dateNow = Functionality.curentDate();
                        // validate metadata dateupdate compare to date now
                        if (metaDate.contains(dateNow)) {
                            csvFiles.add(csvFile);
                        } else {
                            if (csvFilesNotValidMetaDate == null) {
                                csvFilesNotValidMetaDate = new ArrayList<>();
                            }
                            csvFilesNotValidMetaDate.add(csvFile);

                            String notificationTitle = context.getResources().getString(R.string.notification);
                            String notificationText = context.getResources().getString(R.string.notification_no_csv);
                            createNotification(context, NOTIFICATION_CHANNEL_ID1, notificationTitle, notificationText, NOTIFICATION_CHANNEL_ID1i);

                            repeatSendCSV(context);
                        }
                    } catch (IOException e) {
                        Log.d(TAG, e.getMessage());
                    }
                } else {
                    csvFiles.add(csvFile);
                }

            }

            // if any file send to api
            if (csvFiles!=null && !csvFiles.isEmpty()) {
                uploadCSVs(context);
            }

        } else {

            String notificationTitle = context.getResources().getString(R.string.notification);
            String notificationText = context.getResources().getString(R.string.notification_no_csv);
            createNotification(context, NOTIFICATION_CHANNEL_ID1, notificationTitle, notificationText, NOTIFICATION_CHANNEL_ID1i);

            repeatSendCSV(context);

        }
    }

    /**
     * Show notification info csv file prgoress
     *
     * @param context Context
     * @param id id notification
     * @param notificationTitle title
     * @param notificationText content
     * @param idI id notification chanel
     */

    private void createNotification(Context context, String id, String notificationTitle, String notificationText, Integer idI){

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("WrongConstant") NotificationChannel notificationChannel = new NotificationChannel(id, "Sawber Notifications", NotificationManager.IMPORTANCE_HIGH);
            // Configure the notification channel.
            notificationChannel.setDescription("Notification");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, id);

        // 2 is for retry send csv
        if (2 == idI) {

            Intent notificationIntent = new Intent(context, Receiver.class);
            PendingIntent intent = PendingIntent.getBroadcast(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notificationBuilder.setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher_app)
                    .setTicker("Sawber")
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationTitle))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                    .setPriority(Notification.PRIORITY_MAX)
                    .setContentIntent(intent);
            notificationManager.notify(idI, notificationBuilder.build());
        } else {
            notificationBuilder.setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.mipmap.ic_launcher_app)
                    .setTicker("Sawber")
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationTitle))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                    .setPriority(Notification.PRIORITY_MAX);
            notificationManager.notify(idI, notificationBuilder.build());
        }

    }

    /**
     * upload csv file to api
     *
     * @param context
     */
    private void uploadCSVs(Context context){

        MultipartBody.Part body = null;

        try {

            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), csvFiles.get(0));

            body = MultipartBody.Part.createFormData("efact_csv", csvFiles.get(0).getName(), requestFile);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }

        if (body!=null) {

            NetworkStores apiStoresGits = NetworkClient.getRetrofitGits().create(NetworkStores.class);

            addSubscribe(apiStoresGits.uploadCSV(body), new NetworkCallback<DefaultGitsResponse>() {

                @Override
                public void onSuccess(DefaultGitsResponse defaultGitsResponse) {

                    // if success stop service
                    try {
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(configuredResendCSVReceiver);
                    } catch (Exception e) {
                        Log.d(TAG, e.getMessage());
                    }

                    context.stopService(new Intent(context, ResendCSVService.class));
                    ResendCSVService.Companion.cancelTimer();

                    if (timer!=null) {
                        timer.cancel();
                    }

                    if (csvFiles!=null && !csvFiles.isEmpty()) {
                        // move file to sawber directory
                        moveFile(context, pathEfact, csvFiles.get(0).getName(), pathSawber + Functionality.curentDate() + "/");
                    }

                }

                @Override
                public void onFailure(String message) {
                    // if failed send but not cause wrong format repeat send
                    if (!"Failed".equalsIgnoreCase(message)) {
                        if (!csvFiles.isEmpty()) {

                            List<String> fileNames = new ArrayList<>();

                            for (File file: csvFiles) {
                                fileNames.add(file.getName());
                            }

                            StringBuilder fileName = new StringBuilder();

                            if (fileNames.size()>1) {
                                for (String s : fileNames) {
                                    fileName.append(s + ", ");
                                }
                            } else {
                                fileName.append(fileNames.get(0));
                            }


                            String notificationTitle = context.getResources().getString(R.string.notification);
                            String notificationText = context.getResources().getString(R.string.notification_failed_csv, fileName);
                            createNotification(context, NOTIFICATION_CHANNEL_ID2, notificationTitle, notificationText, NOTIFICATION_CHANNEL_ID2i);
                            repeatSendCSV(context);
                        }
                    } else {
                        // if failed send cause wrong format delete file
                        delFile(context, pathEfact, csvFiles.get(0).getName());
                    }

                }

                @Override
                public void onFinish() {

                }

            });

        }

    }



    private void repeatSendCSV(Context context) {

        try {

            // start resend with backgroud service

            try {
                context.stopService(new Intent(context, ResendCSVService.class));
                ResendCSVService.Companion.cancelTimer();
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }

            context.startService(new Intent(context, ResendCSVService.class));

            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(configuredResendCSVReceiver);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }

            IntentFilter filterCSV = new IntentFilter();
            filterCSV.addAction(INTENT_CSV);
            LocalBroadcastManager.getInstance(context).registerReceiver(configuredResendCSVReceiver, filterCSV);

            if (timer!=null) {
                timer.cancel();
            }


        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
            // start resend with foreground service
            repeatSendCSV2(context);
        }

    }

    private void repeatSendCSV2(Context context) {

        timer = new CountDownTimer((long) (15 * 60 * 1000), 1000) {
            public void onTick(long diff) {

            }

            public void onFinish() {
                timer.cancel();
                preCheckCSVs(context);
            }
        }.start();
    }



    private void moveFile(Context context, String inputPath, String inputFile, String outputPath) {

        InputStream in = null;
        OutputStream out = null;
        try {

//            create output directory if it doesn't exist
            File dir = new File (outputPath);
            if (!dir.exists())
            {
                dir.mkdirs();
            }


            in = new FileInputStream(inputPath + inputFile);
            out = new FileOutputStream(outputPath + inputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;

            // write the output file
            out.flush();
            out.close();
            out = null;

            // delete the original file
            new File(inputPath + inputFile).delete();
            if (!csvFiles.isEmpty()) {
                csvFiles.remove(0);
            }

            if (csvFiles.isEmpty()) {
                String notificationTitle = context.getResources().getString(R.string.notification);
                String notificationText = context.getResources().getString(R.string.notification_uploaded_csv);
                createNotification(context, NOTIFICATION_CHANNEL_ID3, notificationTitle, notificationText, NOTIFICATION_CHANNEL_ID3i);
            }


        }

        catch (FileNotFoundException fnfe1) {
            Log.e(TAG, fnfe1.getMessage());
        }
        catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        if (!csvFiles.isEmpty()) {
            uploadCSVs(context);
        }

    }

    private void delFile(Context context, String inputPath, String inputFile) {

        // delete the original file
        new File(inputPath + inputFile).delete();
        if (!csvFiles.isEmpty()) {
            csvFiles.remove(0);
        }

        if (!csvFiles.isEmpty()) {
            uploadCSVs(context);
        }

    }


}
