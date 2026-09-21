"""Loopback-only deterministic WebDAV subset for isolated integration tests."""
import base64
import hashlib
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

files={}
raced=set()
lock=threading.Lock()
def etag(data): return '"'+hashlib.sha256(data).hexdigest()+'"'

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def send(self, code, body=b"", tag=None, location=None):
        self.send_response(code)
        self.send_header("Content-Length",str(len(body)))
        if tag: self.send_header("ETag",tag)
        if location: self.send_header("Location",location)
        self.end_headers()
        if body:self.wfile.write(body)
    def handle_request(self):
        size=int(self.headers.get("Content-Length","0"))
        if size>4*1024*1024:return self.send(413)
        data=self.rfile.read(size)
        if self.headers.get("Authorization")!="Basic "+base64.b64encode(b"test-user:test-password").decode() or self.path.startswith("/unauthorized/"):return self.send(401)
        if self.path.startswith("/redirect/"):return self.send(302,location="https://example.invalid/should-not-follow")
        with lock:
            old=files.get(self.path)
            if self.command=="GET":
                if old is None:return self.send(404)
                tag=None if self.path.startswith("/no-etag/") else etag(old)
                return self.send(200,old,tag)
            if self.command=="PUT":
                if self.path.startswith("/race-") and self.path.endswith("qingjian-tasks-v1.json") and old is not None and self.headers.get("If-Match") and self.path not in raced:
                    changed=json.loads(old)
                    changed["items"].append(dict(id="f"*32,title="并发新增",notes="",due=0,created=1789990000123,done=False,lead=60,deleted=False))
                    files[self.path]=json.dumps(changed,ensure_ascii=False).encode()
                    raced.add(self.path)
                    return self.send(412)
                if not self.path.startswith("/no-cas/"):
                    if self.headers.get("If-None-Match")=="*" and old is not None:return self.send(412)
                    match=self.headers.get("If-Match")
                    if match is not None and (old is None or match!=etag(old)):return self.send(412)
                files[self.path]=data
                return self.send(201 if old is None else 204,tag=etag(data))
            if self.command=="DELETE":
                files.pop(self.path,None)
                return self.send(204)
            return self.send(405)
    do_GET=do_PUT=do_DELETE=handle_request

if __name__=="__main__":
    print("QingJian test WebDAV listening on 127.0.0.1:18766",flush=True)
    ThreadingHTTPServer(("127.0.0.1",18766),Handler).serve_forever()
