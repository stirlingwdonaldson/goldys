package com.goldys.platform.semantic;

/** A source field path and its plain-text scalar value; null, empty and false remain distinct. */
public record RecipeField(String path, String value, String type) {}
