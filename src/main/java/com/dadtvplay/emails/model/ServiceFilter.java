package com.dadtvplay.emails.model;

import java.util.List;

public record ServiceFilter(
    String key,
    String displayName,
    List<String> fromContains,
    List<String> subjectContains
) {}
