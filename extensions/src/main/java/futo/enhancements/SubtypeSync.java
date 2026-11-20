package futo.enhancements;
import android.content.*;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.view.inputmethod.*;
import android.util.Log;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class SubtypeSync {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<Integer,String> layouts=new HashMap<>();
    private static Set<String> previous=Collections.emptySet();
    private static boolean fromSystem;
    private static String imeId;
    private static String layoutName(Context context,String id) {
        try {
            Object keyboard;
            if(id.startsWith("custom")) {
                Object companion=Reflect.field(Reflect.type("org.futo.inputmethod.latin.uix.settings.pages.CustomLayout"),"Companion");
                keyboard=Reflect.call(Reflect.type("org.futo.inputmethod.latin.uix.settings.pages.DevLayoutEditorKt"),"getCustomLayout",companion,context,id);
            } else {
                Map<?,?> layouts=(Map<?,?>)Reflect.field(Reflect.type("org.futo.inputmethod.v2keyboard.LayoutManager"),"layoutsById");
                Object lazy=layouts==null?null:layouts.get(id);
                keyboard=lazy==null?null:Reflect.call(lazy,"get",context);
            }
            return keyboard==null?id:(String)Reflect.field(keyboard,"name");
        } catch(Exception e) { return id; }
    }
    public static void register(InputMethodService ime) {
        Config.init(ime);
        if(!Config.subtypesInstalled() || !((UserManager)ime.getSystemService(Context.USER_SERVICE)).isUserUnlocked()) return;
        try {
            Set<String> enabled=new LinkedHashSet<>((Set<String>)Reflect.readSetting(ime,Reflect.setting("SubtypesSetting")));
            if(enabled.equals(previous) && imeId!=null) return;
            InputMethodManager imm=ime.getSystemService(InputMethodManager.class);
            InputMethodInfo info=null;
            for(InputMethodInfo candidate:imm.getInputMethodList()) if(candidate.getPackageName().equals(ime.getPackageName()) && candidate.getServiceName().equals("org.futo.inputmethod.latin.LatinIME")) info=candidate;
            if(info==null) return;
            List<InputMethodSubtype> registered=new ArrayList<>();
            Map<Integer,String> next=new HashMap<>();
            for(String value:enabled) {
                InputMethodSubtype original=(InputMethodSubtype)Reflect.call(Reflect.type("org.futo.inputmethod.latin.R"),"convertToSubtype",value);
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
                int id=((digest[0]&127)<<24)|((digest[1]&255)<<16)|((digest[2]&255)<<8)|(digest[3]&255);
                if(id==0) id=1;
                if(next.put(id,value)!=null) throw new IllegalStateException("Subtype identifier collision");
                Locale locale=Locale.forLanguageTag(original.getLanguageTag().isEmpty()?original.getLocale().replace('_','-'):original.getLanguageTag());
                String tag=locale.getLanguage().isEmpty()?"und":locale.toLanguageTag();
                String layout=original.getExtraValueOf("KeyboardLayoutSet");
                String title=locale.getDisplayName();
                if(layout!=null) title+=" — "+layoutName(ime,layout);
                InputMethodSubtype.InputMethodSubtypeBuilder builder=new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(id).setSubtypeMode("keyboard").setSubtypeLocale(original.getLocale()).setLanguageTag(tag)
                    .setIsAsciiCapable(locale.getLanguage().equals("en")).setIsAuxiliary(false)
                    .setOverridesImplicitlyEnabledSubtype(false).setSubtypeExtraValue(original.getExtraValue()+",FutoLayoutId="+id);
                if(Build.VERSION.SDK_INT>=34) builder.setSubtypeNameOverride(title);
                registered.add(builder.build());
            }
            imm.setAdditionalInputMethodSubtypes(info.getId(),registered.toArray(new InputMethodSubtype[0]));
            if(Build.VERSION.SDK_INT>=34) {
                int[] ids=new int[registered.size()];
                for(int i=0;i<ids.length;i++) ids[i]=registered.get(i).hashCode();
                imm.setExplicitlyEnabledInputMethodSubtypes(info.getId(),ids);
            }
            layouts.clear(); layouts.putAll(next); previous=enabled; imeId=info.getId();
            Log.i("FutoEnhancements","Registered "+registered.size()+" FUTO layout subtypes");
        } catch(Exception e) {
            Log.e("FutoEnhancements","Subtype registration failed");
            Toast.makeText(ime,"Could not update Android layout list",Toast.LENGTH_LONG).show();
        }
    }
    public static void changed(InputMethodService ime,InputMethodSubtype subtype) {
        if(!Config.subtypesInstalled() || subtype==null) return;
        register(ime);
        String value=layouts.get(subtype.hashCode());
        if(value==null) return;
        try {
            Object key=Reflect.setting("ActiveSubtype");
            if(value.equals(Reflect.readSetting(ime,key))) return;
            fromSystem=true;
            try {
                Reflect.writeSetting(ime,key,value);
                Reflect.call(ime,"changeSubtype",value);
            } finally { fromSystem=false; }
        } catch(Exception e) { Log.e("FutoEnhancements","Could not activate Android-selected FUTO layout"); }
    }
    public static void internal(InputMethodService ime,String value) {
        if(!Config.subtypesInstalled() || fromSystem) return;
        register(ime);
        InputMethodManager imm=ime.getSystemService(InputMethodManager.class);
        InputMethodSubtype current=imm.getCurrentInputMethodSubtype();
        for(Map.Entry<Integer,String> entry:layouts.entrySet()) if(entry.getValue().equals(value)) {
            if(current!=null && current.hashCode()==entry.getKey()) return;
            InputMethodSubtype target=null;
            for(InputMethodInfo info:imm.getInputMethodList()) if(info.getId().equals(imeId))
                for(int i=0;i<info.getSubtypeCount();i++) if(info.getSubtypeAt(i).hashCode()==entry.getKey()) target=info.getSubtypeAt(i);
            if(target!=null) ime.switchInputMethod(imeId,target);
            return;
        }
    }
    public static void start(InputMethodService ime) { register(ime); }
}
