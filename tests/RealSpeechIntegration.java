package futo.enhancements;
import java.nio.file.*;
import org.json.*;
public final class RealSpeechIntegration {
    public static void main(String[] args) throws Exception {
        SpeechBackend.Options o=new SpeechBackend.Options();
        o.kind=SpeechBackend.Kind.WHISPER_ASR;o.endpoint=args[0];o.allowHttp=true;o.timeoutMs=300000;
        JSONArray results=new JSONArray();
        for(String language:new String[]{"en","ru",""}) {
            o.language=language;o.output="json";
            byte[] audio=Files.readAllBytes(Path.of("build/test-audio/"+(language.equals("ru")?"ru":"en")+".wav"));
            String transcript=SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            results.put(new JSONObject().put("language",language.isEmpty()?"auto":language).put("output","json").put("text",transcript));
            System.out.println("Transcribed "+(language.isEmpty()?"auto":language)+": "+transcript);
        }
        o.language="en";o.output="text";
        String transcript=SpeechBackend.transcribe(Files.readAllBytes(Path.of("build/test-audio/en.wav")),o,new SpeechBackend.Cancellation());
        results.put(new JSONObject().put("language","en").put("output","txt").put("text",transcript));
        System.out.println("Transcribed plain text: "+transcript);
        Files.writeString(Path.of("build/real-asr-results.json"),new JSONObject().put("service","onerahmet/openai-whisper-asr-webservice:v1.10.0").put("engine",args.length>1?args[1]:"unspecified").put("model","tiny").put("results",results).toString(2));
    }
}
