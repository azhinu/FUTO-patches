package futo.enhancements;
import org.futo.inputmethod.latin.settings.Settings;
public final class SpacebarDurationTest {
    public static final class Key { public int code; Key(int value){code=value;} }
    public static final class Tracker { public Key mCurrentKey; Tracker(int code){mCurrentKey=new Key(code);} }
    private static void check(int actual,int expected){if(actual!=expected)throw new AssertionError("Unexpected long press delay: "+actual+" != "+expected);}
    public static void main(String[] args) {
        Tracker space=new Tracker(32);
        check(SpacebarDuration.timeout(space,400),200);
        check(SpacebarDuration.timeout(space,600),200);
        check(SpacebarDuration.timeout(space,100),200);
        check(SpacebarDuration.timeout(new Tracker(65),600),600);
        check(SpacebarDuration.timeout(new Tracker(-1),500),500);
        Settings.sInstance.mSettingsValues.mKeyLongpressTimeout=350;
        check(SpacebarDuration.timeout(space,700),350);
        Config.preferences.mode="stock";
        check(SpacebarDuration.timeout(space,700),700);
        Config.preferences.mode="custom";
        Config.preferences.duration=425;
        check(SpacebarDuration.timeout(space,700),425);
        check(SpacebarDuration.timeout(new Tracker(65),600),600);
        Config.preferences.duration=5;
        check(SpacebarDuration.timeout(space,700),25);
        Config.preferences.duration=9000;
        check(SpacebarDuration.timeout(space,700),1200);
        Config.preferences.mode="keyboard";
        space.mCurrentKey=null;
        check(SpacebarDuration.timeout(space,700),700);
        System.out.println("Passed 12 spacebar timer cases");
    }
}
