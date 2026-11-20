package futo.enhancements;
import android.util.Log;
/** Preserve FUTO's gesture/action flow; change only the delay sent to its timer. */
public final class SpacebarDuration {
    public static int timeout(Object tracker,int original) {
        if(!Config.unlocked())return original;
        String mode=Config.prefs().getString("spacebar_mode","keyboard");
        if(mode.equals("stock"))return original;
        try {
            Object key=Reflect.field(tracker,"mCurrentKey");
            if(key==null || ((Number)Reflect.field(key,"code")).intValue()!=32) return original;
            if(mode.equals("custom"))return Math.max(25,Math.min(1200,Config.prefs().getInt("spacebar_duration",300)));
            Object settings=Reflect.field(Reflect.type("org.futo.inputmethod.latin.settings.Settings"),"sInstance");
            return ((Number)Reflect.field(Reflect.field(settings,"mSettingsValues"),"mKeyLongpressTimeout")).intValue();
        } catch(ReflectiveOperationException e) {
            Log.e("FutoEnhancements","Could not synchronize spacebar long press duration");
            return original;
        }
    }
}
