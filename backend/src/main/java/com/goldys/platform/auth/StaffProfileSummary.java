package com.goldys.platform.auth;

/** The fields an API response may expose about the current staff identity. */
public record StaffProfileSummary(String displayName, String department, String seniority) {}
