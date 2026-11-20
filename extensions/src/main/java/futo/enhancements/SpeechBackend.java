package futo.enhancements;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Provider adapters share one WAV encoder and one cancellable HTTP transport. */
public final class SpeechBackend {
    public enum Kind { OPENROUTER, WHISPER_ASR, OPENAI }
    public static final class Options {
        public Kind kind=Kind.OPENAI;
        public String endpoint="", key="", model="", language="", task="transcribe", output="json";
        public String customHeader="", customHeaderValue="", retention="";
        public String temperature="";
        public boolean multipart=false, allowHttp=false, encode=true, vad=false, timestamps=false;
        public int timeoutMs=30000;
        public Map<String,String> headers=new LinkedHashMap<>();
    }
    public static final class Failure extends IOException {
        public final int status;
        public Failure(String message) { this(message,0); }
        public Failure(String message,int status) { super(message); this.status=status; }
    }
    public static final class Cancellation {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;
        public synchronized void cancel() { cancelled=true; if(connection!=null) connection.disconnect(); }
        public void check() throws Failure { if(cancelled || Thread.currentThread().isInterrupted()) throw new Failure("Transcription cancelled"); }
        synchronized void attach(HttpURLConnection c) throws Failure { check(); connection=c; }
        synchronized void detach() { connection=null; }
    }
    private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"FutoSpeechDeadline"); t.setDaemon(true); return t;
    });
    public static byte[] wav(float[] samples) throws Failure {
        if(samples.length==0) throw new Failure("No audio recorded");
        if(samples.length > 16000*600) throw new Failure("Audio too large");
        ByteBuffer b=ByteBuffer.allocate(44+samples.length*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples.length*2);
        b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples.length*2);
        for(float f:samples) {
            if(!Float.isFinite(f)) throw new Failure("Audio encoding failed");
            b.putShort((short)Math.round(Math.max(-1f,Math.min(1f,f))*32767));
        }
        return b.array();
    }
    public static URL validate(Options o,String endpoint) throws Failure {
        try {
            URL u=new URL(endpoint);
            if(!u.getProtocol().equals("https") && !(o.allowHttp && u.getProtocol().equals("http"))) throw new Failure("HTTPS required; explicitly allow HTTP for private servers");
            if(u.getUserInfo()!=null || u.getRef()!=null || u.getHost().isEmpty()) throw new Failure("Invalid server URL");
            if(o.kind==Kind.OPENROUTER && (!u.getProtocol().equals("https") || !u.getHost().equals("openrouter.ai") || u.getPort()!=-1)) throw new Failure("OpenRouter requires https://openrouter.ai");
            if(o.timeoutMs<1000 || o.timeoutMs>300000) throw new Failure("Timeout must be between 1 and 300 seconds");
            return u;
        } catch(MalformedURLException e) { throw new Failure("Invalid server URL"); }
    }
    private static String language(Options o) throws Failure {
        String l=o.language.trim().toLowerCase(Locale.ROOT);
        if(!l.isEmpty() && !l.matches("[a-z]{2}")) throw new Failure("Language must be a two-letter ISO code");
        return l;
    }
    private static String asrUrl(Options o) throws Failure {
        if(!o.task.equals("transcribe") && !o.task.equals("translate")) throw new Failure("Unsupported ASR task");
        if(!o.output.equals("json") && !o.output.equals("text")) throw new Failure("Unsupported ASR output");
        String endpoint=o.endpoint;
        if(endpoint.contains("?")) throw new Failure("Configure the ASR endpoint without query parameters");
        String l=language(o);
        return endpoint+"?task="+o.task+"&output="+(o.output.equals("text")?"txt":o.output)+"&encode="+o.encode+"&word_timestamps="+o.timestamps
            +(l.isEmpty()?"":"&language="+l)+(o.vad?"&vad_filter=true":"");
    }
    private static void part(ByteArrayOutputStream b,String boundary,String name,String value) throws IOException {
        b.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }
    public static String transcribe(byte[] audio,Options o,Cancellation cancellation) throws IOException {
        cancellation.check();
        if(audio.length<44) throw new Failure("Audio encoding failed");
        if(audio.length>25*1024*1024 && o.kind==Kind.OPENROUTER) throw new Failure("Audio exceeds OpenRouter's 25 MB limit",413);
        if(o.kind==Kind.OPENROUTER && o.key.trim().isEmpty()) throw new Failure("OpenRouter API key is required");
        if(o.kind!=Kind.WHISPER_ASR && o.model.trim().isEmpty()) throw new Failure("Select a transcription model");
        String l=language(o), contentType;
        byte[] body;
        try {
            if(o.kind==Kind.OPENROUTER && !o.multipart) {
                JSONObject request=new JSONObject().put("model",o.model).put("input_audio",new JSONObject()
                    .put("data",Base64.getEncoder().encodeToString(audio)).put("format","wav"));
                if(!l.isEmpty()) request.put("language",l);
                if(!o.temperature.isEmpty()) request.put("temperature",Double.parseDouble(o.temperature));
                if(!o.retention.isEmpty()) {
                    if(!o.retention.equals("deny") && !o.retention.equals("allow")) throw new Failure("Invalid provider data retention policy");
                    request.put("provider",new JSONObject().put("data_collection",o.retention));
                }
                body=request.toString().getBytes(StandardCharsets.UTF_8); contentType="application/json";
            } else {
                String boundary="Futo"+UUID.randomUUID().toString().replace("-","");
                ByteArrayOutputStream b=new ByteArrayOutputStream();
                if(o.kind!=Kind.WHISPER_ASR) {
                    part(b,boundary,"model",o.model);
                    if(!l.isEmpty()) part(b,boundary,"language",l);
                    if(!o.temperature.isEmpty()) part(b,boundary,"temperature",o.temperature);
                    if(!o.retention.isEmpty()) throw new Failure("Provider retention controls require JSON mode");
                }
                String field=o.kind==Kind.WHISPER_ASR?"audio_file":"file";
                b.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field+"\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                b.write(audio); b.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                body=b.toByteArray(); contentType="multipart/form-data; boundary="+boundary;
            }
        } catch(JSONException | NumberFormatException e) { throw new Failure("Invalid transcription options"); }
        String endpoint=o.kind==Kind.WHISPER_ASR?asrUrl(o):o.endpoint;
        Reply r=request(o,endpoint,"POST",contentType,body,cancellation);
        String response=r.body;
        if(r.type.toLowerCase(Locale.ROOT).contains("html")) throw new Failure("Invalid transcript response");
        String text;
        if(o.kind==Kind.WHISPER_ASR && o.output.equals("text") && !r.type.toLowerCase(Locale.ROOT).contains("json") && !response.trim().startsWith("{") && !response.trim().startsWith("[")) text=response;
        else try {
            Object value=new JSONObject(response).opt("text");
            if(!(value instanceof String)) throw new Failure("Invalid transcript response");
            text=(String)value;
        } catch(JSONException e) { throw new Failure("Invalid JSON response"); }
        if(text.trim().isEmpty()) throw new Failure("Empty transcript");
        return text;
    }
    public static List<String> models(Options o,Cancellation c) throws IOException {
        Reply reply=request(o,"https://openrouter.ai/api/v1/models?output_modalities=transcription","GET",null,null,c);
        try {
            JSONArray data=new JSONObject(reply.body).getJSONArray("data");
            List<String> ids=new ArrayList<>();
            for(int i=0;i<data.length();i++) {
                JSONObject model=data.getJSONObject(i), arch=model.optJSONObject("architecture");
                JSONArray outputs=arch==null?null:arch.optJSONArray("output_modalities");
                if(outputs==null) continue;
                for(int j=0;j<outputs.length();j++) if(outputs.getString(j).equals("transcription")) { ids.add(model.getString("id")); break; }
            }
            return ids;
        } catch(JSONException e) { throw new Failure("Invalid model catalog response"); }
    }
    /** Connection diagnostics never upload audio or invoke a paid transcription. */
    public static String testConnection(Options o,Cancellation c) throws IOException {
        if(o.kind==Kind.OPENROUTER) { models(o,c); return "Model catalog responded. Transcription has not been verified."; }
        URL u=validate(o,o.endpoint);
        if(o.kind==Kind.WHISPER_ASR) {
            String endpoint=o.endpoint;
            if(!u.getPath().endsWith("/asr") || u.getQuery()!=null) throw new Failure("Configure the full ASR endpoint ending in /asr");
            String schema=endpoint.substring(0,endpoint.length()-4)+"/openapi.json";
            Reply reply=request(o,schema,"GET",null,null,c);
            try {
                if(!new JSONObject(reply.body).getJSONObject("paths").has("/asr")) throw new Failure("Server does not advertise the ASR endpoint");
            } catch(JSONException e) { throw new Failure("Invalid ASR server API description"); }
            return "ASR API responded. Transcription and upload authentication have not been verified.";
        }
        try { request(o,u.toString(),"HEAD",null,null,c); }
        catch(Failure e) {
            if(e.status!=405) throw e;
            return "Endpoint responded but does not support HEAD. Use Test Transcription to verify it.";
        }
        return "Endpoint responded. Transcription has not been verified.";
    }
    private static final class Reply { final String body,type; Reply(String b,String t){body=b;type=t==null?"":t;} }
    private static Reply request(Options o,String endpoint,String method,String type,byte[] body,Cancellation c) throws IOException {
        HttpURLConnection conn=(HttpURLConnection)validate(o,endpoint).openConnection();
        c.attach(conn);
        ScheduledFuture<?> deadline=deadlines.schedule(conn::disconnect,o.timeoutMs,TimeUnit.MILLISECONDS);
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(o.timeoutMs); conn.setReadTimeout(o.timeoutMs);
            conn.setRequestMethod(method); conn.setUseCaches(false); conn.setRequestProperty("Accept","application/json, text/plain");
            if(!o.key.isEmpty()) conn.setRequestProperty("Authorization","Bearer "+safeHeader(o.key));
            for(Map.Entry<String,String> h:o.headers.entrySet()) setHeader(conn,h.getKey(),h.getValue());
            if(!o.customHeader.isEmpty()) setHeader(conn,o.customHeader,o.customHeaderValue);
            if(body!=null) {
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type",type); conn.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=conn.getOutputStream()) {
                    for(int i=0;i<body.length;i+=8192) { c.check(); out.write(body,i,Math.min(8192,body.length-i)); }
                }
            }
            c.check();
            int status=conn.getResponseCode();
            if(status<200 || status>=300) throw status(status);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!method.equals("HEAD")) try(InputStream in=conn.getInputStream()) {
                byte[] chunk=new byte[8192]; int n;
                while((n=in.read(chunk))!=-1) { c.check(); if(out.size()+n>2*1024*1024) throw new Failure("Server response too large"); out.write(chunk,0,n); }
            }
            c.check();
            return new Reply(new String(out.toByteArray(),StandardCharsets.UTF_8),conn.getContentType());
        } catch(SocketTimeoutException e) { throw new Failure("Request timed out"); }
        catch(UnknownHostException e) { throw new Failure("Server name could not be resolved"); }
        catch(javax.net.ssl.SSLException e) { throw new Failure("TLS certificate validation failed"); }
        catch(Failure e) { throw e; }
        catch(IOException e) { c.check(); throw new Failure(deadline.isDone()?"Request timed out":"Could not connect to speech server"); }
        finally { deadline.cancel(false); c.detach(); conn.disconnect(); }
    }
    private static String safeHeader(String value) throws Failure {
        if(value.indexOf('\r')>=0 || value.indexOf('\n')>=0) throw new Failure("Invalid HTTP header"); return value;
    }
    private static void setHeader(HttpURLConnection c,String name,String value) throws Failure {
        if(!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || Arrays.asList("host","content-length","content-type","connection","transfer-encoding").contains(name.toLowerCase(Locale.ROOT))) throw new Failure("Unsupported custom header");
        c.setRequestProperty(name,safeHeader(value));
    }
    private static Failure status(int code) {
        String message;
        switch(code) {
            case 400: message="Invalid transcription request"; break;
            case 401: case 403: message="Speech server authentication failed"; break;
            case 404: message="Endpoint or model not found"; break;
            case 413: message="Audio too large"; break;
            case 429: message="Speech server rate limit reached"; break;
            default: message=code>=500?"Speech server or provider failed":"Unexpected HTTP response";
        }
        return new Failure(message+" (HTTP "+code+")",code);
    }
}
