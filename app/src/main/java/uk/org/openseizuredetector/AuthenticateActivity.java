package uk.org.openseizuredetector;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;

import org.json.JSONObject;
import java.util.Arrays;

/**
 * AuthenticateActivity - Handles User Authentication for Web API
 * Updated to use the generic SdServiceConnection architecture.
 */
public class AuthenticateActivity extends AppCompatActivity {
    private static final String TAG = "AuthenticateActivity";
    private OsdUtil mUtil;
    private EditText mUnameEt;
    private EditText mPasswdEt;
    private SdServiceConnection mConnection;
    final Handler serverStatusHandler = new Handler(Looper.getMainLooper());
    private WebApiConnection mWac;
    private LogManager mLm;
    private static final String TOKEN_ID = "webApiAuthToken";

    // --- 1. INITIALISE LAUNCHERS ---
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(), (result) -> updateUi());

    // --- 2. DYNAMIC RESOURCE HELPERS ---
    private int resId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private View safeFind(String idName) {
        int id = resId(idName, "id");
        return (id != 0) ? findViewById(id) : null;
    }

    private void setupButton(String name, View.OnClickListener listener) {
        View v = safeFind(name);
        if (v != null) v.setOnClickListener(listener);
    }

    // --- 3. CLICK LISTENERS ---
    private final View.OnClickListener onCancel = v -> finish();

    private final View.OnClickListener onAboutDataSharing = v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OsdUtil.DATA_SHARING_URL)));

    private final View.OnClickListener onPrivacyPolicy = v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OsdUtil.PRIVACY_POLICY_URL)));

    private final View.OnClickListener onRegister = v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://osdapi.ddns.net/static/register.html")));

    private final View.OnClickListener onResetPassword = v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://osdapi.ddns.net/static/request_password_reset.html")));

    private final View.OnClickListener onLogin = v -> {
        if (LogManager.USE_FIREBASE_BACKEND) {
            Intent signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(Arrays.asList(
                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                            new AuthUI.IdpConfig.EmailBuilder().build()))
                    .build();
            signInLauncher.launch(signInIntent);
        } else {
            if (mUnameEt != null && mPasswdEt != null) {
                if (mWac != null) {
                    mWac.authenticate(mUnameEt.getText().toString(), mPasswdEt.getText().toString(), retVal -> {
                        if (retVal != null) {
                            saveAuthToken(retVal);
                            updateUi();
                        } else {
                            mUtil.showToast("Authentication Failed");
                        }
                    });
                }
            }
        }
    };

    private final View.OnClickListener onLogout = v -> {
        if (LogManager.USE_FIREBASE_BACKEND) {
            AuthUI.getInstance().signOut(this).addOnCompleteListener(task -> updateUi());
        } else if (mWac != null) {
            mWac.logout();
            saveAuthToken(null);
            updateUi();
        }
    };

    // --- 4. LIFECYCLE ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int layoutId = resId("activity_authenticate", "layout");
        if (layoutId != 0) setContentView(layoutId);

        mUtil = new OsdUtil(getApplicationContext(), serverStatusHandler);

        if (!mUtil.isServerRunning()) {
            int errStr = resId("error_server_not_running", "string");
            if (errStr != 0) mUtil.showToast(getString(errStr));
            finish();
            return;
        }

        setupButton("cancelBtn", onCancel);
        setupButton("loginBtn", onLogin);
        setupButton("logoutCancelBtn", onCancel);
        setupButton("logoutBtn", onLogout);
        setupButton("RegisterBtn", onRegister);
        setupButton("ResetPasswordBtn", onResetPassword);
        setupButton("aboutDataSharingBtn", onAboutDataSharing);
        setupButton("privacyPolicyBtn", onPrivacyPolicy);

        if (!LogManager.USE_FIREBASE_BACKEND) {
            // FIX: Pass the class to the constructor to match the generic architecture
            mConnection = new SdServiceConnection(this);
            mUnameEt = (EditText) safeFind("username");
            mPasswdEt = (EditText) safeFind("password");
        }
    }

    private void updateUi() {
        if (mWac == null) return;
        View loginLl = safeFind("login_ui");
        View logoutLl = safeFind("logout_ui");

        if (mWac.isLoggedIn()) {
            if (loginLl != null) loginLl.setVisibility(View.GONE);
            if (logoutLl != null) logoutLl.setVisibility(View.VISIBLE);
            mWac.getUserProfile((JSONObject profileObj) -> {
                try {
                    TextView userIdTv = (TextView) safeFind("userIdTv");
                    TextView usernameTv = (TextView) safeFind("usernameTv");
                    if (userIdTv != null) userIdTv.setText(profileObj.getString("id"));
                    if (usernameTv != null) usernameTv.setText(profileObj.getString("username"));
                } catch (Exception e) { Log.e(TAG, "UI Update error"); }
            });
        } else {
            if (loginLl != null) loginLl.setVisibility(View.VISIBLE);
            if (logoutLl != null) logoutLl.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!LogManager.USE_FIREBASE_BACKEND && mConnection != null) {
            if (!mConnection.mBound) mConnection.doBindService(); // Use internal bind method
            waitForConnection();
        } else if (LogManager.USE_FIREBASE_BACKEND) {
            updateUi();
        }
    }

    /**
     * Waits for service binding and initialises connections to the Web API.
     */
    private void waitForConnection() {
        if (mConnection != null && mConnection.mBound && mConnection.mSdService != null) {
            // Access the LogManager via the bound service
            if (mConnection.mSdService instanceof AndroidSdService) {
                mLm = ((AndroidSdService) mConnection.mSdService).mLm;
                if (mLm != null) {
                    mWac = mLm.mWac;
                    updateUi();
                }
            }
        } else {
            // Retry on UI thread
            new Handler(Looper.getMainLooper()).postDelayed(this::waitForConnection, 100);
        }
    }

    private void saveAuthToken(String tokenStr) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putString(TOKEN_ID, tokenStr).apply();
        if (mWac != null) mWac.setStoredToken(tokenStr);
    }
}