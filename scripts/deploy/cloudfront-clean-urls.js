function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (!uri || uri === "/") {
    return request;
  }
  if (uri.endsWith("/")) {
    request.uri = uri + "index.html";
    return request;
  }
  var last = uri.substring(uri.lastIndexOf("/") + 1);
  if (last.indexOf(".") === -1) {
    request.uri = uri + ".html";
  }
  return request;
}
