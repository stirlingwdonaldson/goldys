package com.goldys.platform.canonical;

/** A read-only alias pair exposed to matchers outside the canonical package. */
public record ProductAliasView(String canonicalName, String alias) {}
