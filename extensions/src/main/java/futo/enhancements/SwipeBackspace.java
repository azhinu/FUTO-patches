package futo.enhancements;
import android.util.Log;
import java.util.WeakHashMap;
/** Resume a gesture word as ordinary composing text before the original delete path. */
public final class SwipeBackspace {
    private static final WeakHashMap<Object,Boolean> editing=new WeakHashMap<>();
    public static void beforeInput(Object logic,Object settings,Object event) {
        try {
            Object composer=Reflect.field(logic,"mWordComposer");
            if(!Config.swipeBackspace()) { editing.remove(composer); return; }
            if(Boolean.TRUE.equals(Reflect.call(Reflect.field(logic,"mConnection"),"hasSelection"))
                || Boolean.TRUE.equals(Reflect.call(composer,"isCursorFrontOrMiddleOfComposingWord"))) {
                editing.remove(composer); return;
            }
            int key=((Number)Reflect.field(event,"mKeyCode")).intValue();
            boolean composing=Boolean.TRUE.equals(Reflect.call(composer,"isComposingWord"));
            if(!composing)editing.remove(composer);
            if(key==-5 && composing && Boolean.TRUE.equals(Reflect.field(composer,"mIsBatchMode"))) {
                String word=Reflect.field(composer,"mTypedWordCache").toString();
                int[] points=word.codePoints().toArray();
                Object coordinates=Reflect.call(Reflect.field(logic,"mImeHelper"),"getCodepointCoordinates",points);
                if(Boolean.TRUE.equals(Reflect.call(composer,"setComposingWord",points,coordinates)))
                    editing.put(composer,Boolean.TRUE);
            } else if(key!=-5) {
                int point=((Number)Reflect.field(event,"mCodePoint")).intValue();
                if(!Character.isLetterOrDigit(point) && point!=39 && point!=0x2019)editing.remove(composer);
            }
        } catch(ReflectiveOperationException e) {
            Log.e("FutoEnhancements","Could not resume swipe word for character deletion");
        }
    }
    public static int holdMode(Object logic,Object settings) {
        try {
            Object composer=Reflect.field(logic,"mWordComposer");
            if(Config.swipeBackspace() && editing.containsKey(composer))return 1;
            return ((Number)Reflect.field(settings,"mBackspaceModeHold")).intValue();
        } catch(ReflectiveOperationException e) {
            Log.e("FutoEnhancements","Could not read backspace hold mode");
            return 0;
        }
    }
}
