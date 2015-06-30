package de.tum.in.tumcampus.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.AlarmClock;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import java.util.Calendar;
import java.util.Date;

import de.psdev.licensesdialog.LicensesDialog;
import de.tum.in.tumcampus.R;
import de.tum.in.tumcampus.activities.MainActivity;
import de.tum.in.tumcampus.activities.StartupActivity;
import de.tum.in.tumcampus.activities.TransportationActivity;
import de.tum.in.tumcampus.activities.wizard.WizNavStartActivity;
import de.tum.in.tumcampus.auxiliary.AccessTokenManager;
import de.tum.in.tumcampus.auxiliary.Const;
import de.tum.in.tumcampus.auxiliary.NetUtils;
import de.tum.in.tumcampus.auxiliary.SmartAlarmUtils;
import de.tum.in.tumcampus.auxiliary.Utils;
import de.tum.in.tumcampus.models.managers.CacheManager;
import de.tum.in.tumcampus.models.managers.CalendarManager;
import de.tum.in.tumcampus.models.managers.CardManager;
import de.tum.in.tumcampus.models.managers.DatabaseManager;
import de.tum.in.tumcampus.models.managers.NewsManager;
import de.tum.in.tumcampus.services.BackgroundService;
import de.tum.in.tumcampus.services.SilenceService;
import de.tum.in.tumcampus.services.SmartAlarmReceiver;

public class SettingsFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private static final int MVV_STATION_REQUEST = 1;
    private static final int RINGTONE_REQUEST = 2;

    private FragmentActivity mContext;
    private Preference smartAlarmPublic, smartAlarmPrivate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        mContext = getActivity();

        // Disables silence service if the app is used without TUMOnline access
        CheckBoxPreference silent = (CheckBoxPreference) findPreference("silent_mode");
        if (!new AccessTokenManager(mContext).hasValidAccessToken()) {
            silent.setEnabled(false);
        }

        // Hide unneeded settings of smart alarm
        PreferenceScreen smartAlarmScreen = (PreferenceScreen) findPreference(Const.SMART_ALARM_SCREEN);
        smartAlarmPublic = findPreference(Const.SMART_ALARM_CAT_PUBLIC);
        smartAlarmPrivate = findPreference(Const.SMART_ALARM_CAT_PRIVATE);
        if (((CheckBoxPreference)findPreference(Const.SMART_ALARM_MODE)).isChecked()) {
            smartAlarmScreen.removePreference(smartAlarmPrivate);
        } else {
            smartAlarmScreen.removePreference(smartAlarmPublic);
        }

        // display mvv station in summary
        Preference mvv_station_picker = findPreference("smart_alarm_home_button");
        String station = PreferenceManager.getDefaultSharedPreferences(mContext).getString("smart_alarm_home", "");
        if (station == null || station.equals("")) {
            mvv_station_picker.setSummary(getResources().getString(R.string.smart_alarm_nostation));
        } else {
            mvv_station_picker.setSummary(station);
        }

        // display selected ringtone
        Preference ringtone_picker = findPreference("smart_alarm_ringtone");
        String ringtone = PreferenceManager.getDefaultSharedPreferences(mContext).getString("smart_alarm_ringtone", "");
        Uri ringtoneURI;
        if (ringtone == null || ringtone.equals("")) {
            ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        } else {
            ringtoneURI = Uri.parse(ringtone);
        }
        ringtone_picker.setSummary(RingtoneManager.getRingtone(mContext, ringtoneURI).getTitle(mContext));

        // Click listener for preference list entries. Used to simulate a button
        // (since it is not possible to add a button to the preferences screen)
        findPreference("smart_alarm_home_button").setOnPreferenceClickListener(this);
        findPreference("smart_alarm_ringtone").setOnPreferenceClickListener(this);
        findPreference("button_wizard").setOnPreferenceClickListener(this);
        findPreference("button_clear_cache").setOnPreferenceClickListener(this);
        findPreference("facebook").setOnPreferenceClickListener(this);
        findPreference("github").setOnPreferenceClickListener(this);
        findPreference("first_run").setOnPreferenceClickListener(this);
        findPreference("licenses").setOnPreferenceClickListener(this);
        findPreference("feedback").setOnPreferenceClickListener(this);
        findPreference("privacy").setOnPreferenceClickListener(this);

        // Set summary for these preferences
        setSummary("card_cafeteria_default_G");
        setSummary("card_cafeteria_default_K");
        setSummary("card_cafeteria_default_W");
        setSummary("card_role");
        setSummary("card_stations_default_G");
        setSummary("card_stations_default_C");
        setSummary("card_stations_default_K");
        setSummary("card_default_campus");
        setSummary("silent_mode_set_to");

        // Register the change listener to react immediately on changes
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .registerOnSharedPreferenceChangeListener(this);

        // Populate news sources
        populateNewsSources();

        // Open a card's preference screen if selected from it's context menu
        Bundle bundle = getArguments();
        if (bundle != null && bundle.containsKey(Const.PREFERENCE_SCREEN)) {
            final String key = bundle.getString(Const.PREFERENCE_SCREEN);

            PreferenceScreen screen = (PreferenceScreen) findPreference("cards_pref_container");
            final PreferenceScreen cardPreferenceScreen = (PreferenceScreen) findPreference(key);
            cardPreferenceScreen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    initializeActionBar(cardPreferenceScreen);
                    return true;
                }
            });

            final ListAdapter listAdapter = screen.getRootAdapter();
            final int itemsCount = listAdapter.getCount();
            for (int i = 0; i < itemsCount; ++i) {
                if (listAdapter.getItem(i).equals(cardPreferenceScreen)) {
                    screen.onItemClick(null, null, i, 0);
                    break;
                }
            }
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        getListView().setPadding((int) (16 * metrics.density), 0, (int) (16 * metrics.density), 0);
    }

    private void populateNewsSources() {
        PreferenceCategory news_sources = (PreferenceCategory) findPreference("card_news_sources");
        NewsManager cm = new NewsManager(mContext);
        Cursor cur = cm.getNewsSources();
        if (cur.moveToFirst()) {
            do {
                final CheckBoxPreference pref = new CheckBoxPreference(mContext);
                pref.setKey("card_news_source_" + cur.getString(0));
                pref.setDefaultValue(true);
                if (Build.VERSION.SDK_INT >= 11) {
                    // Load news source icon in background and set it
                    final String url = cur.getString(1);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            NetUtils net = new NetUtils(mContext);
                            final Bitmap bmp = net.downloadImageToBitmap(url);
                            mContext.runOnUiThread(new Runnable() {
                                @TargetApi(11)
                                @Override
                                public void run() {
                                    pref.setIcon(new BitmapDrawable(getResources(), bmp));
                                }
                            });
                        }
                    }).start();
                }
                pref.setTitle(cur.getString(2));
                news_sources.addPreference(pref);
            } while (cur.moveToNext());
        }
        cur.close();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) pref;
            listPreference.setSummary(listPreference.getEntry());
        }

        if (key.startsWith("card_")) {
            CardManager.shouldRefresh = true;
        }

        // When newspread selection changes
        // deselect all newspread sources and select only the
        // selected source if one of all was selected before
        if (key.equals("news_newspread")) {
            SharedPreferences.Editor e = sharedPreferences.edit();
            boolean value = false;
            for (int i = 7; i < 14; i++) {
                if (sharedPreferences.getBoolean("card_news_source_" + i, false))
                    value = true;
                e.putBoolean("card_news_source_" + i, false);
            }
            String new_source = sharedPreferences.getString(key, "7");
            e.putBoolean("card_news_source_" + new_source, value);
            e.apply();
            CardManager.shouldRefresh = true;
        }

        // If the silent mode was activated, start the service. This will invoke
        // the service to call onHandleIntent which checks available lectures
        if (key.equals(Const.SILENCE_SERVICE)) {
            Intent service = new Intent(mContext, SilenceService.class);
            if (sharedPreferences.getBoolean(key, false)) {
                mContext.startService(service);
            } else {
                mContext.stopService(service);
            }
        }

        // If the background mode was activated, start the service. This will invoke
        // the service to call onHandleIntent which updates all background data
        if (key.equals(Const.BACKGROUND_MODE)) {
            Intent service = new Intent(mContext, BackgroundService.class);
            if (sharedPreferences.getBoolean(key, false)) {
                mContext.startService(service);
            } else {
                mContext.stopService(service);
            }
        }

        if (key.equals(Const.SMART_ALARM_ACTIVE)) {
            //noinspection ConstantConditions
            if (((CheckBoxPreference) pref).isChecked()) {
                // TODO: start alarm
                AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Service.ALARM_SERVICE);
                Intent i = new Intent(mContext, SmartAlarmReceiver.class);
                PendingIntent p = PendingIntent.getBroadcast(mContext, SmartAlarmReceiver.PRE_ALARM_REQUEST, i, 0);
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY));
                c.set(Calendar.MINUTE, c.get(Calendar.MINUTE) + 1);
                alarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), p);
                // alarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), p);
                SmartAlarmUtils.schedulePreAlarm(mContext);
            } else {
                // TODO: stop alarm
            }
        }

        if (key.equals(Const.SMART_ALARM_MODE)) {
            PreferenceScreen smartAlarmScreen = (PreferenceScreen) findPreference(Const.SMART_ALARM_SCREEN);
            //noinspection ConstantConditions
            if (((CheckBoxPreference) pref).isChecked()) {
                smartAlarmScreen.removePreference(findPreference(Const.SMART_ALARM_CAT_PRIVATE));
                smartAlarmScreen.addPreference(smartAlarmPublic);
            } else {
                smartAlarmScreen.removePreference(findPreference(Const.SMART_ALARM_CAT_PUBLIC));
                smartAlarmScreen.addPreference(smartAlarmPrivate);
            }
        }
    }

    //    @SuppressWarnings("deprecation")
    void setSummary(String key) {
        Preference t = findPreference(key);
        if (t instanceof ListPreference) {
            ListPreference pref = (ListPreference) t;
            pref.setSummary(pref.getEntry());
        }
    }

    /**
     * Handle all clicks on 'button'-preferences
     *
     * @param preference Preference that has been clicked
     * @return True, if handled
     */
    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();

        switch (key) {
            case "button_wizard":
                mContext.finish();
                startActivity(new Intent(mContext, WizNavStartActivity.class));


                break;
            case "button_clear_cache":
                // This button invokes the clear cache method
                new AlertDialog.Builder(mContext)
                        .setMessage(R.string.delete_chache_sure)
                        .setPositiveButton(R.string.yes, new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                clearCache();
                            }
                        })
                        .setNegativeButton(R.string.no, null).show();

                break;
            case "facebook":
                // Open the facebook app or view in a browser when not installed
                Intent facebook;
                try {
                    //Try to get facebook package to check if fb app is installed
                    mContext.getPackageManager().getPackageInfo("com.facebook.katana", 0);
                    facebook = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_link_app)));
                } catch (Exception e) {
                    //otherwise just open the normal url
                    facebook = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_link)));
                }
                startActivity(facebook);


                break;
            case "github":
                // Open TCA-github web page
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_link))));


                break;
            case "first_run":
                // Show first use tutorial
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean(CardManager.SHOW_TUTORIAL_1, true);
                e.putBoolean(CardManager.SHOW_TUTORIAL_2, true);
                e.apply();
                CardManager.update(mContext);
                startActivity(new Intent(mContext, MainActivity.class));


                break;
            case "licenses":
                // Show licences
                new LicensesDialog(mContext, R.raw.notices, false, true).show();


                break;
            case "feedback":
            /* Create the Intent */
                Uri uri = Uri.parse("mailto:tca-support.os.in@tum.de?subject=" + getString(R.string.feedbackSubj));

                Intent sendIntent = new Intent(Intent.ACTION_SENDTO);
                sendIntent.setData(uri);

		    /* Send it off to the Activity-Chooser */
                startActivity(Intent.createChooser(sendIntent, getString(R.string.send_email)));
                break;
            case "privacy":
                Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_privacy_policy)));
                startActivity(myIntent);
                break;

            case "smart_alarm_home_button":
                Intent mvv = new Intent(mContext, TransportationActivity.class);
                startActivityForResult(mvv, MVV_STATION_REQUEST);
                break;

            case "smart_alarm_ringtone":
                Intent ringtonePicker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                ringtonePicker.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getResources().getString(R.string.smart_alarm_ringtone_selectortitle));
                ringtonePicker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                ringtonePicker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                ringtonePicker.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE | RingtoneManager.TYPE_ALARM);
                startActivityForResult(ringtonePicker, RINGTONE_REQUEST);
                break;

            default:
                return false;
        }

        return true;
    }

    /**
     * Clears all downloaded data from SD card and database
     */
    private void clearCache() {
        DatabaseManager.resetDb(mContext);

        CacheManager manager = new CacheManager(mContext);
        manager.clearCache();

        // delete local calendar
        Utils.setInternalSetting(mContext, Const.SYNC_CALENDAR, false);
        if (Build.VERSION.SDK_INT >= 14) {
            CalendarManager.deleteLocalCalendar(mContext);
        }

        Utils.showToast(mContext, R.string.success_clear_cache);
        Utils.setInternalSetting(mContext, Const.EVERYTHING_SETUP, false);

        mContext.finish();
        startActivity(new Intent(mContext, StartupActivity.class));
    }

    /**
     * Sets up the action bar for an {@link PreferenceScreen}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void initializeActionBar(PreferenceScreen preferenceScreen) {
        final Dialog dialog = preferenceScreen.getDialog();

        //Check if dialog is open and if we are on a supported android version
        if (dialog != null && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            //Setup a dialog back button pressed listener
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mContext.finish();
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent res) {
        switch (requestCode) {
            case MVV_STATION_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    SharedPreferences.Editor prefEditor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                    String station = res.getExtras().getString("station");
                    prefEditor.putString("smart_alarm_home", station);
                    prefEditor.apply();
                    findPreference("smart_alarm_home_button").setSummary(station);
                }
                break;

            case RINGTONE_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    Uri ringtone = res.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (ringtone != null) {
                        SharedPreferences.Editor prefEditor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                        prefEditor.putString("smart_alarm_ringtone", ringtone.toString());
                        prefEditor.apply();
                        findPreference("smart_alarm_ringtone").setSummary(RingtoneManager.getRingtone(mContext, ringtone).getTitle(mContext));
                    }
                }
                break;

            default:
                break;
        }
    }
}