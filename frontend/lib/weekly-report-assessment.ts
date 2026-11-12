import type { WeeklyReportAssessment } from "./api/types";

type Presentation = {
  label: string;
  tone: "good" | "bad" | "target" | "qualified" | "unavailable";
};

/** Wording only: eligibility and verdicts belong to the authorised report snapshot. */
export function presentWeeklyAssessment(assessment: WeeklyReportAssessment | undefined): Presentation {
  switch (assessment?.outcome) {
    case "MEETS_TARGET": return { label: "Meets target", tone: "good" };
    case "BELOW_TARGET": return { label: "Below target", tone: "bad" };
    case "ABOVE_LIMIT": return { label: "Above limit", tone: "bad" };
    case "OUTSIDE_RANGE": return { label: "Outside target range", tone: "bad" };
    case "UNAVAILABLE": return { label: "Unavailable", tone: "unavailable" };
    case "SOURCE_QUALIFIED": return { label: "Source-qualified", tone: "qualified" };
    case "TARGET": return { label: "Planning target", tone: "target" };
    case "WITHHELD": {
      const labels = {
        INCOMPLETE_EVIDENCE: "Incomplete comparison evidence",
        NO_EFFECTIVE_TARGET: "No effective target",
        BASIS_MISMATCH: "Basis differs",
        COMPARISON_UNAVAILABLE: "Comparison unavailable",
      };
      return { label: assessment.reason ? labels[assessment.reason] : "Comparison unavailable", tone: "qualified" };
    }
    // Older snapshots cannot acquire a verdict merely because a numeric total exists.
    default: return { label: "Assessment unavailable", tone: "qualified" };
  }
}
