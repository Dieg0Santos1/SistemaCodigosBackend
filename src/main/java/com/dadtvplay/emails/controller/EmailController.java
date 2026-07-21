package com.dadtvplay.emails.controller;

import com.dadtvplay.emails.model.EmailResponse;
import com.dadtvplay.emails.model.ServiceFilter;
import com.dadtvplay.emails.service.ImapEmailService;
import com.dadtvplay.emails.service.ServiceCatalog;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api")
@Validated
public class EmailController {

  private final ImapEmailService imapEmailService;
  private final ServiceCatalog serviceCatalog;
  private final Set<String> allowedDomains;

  public EmailController(
      ImapEmailService imapEmailService,
      ServiceCatalog serviceCatalog,
      @Value("${app.email.allowed-domains:klbdescuentos.com,gmail.com,outlook.com,hotmail.com}") String allowedDomainsValue
  ) {
    this.imapEmailService = imapEmailService;
    this.serviceCatalog = serviceCatalog;
    this.allowedDomains = parseAllowedDomains(allowedDomainsValue);
  }

  @GetMapping("/email/last")
  public ResponseEntity<?> lastEmail(
      @RequestParam("email") @NotBlank String email,
      @RequestParam("service") @NotBlank String service
  ) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (!isAllowedDomain(normalizedEmail)) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Email invalido. Debe terminar en uno de estos dominios: " + String.join(", ", allowedDomains)
      ));
    }

    Optional<ServiceFilter> filterOpt = serviceCatalog.get(service);
    if (filterOpt.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Servicio no soportado: " + service,
          "supported", serviceCatalog.all().keySet()
      ));
    }

    try {
      EmailResponse res = imapEmailService.findLastEmail(normalizedEmail, filterOpt.get());
      return ResponseEntity.ok(res);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
          "error", "Error consultando IMAP",
          "details", e.getClass().getSimpleName() + ": " + e.getMessage()
      ));
    }
  }

  @GetMapping("/email/last-any")
  public ResponseEntity<?> lastEmailAny(
      @RequestParam("email") @NotBlank String email
  ) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (!isAllowedDomain(normalizedEmail)) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Email invalido. Debe terminar en uno de estos dominios: " + String.join(", ", allowedDomains)
      ));
    }

    try {
      EmailResponse res = imapEmailService.findLastEmailAny(normalizedEmail);
      return ResponseEntity.ok(res);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
          "error", "Error consultando IMAP",
          "details", e.getClass().getSimpleName() + ": " + e.getMessage()
      ));
    }
  }

  @GetMapping("/services")
  public Map<String, Object> services() {
    Map<String, Object> out = new LinkedHashMap<>();
    serviceCatalog.all().forEach((k, v) -> {
      out.put(k, Map.of(
          "key", v.key(),
          "displayName", v.displayName()
      ));
    });
    return out;
  }

  private boolean isAllowedDomain(String email) {
    if (email == null || email.isBlank() || !email.contains("@") || email.startsWith("@")) {
      return false;
    }

    int atIndex = email.lastIndexOf('@');
    if (atIndex < 0 || atIndex == email.length() - 1) {
      return false;
    }

    String domain = email.substring(atIndex + 1).trim().toLowerCase(Locale.ROOT);
    return allowedDomains.contains(domain);
  }

  private Set<String> parseAllowedDomains(String value) {
    Set<String> domains = new LinkedHashSet<>();
    if (value == null || value.isBlank()) {
      return domains;
    }

    for (String item : value.split(",")) {
      String domain = item.trim().toLowerCase(Locale.ROOT);
      if (!domain.isBlank()) {
        domains.add(domain);
      }
    }

    return domains;
  }
}
