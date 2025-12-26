package com.dadtvplay.emails.service;

import com.dadtvplay.emails.model.EmailResponse;
import com.dadtvplay.emails.model.ServiceFilter;
import com.dadtvplay.emails.util.MailBodyExtractor;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ImapEmailService {

  @Value("${imap.host}")
  private String host;

  @Value("${imap.port}")
  private int port;

  @Value("${imap.username}")
  private String username;

  @Value("${imap.password}")
  private String password;

  @Value("${imap.folder}")
  private String folderName;

  @Value("${imap.ssl.trust:}")
  private String sslTrust;

  @Value("${imap.scan.max:500}")
  private int maxScan;

  public EmailResponse findLastEmail(String mailboxEmail, ServiceFilter filter) throws Exception {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new IllegalStateException("IMAP_USERNAME/IMAP_PASSWORD no están configuradas en variables de entorno.");
    }

    Properties props = new Properties();
    props.put("mail.store.protocol", "imaps");
    props.put("mail.imaps.host", host);
    props.put("mail.imaps.port", String.valueOf(port));
    props.put("mail.imaps.ssl.enable", "true");

    // Para servidores con certificado self-signed o cadena incompleta.
    // Ejemplo recomendado: IMAP_SSL_TRUST=mail.tudominio.com (o "*" si no hay alternativa).
    String trust = (sslTrust == null || sslTrust.isBlank()) ? host : sslTrust.trim();
    props.put("mail.imaps.ssl.trust", trust);

    Session session = Session.getInstance(props);

    Store store = null;
    Folder inbox = null;

    try {
      store = session.getStore("imaps");
      store.connect(host, port, username, password);

      inbox = store.getFolder(folderName);
      inbox.open(Folder.READ_ONLY);

      // 1) Escaneo local de los últimos N mensajes (suele ser MUCHO más rápido que SEARCH en buzones grandes)
      Message last = scanLastMessages(inbox, mailboxEmail, filter, Math.max(1, maxScan));

      // 2) Fallback: búsqueda del servidor (puede ser lenta en algunos servidores cPanel)
      if (last == null) {
        SearchTerm term = buildSearchTerm(filter);
        Message[] matches = inbox.search(term);
        last = pickLatest(matches);
      }

      if (last == null) {
        throw new NoSuchElementException("No se encontró ningún correo para el servicio: " + filter.key());
      }

      String subject = safeString(last.getSubject());
      String from = extractFrom(last);
      Date received = last.getReceivedDate();
      if (received == null) received = last.getSentDate();
      Instant receivedAt = received != null ? received.toInstant() : null;

      MailBodyExtractor.BodyResult bodyRes = MailBodyExtractor.extract(last);

      return new EmailResponse(
          filter.key(),
          mailboxEmail,
          subject,
          from,
          receivedAt,
          bodyRes.body(),
          bodyRes.contentType()
      );
    } finally {
      try {
        if (inbox != null && inbox.isOpen()) inbox.close(false);
      } catch (Exception ignored) {}
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {}
    }
  }

  private SearchTerm buildSearchTerm(ServiceFilter filter) {
    SearchTerm fromTerm = orTerms(
        filter.fromContains(),
        s -> new FromStringTerm(s)
    );

    SearchTerm subjectTerm = orTerms(
        filter.subjectContains(),
        s -> new SubjectTerm(s)
    );

    SearchTerm combined = andNullable(fromTerm, subjectTerm);
    return combined != null ? combined : new MatchAllTerm();
  }

  private static final class MatchAllTerm extends SearchTerm {
    @Override
    public boolean match(Message msg) {
      return true;
    }
  }

  private Message pickLatest(Message[] messages) {
    if (messages == null || messages.length == 0) return null;

    Message best = null;
    long bestTs = Long.MIN_VALUE;

    for (Message m : messages) {
      try {
        Date d = m.getReceivedDate();
        if (d == null) d = m.getSentDate();
        long ts = d != null ? d.getTime() : Long.MIN_VALUE;

        if (best == null || ts > bestTs) {
          best = m;
          bestTs = ts;
        } else if (ts == bestTs && best != null) {
          // desempate: message number
          try {
            if (m.getMessageNumber() > best.getMessageNumber()) best = m;
          } catch (Exception ignored) {}
        }
      } catch (Exception ignored) {}
    }

    return best;
  }

  public EmailResponse findLastEmailAny(String mailboxEmail) throws Exception {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new IllegalStateException("IMAP_USERNAME/IMAP_PASSWORD no están configuradas en variables de entorno.");
    }

    Properties props = new Properties();
    props.put("mail.store.protocol", "imaps");
    props.put("mail.imaps.host", host);
    props.put("mail.imaps.port", String.valueOf(port));
    props.put("mail.imaps.ssl.enable", "true");

    String trust = (sslTrust == null || sslTrust.isBlank()) ? host : sslTrust.trim();
    props.put("mail.imaps.ssl.trust", trust);

    Session session = Session.getInstance(props);

    Store store = null;
    Folder inbox = null;

    try {
      store = session.getStore("imaps");
      store.connect(host, port, username, password);

      inbox = store.getFolder(folderName);
      inbox.open(Folder.READ_ONLY);

      Message last = scanLastMessagesAny(inbox, mailboxEmail, Math.max(1, maxScan));
      if (last == null) {
        throw new NoSuchElementException("No se encontró ningún correo reciente para: " + mailboxEmail);
      }

      String subject = safeString(last.getSubject());
      String from = extractFrom(last);
      Date received = last.getReceivedDate();
      if (received == null) received = last.getSentDate();
      Instant receivedAt = received != null ? received.toInstant() : null;

      MailBodyExtractor.BodyResult bodyRes = MailBodyExtractor.extract(last);

      return new EmailResponse(
          "any",
          mailboxEmail,
          subject,
          from,
          receivedAt,
          bodyRes.body(),
          bodyRes.contentType()
      );
    } finally {
      try {
        if (inbox != null && inbox.isOpen()) inbox.close(false);
      } catch (Exception ignored) {}
      try {
        if (store != null && store.isConnected()) store.close();
      } catch (Exception ignored) {}
    }
  }

  private Message scanLastMessages(Folder folder, String targetEmail, ServiceFilter filter, int max) {
    try {
      int total = folder.getMessageCount();
      if (total <= 0) return null;

      int start = Math.max(1, total - max + 1);
      Message[] msgs = folder.getMessages(start, total);

      FetchProfile fp = new FetchProfile();
      fp.add(FetchProfile.Item.ENVELOPE);
      folder.fetch(msgs, fp);

      String target = (targetEmail == null) ? "" : targetEmail.trim().toLowerCase(Locale.ROOT);

      // iterar de más nuevo a más viejo
      for (int i = msgs.length - 1; i >= 0; i--) {
        Message m = msgs[i];
        if (matchesFilter(m, target, filter)) {
          return m;
        }
      }

      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private Message scanLastMessagesAny(Folder folder, String targetEmail, int max) {
    try {
      int total = folder.getMessageCount();
      if (total <= 0) return null;

      int start = Math.max(1, total - max + 1);
      Message[] msgs = folder.getMessages(start, total);

      FetchProfile fp = new FetchProfile();
      fp.add(FetchProfile.Item.ENVELOPE);
      folder.fetch(msgs, fp);

      String target = (targetEmail == null) ? "" : targetEmail.trim().toLowerCase(Locale.ROOT);

      // iterar de más nuevo a más viejo
      for (int i = msgs.length - 1; i >= 0; i--) {
        Message m = msgs[i];
        if (target.isBlank()) return m;
        if (matchesRecipient(m, target)) return m;
      }

      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private boolean matchesFilter(Message m, String targetEmailLower, ServiceFilter filter) {
    try {
      // 1) filtros del servicio (rápido). Primero filtramos aquí para no leer headers de miles de mensajes.
      String from = extractFrom(m).toLowerCase(Locale.ROOT);
      String subject = safeString(m.getSubject()).toLowerCase(Locale.ROOT);

      boolean fromOk = matchesAny(from, filter.fromContains());
      boolean subjectOk = matchesAny(subject, filter.subjectContains());

      if (filter.fromContains() != null && !filter.fromContains().isEmpty() && !fromOk) return false;
      if (filter.subjectContains() != null && !filter.subjectContains().isEmpty() && !subjectOk) return false;

      // 2) destinatario (solo para los candidatos del servicio)
      if (targetEmailLower != null && !targetEmailLower.isBlank()) {
        if (!matchesRecipient(m, targetEmailLower)) return false;
      }

      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean matchesRecipient(Message m, String targetEmailLower) {
    try {
      // A) API estándar (envelope): normalmente no requiere bajar todo el header.
      Address[] all = m.getAllRecipients();
      if (containsAddress(all, targetEmailLower)) return true;

      // B) Algunos servidores IMAP no llenan bien "recipients" en el envelope.
      // Revisamos headers comunes (incluyendo To/Cc) antes de declarar que no es para el destinatario.
      if (headerContains(m, "To", targetEmailLower)) return true;
      if (headerContains(m, "Cc", targetEmailLower)) return true;

      // C) Headers típicos cuando hay forward/catch-all (esto puede ser más caro; por eso va después)
      if (headerContains(m, "Delivered-To", targetEmailLower)) return true;
      if (headerContains(m, "X-Original-To", targetEmailLower)) return true;
      if (headerContains(m, "Envelope-To", targetEmailLower)) return true;

      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean containsAddress(Address[] addrs, String targetEmailLower) {
    if (addrs == null || addrs.length == 0) return false;
    for (Address a : addrs) {
      if (a == null) continue;
      String s = a.toString().toLowerCase(Locale.ROOT);
      if (s.contains(targetEmailLower)) return true;
    }
    return false;
  }

  private boolean headerContains(Message m, String headerName, String targetEmailLower) {
    try {
      String[] vals = m.getHeader(headerName);
      if (vals == null) return false;
      for (String v : vals) {
        if (v != null && v.toLowerCase(Locale.ROOT).contains(targetEmailLower)) return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean matchesAny(String haystackLower, List<String> needles) {
    if (needles == null || needles.isEmpty()) return true;
    for (String n : needles) {
      if (n == null || n.isBlank()) continue;
      if (haystackLower.contains(n.toLowerCase(Locale.ROOT))) return true;
    }
    return false;
  }

  private interface TermFactory {
    SearchTerm create(String s);
  }

  private SearchTerm orTerms(List<String> values, TermFactory factory) {
    if (values == null || values.isEmpty()) return null;

    List<SearchTerm> terms = new ArrayList<>();
    for (String v : values) {
      if (v == null || v.isBlank()) continue;
      terms.add(factory.create(v));
    }

    if (terms.isEmpty()) return null;
    if (terms.size() == 1) return terms.get(0);

    return new OrTerm(terms.toArray(new SearchTerm[0]));
  }

  private SearchTerm andNullable(SearchTerm a, SearchTerm b) {
    if (a == null) return b;
    if (b == null) return a;
    return new AndTerm(a, b);
  }

  private String extractFrom(Message msg) {
    try {
      Address[] from = msg.getFrom();
      if (from == null || from.length == 0) return "";
      Address first = from[0];
      if (first instanceof InternetAddress ia) {
        String email = ia.getAddress();
        String personal = ia.getPersonal();
        if (personal != null && !personal.isBlank()) {
          return personal + " <" + safeString(email) + ">";
        }
        return safeString(email);
      }
      return safeString(first.toString());
    } catch (Exception e) {
      return "";
    }
  }

  private String safeString(String s) {
    return s == null ? "" : s;
  }
}
