package futo.enhancements.test;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.util.Log;
import org.json.*;
import java.util.*;
public final class MainActivity extends Activity {
    private EditText editor;
    private TextView status;
    private InputMethodManager imm;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        public void onReceive(Context c,Intent intent) {
            String command=intent.getStringExtra("command");
            if("sample".equals(command)) sample(intent.getStringExtra("language"));
            else if("clear".equals(command)) editor.setText("");
            else if("secrets".equals(command)) testSecrets(MainActivity.this);
            else if("picker".equals(command)) imm.showInputMethodPicker();
            else if("password".equals(command)) editor.setInputType(129);
            else if("noSuggestions".equals(command)) editor.setInputType(1|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            else if("normal".equals(command)) editor.setInputType(1|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            else if("show".equals(command)) { editor.requestFocus(); imm.showSoftInput(editor,0); }
            snapshot();
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        imm=getSystemService(InputMethodManager.class);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,100,24,40);
        TextView title=new TextView(this); title.setText("FUTO keyboard integration tests");title.setTextSize(21);root.addView(title);
        TextView description=new TextView(this);description.setText("Standard Android EditText. Only synthetic test text is logged.");root.addView(description);
        editor=new EditText(this); editor.setId(0x00f07002);editor.setHint("Type here");editor.setMinLines(3);
        editor.setInputType(1|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);root.addView(editor,new LinearLayout.LayoutParams(-1,280));
        for(String language:new String[]{"Russian","English","Mixed"}) {
            Button b=new Button(this);b.setText(language+" sample");b.setOnClickListener(v->sample(language));root.addView(b);
        }
        Button clear=new Button(this);clear.setText("Clear");clear.setOnClickListener(v->editor.setText(""));root.addView(clear);
        Button picker=new Button(this);picker.setText("System keyboard picker");picker.setOnClickListener(v->imm.showInputMethodPicker());root.addView(picker);
        Button snapshot=new Button(this);snapshot.setText("Snapshot");snapshot.setOnClickListener(v->snapshot());root.addView(snapshot);
        status=new TextView(this);root.addView(status);
        setContentView(root);
        IntentFilter filter=new IntentFilter("futo.enhancements.test.COMMAND");
        registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable text){handler.removeCallbacks(refresh);handler.postDelayed(refresh,1500);}
        });
        editor.requestFocus();editor.postDelayed(()->imm.showSoftInput(editor,0),500);
    }
    public static void testSecrets(Context context) {
        try {
            futo.enhancements.Config.init(context);
            String sample="Synthetic credential used only for the Keystore test";
            futo.enhancements.Secrets.put("test",sample);
            String first=futo.enhancements.Config.prefs().getString("secret_test","");
            if(!sample.equals(futo.enhancements.Secrets.get("test")) || first.contains(sample)) throw new AssertionError("Credential round trip failed");
            futo.enhancements.Secrets.put("test",sample);
            String second=futo.enhancements.Config.prefs().getString("secret_test","");
            if(first.equals(second)) throw new AssertionError("Credential encryption reused its IV");
            java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(store.getKey("futo.enhancements.credentials.v1",null).getEncoded()!=null) throw new AssertionError("Credential key is exportable");
            futo.enhancements.Config.prefs().edit().putString("secret_test","Invalid ciphertext").commit();
            try { futo.enhancements.Secrets.get("test");throw new AssertionError("Malformed ciphertext accepted"); }
            catch(java.security.GeneralSecurityException expected) {}
            futo.enhancements.Config.prefs().edit().remove("secret_test").commit();
            if(!futo.enhancements.Secrets.get("test").isEmpty()) throw new AssertionError("Credential removal failed");
            Log.i("FutoPatchTest","Passed Android Keystore encryption, fresh IV, non-exportability, malformed ciphertext, removal");
        } catch(Exception | AssertionError e) {Log.e("FutoPatchTest","Android Keystore test failed");}
    }
    private final Runnable refresh=()->snapshot();
    private void sample(String language) {
        editor.setInputType(1|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if("Russian".equals(language)) {editor.setTextLocale(Locale.forLanguageTag("ru-RU"));editor.setText("Это ошыпка в тексте.");}
        else if("English".equals(language)) {editor.setTextLocale(Locale.US);editor.setText("Thiss sentense has mistaks.");}
        else {editor.setTextLocales(new android.os.LocaleList(Locale.forLanguageTag("ru-RU"),Locale.US));editor.setText("Это ошыпка. Thiss sentense has mistaks.");}
        editor.setSelection(editor.length());editor.requestFocus();imm.restartInput(editor);imm.showSoftInput(editor,0);
        handler.postDelayed(refresh,5000);
    }
    private void snapshot() {
        try {
            Editable text=editor.getText();JSONArray suggestions=new JSONArray();
            for(SuggestionSpan span:text.getSpans(0,text.length(),SuggestionSpan.class)) {
                JSONObject s=new JSONObject().put("start",text.getSpanStart(span)).put("end",text.getSpanEnd(span)).put("flags",span.getFlags()).put("suggestions",new JSONArray(Arrays.asList(span.getSuggestions())));
                suggestions.put(s);
            }
            InputMethodSubtype subtype=imm.getCurrentInputMethodSubtype();
            int keyboardHeight=0;WindowInsets insets=editor.getRootWindowInsets();if(insets!=null)keyboardHeight=insets.getInsets(WindowInsets.Type.ime()).bottom;
            JSONObject result=new JSONObject().put("text",text.toString()).put("suppressed",imm.isInputMethodSuppressingSpellChecker()).put("suggestions",suggestions)
                .put("subtypeId",subtype==null?0:subtype.hashCode()).put("language",subtype==null?"":subtype.getLanguageTag())
                .put("keyboardHeight",keyboardHeight).put("selection",editor.getSelectionStart()).put("inputType",editor.getInputType());
            String output=result.toString();status.setText(output);Log.i("FutoPatchTest","Snapshot "+output);
        } catch(Exception e) {Log.e("FutoPatchTest","Could not collect test snapshot");}
    }
    @Override public void onDestroy(){unregisterReceiver(receiver);handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
