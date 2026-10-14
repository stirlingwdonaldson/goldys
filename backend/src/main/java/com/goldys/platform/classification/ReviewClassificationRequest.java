package com.goldys.platform.classification;

import java.util.List;

/**
 * A request to classify one review: the free text the classifier reads (built from the review's
 * comment, private note and tags) and the aspect taxonomy the classifier may detect sentiment for.
 */
public record ReviewClassificationRequest(String featureText, List<String> aspects) {}
