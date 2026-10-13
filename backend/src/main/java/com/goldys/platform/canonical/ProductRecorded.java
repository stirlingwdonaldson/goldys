package com.goldys.platform.canonical;

/**
 * Published after a canonical product fact is recorded, so the recipe projector can rebuild the
 * menu-state projection in the same transaction.
 */
public record ProductRecorded(int productId) {}
