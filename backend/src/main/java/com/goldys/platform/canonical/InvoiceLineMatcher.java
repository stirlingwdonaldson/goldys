package com.goldys.platform.canonical;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Selects the current canonical invoice line a parsed PDF line refers to.
 *
 * <p>The CTB export and its PDFs disagree on how the same line is keyed: CTB writes a synthetic
 * {@code PDF_<hex>} stock code when it has no real code, codes appear with variant suffixes ({@code
 * HAMB008} vs {@code HAMB008A1c}) or with the description embedded in the code column, and
 * descriptions carry trailing noise ({@code ... SET PRICE ... 4 KG PRICE}, {@code ... ORDER BY
 * BOX}) or a different token order. Exact matches on the raw code then the raw description — what
 * the enrichment used to do — therefore reported lines that are present in the CSV as PDF-only.
 *
 * <p>This selector tries progressively looser keys, all scoped to one invoice (the caller fetches
 * the invoice's current lines), and refuses to guess: if more than one candidate wins a tier it
 * returns {@link Result#AMBIGUOUS} — unless a single one of the tied candidates agrees with the PDF
 * line on quantity (then on amount), which is enough to break the tie without guessing. That keeps
 * "one CSV line, one PDF line" honest in both directions.
 */
public final class InvoiceLineMatcher {

  private InvoiceLineMatcher() {}

  public enum Result {
    MATCH,
    NO_MATCH,
    AMBIGUOUS
  }

  /** A current canonical line, reduced to the fields matching keys on. */
  public record Candidate(
      String stockCode, String productNameKey, BigDecimal quantity, BigDecimal amount) {

    /** Convenience constructor for callers that do not disambiguate on quantity/amount. */
    public Candidate(String stockCode, String productNameKey) {
      this(stockCode, productNameKey, null, null);
    }
  }

  /**
   * The selection outcome: {@code index} is the winning candidate index (only meaningful on MATCH).
   */
  public record Selection(Result result, int index) {
    static Selection noMatch() {
      return new Selection(Result.NO_MATCH, -1);
    }

    static Selection ambiguous() {
      return new Selection(Result.AMBIGUOUS, -1);
    }

    static Selection match(int index) {
      return new Selection(Result.MATCH, index);
    }
  }

  /**
   * Packaging/qualifier words that may differ between a CSV description and the same line on its
   * PDF without implying a different product.
   */
  private static final Set<String> NOISE =
      Set.of(
          "x", "and", "of", "for", "the", "with", "new", "order", "by", "set", "price", "ex",
          "excl", "incl", "inc", "gst", "each", "per", "kg", "kgs", "kilo", "kilos", "g", "gm",
          "gms", "gram", "grams", "l", "ltr", "ltrs", "litre", "litres", "ml", "box", "boxes",
          "pack", "packs", "pkt", "pcs", "pc", "ctn", "carton", "cartons", "btl", "btls", "bottle",
          "bottles", "can", "cans", "tin", "tins", "unit", "units", "case", "cases", "tray",
          "trays", "bag", "bags", "bunch", "bunches");

  /**
   * CTB's placeholder stock code for a line it has no real code for, e.g. {@code
   * PDF_13ACDAD38B042B}.
   */
  private static final Pattern SYNTHETIC_CODE = Pattern.compile("PDF[0-9A-F]{6,}");

  private static final int MIN_CODE_VARIANT_LENGTH = 4;
  private static final int MIN_SHARED_SIGNIFICANT_TOKENS = 2;

  public static Selection select(
      String pdfStockCode,
      String pdfDescription,
      BigDecimal pdfQuantity,
      BigDecimal pdfAmount,
      List<Candidate> candidates) {
    String pdfCode = normalizeCode(pdfStockCode);
    if (pdfCode != null) {
      Selection exact =
          unique(
              candidates,
              c -> pdfCode.equals(normalizeCode(c.stockCode())),
              pdfQuantity,
              pdfAmount);
      if (exact != null) {
        return exact;
      }
      Selection variant =
          unique(
              candidates,
              c -> isCodeVariant(pdfCode, normalizeCode(c.stockCode())),
              pdfQuantity,
              pdfAmount);
      if (variant != null) {
        return variant;
      }
    }

    String pdfDescriptionKey = ProductNameKey.normalize(pdfDescription);
    if (!pdfDescriptionKey.isEmpty()) {
      Selection exact =
          unique(
              candidates,
              c -> pdfDescriptionKey.equals(c.productNameKey()),
              pdfQuantity,
              pdfAmount);
      if (exact != null) {
        return exact;
      }
      Set<String> pdfTokens = tokenSet(pdfDescriptionKey);
      Selection contained =
          unique(
              candidates,
              c -> contains(pdfTokens, tokenSet(c.productNameKey())),
              pdfQuantity,
              pdfAmount);
      if (contained != null) {
        return contained;
      }
      Selection overlap =
          bestTokenOverlap(significant(pdfTokens), candidates, pdfQuantity, pdfAmount);
      if (overlap != null) {
        return overlap;
      }
    }
    return Selection.noMatch();
  }

  private static boolean isCodeVariant(String pdfCode, String candidateCode) {
    return candidateCode != null
        && candidateCode.length() >= MIN_CODE_VARIANT_LENGTH
        && !candidateCode.equals(pdfCode)
        && (candidateCode.startsWith(pdfCode) || pdfCode.startsWith(candidateCode));
  }

  /**
   * The single candidate a predicate selects, {@link Selection#ambiguous()} for several, else null.
   * When several candidates tie on a key, a unique agreement on quantity (then amount) breaks the
   * tie; otherwise it stays ambiguous rather than guessing.
   */
  private static Selection unique(
      List<Candidate> candidates,
      java.util.function.Predicate<Candidate> p,
      BigDecimal pdfQuantity,
      BigDecimal pdfAmount) {
    List<Integer> matches = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      if (p.test(candidates.get(i))) {
        matches.add(i);
      }
    }
    if (matches.isEmpty()) {
      return null;
    }
    if (matches.size() == 1) {
      return Selection.match(matches.get(0));
    }
    Integer resolved = disambiguate(matches, candidates, pdfQuantity, pdfAmount);
    return resolved != null ? Selection.match(resolved) : Selection.ambiguous();
  }

  /**
   * True when one token set contains the other. A single-token overlap is only accepted when the
   * extra tokens are all packaging noise, so {@code "tomatoes"} never absorbs {@code "cherry
   * tomatoes"} but {@code "corn"} still matches {@code "corn x box"}.
   */
  private static boolean contains(Set<String> a, Set<String> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return false;
    }
    Set<String> smaller = a.size() <= b.size() ? a : b;
    Set<String> larger = a.size() <= b.size() ? b : a;
    if (!larger.containsAll(smaller)) {
      return false;
    }
    if (smaller.size() >= 2) {
      return true;
    }
    Set<String> extra = new HashSet<>(larger);
    extra.removeAll(smaller);
    return NOISE.containsAll(extra);
  }

  /**
   * The candidate sharing the most significant tokens with the PDF line, when at least {@value
   * #MIN_SHARED_SIGNIFICANT_TOKENS} tokens overlap and it is the unique best (a tie is ambiguous,
   * unless quantity/amount breaks it).
   */
  private static Selection bestTokenOverlap(
      Set<String> pdfSignificant,
      List<Candidate> candidates,
      BigDecimal pdfQuantity,
      BigDecimal pdfAmount) {
    int bestScore = 0;
    List<Integer> best = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      int score = 0;
      for (String token : significant(tokenSet(candidates.get(i).productNameKey()))) {
        if (pdfSignificant.contains(token)) {
          score++;
        }
      }
      if (score < MIN_SHARED_SIGNIFICANT_TOKENS) {
        continue;
      }
      if (score > bestScore) {
        bestScore = score;
        best = new ArrayList<>(List.of(i));
      } else if (score == bestScore) {
        best.add(i);
      }
    }
    if (best.isEmpty()) {
      return null;
    }
    if (best.size() == 1) {
      return Selection.match(best.get(0));
    }
    Integer resolved = disambiguate(best, candidates, pdfQuantity, pdfAmount);
    return resolved != null ? Selection.match(resolved) : Selection.ambiguous();
  }

  /**
   * Among tied candidates, the unique one agreeing with the PDF line on quantity, else on amount;
   * null when neither field can single one out (no agreement, or more than one agrees).
   */
  private static Integer disambiguate(
      List<Integer> indices,
      List<Candidate> candidates,
      BigDecimal pdfQuantity,
      BigDecimal pdfAmount) {
    Integer byQuantity = agreeOnQuantity(indices, candidates, pdfQuantity);
    if (byQuantity != null) {
      return byQuantity;
    }
    return agreeOnAmount(indices, candidates, pdfAmount);
  }

  /** The unique tied candidate whose quantity equals {@code pdfQuantity}, else null. */
  private static Integer agreeOnQuantity(
      List<Integer> indices, List<Candidate> candidates, BigDecimal pdfQuantity) {
    if (pdfQuantity == null) {
      return null;
    }
    Integer found = null;
    for (int i : indices) {
      if (sameAmount(candidates.get(i).quantity(), pdfQuantity)) {
        if (found != null) {
          return null;
        }
        found = i;
      }
    }
    return found;
  }

  /** The unique tied candidate whose amount equals {@code pdfAmount}, else null. */
  private static Integer agreeOnAmount(
      List<Integer> indices, List<Candidate> candidates, BigDecimal pdfAmount) {
    if (pdfAmount == null) {
      return null;
    }
    Integer found = null;
    for (int i : indices) {
      if (sameAmount(candidates.get(i).amount(), pdfAmount)) {
        if (found != null) {
          return null;
        }
        found = i;
      }
    }
    return found;
  }

  private static boolean sameAmount(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return false;
    }
    return a.compareTo(b) == 0;
  }

  private static Set<String> tokenSet(String normalizedDescription) {
    if (normalizedDescription == null || normalizedDescription.isBlank()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (String token : normalizedDescription.split("\\s+")) {
      if (!token.isEmpty()) {
        out.add(token);
      }
    }
    return out;
  }

  private static Set<String> significant(Set<String> tokens) {
    Set<String> out = new HashSet<>();
    for (String token : tokens) {
      if (token.length() >= 4 && !NOISE.contains(token)) {
        out.add(token);
      }
    }
    return out;
  }

  /** Upper-cased alphanumerics, or null for blank input or a CTB {@code PDF_<hex>} placeholder. */
  private static String normalizeCode(String code) {
    if (code == null) {
      return null;
    }
    String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    if (normalized.isEmpty() || SYNTHETIC_CODE.matcher(normalized).matches()) {
      return null;
    }
    return normalized;
  }
}
