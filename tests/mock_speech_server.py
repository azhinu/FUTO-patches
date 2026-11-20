"""Controlled local endpoint for Android recorder/insertion/cancellation tests."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import time

state = {"mode": "success", "delay": 0}
class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print("Request " + format % args, flush=True)
    def reply(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        try:
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError):
            pass
    def do_HEAD(self):
        self.send_response(405)
        self.end_headers()
    def do_GET(self):
        if self.path == "/openapi.json":
            self.reply(200, {"paths": {"/asr": {}}})
        else:
            self.reply(200, state)
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path == "/control":
            state.update(json.loads(body))
            self.reply(200, state)
            return
        print("Received audio request bytes=" + str(len(body)), flush=True)
        del body
        mode, delay = state["mode"], state["delay"]
        time.sleep(delay)
        if mode == "success":
            self.reply(200, {"text": "Remote transcription test."})
        elif mode == "empty":
            self.reply(200, {"text": ""})
        else:
            self.reply(int(mode), {"error": "Synthetic provider error; never insert this"})
ThreadingHTTPServer(("0.0.0.0", 9000), Handler).serve_forever()
