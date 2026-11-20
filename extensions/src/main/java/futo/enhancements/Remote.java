package futo.enhancements;
import android.content.*;
import android.os.*;
import android.widget.Toast;
import android.util.Log;
import java.nio.FloatBuffer;
import java.util.*;
public final class Remote {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<Object,SpeechBackend.Cancellation> requests=Collections.synchronizedMap(new WeakHashMap<>());
    public static SpeechBackend.Options options() throws Exception {
        SharedPreferences p=Config.prefs();
        SpeechBackend.Options o=new SpeechBackend.Options();
        String backend=p.getString("backend","local");
        o.kind=backend.equals("openrouter")?SpeechBackend.Kind.OPENROUTER:backend.equals("asr")?SpeechBackend.Kind.WHISPER_ASR:SpeechBackend.Kind.OPENAI;
        String prefix=backend+"_";
        o.endpoint=p.getString(prefix+"endpoint",backend.equals("openrouter")?"https://openrouter.ai/api/v1/audio/transcriptions":backend.equals("asr")?"http://192.168.1.10:9000/asr":"");
        o.key=Secrets.get(prefix+"key"); o.model=p.getString(prefix+"model","");
        o.language=p.getString(prefix+"language",""); o.timeoutMs=p.getInt(prefix+"timeout",30)*1000;
        o.allowHttp=p.getBoolean(prefix+"http",false); o.multipart=p.getBoolean(prefix+"multipart",false);
        o.temperature=p.getString(prefix+"temperature",""); o.retention=p.getString(prefix+"retention","");
        o.task=p.getString(prefix+"task","transcribe"); o.output=p.getString(prefix+"output","json");
        o.vad=p.getBoolean(prefix+"vad",false); o.timestamps=p.getBoolean(prefix+"timestamps",false); o.encode=p.getBoolean(prefix+"encode",true);
        o.customHeader=p.getString(prefix+"auth_header",""); o.customHeaderValue=Secrets.get(prefix+"auth_value");
        String headers=Secrets.get(prefix+"headers");
        if(!headers.isEmpty()) {
            org.json.JSONObject json=new org.json.JSONObject(headers);
            Iterator<String> names=json.keys();
            while(names.hasNext()) { String n=names.next(); o.headers.put(n,json.getString(n)); }
        }
        return o;
    }
    public static boolean skipLocalLoading() { return Config.remote() && !Config.prefs().getBoolean("fallback",false); }
    public static void cancel(Object recognizer) {
        SpeechBackend.Cancellation c=requests.remove(recognizer); if(c!=null)c.cancel();
    }
    /** Called on FUTO's original transcription dispatcher, before its local coroutine state machine. */
    public static boolean run(Object recognizer,Object continuation) {
        // A local fallback resumes this entry point repeatedly; never upload or bill again on resume.
        if(continuation!=null && continuation.getClass().getName().equals("org.futo.voiceinput.shared.AudioRecognizer$runModel$1")) return false;
        if(!Config.remote()) return false;
        SpeechBackend.Cancellation token=new SpeechBackend.Cancellation();
        requests.put(recognizer,token);
        try {
            FloatBuffer buffer=(FloatBuffer)Reflect.field(recognizer,"floatSamples");
            float[] samples=Arrays.copyOf(buffer.array(),buffer.position());
            byte[] wav=SpeechBackend.wav(samples);
            Arrays.fill(samples,0f);
            String result;
            try { result=SpeechBackend.transcribe(wav,options(),token); }
            finally { Arrays.fill(wav,(byte)0); }
            token.check();
            main.post(() -> {
                if(requests.get(recognizer)!=token) return;
                try {
                    token.check();
                    Object scope=Reflect.field(recognizer,"lifecycleScope");
                    Class<?> callback=Reflect.type("org.futo.voiceinput.shared.AudioRecognizer$runModel$3");
                    Object block=callback.getConstructors()[0].newInstance(recognizer,result,null);
                    Reflect.call(Reflect.type("kotlinx.coroutines.JobKt"),"launch$default",scope,null,block,3);
                } catch(Exception e) { showError(recognizer,"Could not deliver transcript"); }
                finally { requests.remove(recognizer,token); }
            });
            return true;
        } catch(Exception e) {
            if(requests.get(recognizer)!=token) return true;
            try { token.check(); } catch(SpeechBackend.Failure cancelled) { return true; }
            if(Config.prefs().getBoolean("fallback",false)) {
                requests.remove(recognizer,token);
                main.post(() -> Toast.makeText(Config.context(),"Remote transcription failed; using local FUTO",Toast.LENGTH_SHORT).show());
                return false;
            }
            String message=errorMessage(e);
            main.post(() -> {
                if(requests.get(recognizer)==token) { showError(recognizer,message); requests.remove(recognizer,token); }
            });
            return true;
        }
    }
    public static String errorMessage(Exception error) {
        String message=error instanceof SpeechBackend.Failure?error.getMessage():"Could not transcribe audio";
        if(error instanceof SpeechBackend.Failure && ((SpeechBackend.Failure)error).status==0
            && Config.context().checkSelfPermission(android.Manifest.permission.INTERNET)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            message+=" Android reports INTERNET denied.";
        return message;
    }
    private static void showError(Object recognizer,String message) {
        try {
            Object view=Reflect.field(Reflect.field(recognizer,"listener"),"events");
            Reflect.state(view,"loadingCircleText",message+" — Cancel to return");
            Toast.makeText(Config.context(),message,Toast.LENGTH_LONG).show();
        } catch(ReflectiveOperationException e) { Log.e("FutoEnhancements","Could not display speech error"); }
    }
}
