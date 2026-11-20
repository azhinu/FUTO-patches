package futo.enhancements;
public final class SwipeBackspaceTest {
    public static final class Composer {
        public String mTypedWordCache="Swipe"; public boolean mIsBatchMode=true; public boolean composing=true,middle;
        public boolean setComposingWord(int[] points,int[] coords){mIsBatchMode=false; return true;}
        public boolean isComposingWord(){return composing;}
        public boolean isCursorFrontOrMiddleOfComposingWord(){return middle;}
    }
    public static final class Connection { public boolean selected; public boolean hasSelection(){return selected;} }
    public static final class Helper { public int[] getCodepointCoordinates(int[] points){return new int[points.length*2];} }
    public static final class Logic { public final Helper mImeHelper=new Helper(); public final Composer mWordComposer=new Composer(); public final Connection mConnection=new Connection(); }
    public static final class Settings { public int mBackspaceModeHold=0; }
    public static final class Event { public int mKeyCode,mCodePoint; Event(int key,int code){mKeyCode=key;mCodePoint=code;} }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        Logic logic=new Logic(); Settings settings=new Settings();
        Config.swipe=true;
        SwipeBackspace.beforeInput(logic,settings,new Event(-5,0));
        check(!logic.mWordComposer.mIsBatchMode,"Swipe backspace still takes the whole-word rejection branch");
        check(SwipeBackspace.holdMode(logic,settings)==1,"Hold must delete the edited swipe word");
        SwipeBackspace.beforeInput(logic,settings,new Event(0,'a'));
        check(SwipeBackspace.holdMode(logic,settings)==1,"Appending letters must retain word deletion on hold");
        SwipeBackspace.beforeInput(logic,settings,new Event(32,32));
        check(SwipeBackspace.holdMode(logic,settings)==0,"Space must restore the ordinary hold preference");
        Config.swipe=false; logic.mWordComposer.mIsBatchMode=true;
        SwipeBackspace.beforeInput(logic,settings,new Event(-5,0));
        check(logic.mWordComposer.mIsBatchMode,"Disabled toggle must preserve stock swipe deletion");
        Config.swipe=true; logic.mConnection.selected=true;
        SwipeBackspace.beforeInput(logic,settings,new Event(-5,0));
        check(logic.mWordComposer.mIsBatchMode,"Selection deletion must stay original");
        logic.mConnection.selected=false; logic.mWordComposer.middle=true;
        SwipeBackspace.beforeInput(logic,settings,new Event(-5,0));
        check(logic.mWordComposer.mIsBatchMode,"Cursor recorrection must stay original");
        logic.mWordComposer.middle=false; logic.mWordComposer.mIsBatchMode=false;
        SwipeBackspace.beforeInput(logic,settings,new Event(-5,0));
        check(SwipeBackspace.holdMode(logic,settings)==0,"Ordinary typed words must keep their hold preference");
        System.out.println("Passed 8 swipe backspace cases");
    }
}
