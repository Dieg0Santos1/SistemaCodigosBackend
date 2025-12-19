package com.dadtvplay.emails.model;

import java.time.Instant;

public record EmailResponse(
    String service,
    String mailbox,
    String subject,
    String from,
    Instant receivedAt,
    String body,
    String bodyContentType
) {}
