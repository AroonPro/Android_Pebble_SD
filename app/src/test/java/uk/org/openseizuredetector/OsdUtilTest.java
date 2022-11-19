package uk.org.openseizuredetector;

import android.os.Handler;

import org.junit.Test;

import static org.hamcrest.Matchers.is;

import static org.junit.Assert.*;

/**
 * Created by graham on 01/01/16.
 */
public class OsdUtilTest {

    @Test
    public void testIsServerRunning() throws Exception {

    }

    @Test
    public void testStartServer() throws Exception {
        //Activity a = new Activity();
        Handler handler = new Handler();
        OsdUtil util = new OsdUtil(null,handler);
        assertThat(util.isServerRunning(), is(true));
        assertThat(true, is (true));
        //assertThat(true, is(false));
    }

    @Test
    public void testStopServer() throws Exception {

    }
}