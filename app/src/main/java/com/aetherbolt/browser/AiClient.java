package com.aetherbolt.browser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

final class AiClient {
    static final int MAX_RESPONSE=1024*1024,MAX_INPUT=32000;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    static URI endpoint(String value)throws Exception{
        URI u=new URI(value.trim());
        String host=u.getHost(),scheme=u.getScheme();
        if(host==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!"/v1/chat/completions".equals(u.getRawPath()))throw new IOException("Нужен полный URL /v1/chat/completions");
        if(host.equalsIgnoreCase("api.openai.com"))throw new IOException("Используйте собственный сервер-посредник, не ключ OpenAI в приложении");
        boolean loop="127.0.0.1".equals(host)||"localhost".equalsIgnoreCase(host)||"[::1]".equals(host);
        if(!"https".equalsIgnoreCase(scheme)&&!(loop&&"http".equalsIgnoreCase(scheme)))throw new IOException("HTTPS обязателен, кроме loopback");
        return u;
    }
    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    String request(URI endpoint,String model,String token,JSONArray messages)throws Exception{
        if(cancelled)throw new InterruptedIOException();
        JSONObject data=new JSONObject().put("model",model).put("messages",messages).put("stream",false).put("max_completion_tokens",1200);
        byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);if(body.length>256*1024)throw new IOException("Запрос слишком большой");
        HttpURLConnection c=(HttpURLConnection)endpoint.toURL().openConnection();connection=c;
        try{
            if(cancelled)throw new InterruptedIOException();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setUseCaches(false);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
            if(!token.isEmpty()){if(!(c instanceof HttpsURLConnection))throw new IOException("Токен допускается только по HTTPS");c.setRequestProperty("Authorization","Bearer "+token);}
            c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Сервер вернул HTTP "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(buffer.size()+n>MAX_RESPONSE)throw new IOException("Ответ слишком большой");buffer.write(b,0,n);}
                JSONObject response=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));JSONObject choice=response.getJSONArray("choices").getJSONObject(0);
                String answer=choice.getJSONObject("message").optString("content","");if(answer.trim().isEmpty())throw new IOException("Сервер не вернул текст");
                return answer.substring(0,Math.min(32000,answer.length()));
            }
        }finally{connection=null;c.disconnect();java.util.Arrays.fill(body,(byte)0);}
    }
}
