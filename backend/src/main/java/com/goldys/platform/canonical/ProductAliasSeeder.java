package com.goldys.platform.canonical;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the manual product alias table with the active Lightspeed menu reconciliation pairs. */
@Component
public class ProductAliasSeeder implements ApplicationRunner {
  private static final String MANUAL = "MANUAL";

  private static final List<SeedPair> SEED_PAIRS =
      List.of(
          new SeedPair("Baba Ganoush", "Eggplant Dip"),
          new SeedPair("Charred Corn Salad", "Corn Estiques"),
          new SeedPair("Kids slider", "Kiddies slider"),
          new SeedPair("Mustard Gravy - Side", "Mustard Gravy"),
          new SeedPair("Half Turkish bread - side", "Turkish bread"),
          new SeedPair("B - Havilah Gewurztraminer", "G - Havilah Gewurztraminer"),
          new SeedPair("B - Spinifex Rose 2025", "G - Spinifex Rose 2025"),
          new SeedPair("G - Deep Woods Estate Chardonnay", "G - Deepwoods Estate Chardonnay"),
          new SeedPair("B - Lighthand, Pinot Noir", "G - Lighthand, Pinot Noir"),
          new SeedPair("B - Denton Shed Chardonnay (2023)", "G - Denton Shed Chardonnay"),
          new SeedPair("Aperol Spritz (NV Three Pillars)", "Aperol Spritz (NV Continental)"),
          new SeedPair("Campari Spritz (NV Three Pillars)", "Campari Spritz (NV Continental)"),
          new SeedPair(
              "Limoncello Spritz (NV Three Pillars)", "Limoncello Spritz (NV Continental)"),
          new SeedPair(
              "Montenegro Spritz (NV Three Pillars)", "Montenegro Spritz (NV Continental)"),
          new SeedPair(
              "B - NV Three Pillars Sparkling Brut Cuvee", "B - NV Continental Platter Prosecco"),
          new SeedPair(
              "G - NV Three Pillars Sparkling Brut Cuvee", "G - NV Continental Platter Prosecco"),
          new SeedPair(".5L - Jamsheed Mataro (Tap)", ".5L - Jamsheed Mataro (Chilled)"),
          new SeedPair("G - Jamsheed Mataro (Tap)", "G - Jamsheed Mataro (Chilled)"),
          new SeedPair("Gnocchi - Pumpkin Butter Sage", "Gnocchi - Pumpkin"),
          new SeedPair("Ouzo - 30ml", "Katsaros Brothers Ouzo - 30ml"),
          new SeedPair("F - Ruben Croquette", "Ruben Croquettes"),
          new SeedPair("+1 Croquette", "+ 1 Ruben Croquette"),
          new SeedPair("Kids Gnocchi Bolognese", "Kids Gnocchi"),
          new SeedPair("Kids Gnocchi Napoli", "Kids Gnocchi"),
          new SeedPair("Jug - Keeper Pilsner", "Keeper Pilsner - Jug"),
          new SeedPair("Pint - Keeper Pilsner", "Keeper Pilsner - Pint"),
          new SeedPair("Pot - Keeper Pilsner", "Keeper Pilsner - Pot"),
          new SeedPair(
              "Jug - Goldy's 'An Old One' Creaming Old Ale Shandy",
              "Goldy's 'An Old One' Creaming Old Ale Shandy - Jug"),
          new SeedPair(
              "Pint - Goldy's 'An Old One' Creaming Old Ale Shandy",
              "Goldy's 'An Old One' Creaming Old Ale Shandy - Pint"),
          new SeedPair(
              "Pot - Goldy's 'An Old One' Creaming Old Ale Shandy",
              "Goldy's 'An Old One' Creaming Old Ale Shandy - Pot"),
          new SeedPair("Jug - Philter XPA", "Philter XPA - Jug"),
          new SeedPair("Pint - Philter XPA", "Philter XPA - Pint"),
          new SeedPair("Pot - Philter XPA", "Philter XPA - Pot"),
          new SeedPair("Jug - Loveshack Mid Red-Ale", "Loveshack Mid Red-Ale - Jug"),
          new SeedPair("Pint - Loveshack Mid Red-Ale", "Loveshack Mid Red-Ale - Pint"),
          new SeedPair("Pot - Loveshack Mid Red-Ale", "Loveshack Mid Red-Ale - Pot"),
          new SeedPair(
              "Can - Bridge Road Brewers 'Beechworth' Pale Ale",
              "Bridge Road Brewers 'Beechworth' Pale Ale"),
          new SeedPair("Dolin Dry Vermouth - 30ml", "Dolin Dry Vermouth - 45ml"));

  private final ProductAliasRepository repository;

  public ProductAliasSeeder(ProductAliasRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments args) {
    Instant createdAt = Instant.now();
    for (SeedPair seed : SEED_PAIRS) {
      String canonicalName = ProductNameKey.normalize(seed.canonicalName());
      String alias = ProductNameKey.normalize(seed.alias());
      if (!repository.existsByCanonicalNameAndAlias(canonicalName, alias)) {
        repository.save(ProductAlias.create(canonicalName, alias, MANUAL, createdAt));
      }
    }
  }

  private record SeedPair(String canonicalName, String alias) {}
}
