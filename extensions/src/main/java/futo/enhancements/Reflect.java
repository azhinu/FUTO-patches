package futo.enhancements;
import java.lang.reflect.*;
public final class Reflect {
    public static Object field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner instanceof Class ? null : owner); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException("Missing field: " + name);
    }
    public static Object call(Object owner, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        for (; type != null; type = type.getSuperclass()) for (Method m : type.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
            boolean matches = true;
            for (int i=0; i<args.length; i++) {
                Class<?> p=m.getParameterTypes()[i];
                if(args[i]==null) { if(p.isPrimitive())matches=false; }
                else if(p.isPrimitive()) {
                    Class<?> boxed=p==boolean.class?Boolean.class:p==int.class?Integer.class:p==long.class?Long.class:
                        p==float.class?Float.class:p==double.class?Double.class:p==short.class?Short.class:p==byte.class?Byte.class:Character.class;
                    if(!boxed.isInstance(args[i]))matches=false;
                } else if(!p.isInstance(args[i]))matches=false;
            }
            if (!matches) continue;
            m.setAccessible(true);
            return m.invoke(owner instanceof Class ? null : owner, args);
        }
        throw new NoSuchMethodException("Missing method: " + name);
    }
    public static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    public static Object setting(String name) throws ReflectiveOperationException {
        return field(type("org.futo.inputmethod.latin.SubtypesKt"), name);
    }
    public static Object readSetting(android.content.Context context, Object key) throws ReflectiveOperationException {
        return call(type("org.futo.inputmethod.latin.uix.SettingsKt"), "getSettingBlocking", context, key);
    }
    public static void writeSetting(android.content.Context context, Object key, Object value) throws ReflectiveOperationException {
        call(type("org.futo.inputmethod.latin.uix.SettingsKt"), "setSettingBlocking", context, field(key,"key"), value);
    }
    public static void state(Object target, String field, Object value) throws ReflectiveOperationException {
        call(field(target, field), "setValue", value);
    }
}
