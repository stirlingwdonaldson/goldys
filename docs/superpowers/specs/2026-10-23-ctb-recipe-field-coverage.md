# Recipe business-field display checklist

Every business scalar below is exposed in its corresponding expandable detail section.
Array order is retained; each displayed field has its exact source path in Source details.
Null, empty string, false and zero stay distinct. Empty collections have explicit markers.
Text is extracted to plain text; source bytes remain the audit authority.

Account/approval-user objects and permission/session flags are excluded from business display.
Raw inspection remains subject to connectors READ. Equipment has no populated row schema;
ingredient-method mode also has no populated example. Unknown populated rows are retained
raw and reported as unsupported rather than silently mapped.

## ALLERGENS

- `data[*].allergenId`
- `data[*].allergenName`
- `data[*].isChecked`
- `data[*].order`
- `data[*].sequenceOrderNumber`

## DIETARY

- `data[*].dietaryRequirementId`
- `data[*].dietaryRequirementName`
- `data[*].isChecked`
- `data[*].order`
- `data[*].sequenceOrderNumber`

## EQUIPMENT


## EQUIPMENT_OPTIONS

- `data[*].equipmentId`
- `data[*].equipmentMediaId`
- `data[*].equipmentName`
- `data[*].isActive`

## HEADER

- `cookingVideoURL`
- `costChefPerHour`
- `costPerPortion`
- `createdByName`
- `customFoodCostPercentageLimit`
- `customFoodCostPercentageLimitWithLabour`
- `difficultyRating`
- `foodCost`
- `foodCostWithLabour`
- `globalFoodCostLimit`
- `grossProfit`
- `isActive`
- `isCountableOnStocktake`
- `isLocked`
- `isProductionItem`
- `kitchenRevenue`
- `labourCost`
- `lessGST`
- `marginContributionWithLabour`
- `menuDescription`
- `menuSellingPrice`
- `metaData.approveDate`
- `metaData.approvedBy`
- `metaData.archivedBy`
- `metaData.createdByName`
- `metaData.isMultipleLineShown`
- `metaData.requestDate`
- `metaData.requestForApprovalBy`
- `noOfPortion`
- `packagingType`
- `portionIncludingLabour`
- `portionMeasurement`
- `portionSize`
- `recipeBarcode`
- `recipeDate`
- `recipeDesc`
- `recipeFolderId`
- `recipeHandyHints`
- `recipeId`
- `recipeMediaId`
- `recipeName`
- `recipeUnitOfMeasurementId`
- `recipeUnitOfMeasurementName`
- `stockCategoryId`
- `timeTaken`
- `totalCost`
- `warningOverFoodCostLimitWrapper.foodCostPercentage`
- `warningOverFoodCostLimitWrapper.isOverLimit`
- `warningOverFoodCostLimitWrapper.warningMessage`

## INGREDIENT_METHODS


## NUTRITION

- `data.calcium`
- `data.carbohydrate`
- `data.dietaryFiber`
- `data.energy`
- `data.fatTotal`
- `data.isCustomized`
- `data.magnesium`
- `data.protein`
- `data.saturated`
- `data.sodium`
- `data.sugar`
- `data.vitaminB6`

## RECIPE FACTS

- `data[*].isActive`
- `data[*].isChecked`
- `data[*].isNotAllowDeleted`
- `data[*].order`
- `data[*].recipeFactDescription`
- `data[*].recipeFactId`
- `data[*].recipeFactType`
- `data[*].recipeId`

## STOCKS

- `data[*].costPerQty`
- `data[*].costPerUnit`
- `data[*].ingredientDescription`
- `data[*].ingredientMethodGroupIds`
- `data[*].isBrokenLink`
- `data[*].isNutritionLinked`
- `data[*].isSubRecipeIngredient`
- `data[*].nutritionDatabaseID`
- `data[*].nutritionFoodID`
- `data[*].portionWeight`
- `data[*].recipeDetailId`
- `data[*].recipeId`
- `data[*].recipeName`
- `data[*].stockCode`
- `data[*].stockDesc`
- `data[*].stockId`
- `data[*].stockSupplierId`
- `data[*].stockUOMShortname`
- `data[*].stockUnit`
- `data[*].stockUnitOfMeasurementId`
- `data[*].stockUsable`
- `data[*].stockWastage`
- `data[*].supplierName`
- `data[*].supplierStockCode`
- `data[*].unitOfMeasurementId`
- `data[*].unitOfMeasurementName`

## USAGES

- `data[*].costPerPortion`
- `data[*].costPerPortionIncludingLabour`
- `data[*].costPerQty`
- `data[*].costPerQtyIncludingLabour`
- `data[*].ingredientDescription`
- `data[*].ingredientMethodGroupIds`
- `data[*].isBrokenLink`
- `data[*].noOfPortion`
- `data[*].originalPortionSize`
- `data[*].originalUOM`
- `data[*].originalUOMId`
- `data[*].portionWeight`
- `data[*].portionWeightConverted`
- `data[*].recipeAsIngredientId`
- `data[*].recipeId`
- `data[*].recipeName`
- `data[*].recipeUsedId`
- `data[*].sequenceNo`
- `data[*].unitOfMeasurementId`
- `data[*].uomName`
