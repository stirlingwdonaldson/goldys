package com.goldys.platform.semantic;

import java.time.Instant;

/**
 * Why and how a resolved value was chosen.
 *
 * @param kind the resolution mechanism
 * @param source the chosen source, if any
 * @param reason a human-readable justification
 * @param actor who made the decision, if a person did
 * @param at when the decision was made
 */
public record ResolutionDetail(
    String kind, String source, String reason, String actor, Instant at) {}
