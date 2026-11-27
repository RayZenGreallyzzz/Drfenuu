package com.aetherbolt.browser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** Bounded HTTP client: explicit SOCKS5 domain request over loopback, never direct DNS/HTTP. */
final class TorHttp {
    static final int LIMIT=128*1024;
    static final class Reply {
        final int status; final String type,body;
        Reply(int s,String t,String b){status=s;type=t;body=b;}
    }
    static URI onion(String raw){
        try{
            URI u=new URI(raw.trim());
            String host=u.getHost(), scheme=u.getScheme();
            if(host==null||!host.toLowerCase(Locale.ROOT).matches("[a-z2-7]{56}\\.onion"))return null;
            if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme))return null;
            if(u.getRawUserInfo()!=null||u.getPort()==0||u.getPort()>65535)return null;
            return u;
        }catch(Exception e){return null;}
    }
    interface Connection { void opened(Socket socket)throws IOException; }
    static Reply fetch(int socks,URI u,Connection connection)throws IOException {
        if(socks<1||socks>65535)throw new IOException("SOCKS Tor недоступен");
        String host=u.getHost();boolean tls="https".equalsIgnoreCase(u.getScheme());
        int port=u.getPort()>0?u.getPort():tls?443:80;
        Socket raw=new Socket(Proxy.NO_PROXY);
        try{
            connection.opened(raw);
            raw.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),socks),10000);
            raw.setSoTimeout(25000);
            DataInputStream in=new DataInputStream(raw.getInputStream());
            OutputStream out=raw.getOutputStream();
            out.write(new byte[]{5,1,0});out.flush();
            if(in.readUnsignedByte()!=5||in.readUnsignedByte()!=0)throw new IOException("SOCKS5: отказ согласования");
            byte[] name=host.getBytes(StandardCharsets.US_ASCII);
            if(name.length<1||name.length>255)throw new IOException("Недопустимый адрес");
            out.write(new byte[]{5,1,0,3,(byte)name.length});out.write(name);out.write(port>>>8);out.write(port&255);out.flush();
            int version=in.readUnsignedByte(),code=in.readUnsignedByte(),reserved=in.readUnsignedByte(),atyp=in.readUnsignedByte();
            if(version!=5||code!=0||reserved!=0)throw new IOException("Tor не установил соединение (SOCKS "+code+")");
            int n=atyp==1?4:atyp==4?16:atyp==3?in.readUnsignedByte():-1;
            if(n<0)throw new IOException("SOCKS: неверный ответ");byte[] skip=new byte[n+2];in.readFully(skip);
            Socket transport=raw;
            if(tls){
                SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,host,port,true);
                SSLParameters params=ssl.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");
                params.setServerNames(Collections.singletonList(new SNIHostName(host)));ssl.setSSLParameters(params);
                ssl.setSoTimeout(25000);ssl.startHandshake();transport=ssl;
            }
            try{
                String path=u.getRawPath();if(path==null||path.isEmpty())path="/";
                if(u.getRawQuery()!=null)path+="?"+u.getRawQuery();
                String authority=host+(u.getPort()>0?":"+port:"");
                OutputStream request=transport.getOutputStream();
                request.write(("GET "+path+" HTTP/1.1\r\nHost: "+authority+"\r\nUser-Agent: AetherBolt-Tor-Text/1\r\nAccept: text/plain,text/html,application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));request.flush();
                BufferedInputStream response=new BufferedInputStream(transport.getInputStream());
                String status=line(response);String[] parts=status.split(" ",3);
                if(parts.length<2||!parts[0].startsWith("HTTP/"))throw new IOException("Неверный HTTP-ответ");
                int statusCode=Integer.parseInt(parts[1]);Map<String,String> headers=new HashMap<>();
                int total=0;for(;;){String h=line(response);total+=h.length();if(total>32768)throw new IOException("Слишком большие заголовки");if(h.isEmpty())break;int i=h.indexOf(':');if(i>0)headers.put(h.substring(0,i).toLowerCase(Locale.ROOT),h.substring(i+1).trim());}
                String type=headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT);
                if(statusCode<200||statusCode>=300)return new Reply(statusCode,type,"Ответ HTTP "+statusCode+". Переходы по перенаправлениям не выполняются.");
                String encoding=headers.getOrDefault("content-encoding","identity");if(!"identity".equalsIgnoreCase(encoding))throw new IOException("Сжатый ответ не поддерживается");
                if(!type.startsWith("text/")&&!type.startsWith("application/json"))throw new IOException("Поддерживаются только текстовые ответы");
                ByteArrayOutputStream body=new ByteArrayOutputStream();
                if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))){
                    while(body.size()<LIMIT){String size=line(response).split(";",2)[0].trim();long chunk=Long.parseLong(size,16);if(chunk<0)throw new IOException("Неверный размер блока");if(chunk==0)break;int count=(int)Math.min(chunk,LIMIT-body.size());copy(response,body,count);if(count<chunk)break;if(!line(response).isEmpty())throw new IOException("Неверный конец блока");}
                }else{
                    String length=headers.get("content-length");
                    if(length!=null){long nbytes=Long.parseLong(length);if(nbytes<0)throw new IOException("Неверный размер");copy(response,body,(int)Math.min(LIMIT,nbytes));}
                    else {byte[] b=new byte[4096];while(body.size()<LIMIT){int count=response.read(b,0,Math.min(b.length,LIMIT-body.size()));if(count<0)break;body.write(b,0,count);}}
                }
                return new Reply(statusCode,type,new String(body.toByteArray(),StandardCharsets.UTF_8)+(body.size()==LIMIT?"\n[Ответ ограничен 128 КБ]":""));
            }finally{if(transport!=raw)transport.close();}
        }catch(NumberFormatException e){throw new IOException("Некорректный HTTP-ответ",e);}
        finally{raw.close();}
    }
    private static void copy(InputStream in,ByteArrayOutputStream out,int n)throws IOException{
        byte[] b=new byte[4096];while(n>0){int count=in.read(b,0,Math.min(n,b.length));if(count<0)throw new EOFException();out.write(b,0,count);n-=count;}
    }
    private static String line(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        for(;;){int c=in.read();if(c<0)throw new EOFException();if(c=='\n')break;if(b.size()>=8192)throw new IOException("Слишком длинная строка ответа");if(c!='\r')b.write(c);}
        return new String(b.toByteArray(),StandardCharsets.ISO_8859_1);
    }
}
