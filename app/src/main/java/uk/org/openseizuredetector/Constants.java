package uk.org.openseizuredetector;

import android.net.Uri;

public class Constants {
    public interface GLOBAL_CONSTANTS {
        public static final int PERMISSION_REQUEST_BODY_SENSORS = 16;
        public final String wearableAppCheckPayload = "AppOpenWearable";
        public final String wearableAppCheckPayloadReturnACK = "AppOpenWearableACK";
        public final String TAG_MESSAGE_RECEIVED = "SdDataSourceAw";
        public final String MESSAGE_ITEM_RECEIVED_PATH = "/message-item-received";
        public final String MESSAGE_ITEM_OSD_TEST = "/testMsg";
        public final String MESSAGE_ITEM_OSD_DATA = "/data";
        public final String MESSAGE_ITEM_OSD_TEST_RECEIVED = "/testMsg-received";
        public final String MESSAGE_ITEM_OSD_DATA_REQUESTED = "/data-requested";
        public final String MESSAGE_ITEM_OSD_DATA_RECEIVED = "/data-received";
        public final String MESSAGE_ITEM_PATH = "/message-item";
        public final String APP_OPEN_WEARABLE_PAYLOAD_PATH = "/APP_OPEN_WEARABLE_PAYLOAD";
        public final String TAG_GET_NODES = "getnodes1";
        public final String mAppPackageName = "uk.org.openseizuredetector";
        public final String mAppPackageNameWearReceiver = "uk.org.openseizuredetector.aw";
        public final String mAppPackageNameWearSD = "uk.org.openseizuredetector.aw";
        public final Uri mStartUri = Uri.parse("Start");
        public final Uri mStopUri = Uri.parse("Stop");
        public final Uri mPASSUri = Uri.parse("PASS");
        public final String mPowerLevel = "powerLevel";
        public final String mSettingsString = "settingsJson";
        public final String mSdServerIntent = "sdServerIntent";

    }

    public interface ACTION {
        public static String STARTFOREGROUND_ACTION = "uk.org.openseizuredetect.startforeground";
        public static String STOPFOREGROUND_ACTION = "uk.org.openseizuredetect.stopforeground";
        public static String BATTERYUPDATE_ACTION = "uk.org.openseizuredetector.onBatteryUpdate";
        public static String CONNECTIONUPDATE_ACTION = "uk.org.openseizuredetector.onConnectionUpdate";
        public static String PUSH_SETTINGS_ACTION = "uk.org.openseizuredetector.aw.wear.pushSettings";
        public static String PULL_SETTINGS_ACTION = "uk.org.openseizuredetector.aw.wear.pullSettings";
        public static String START_WEAR_APP_ACTION = "uk.org.openseizuredetector.aw.wear.startWear";
        public static String STOP_WEAR_APP_ACTION = "uk.org.openseizuredetector.aw.wear.stopWear";
        public static String START_WEAR_SD_ACTION = "uk.org.openseizuredetector.aw.wear.startWearSD";
        public static String STOP_WEAR_SD_ACTION = "uk.org.openseizuredetector.aw.wear.stopWearSD";
        public static String START_MOBILE_RECEIVER_ACTION = "uk.org.openseizuredetector.aw.mobile.startWearReceiver";
        public static String START_MOBILE_SD_ACTION = "uk.org.openseizuredetector.aw.mobile.startSeizureDetectorServer";
        public static String REGISTER_START_INTENT_AW = "uk.org.openseizuredetector.aw.mobile.registerStartIntents";
        public static String REGISTER_START_INTENT = "uk.org.openseizuredetector.registerStartIntents";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
    }

    public interface TAGS {
        public static String AWSDService = "AWSdService";
    }

    public interface SD_SERVICE_CONSTANTS{
        public static short defaultSampleRate = 25;
        public static short defaultSampleTime = 10;
        public static short defaultSampleCount = defaultSampleRate * defaultSampleTime;
    }
}
