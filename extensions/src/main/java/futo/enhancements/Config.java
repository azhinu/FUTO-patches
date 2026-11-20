package futo.enhancements;
import android.content.Context;
import android.content.SharedPreferences;
public final class Config {
    private static Context context;
    public static boolean dynamicInstalled() { return false; }
    public static boolean remoteInstalled() { return false; }
    public static boolean swipeBackspaceInstalled() { return false; }
    public static boolean spacebarInstalled() { return false; }
    public static boolean swipeBackspace() { return swipeBackspaceInstalled() && unlocked() && prefs().getBoolean("swipe_backspace",false); }
    public static boolean subtypesInstalled() { return false; }
    public static void init(Context value) { context = value.getApplicationContext(); }
    public static Context context() { return context; }
    public static SharedPreferences prefs() {
        if (context == null) throw new IllegalStateException("Enhancements are not initialized");
        return context.getSharedPreferences("futo_enhancements", Context.MODE_PRIVATE);
    }
    public static boolean unlocked() { return context!=null && context.getSystemService(android.os.UserManager.class).isUserUnlocked(); }
    public static boolean dynamic() { return dynamicInstalled() && unlocked() && prefs().getBoolean("dynamic", false); }
    public static boolean remote() { return remoteInstalled() && unlocked() && !prefs().getString("backend", "local").equals("local") && prefs().getBoolean("remote_consent", false); }
}
