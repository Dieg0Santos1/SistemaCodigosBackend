package com.dadtvplay.emails.controller;

import com.dadtvplay.emails.model.EmailResponse;
import com.dadtvplay.emails.model.ServiceFilter;
import com.dadtvplay.emails.service.ImapEmailService;
import com.dadtvplay.emails.service.ServiceCatalog;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@Validated
public class EmailController {

  private final ImapEmailService imapEmailService;
  private final ServiceCatalog serviceCatalog;

  public EmailController(ImapEmailService imapEmailService, ServiceCatalog serviceCatalog) {
    this.imapEmailService = imapEmailService;
    this.serviceCatalog = serviceCatalog;
  }

  @GetMapping("/email/last")
  public ResponseEntity<?> lastEmail(
      @RequestParam("email") @NotBlank String email,
      @RequestParam("service") @NotBlank String service
  ) {
    String normalizedEmail = email.trim().toLowerCase();
    if (!isAllowedDomain(normalizedEmail)) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Email inválido. Debe terminar en @klbdescuentos.com"
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
    String normalizedEmail = email.trim().toLowerCase();
    if (!isAllowedDomain(normalizedEmail)) {
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Email inválido. Debe terminar en @klbdescuentos.com"
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

  // Útil para el frontend (llenar el select dinámicamente)
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
    return email.endsWith("@klbdescuentos.com") && email.contains("@") && !email.startsWith("@");
  }
}
