package org.opendiplom.web;

/** Building blocks of the pages: every text from a file goes through {@link #escape}. */
final class Html {
    private static final String STYLE = String.join("\n",
        "body{font:15px/1.45 system-ui,-apple-system,'Segoe UI',Arial,sans-serif;margin:0;color:#1d1d1f;background:#fafafa}",
        "main{max-width:1100px;margin:0 auto;padding:16px}",
        "h1{font-size:22px}h2{font-size:18px;margin-top:28px}",
        "section{background:#fff;border:1px solid #ddd;border-radius:6px;padding:12px 16px;margin:12px 0}",
        "label{display:block;margin:8px 0 2px}input[type=text],input[type=number]{width:100%;max-width:520px;padding:4px}",
        "button{margin-top:10px;padding:6px 14px}select{max-width:100%}td select{max-width:360px}",
        "table{border-collapse:collapse;margin:8px 0;font-size:13px}td,th{border:1px solid #ccc;padding:3px 6px;text-align:left;vertical-align:top}",
        ".note{color:#8a4b00}.error{background:#fdecea;border-color:#e0b4b4;white-space:pre-wrap}.muted{color:#666}",
        ".scroll{overflow-x:auto}"
    );

    /**
     * Where the application is served from, with a slash at the end: links of
     * the pages are relative to it, so a proxy may serve it under a prefix.
     */
    private static volatile String base = "/";

    private Html() {
    }

    /** Sets the prefix, «» or «/open-diplom», once when the server is made. */
    static void root(final String contextPath) {
        base = contextPath + "/";
    }

    static String escape(final Object value) {
        if (value == null) {
            return "";
        }
        final String text = value.toString();
        final StringBuilder out = new StringBuilder(text.length());
        for (final char letter : text.toCharArray()) {
            switch (letter) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(letter);
            }
        }
        return out.toString();
    }

    /** A whole page; the body is already HTML. */
    static String page(final String title, final String body) {
        return "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<base href=\"" + escape(base) + "\">"
            + "<title>" + escape(title) + "</title><style>" + STYLE + "</style></head>"
            + "<body><main><p><a href=\"./\">Открытый диплом</a> · прототип</p>"
            + body + "</main></body></html>";
    }
}
