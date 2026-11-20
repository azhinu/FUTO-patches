package futo.enhancements;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
public final class SpeechBackendTest {
    static int passed;
    static volatile int status=200;
    static volatile String response="{\"text\":\"Привет, World!\"}",type="application/json";
    static volatile String path,body,authorization,contentType,verb;
    static volatile byte[] bytes;
    static volatile long delay;
    static AtomicInteger calls=new AtomicInteger();
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); passed++; }
    static void failure(Throwing action,String contains) throws Exception {
        try { action.run(); throw new AssertionError("Expected failure: "+contains); }
        catch(SpeechBackend.Failure e) { check(e.getMessage().contains(contains),"Unexpected failure: "+e.getMessage()); }
    }
    interface Throwing { void run() throws Exception; }
    static SpeechBackend.Options options(String endpoint) {
        SpeechBackend.Options o=new SpeechBackend.Options(); o.endpoint=endpoint;o.allowHttp=true;o.model="custom/model"; return o;
    }
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService executor=Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/",exchange -> {
            calls.incrementAndGet();verb=exchange.getRequestMethod();
            path=exchange.getRequestURI().toString(); bytes=exchange.getRequestBody().readAllBytes();
            body=new String(bytes,StandardCharsets.UTF_8);
            authorization=exchange.getRequestHeaders().getFirst("Authorization");
            contentType=exchange.getRequestHeaders().getFirst("Content-Type");
            try {
                if(delay>0) Thread.sleep(delay);
                byte[] out=response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",type);
                exchange.sendResponseHeaders(status,exchange.getRequestMethod().equals("HEAD")?-1:out.length);
                if(!exchange.getRequestMethod().equals("HEAD")) exchange.getResponseBody().write(out);
            } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch(IOException ignored) {}
            finally { exchange.close(); }
        });
        server.start();
        int port=server.getAddress().getPort();
        // In this test process only, official HTTPS URLs terminate at our controlled mock server.
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
            protected URLConnection openConnection(URL u) throws IOException {
                if(u.getHost().equals("dns.invalid") || u.getHost().equals("tls.invalid")) return new HttpURLConnection(u) {
                    public void connect() {}
                    public void disconnect() {}
                    public boolean usingProxy() { return false; }
                    public OutputStream getOutputStream() throws IOException {
                        if(u.getHost().equals("dns.invalid")) throw new UnknownHostException("Synthetic DNS failure");
                        throw new javax.net.ssl.SSLHandshakeException("Synthetic certificate failure");
                    }
                };
                return new URL("http","127.0.0.1",port,u.getFile()).openConnection();
            }
        }:null);
        String endpoint="http://127.0.0.1:"+port+"/custom/transcribe";
        byte[] audio=SpeechBackend.wav(new float[]{-1,0,1,0.5f});
        try {
            ByteBuffer wav=ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
            check(new String(audio,0,4,StandardCharsets.US_ASCII).equals("RIFF"),"WAV RIFF");
            check(wav.getInt(24)==16000 && wav.getShort(22)==1 && wav.getShort(34)==16,"WAV PCM format");
            check(audio.length==52 && wav.getShort(44)==-32767 && wav.getShort(48)==32767,"WAV samples");
            failure(()->SpeechBackend.wav(new float[]{}),"No audio");
            failure(()->SpeechBackend.wav(new float[]{Float.NaN}),"encoding");
            SpeechBackend.Options o=options(endpoint);
            String result=SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            check(result.equals("Привет, World!"),"Preserve punctuation and capitalization");
            check(path.equals("/custom/transcribe") && body.contains("name=\"model\"\r\n\r\ncustom/model"),"Custom endpoint and model");
            check(body.contains("name=\"file\"; filename=\"audio.wav\"") && contentType.startsWith("multipart/form-data;"),"OpenAI multipart");
            check(authorization==null,"Optional authentication");
            check(!body.contains("name=\"language\""),"OpenAI auto language omitted");
            o.key="test-key";o.language="ru";o.headers.put("X-Test","custom");
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            check(authorization.equals("Bearer test-key") && body.contains("\r\nru\r\n"),"OpenAI authentication and Russian");
            o=options("https://openrouter.ai/api/v1/audio/transcriptions");o.kind=SpeechBackend.Kind.OPENROUTER;o.key="test-key";o.allowHttp=false;
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            JSONObject json=new JSONObject(body);
            check(path.equals("/api/v1/audio/transcriptions") && contentType.equals("application/json"),"OpenRouter JSON endpoint");
            check(json.getString("model").equals("custom/model") && !json.has("language"),"OpenRouter manual model and auto language");
            check(Arrays.equals(Base64.getDecoder().decode(json.getJSONObject("input_audio").getString("data")),audio),"OpenRouter Base64 audio");
            check(json.getJSONObject("input_audio").getString("format").equals("wav"),"OpenRouter real encoding");
            o.language="ru";o.retention="deny";o.temperature="0.2";
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            json=new JSONObject(body);
            check(json.getString("language").equals("ru") && json.getJSONObject("provider").getString("data_collection").equals("deny"),"Russian and retention");
            check(json.getDouble("temperature")==0.2,"Optional temperature");
            o.multipart=true;o.retention="";
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            check(body.contains("name=\"file\"") && body.contains("name=\"language\"\r\n\r\nru"),"OpenRouter multipart");
            SpeechBackend.Options router=o;
            router.key=""; failure(()->SpeechBackend.transcribe(audio,router,new SpeechBackend.Cancellation()),"API key");router.key="test-key";
            failure(()->SpeechBackend.transcribe(new byte[25*1024*1024+1],router,new SpeechBackend.Cancellation()),"25 MB");
            response="{\"data\":[{\"id\":\"stt/model\",\"architecture\":{\"output_modalities\":[\"transcription\"]}},{\"id\":\"chat/model\",\"architecture\":{\"output_modalities\":[\"text\"]}},{\"id\":\"unknown/model\"}]}";
            check(SpeechBackend.models(router,new SpeechBackend.Cancellation()).equals(List.of("stt/model")),"Filter transcription-capable models");
            check(path.equals("/api/v1/models?output_modalities=transcription"),"Official model discovery filter");
            o=options(endpoint.replace("/custom/transcribe","/asr"));o.kind=SpeechBackend.Kind.WHISPER_ASR;o.model="";
            response="{\"text\":\"Привет\",\"segments\":[],\"language\":\"ru\"}";
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            check(path.equals("/asr?task=transcribe&output=json&encode=true&word_timestamps=false"),"ASR auto language parameters");
            check(body.contains("name=\"audio_file\"") && !body.contains("name=\"model\""),"ASR correct upload field and no model");
            check(!path.contains("vad_filter"),"ASR VAD omitted by default");
            o.language="ru";o.vad=true;o.task="translate";o.encode=false;
            SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation());
            check(path.contains("language=ru") && path.contains("vad_filter=true") && path.contains("task=translate") && path.contains("encode=false"),"ASR optional parameters");
            o.customHeader="X-Api-Key";o.customHeaderValue="private-token";o.output="text";
            response="Plain transcript.";type="text/plain";
            check(SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation()).equals(response),"ASR plain text response");
            check(path.contains("output=txt"),"Official ASR plain text output value");
            response="{\"text\":\"JSON from a text endpoint.\",\"segments\":[]}";
            check(SpeechBackend.transcribe(audio,o,new SpeechBackend.Cancellation()).equals("JSON from a text endpoint."),"ASR JSON text extracted even when requested output and MIME are plain text");
            response="{\"paths\":{\"/asr\":{\"post\":{}}}}";type="application/json";
            check(SpeechBackend.testConnection(o,new SpeechBackend.Cancellation()).contains("ASR API responded"),"ASR connection schema");
            check(path.equals("/openapi.json") && verb.equals("GET") && bytes.length==0,"Connection test does not upload audio");
            o=options(endpoint); status=405;
            check(SpeechBackend.testConnection(o,new SpeechBackend.Cancellation()).contains("does not support HEAD"),"OpenAI HEAD method rejection is reachability only");
            status=200;
            o=options(endpoint);
            for(int error:new int[]{400,401,403,404,413,429,500,503,302}) {
                status=error; response="SECRET_SERVER_ERROR"; final SpeechBackend.Options current=o;
                int before=calls.get(); failure(()->SpeechBackend.transcribe(audio,current,new SpeechBackend.Cancellation()),"HTTP "+error);
                check(calls.get()==before+1,"No automatic retries or redirects");
            }
            status=200;type="application/json";
            response="not-json"; final SpeechBackend.Options normal=o;
            failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Invalid JSON");
            response="{\"text\":\"  \"}";failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Empty transcript");
            response="{\"text\":123}";failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Invalid transcript");
            response="{\"text\":\"OK\",\"usage\":null}";
            check(SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()).equals("OK"),"Usage is optional");
            normal.allowHttp=false;int before=calls.get();
            failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"HTTPS required");
            check(calls.get()==before,"HTTP rejected before network access");normal.allowHttp=true;
            normal.headers.put("Host","evil");failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Unsupported custom header");normal.headers.clear();
            normal.key="key\r\nX-Evil: injected";failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Invalid HTTP header");normal.key="";
            SpeechBackend.Cancellation cancelled=new SpeechBackend.Cancellation();cancelled.cancel();
            failure(()->SpeechBackend.transcribe(audio,normal,cancelled),"cancelled");
            SpeechBackend.Options dns=options("https://dns.invalid/transcribe");
            failure(()->SpeechBackend.transcribe(audio,dns,new SpeechBackend.Cancellation()),"could not be resolved");
            SpeechBackend.Options tls=options("https://tls.invalid/transcribe");
            failure(()->SpeechBackend.transcribe(audio,tls,new SpeechBackend.Cancellation()),"TLS certificate validation failed");
            response="<html>Provider login page</html>";type="text/html";
            failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"Invalid transcript response");
            response="x".repeat(2*1024*1024+1);type="text/plain";
            failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"response too large");
            response="{\"text\":\"OK\"}";type="application/json";
            normal.timeoutMs=1000;delay=1800;
            failure(()->SpeechBackend.transcribe(audio,normal,new SpeechBackend.Cancellation()),"timed out");
            delay=3000;normal.timeoutMs=10000;
            SpeechBackend.Cancellation pending=new SpeechBackend.Cancellation();
            Future<?> request=executor.submit(()-> { try { SpeechBackend.transcribe(audio,normal,pending);throw new AssertionError("Cancellation expected"); } catch(SpeechBackend.Failure e) { check(e.getMessage().contains("cancelled"),"In-flight cancellation"); } catch(IOException e) { throw new RuntimeException(e); } });
            Thread.sleep(150);pending.cancel();request.get(3,TimeUnit.SECONDS);
            delay=0;
            SpeechBackend.Options offline=options("http://127.0.0.1:1/asr");offline.kind=SpeechBackend.Kind.WHISPER_ASR;
            failure(()->SpeechBackend.transcribe(audio,offline,new SpeechBackend.Cancellation()),"Could not connect");
            System.out.println("Passed "+passed+" speech protocol assertions");
        } finally { server.stop(0);executor.shutdownNow(); }
    }
}
