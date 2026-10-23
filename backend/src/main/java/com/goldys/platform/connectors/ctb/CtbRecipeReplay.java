package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.RecipeDefinitionIngest;
import com.goldys.platform.ingestion.RawLedgerQuery;
import com.goldys.platform.ingestion.RawLedgerQuery.RawRecordRef;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Replays complete capture groups, never combining independently fetched versions of a recipe. */
@Component
@Order(-100)
@ConditionalOnProperty(name = "ctb.recipes.replay", havingValue = "true")
public class CtbRecipeReplay implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CtbRecipeReplay.class);
  private static final Pattern RECIPE =
      Pattern.compile("recipe:(\\d+):capture:([a-f0-9-]+):http:(2\\d\\d)");
  private static final Pattern CATALOG =
      Pattern.compile("catalog:([a-f0-9-]+):folder:(\\d+):http:(2\\d\\d)");
  private final RawLedgerQuery raw;
  private final RecipeDefinitionIngest ingest;
  private final CtbRecipeDefinitionParser parser;

  public CtbRecipeReplay(
      RawLedgerQuery raw, RecipeDefinitionIngest ingest, CtbRecipeDefinitionParser parser) {
    this.raw = raw;
    this.ingest = ingest;
    this.parser = parser;
  }

  @Override
  public void run(ApplicationArguments args) {
    int completed = replayRecipes();
    int catalogs = replayCatalogs();
    log.info(
        "Replayed {} complete recipe captures and {} catalogs; incomplete captures retained as raw only",
        completed,
        catalogs);
  }

  int replayRecipes() {
    Map<String, Map<String, RawRecordRef>> groups = new LinkedHashMap<>();
    for (String fetcher :
        List.of(CtbRecipeRefresh.HEADER, CtbRecipeRefresh.STOCKS, CtbRecipeRefresh.USAGES)) {
      for (RawRecordRef ref : raw.rawRecordRefsForFetcher("CTB", fetcher)) {
        var matcher = RECIPE.matcher(Objects.toString(ref.sourceRecordRef(), ""));
        if (matcher.matches())
          groups
              .computeIfAbsent(matcher.group(1) + ":" + matcher.group(2), k -> new HashMap<>())
              .put(fetcher, ref);
      }
    }
    int completed = 0;
    for (Map<String, RawRecordRef> group : groups.values()) {
      if (group.size() != 3) {
        log.warn("Incomplete raw recipe capture skipped");
        continue;
      }
      RawRecordRef h = group.get(CtbRecipeRefresh.HEADER),
          s = group.get(CtbRecipeRefresh.STOCKS),
          u = group.get(CtbRecipeRefresh.USAGES);
      int id = Integer.parseInt(h.sourceRecordRef().split(":")[1]);
      ingest.record(
          parser.parse(
              id,
              raw.payloadBytes(h.id()),
              raw.payloadBytes(s.id()),
              raw.payloadBytes(u.id()),
              h.fetchedAt()),
          h.id(),
          s.id(),
          u.id());
      completed++;
    }
    return completed;
  }

  private int replayCatalogs() {
    Map<String, List<RawRecordRef>> groups = new LinkedHashMap<>();
    for (RawRecordRef ref : raw.rawRecordRefsForFetcher("CTB", CtbRecipeRefresh.FOLDERS)) {
      var matcher = CATALOG.matcher(Objects.toString(ref.sourceRecordRef(), ""));
      if (matcher.matches())
        groups.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(ref);
    }
    int completed = 0;
    for (List<RawRecordRef> group : groups.values()) {
      CtbRecipeCatalogParser tree = new CtbRecipeCatalogParser();
      for (RawRecordRef ref : group) {
        int folder = Integer.parseInt(ref.sourceRecordRef().split(":")[3]);
        tree.accept(folder, raw.payloadBytes(ref.id()));
      }
      if (!tree.pending().isEmpty()) {
        log.warn("Incomplete raw recipe catalog skipped");
        continue;
      }
      ingest.recordCatalog(
          tree.finish(group.getFirst().fetchedAt()), group.stream().map(RawRecordRef::id).toList());
      completed++;
    }
    return completed;
  }
}
