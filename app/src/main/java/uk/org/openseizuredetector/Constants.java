package uk.org.openseizuredetector;

public class Constants {
    public interface ACTION {
        public static String STARTFOREGROUND_ACTION = "uk.org.openseizuredetect.startforeground";
        public static String STOPFOREGROUND_ACTION = "uk.org.openseizuredetect.stopforeground";
        public static String BATTERYUPDATE_ACTION = "uk.org.openseizuredetector.onBatteryUpdate";
        public static String CONNECTIONUPDATE_ACTION = "uk.org.openseizuredetector.onConnectionUpdate";
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
