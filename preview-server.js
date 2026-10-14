const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
http.createServer((request, response) => {
  const requestedPath = request.url.split("?")[0];
  const allowed = new Set(["/", "/index.html", "/manifest.webmanifest", "/service-worker.js", "/icon.svg", "/brain-mascot.svg"]);
  if (!allowed.has(requestedPath)) {
    response.writeHead(404).end("Not found");
    return;
  }
  const file = path.join(root, requestedPath === "/" ? "index.html" : requestedPath.slice(1));
  const type = requestedPath.endsWith(".webmanifest") ? "application/manifest+json"
    : requestedPath.endsWith(".js") ? "text/javascript; charset=utf-8"
    : requestedPath.endsWith(".svg") ? "image/svg+xml"
    : "text/html; charset=utf-8";
  response.writeHead(200, { "Content-Type": type, "Cache-Control": requestedPath === "/service-worker.js" ? "no-cache" : "no-store" });
  fs.createReadStream(file).pipe(response);
}).listen(8765, "127.0.0.1", () => {
  console.log("Reel Count web app running at http://127.0.0.1:8765");
});
