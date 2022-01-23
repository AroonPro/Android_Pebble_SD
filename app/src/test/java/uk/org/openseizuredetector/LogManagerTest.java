package uk.org.openseizuredetector;

import android.os.Build;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.O_MR1}, packageName = "uk.org.openseizuredetector")
public class LogManagerTest extends TestCase {
    LogManager mLm;
    public void setUp() throws Exception {
        super.setUp();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        mLm = new LogManager(RuntimeEnvironment.systemContext);
    }

    public void tearDown() throws Exception {
        mLm.close();
    }

    SdData getFakeSdData() {
        SdData sdData = new SdData();
        return sdData;
    }


    @Test
    public void testWriteToLocalDb() {
        SdData sd1 = getFakeSdData();
        mLm.writeToLocalDb(sd1);
        assertTrue(true);
    }

    public void testGetDatapointById() {
    }

    public void testSetDatapointToUploaded() {
    }

    public void testSetDatapointStatus() {
    }

    public void testGetDatapointsByDate() {
    }

    public void testGetEventsList() {
    }

    public void testPruneLocalDb() {
    }

    public void testGetNextEventToUpload() {
    }

    public void testGetNearestDatapointToDate() {
    }

    public void testGetLocalEventsCount() {
    }

    public void testGetLocalDatapointsCount() {
    }

    public void testWriteToRemoteServer() {
    }

    public void testUploadSdData() {
    }

    public void testAuthCallback() {
    }

    public void testFinishUpload() {
    }

    public void testEventCallback() {
    }

    public void testUploadNextDatapoint() {
    }

    public void testDatapointCallback() {
    }

    public void testClose() {
    }

    public void testStopRemoteLogTimer() {
    }

    public void testStopAutoPruneTimer() {
    }
}