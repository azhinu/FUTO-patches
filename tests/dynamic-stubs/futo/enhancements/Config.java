package futo.enhancements;
public class Config {
    public static boolean swipe;
    public static boolean dynamic(){return true;}
    public static boolean swipeBackspace(){return swipe;}
    public static boolean unlocked(){return true;}
    public static void init(android.content.Context c){}
    public static final Prefs preferences=new Prefs();
    public static Prefs prefs(){return preferences;}
    public static final class Prefs {
        public String mode="keyboard"; public int duration=300;
        public String getString(String key,String fallback){return mode;}
        public int getInt(String key,int fallback){return duration;}
    }
}
