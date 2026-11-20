package futo.enhancements.test;
import android.os.SystemClock;
import android.view.*;
/** Shell-only gesture runner for a controlled hold followed by cursor movement. */
public final class Touch {
    public static void main(String[] args) throws Exception {
        int delay=Integer.parseInt(args[0]);
        Class<?> type=Class.forName("android.hardware.input.InputManagerGlobal");
        Object manager=type.getMethod("getInstance").invoke(null);
        java.lang.reflect.Method inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
        long down=SystemClock.uptimeMillis();
        send(manager,inject,down,MotionEvent.ACTION_DOWN,580,2180);
        SystemClock.sleep(delay);
        send(manager,inject,down,MotionEvent.ACTION_MOVE,450,2180);
        SystemClock.sleep(20);
        send(manager,inject,down,MotionEvent.ACTION_UP,450,2180);
        System.out.println("Completed controlled hold: "+delay+" ms");
    }
    private static void send(Object manager,java.lang.reflect.Method inject,long down,int action,float x,float y) throws Exception {
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { if(!Boolean.TRUE.equals(inject.invoke(manager,event,2)))throw new IllegalStateException("Touch injection rejected"); }
        finally { event.recycle(); }
    }
}
