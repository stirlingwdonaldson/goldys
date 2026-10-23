package com.goldys.platform.semantic;

/** Source-independent recipe observation; quantities and costs retain their signs. */
public record RecipeFolder(int folderId, Integer parentId, String name) {}
