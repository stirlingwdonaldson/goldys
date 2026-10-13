package com.goldys.platform.classification;

import java.util.List;

/**
 * A request to classify one product: the free text describing it (name, description, ingredients)
 * and the candidate categories the classifier may choose from.
 */
public record ProductClassificationRequest(String featureText, List<CategoryOption> options) {}
