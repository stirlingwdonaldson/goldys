import { describe, expect, it } from "vitest";
import type { RecipeSection } from "@/lib/api/types";
import { selectedRecipeLabels, recipeFieldValue } from "./recipe-presentation";

describe("recipe presentation", () => {
  it("pairs labels and selections by source row without inferring from the name", () => {
    const section = {
      fields: [
        {
          path: "data[0].dietaryRequirementName",
          value: "Vegan",
          type: "TEXT",
        },
        { path: "data[1].isChecked", value: "true", type: "BOOLEAN" },
        { path: "data[0].isChecked", value: "false", type: "BOOLEAN" },
        {
          path: "data[1].dietaryRequirementName",
          value: "Dairy Friendly",
          type: "TEXT",
        },
        {
          path: "data[2].dietaryRequirementName",
          value: "Vegan",
          type: "TEXT",
        },
        { path: "data[2].isChecked", value: null, type: "NULL" },
      ],
    } as RecipeSection;
    expect(selectedRecipeLabels(section)).toEqual(["Dairy Friendly"]);
  });
  it("keeps zero, false, empty strings, missing and empty collections distinct", () => {
    expect(recipeFieldValue({ value: "0", type: "NUMBER" })).toBe("0");
    expect(recipeFieldValue({ value: "false", type: "BOOLEAN" })).toBe("No");
    expect(recipeFieldValue({ value: "", type: "TEXT" })).toBe("Empty in CTB");
    expect(recipeFieldValue({ value: null, type: "NULL" })).toBe(
      "Not supplied",
    );
    expect(recipeFieldValue({ value: "[]", type: "EMPTY_ARRAY" })).toBe(
      "No entries recorded",
    );
  });
});
