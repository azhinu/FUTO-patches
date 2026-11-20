package futo.enhancements;
import java.util.*;
public final class DynamicIntegrationTest {
    public static final class State { Object value; State(Object v){value=v;} public Object getValue(){return value;} public void setValue(Object v){value=v;} }
    public static final class Composer { boolean composing; public boolean mIsBatchMode; public boolean isComposingWord(){return composing;} public boolean isBatchMode(){return mIsBatchMode;} }
    public static final class InputLogic { public final Composer mWordComposer=new Composer(); }
    public static final class Engine { public final InputLogic inputLogic=new InputLogic(); }
    public static final class Manager { public final State activeIme$delegate=new State(new Engine()); }
    public static final class Words { public final int mInputStyle; public final List<String> mSuggestedWordInfoList; Words(int style,String...words){mInputStyle=style;mSuggestedWordInfoList=Arrays.asList(words);} }
    public static final class UI { public final State isActionsExpanded=new State(true); public final State suggestedWords=new State(new Words(0)); }
    public static final class IME extends android.inputmethodservice.InputMethodService { public final UI uixManager=new UI(); public final Manager imeManager=new Manager(); }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static final class Overloaded {
        public String hint(int resource){return "Resource";}
        public String hint(CharSequence text){return text.toString();}
    }
    public static void main(String[] args) throws Exception {
        check("Label".equals(Reflect.call(new Overloaded(),"hint","Label")),"Material input label must resolve the text overload");
        check("Resource".equals(Reflect.call(new Overloaded(),"hint",42)),"Integer overload must remain available");
        IME ime=new IME();
        Dynamic.start(ime);
        check(Boolean.TRUE.equals(ime.uixManager.isActionsExpanded.getValue()),"Idle must show actions");
        // Swipe tail candidates are delivered after the engine commits the word and clears composing.
        ime.uixManager.suggestedWords.setValue(new Words(3,"Well","Will","Wall"));
        Dynamic.update(ime);
        check(Boolean.FALSE.equals(ime.uixManager.isActionsExpanded.getValue()),"Swipe alternatives are hidden by the action bar");
        Dynamic.toggle(ime.uixManager);
        check(Boolean.TRUE.equals(ime.uixManager.isActionsExpanded.getValue()),"Manual actions must open");
        Dynamic.update(ime);
        check(Boolean.TRUE.equals(ime.uixManager.isActionsExpanded.getValue()),"Same candidates must preserve manual choice");
        ime.uixManager.suggestedWords.setValue(new Words(3,"Next","Text"));
        Dynamic.update(ime);
        check(Boolean.FALSE.equals(ime.uixManager.isActionsExpanded.getValue()),"Next swipe must restore candidates");
        ime.uixManager.suggestedWords.setValue(new Words(6,"Prediction"));
        Dynamic.update(ime);
        check(Boolean.TRUE.equals(ime.uixManager.isActionsExpanded.getValue()),"Idle predictions must not hide actions");
        Engine engine=(Engine)ime.imeManager.activeIme$delegate.getValue();
        engine.inputLogic.mWordComposer.mIsBatchMode=true;
        Dynamic.update(ime);
        check(Boolean.FALSE.equals(ime.uixManager.isActionsExpanded.getValue()),"Gesture in progress must show candidates");
        engine.inputLogic.mWordComposer.mIsBatchMode=false;
        engine.inputLogic.mWordComposer.composing=true;
        Dynamic.update(ime);
        check(Boolean.FALSE.equals(ime.uixManager.isActionsExpanded.getValue()),"Typing must show candidates");
        ime.info.inputType=0x81;
        Dynamic.update(ime);
        check(Boolean.TRUE.equals(ime.uixManager.isActionsExpanded.getValue()),"Password must hide candidates");
        System.out.println("Passed 8 dynamic bar transitions");
    }
}
