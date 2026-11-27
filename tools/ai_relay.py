#!/usr/bin/env python3
"""Personal single-user OpenAI relay. Bind loopback; expose ONLY via trusted HTTPS
reverse proxy with rate limiting. No production hosting or TLS setup included."""
import os, json, hmac, urllib.request, urllib.error, time
from http.server import HTTPServer, BaseHTTPRequestHandler

MAX = 256 * 1024
API_KEY = os.environ.get("OPENAI_API_KEY", "")
TOKEN = os.environ.get("AETHER_RELAY_TOKEN", "")
MODEL = os.environ.get("AETHER_MODEL", "")
if not API_KEY or len(TOKEN) < 32 or not MODEL:
    raise SystemExit("Set OPENAI_API_KEY, AETHER_RELAY_TOKEN (32+ chars), AETHER_MODEL")
if TOKEN == API_KEY:
    raise SystemExit("Relay token must not be the API key")

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

opener = urllib.request.build_opener(NoRedirect())
last = 0.0

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def reply(self, code, obj):
        payload = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        global last
        if self.path != "/v1/chat/completions":
            return self.reply(404, {"error":"not found"})
        if not hmac.compare_digest(self.headers.get("Authorization",""), "Bearer "+TOKEN):
            return self.reply(401, {"error":"unauthorized"})
        if time.monotonic() - last < 3:
            return self.reply(429, {"error":"rate limit"})
        last = time.monotonic()
        try:
            size = int(self.headers.get("Content-Length","0"))
            if not 0 < size <= MAX or self.headers.get("Transfer-Encoding"):
                return self.reply(413, {"error":"size"})
            data = json.loads(self.rfile.read(size))
            messages = data["messages"]
            if data["model"] != MODEL or not isinstance(messages,list) or not 1 <= len(messages) <= 13:
                raise ValueError()
            clean = []
            for msg in messages:
                if msg["role"] not in ("user","assistant") or not isinstance(msg["content"],str):
                    raise ValueError()
                clean.append({"role":msg["role"],"content":msg["content"]})
            if sum(len(m["content"]) for m in clean) > 32000 or clean[-1]["role"] != "user":
                raise ValueError()
        except (ValueError, KeyError, TypeError):
            return self.reply(400, {"error":"invalid request"})
        body = json.dumps({"model":MODEL,"input":clean,"max_output_tokens":1200,"store":False}).encode()
        req = urllib.request.Request("https://api.openai.com/v1/responses",data=body,headers={"Authorization":"Bearer "+API_KEY,"Content-Type":"application/json"},method="POST")
        try:
            with opener.open(req, timeout=85) as response:
                raw = response.read(1024*1024+1)
                if len(raw)>1024*1024:
                    raise ValueError()
                result = json.loads(raw)
            answer = "\n".join(c["text"] for item in result.get("output",[]) if item.get("type")=="message" for c in item.get("content",[]) if c.get("type")=="output_text")
            if not answer.strip():
                return self.reply(502, {"error":"no text"})
            return self.reply(200, {"choices":[{"message":{"role":"assistant","content":answer}}]})
        except urllib.error.HTTPError as exc:
            return self.reply(exc.code if exc.code in (400,401,403,429) else 502, {"error":"upstream"})
        except (OSError, ValueError, KeyError, TypeError):
            return self.reply(502, {"error":"upstream unavailable"})

    def setup(self):
        super().setup()
        self.connection.settimeout(15)

if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
