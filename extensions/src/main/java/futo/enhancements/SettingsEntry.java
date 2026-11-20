package futo.enhancements;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
/** Uses FUTO's own themed settings screens and Compose components. */
public final class SettingsEntry {
    private static final String SETTINGS="org.futo.inputmethod.latin.uix.settings.";
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static java.lang.ref.WeakReference<Activity> activity=new java.lang.ref.WeakReference<>(null);
    private static final Map<String,Object> values=new HashMap<>();
    private static volatile SpeechBackend.Cancellation pending;
    private static final Map<String,java.lang.ref.WeakReference<EditText>> inputs=new HashMap<>();
    private static List<String> models=Collections.emptyList();
    private static final String[] IDS={"local","openrouter","asr","openai"};
    private static final String[] LABELS={"Local FUTO (default)","OpenRouter","Whisper ASR Webservice","Custom OpenAI-compatible"};
    public static void install(Activity value) { Config.init(value); activity=new java.lang.ref.WeakReference<>(value); }
    private static Object unit() throws Exception { return Reflect.field(Reflect.type("kotlin.Unit"),"INSTANCE"); }
    private static Object function(String type,java.util.function.Function<Object[],Object> implementation) throws Exception {
        Class<?> iface=Reflect.type("kotlin.jvm.functions."+type);
        return java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)-> {
            switch(method.getName()) {
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy==args[0];
                case "toString": return "FUTO enhancement setting callback";
                default: return implementation.apply(args);
            }
        });
    }
    private interface Render { void draw(Object composer) throws Exception; }
    private static Object component(Render render) throws Exception {
        return function("Function2",args-> {
            try { render.draw(args[0]); return unit(); }
            catch(Exception e) { throw new IllegalStateException("Could not render enhancement setting",e); }
        });
    }
    private static int resource(String name) {
        int id=Config.context().getResources().getIdentifier(name,"string",Config.context().getPackageName());
        if(id==0) throw new IllegalStateException("Missing enhancement resource: "+name);
        return id;
    }
    private static Object row(String resource,Render render) throws Exception {
        return Arrays.stream(Reflect.type(SETTINGS+"UserSetting").getConstructors()).filter(c->c.getParameterCount()==8).findFirst().get()
            .newInstance(resource(resource),null,null,null,null,false,true,component(render));
    }
    private static Object state(String key,Object initial) throws Exception {
        Object state=values.get(key);
        if(state==null) { state=Reflect.call(Reflect.type("androidx.compose.runtime.Stack"),"mutableStateOf$default",initial); values.put(key,state); }
        return state;
    }
    private static Object value(String key,Object initial) throws Exception { return Reflect.call(state(key,initial),"getValue"); }
    private static void changed(String key,Object v) throws Exception { Reflect.call(state(key,v),"setValue",v); }
    private static Object item(String key,Object initial) throws Exception {
        Object setter=function("Function1",args-> {
            try {
                Object v=args[0];
                if(v instanceof Boolean) Config.prefs().edit().putBoolean(key,(Boolean)v).apply();
                else if(v instanceof Integer) Config.prefs().edit().putInt(key,(Integer)v).apply();
                else Config.prefs().edit().putString(key,v.toString()).apply();
                changed(key,v); return unit();
            } catch(Exception e) { throw new IllegalStateException("Could not save enhancement setting",e); }
        });
        return Reflect.type(SETTINGS+"DataStoreItem").getConstructors()[0].newInstance(value(key,initial),setter);
    }
    private static void nativeCall(String method,Object...args) throws Exception { Reflect.call(Reflect.type(SETTINGS+"ComponentsKt"),method,args); }
    private static void tip(Object c,String text) throws Exception { nativeCall("Tip",text,c,0); }
    private static void toggle(Object c,String key,String title,String subtitle,boolean fallback) throws Exception {
        nativeCall("SettingToggleDataStoreItem",title,item(key,Config.prefs().getBoolean(key,fallback)),subtitle,null,false,null,null,c,0);
    }
    private static void action(Object c,String title,String subtitle,Runnable run) throws Exception {
        Object callback=function("Function0",args-> { run.run(); try { return unit(); } catch(Exception e) { throw new IllegalStateException("Could not complete setting action",e); } });
        nativeCall("NavigationItem",title,Reflect.field(Reflect.type(SETTINGS+"NavigationItemStyle"),"Misc"),callback,null,subtitle,false,c,0,40);
    }
    public static List<?> menuItems(String path,List<?> original) {
        if(Config.context()==null) return original;
        if(!(path.equals("keyboard") && (Config.dynamicInstalled() || Config.swipeBackspaceInstalled())) &&
           !(path.equals("voiceInput") && Config.remoteInstalled()) &&
           !(path.equals("longPress") && Config.spacebarInstalled())) return original;
        try {
            List<Object> result=new ArrayList<>(original);
            if(path.equals("keyboard") && Config.swipeBackspaceInstalled())
                result.add(0,row("futo_swipe_backspace",c->toggle(c,"swipe_backspace","Character backspace after swipe","Tap deletes a character; hold deletes the edited swipe word",false)));
            if(path.equals("longPress") && Config.spacebarInstalled())
                result.add(row("futo_spacebar_duration",SettingsEntry::spacebar));
            if(path.equals("keyboard") && Config.dynamicInstalled())
                result.add(0,row("futo_dynamic_bar",c->toggle(c,"dynamic","Dynamic suggestion bar","Actions when idle; alternatives while typing or swiping",false)));
            if(path.equals("voiceInput") && Config.remoteInstalled())
                result.add(0,row("futo_speech_backend",c-> {
                    Object nav=Reflect.call(c,"consume",Reflect.field(Reflect.type("org.futo.inputmethod.latin.uix.UixManagerKt"),"LocalNavController"));
                    action(c,"Speech recognition backend","Local FUTO, OpenRouter and custom servers",()-> {
                        try { Reflect.call(Reflect.type("androidx.navigation.NavHostController"),"navigate$default",nav,"futoSpeech",null,2); }
                        catch(Exception e) { throw new IllegalStateException("Could not open speech settings",e); }
                    });
                }));
            return result;
        } catch(Exception e) { throw new IllegalStateException("Could not extend FUTO settings",e); }
    }
    public static void extendMenus(ArrayList<Object> menus) {
        if(!Config.remoteInstalled()) return;
        try {
            List<Object> rows=Collections.singletonList(row("futo_speech_backend",SettingsEntry::speech));
            Object menu=Arrays.stream(Reflect.type(SETTINGS+"UserSettingsMenu").getConstructors()).filter(c->c.getParameterCount()==6).findFirst().get()
                .newInstance(resource("futo_speech_backend"),null,null,"futoSpeech",true,rows);
            menus.add(menu);
        } catch(Exception e) { throw new IllegalStateException("Could not register speech settings page",e); }
    }
    private static void spacebar(Object c) throws Exception {
        nativeCall("SettingRadio","Spacebar long press duration",
            Arrays.asList("stock","keyboard","custom"),Arrays.asList("Original FUTO","Same as keyboard","Custom"),
            item("spacebar_mode",Config.prefs().getString("spacebar_mode","keyboard")),null,null,null,c,0,112);
        String mode=value("spacebar_mode",Config.prefs().getString("spacebar_mode","keyboard")).toString();
        Reflect.call(c,"startReplaceGroup",157121);
        if(mode.equals("custom")) {
            Class<?> range=Reflect.type("kotlin.ranges.ClosedFloatRange");
            nativeCall("SettingSliderForDataStoreItem","Spacebar long press duration",
                item("spacebar_duration",Config.prefs().getInt("spacebar_duration",300)),300,
                range.getConstructor(float.class,float.class).newInstance(100f,700f),
                function("Function1",args->Math.round(((Number)args[0]).floatValue())),
                function("Function1",args->args[0].toString()+"ms"),
                range.getConstructor(float.class,float.class).newInstance(25f,1200f),1f,
                "How long to hold the spacebar before its configured action starts",23,c,0,0);
        }
        Reflect.call(c,"endReplaceGroup");
        tip(c,"Original FUTO restores its own spacebar timing. Same as keyboard follows Long press duration. Custom changes only the spacebar.");
    }
    private static void speech(Object c) throws Exception {
        nativeCall("SettingRadio","Provider",Arrays.asList(IDS),Arrays.asList(LABELS),
            item("backend",Config.prefs().getString("backend","local")),null,null,null,c,0,112);
        String backend=value("backend",Config.prefs().getString("backend","local")).toString();
        Reflect.call(c,"startReplaceGroup",backend.hashCode());
        if(!backend.equals("local")) {
            toggle(c,"remote_consent","Allow remote audio","Dictation leaves this device. Your provider may retain audio and charge for requests.",false);
            if(!Boolean.TRUE.equals(value("remote_consent",Config.prefs().getBoolean("remote_consent",false)))) {
                tip(c,"Enable remote audio to use this provider. Local FUTO stays active until then.");
            }
            form(c,backend);
        } else tip(c,"Audio is processed on this device by FUTO.");
        Reflect.call(c,"endReplaceGroup");
    }
    private static void form(Object c,String backend) throws Exception {
        String p=backend+"_";
        boolean router=backend.equals("openrouter"),asr=backend.equals("asr");
        field(c,p+"endpoint","Transcription endpoint",router?"https://openrouter.ai/api/v1/audio/transcriptions":asr?"http://192.168.1.10:9000/asr":"",false,false);
        field(c,p+"key",router?"API key (required)":"Bearer token (optional)","",true,false);
        field(c,p+"language","Language","",false,false);
        tip(c,"Leave language empty for Auto, or enter ru, en or another ISO-639-1 code.");
        field(c,p+"timeout","Timeout in seconds (1–300)","30",false,true);
        if(!asr) {
            field(c,p+"model","Full model ID","",false,false);
            field(c,p+"temperature","Temperature (optional)","",false,false);
        }
        if(router) {
            toggle(c,p+"multipart","Multipart upload","Otherwise use JSON / Base64",false);
            field(c,p+"retention","Provider data collection","",false,false);
            tip(c,"Leave empty, or enter deny or allow.");
            action(c,"Fetch transcription models","Retrieve the current OpenRouter catalog",()->fetchModels());
            Object modelList=value("modelCatalog",models);
            @SuppressWarnings("unchecked") List<String> ids=(List<String>)modelList;
            Reflect.call(c,"startReplaceGroup",132971);
            if(!ids.isEmpty())
                nativeCall("DropDownPickerSettingItem","Transcription model",ids,value(p+"model",Config.prefs().getString(p+"model","")),
                    function("Function1",args->{ Config.prefs().edit().putString(p+"model",args[0].toString()).apply(); try { changed(p+"model",args[0]); EditText edit=inputs.containsKey(p+"model")?inputs.get(p+"model").get():null; if(edit!=null)edit.setText(args[0].toString()); return unit(); } catch(Exception e){throw new IllegalStateException("Could not choose model",e);} }),
                    function("Function1",args->args[0].toString()),null,null,c,0,96);
            Reflect.call(c,"endReplaceGroup");
        }
        if(asr) {
            field(c,p+"task","Task: transcribe / translate","transcribe",false,false);
            field(c,p+"output","Response output: json / text","json",false,false);
            toggle(c,p+"encode","Encode audio on server","Decode uploaded audio on the server",true);
            toggle(c,p+"vad","VAD filter","Requires support from the server engine",false);
            toggle(c,p+"timestamps","Word timestamps","Requires support from the server engine",false);
            field(c,p+"auth_header","Custom authentication header name","",false,false);
            field(c,p+"auth_value","Custom authentication header value","",true,false);
        }
        if(!router) toggle(c,p+"http","Allow unencrypted HTTP","Audio and credentials can be intercepted",false);
        if(!router && !asr) field(c,p+"headers","Custom headers as JSON object","",true,false);
        toggle(c,"fallback","Fallback to local transcription","Use FUTO after a remote failure",false);
        action(c,"Test connection","Check configuration and server availability",()->test(false,""));
        tip(c,"Transcription tests send synthetic audio to your provider and may incur a charge.");
        action(c,"Test transcription: English","Send an English sample",()->test(true,"en"));
        action(c,"Test transcription: Russian","Send a Russian sample",()->test(true,"ru"));
        action(c,"Test transcription: silence","Send one second of silence",()->test(true,""));
        action(c,"Cancel current test",null,()-> { if(pending!=null)pending.cancel(); });
        tip(c,value("speechStatus","Settings are saved automatically.").toString());
    }
    /** Material 3 outlined input inside FUTO's Compose screen; secure fields stay native password inputs. */
    private static void field(Object c,String key,String label,String fallback,boolean secret,boolean number) throws Exception {
        Object factory=function("Function1",args-> {
            try {
                Context base=(Context)args[0];
                boolean dark=(base.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
                int theme=base.getResources().getIdentifier("Theme.Material3.DynamicColors."+(dark?"Dark":"Light"),"style",base.getPackageName());
                if(theme==0) throw new IllegalStateException("Missing Material 3 input theme");
                Context themed=new ContextThemeWrapper(base,theme);
                LinearLayout box=(LinearLayout)Reflect.type("com.google.android.material.textfield.TextInputLayout")
                    .getConstructor(Context.class,android.util.AttributeSet.class).newInstance(themed,null);
                Reflect.call(box,"setHint",label);
                Reflect.call(box,"setBoxBackgroundMode",2);
                int pad=(int)(16*base.getResources().getDisplayMetrics().density);
                Object shape=Reflect.call(Reflect.field(box,"shapeAppearanceModel"),"toBuilder");
                Object corner=Reflect.type("com.google.android.material.shape.AbsoluteCornerSize").getConstructor(float.class).newInstance((float)pad);
                for(String name:new String[]{"topLeftCornerSize","topRightCornerSize","bottomLeftCornerSize","bottomRightCornerSize"})
                    shape.getClass().getField(name).set(shape,corner);
                Object inputShape=Reflect.call(shape,"build");
                LinearLayout holder=new LinearLayout(base);
                holder.setPadding(pad,pad/2,pad,pad/2);
                holder.addView(box,new LinearLayout.LayoutParams(-1,-2));
                EditText edit=(EditText)Reflect.type("com.google.android.material.textfield.TextInputEditText")
                    .getConstructor(Context.class,android.util.AttributeSet.class).newInstance(themed,null);
                edit.setSingleLine(true);
                edit.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT | (secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
                edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
                edit.setSaveEnabled(false);
                edit.setBackground(null);
                edit.setPadding(pad,pad,pad,pad);
                edit.setTypeface(android.graphics.Typeface.DEFAULT);
                inputs.put(key,new java.lang.ref.WeakReference<>(edit));
                if(secret) {
                    Reflect.call(box,"setHelperText",Config.prefs().contains("secret_"+key)?"Stored securely; enter to replace":"Not configured");
                } else edit.setText(number?String.valueOf(Config.prefs().getInt(key,Integer.parseInt(fallback))):Config.prefs().getString(key,fallback));
                box.addView(edit,new LinearLayout.LayoutParams(-1,-2));
                Reflect.call(box,"setShapeAppearanceModel",inputShape);
                edit.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                    public void onTextChanged(CharSequence s,int start,int before,int count) {}
                    public void afterTextChanged(Editable text) {
                        try {
                            if(secret) { if(text.length()>0)Secrets.put(key,text.toString()); }
                            else if(number) {
                                int seconds=Integer.parseInt(text.toString());
                                if(seconds<1 || seconds>300)throw new IllegalArgumentException("Timeout must be 1–300 seconds");
                                Config.prefs().edit().putInt(key,seconds).apply();
                            } else { Config.prefs().edit().putString(key,text.toString().trim()).apply(); changed(key,text.toString().trim()); }
                            Reflect.call(box,"setError",(Object)null);
                        } catch(Exception e) {
                            try { Reflect.call(box,"setError",number?"Timeout must be 1–300 seconds":"Could not save credential"); }
                            catch(Exception failure) { throw new IllegalStateException("Could not show setting error",failure); }
                        }
                    }
                });
                return holder;
            } catch(Exception e) { throw new IllegalStateException("Could not create Material 3 input",e); }
        });
        Object modifier=Reflect.field(Reflect.type("androidx.compose.ui.Modifier$Companion"),"$$INSTANCE");
        modifier=Reflect.call(Reflect.type("androidx.compose.foundation.layout.SizeKt"),"fillMaxWidth",modifier,1f);
        Reflect.call(Reflect.type("androidx.compose.ui.viewinterop.AndroidView_androidKt"),"AndroidView",factory,modifier,null,c,0,4);
        if(secret) action(c,"Clear "+label,null,()-> { Config.prefs().edit().remove("secret_"+key).apply(); EditText edit=inputs.containsKey(key)?inputs.get(key).get():null; if(edit!=null)edit.setText(""); report("Stored credential cleared."); });
    }
    private static void report(String text) {
        Activity a=activity.get();
        if(a!=null)a.runOnUiThread(()-> { try { changed("speechStatus",text); } catch(Exception e) { throw new IllegalStateException("Could not display speech status",e); } });
    }
    private static void fetchModels() {
        report("Fetching transcription models…");
        io.execute(()-> {
            try {
                models=SpeechBackend.models(Remote.options(),new SpeechBackend.Cancellation());
                Activity a=activity.get();
                if(a!=null)a.runOnUiThread(()-> { try { changed("modelCatalog",models); report("Transcription models loaded."); } catch(Exception e){report("Could not display transcription models");} });
            } catch(Exception e) { report(e instanceof SpeechBackend.Failure?Remote.errorMessage(e):"Could not load transcription models"); }
        });
    }
    private static void test(boolean transcription,String language) {
        if(!Config.remote()) { report("Enable remote audio before testing this provider."); return; }
        if(pending!=null)pending.cancel();
        SpeechBackend.Cancellation token=new SpeechBackend.Cancellation(); pending=token;
        report("Waiting for server…");
        io.execute(()-> {
            try {
                SpeechBackend.Options options=Remote.options();
                String message;
                if(transcription) {
                    byte[] audio;
                    if(language.isEmpty())audio=SpeechBackend.wav(new float[16000]);
                    else try(java.io.InputStream input=Config.context().getAssets().open("futo-enhancements-test-"+language+".wav")) {
                        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                        byte[] bytes=new byte[8192]; int n;
                        while((n=input.read(bytes))!=-1)out.write(bytes,0,n);
                        audio=out.toByteArray();
                    }
                    try { message=SpeechBackend.transcribe(audio,options,token); }
                    finally { Arrays.fill(audio,(byte)0); }
                } else message=SpeechBackend.testConnection(options,token);
                if(pending==token) report(message);
            } catch(Exception e) { if(pending==token)report(e instanceof SpeechBackend.Failure?Remote.errorMessage(e):"Speech test failed"); }
            finally { if(pending==token)pending=null; }
        });
    }
}
