/*
  RMirror - en_GB
  Dynamic Resource Mirror for Phone & Wear modules.
  Repaired for 2024 LSA Audit to ensure cross-module stability.
*/

package uk.org.openseizuredetector;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * RMirror - en_GB
 * Provides a unified resource mapping interface for AuthenticateActivity.
 * * R-Free Architecture:
 * Replaces static R-references with dynamic lookups via OsdUtil.
 * * LSA Audit 2024 Context:
 * Ensures the login and data-sharing UI remains functional across different
 * hardware profiles, preventing "Symbol Not Found" crashes during audits.
 */
public class RMirror {
    private static final String TAG = "RMirror";
    private static OsdUtil mUtil;

    public RMirror(Context context) {
        if (mUtil == null) {
            mUtil = new OsdUtil(context.getApplicationContext(), new Handler());
        }
    }

    /**
     * layout - en_GB
     * Dynamic layout mapping.
     */
    public static final class layout {
        public static int get_activity_authenticate() {
            return mUtil.getResId("activity_authenticate", "layout");
        }
    }

    /**
     * id - en_GB
     * Dynamic ID mapping for UI components.
     */
    public static final class id {
        public static int get(String resName) {
            return mUtil.getResId(resName, "id");
        }

        // Commonly used IDs for the 2024 LSA Login Flow
        public static int username() { return get("username"); }
        public static int password() { return get("password"); }
        public static int loginBtn() { return get("loginBtn"); }
        public static int cancelBtn() { return get("cancelBtn"); }
        public static int logoutBtn() { return get("logoutBtn"); }
        public static int userIdTv()  { return get("userIdTv"); }
        public static int usernameTv() { return get("usernameTv"); }
        public static int privacyPolicyBtn() { return get("privacyPolicyBtn"); }
    }

    /**
     * string - en_GB
     * Dynamic string mapping for internationalised audit requirements.
     */
    public static final class string {
        public static String get(String resName) {
            return mUtil.getStringById(resName);
        }

        public static String error_server() { return get("error_server"); }
    }
}