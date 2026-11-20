package futo.enhancements;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import android.util.Log;
import java.util.WeakHashMap;
public final class Dynamic {
    private static final WeakHashMap<Object, BarState> states = new WeakHashMap<>();
    private static final class BarState { boolean composing; Object words; Boolean manual; boolean actionUsed; }
    public static Object oldStyleItem(Object original) {
        if(!Config.dynamic()) return original;
        try {
            return original.getClass().getConstructors()[0].newInstance(Boolean.TRUE,Reflect.field(original,"setValue"));
        } catch(ReflectiveOperationException e) {
            Log.e("FutoEnhancements","Could not create single-row action setting");
            return original;
        }
    }
    public static boolean overrideSetting(Object key) {
        if (!Config.dynamic()) return false;
        try { return key == Reflect.field(Reflect.type("org.futo.inputmethod.latin.uix.ActionBarKt"),"OldStyleActionsBar"); }
        catch (ReflectiveOperationException e) { return false; }
    }
    private static BarState state(Object ui) {
        BarState s=states.get(ui);
        if (s==null) { s=new BarState(); states.put(ui,s); }
        return s;
    }
    private static boolean composing(Object ime) throws ReflectiveOperationException {
        Object manager=Reflect.field(ime,"imeManager");
        Object engine=Reflect.call(Reflect.field(manager,"activeIme$delegate"),"getValue");
        if (engine==null) return false;
        try {
            Object composer=Reflect.field(Reflect.field(engine,"inputLogic"),"mWordComposer");
            return Boolean.TRUE.equals(Reflect.call(composer,"isComposingWord"))
                || Boolean.TRUE.equals(Reflect.field(composer,"mIsBatchMode"));
        } catch (NoSuchFieldException ignored) { return false; }
    }
    public static void update(Object ime) {
        if (!Config.dynamic()) return;
        try {
            Object ui=Reflect.field(ime,"uixManager");
            BarState s=state(ui);
            boolean word=composing(ime);
            Object words=Reflect.call(Reflect.field(ui,"suggestedWords"),"getValue");
            int style=words==null?0:((Number)Reflect.field(words,"mInputStyle")).intValue();
            boolean replacements=words!=null && (style==1 || style==2 || style==3 || style==5)
                && !((java.util.List<?>)Reflect.field(words,"mSuggestedWordInfoList")).isEmpty();
            word=word || replacements;
            if (word!=s.composing || (words!=s.words && (style==2 || style==3))) {
                s.manual=null; s.actionUsed=false;
            }
            s.composing=word; s.words=words;
            EditorInfo info=((InputMethodService)ime).getCurrentInputEditorInfo();
            if (info!=null && ((info.inputType & 0xff0)==0x80 || (info.inputType & 0xff0)==0x90 || (info.inputType & 0xff0)==0xe0 || (info.inputType & 0x80000)!=0)) { word=false; s.manual=null; }
            boolean actions=s.manual!=null ? s.manual : !word && !s.actionUsed;
            Reflect.state(ui,"isActionsExpanded",actions);
        } catch (ReflectiveOperationException e) { Log.e("FutoEnhancements","Dynamic bar integration failed"); }
    }
    public static boolean toggle(Object ui) {
        if (!Config.dynamic()) return false;
        try {
            boolean shown=Boolean.TRUE.equals(Reflect.call(Reflect.field(ui,"isActionsExpanded"),"getValue"));
            BarState s=state(ui); s.manual=!shown; s.actionUsed=false;
            Reflect.state(ui,"isActionsExpanded",!shown);
        } catch (ReflectiveOperationException e) { Log.e("FutoEnhancements","Dynamic bar toggle failed"); }
        return true;
    }
    public static void action(Object ui) {
        if (!Config.dynamic()) return;
        try { BarState s=state(ui); s.manual=null; s.actionUsed=true; Reflect.state(ui,"isActionsExpanded",false); }
        catch (ReflectiveOperationException e) { Log.e("FutoEnhancements","Dynamic bar action failed"); }
    }
    public static void start(Context ime) {
        Config.init(ime);
        try { states.remove(Reflect.field(ime,"uixManager")); } catch (ReflectiveOperationException ignored) {}
        update(ime);
    }
}
