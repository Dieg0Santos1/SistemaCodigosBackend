package com.dadtvplay.emails.service;

import com.dadtvplay.emails.model.ServiceFilter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ServiceCatalog {

  // Ajusta estos filtros a los correos reales que recibes.
  private final Map<String, ServiceFilter> services = new LinkedHashMap<>();

  public ServiceCatalog() {
    services.put("netflix", new ServiceFilter(
        "netflix",
        "Netflix",
        // En algunos webmails el "From" visible es solo "Netflix".
        // Por eso incluimos "netflix" además de dominios típicos.
        java.util.List.of(
            "netflix",
            "@netflix.com",
            "@mail.netflix.com",
            "@account.netflix.com",
            "info@account.netflix.com",
            "info@netflix.com"
        ),
        // Nota: hacemos "contains" (no match exacto), así que puedes poner asuntos completos o fragmentos.
        java.util.List.of(
            // genéricos
            "código",
            "code",
            "verification",
            "iniciar sesión",
            "inicio de sesión",
            "login",
            "tu código",
            // asuntos conocidos (según tus ejemplos)
            "Tu código de acceso temporal de Netflix",
            "Importante: Cómo actualizar tu Hogar con Netflix",
            "Netflix: Nueva solicitud de inicio de sesión",
            "Completa tu solicitud de restablecimiento de contraseña",
            "Netflix: Tu código de inicio de sesión",
            "Completa tu solicitud de restablecimiento de contraseña",
            "Restablece tu contraseña",
            "Restablecimiento de contraseña"
        )
    ));

    // Amazon/Prime (en UI mostramos "Amazon Prime" pero puedes usar también subject de Amazon)
    services.put("prime", new ServiceFilter(
        "prime",
        "Amazon Prime",
        java.util.List.of("amazon", "@amazon.com", "@primevideo.com", "account-update@amazon.com"),
        java.util.List.of(
            "amazon.com: Sign-in attempt",
            "amazon.com: Intento de inicio de sesión",
            "Ayuda con la contraseña de Amazon",
            "otp",
            "código",
            "code",
            "verification",
            "inicia sesión",
            "sign-in"
        )
    ));

    services.put("disney", new ServiceFilter(
        "disney",
        "Disney+",
        java.util.List.of("disney", "@disney.com", "@disneyplus.com"),
        java.util.List.of(
            "Tu código de acceso único para Disney+",
            "código",
            "code",
            "verification",
            "one-time"
        )
    ));

    services.put("max", new ServiceFilter(
        "max",
        "Max",
        java.util.List.of("max", "hbo", "@hbomax.com", "@max.com", "@warnermedia.com"),
        java.util.List.of(
            "Tu enlace para restablecer tu contraseña",
            "restablecer tu contraseña",
            "código",
            "code",
            "verification"
        )
    ));

    services.put("spotify", new ServiceFilter(
        "spotify",
        "Spotify",
        // Hacemos contains para cubrir variaciones (no-reply, mail.spotify.com, etc.).
        java.util.List.of(
            "spotify",
            "@spotify.com",
            "no-reply@spotify.com",
            "noreply@spotify.com"
        ),
        java.util.List.of(
            // Genéricos / comunes
            "spotify",
            "código",
            "code",
            "verification",
            "verificación",
            "iniciar sesión",
            "inicio de sesión",
            "login",
            "restablece",
            "restablecer",
            "password",
            "contraseña"
        )
    ));

    services.put("apple", new ServiceFilter(
        "apple",
        "Apple",
        java.util.List.of("apple", "@apple.com", "@id.apple.com"),
        java.util.List.of(
            "Apple+ código de activación",
            "código de activación"
        )
    ));

    services.put("vix", new ServiceFilter(
        "vix",
        "Vix",
        java.util.List.of("vix"),
        java.util.List.of("Cambio de contraseña", "restablecer")
    ));

    services.put("paramount", new ServiceFilter(
        "paramount",
        "Paramount+",
        java.util.List.of("paramount"),
        java.util.List.of("Restablecimiento de la contraseña de Paramount+")
    ));

    services.put("crunchyroll", new ServiceFilter(
        "crunchyroll",
        "Crunchyroll",
        java.util.List.of("crunchyroll"),
        java.util.List.of(
            "Reset Your Crunchyroll Password",
            "Restablece tu contraseña de Crunchyroll",
            "Reinicia tu contraseña de Crunchyroll",
            "reset",
            "restablece"
        )
    ));
  }

  public Map<String, ServiceFilter> all() {
    return Map.copyOf(services);
  }

  public Optional<ServiceFilter> get(String key) {
    if (key == null) return Optional.empty();
    return Optional.ofNullable(services.get(key.trim().toLowerCase()));
  }
}
