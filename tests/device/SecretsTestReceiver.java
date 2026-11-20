package futo.enhancements.test;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Runs only isolated synthetic credentials under the test application's UID. */
public final class SecretsTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        MainActivity.testSecrets(context);
    }
}
