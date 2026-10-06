package com.jobstar.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ResumeFileErrorPage {
    private ResumeFileErrorPage() {
    }

    public static boolean isBrowserFileRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return request.getRequestURI().matches("/api/resumes/[^/]+/file")
                && accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    public static ResponseEntity<String> response(int status) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(html(status));
    }

    public static String html(int status) {
        String message = status == 403
                ? "Sign in to the account that owns this resume, then open the file again."
                : status == 404
                    ? "This resume is unavailable or cannot be accessed from your account."
                    : "We could not open this resume right now. Please return to JobStar and try again.";
        // Only fixed product messages are rendered; provider/exception details stay private.
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Resume unavailable | JobStar</title>
                  <link rel="icon" type="image/svg+xml" href="/favicon.svg?v=jobstar">
                  <style>
                    body { margin:0; min-height:100vh; display:grid; place-items:center;
                      background:linear-gradient(135deg,#f4f1e8,#e4eced); color:#102a43;
                      font-family:Georgia,serif; }
                    main { box-sizing:border-box; width:min(90%%,520px); padding:40px;
                      background:#fffdf7; border-top:6px solid #efb84c; }
                    .brand { color:#1b4f72; letter-spacing:.12em; font-size:14px; }
                    h1 { font-size:30px; } p { line-height:1.6; }
                    a { display:inline-block; margin-top:12px; padding:12px 18px;
                      background:#102a43; color:#fffdf7; text-decoration:none; }
                    a:focus-visible { outline:3px solid #efb84c; outline-offset:3px; }
                  </style>
                </head>
                <body><main><p class="brand">JOBSTAR</p><h1>Unable to open this resume</h1>
                  <p>%s</p><a href="/">Return to JobStar</a>
                </main></body></html>
                """.formatted(message);
    }
}
