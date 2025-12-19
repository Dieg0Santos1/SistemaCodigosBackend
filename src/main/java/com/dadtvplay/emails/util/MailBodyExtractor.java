package com.dadtvplay.emails.util;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;

public final class MailBodyExtractor {

  public record BodyResult(String body, String contentType) {}

  private MailBodyExtractor() {}

  public static BodyResult extract(Message message) throws Exception {
    Object content = message.getContent();
    String ct = safeContentType(message.getContentType());

    if (content == null) return new BodyResult("", ct);

    if (content instanceof String s) {
      return new BodyResult(s, ct);
    }

    if (content instanceof Multipart mp) {
      String plain = null;
      String html = null;

      for (int i = 0; i < mp.getCount(); i++) {
        BodyPart part = mp.getBodyPart(i);

        if (part.isMimeType("text/plain") && plain == null) {
          plain = (String) part.getContent();
        } else if (part.isMimeType("text/html") && html == null) {
          html = (String) part.getContent();
        } else if (part.getContent() instanceof Multipart nested) {
          BodyResult nestedRes = extractFromMultipart(nested);
          if (nestedRes != null) {
            if (nestedRes.contentType().startsWith("text/plain") && plain == null) plain = nestedRes.body();
            if (nestedRes.contentType().startsWith("text/html") && html == null) html = nestedRes.body();
          }
        }
      }

      // Preferimos HTML para renderizar parecido al webmail.
      if (html != null) return new BodyResult(html, "text/html");
      if (plain != null) return new BodyResult(plain, "text/plain");
      return new BodyResult("", ct);
    }

    return new BodyResult(String.valueOf(content), ct);
  }

  private static BodyResult extractFromMultipart(Multipart mp) throws Exception {
    String plain = null;
    String html = null;

    for (int i = 0; i < mp.getCount(); i++) {
      BodyPart part = mp.getBodyPart(i);

      if (part.isMimeType("text/plain") && plain == null) {
        plain = (String) part.getContent();
      } else if (part.isMimeType("text/html") && html == null) {
        html = (String) part.getContent();
      } else if (part.getContent() instanceof Multipart nested) {
        BodyResult nestedRes = extractFromMultipart(nested);
        if (nestedRes != null) {
          if (nestedRes.contentType().startsWith("text/plain") && plain == null) plain = nestedRes.body();
          if (nestedRes.contentType().startsWith("text/html") && html == null) html = nestedRes.body();
        }
      }
    }

    // Preferimos HTML para renderizar parecido al webmail.
    if (html != null) return new BodyResult(html, "text/html");
    if (plain != null) return new BodyResult(plain, "text/plain");
    return null;
  }

  private static String safeContentType(String raw) {
    if (raw == null) return "unknown";
    int idx = raw.indexOf(';');
    return (idx >= 0 ? raw.substring(0, idx) : raw).trim().toLowerCase();
  }
}
