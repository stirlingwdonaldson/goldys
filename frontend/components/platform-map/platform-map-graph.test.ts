import { describe, expect, it } from "vitest";
import type { NetworkGraphDto, NetworkNodeDto } from "@/lib/api/types";
import { demoPlatformMap } from "@/lib/api/demo-platform-map";
import { buildPlatformMap, impactSummary } from "./platform-map-graph";

const n = (id: string, kind: string, tone = "neutral"): NetworkNodeDto => ({
  id,
  kind,
  label: id,
  subtitle: null,
  tone,
  size: 3,
  group: kind,
  href: null,
});
const e = (source: string, target: string) => ({ source, target, weight: 1, label: null });

// Two sources → one domain each → metrics → a derived metric → a dashboard.
const dto: NetworkGraphDto = {
  nodes: [
    n("source:A", "source", "ok"),
    n("source:B", "source", "warn"),
    n("domain:a", "domain"),
    n("domain:b", "domain"),
    n("metric:a", "metric"),
    n("metric:b", "metric"),
    n("metric:orphan", "metric"),
    n("metric:ratio", "derived-metric"),
    n("dashboard:d", "dashboard"),
    n("permission:p", "permission"),
  ],
  edges: [
    e("source:A", "domain:a"),
    e("source:B", "domain:b"),
    e("domain:a", "metric:a"),
    e("domain:b", "metric:b"),
    e("metric:ratio", "metric:a"),
    e("metric:ratio", "metric:b"),
    e("dashboard:d", "metric:ratio"),
    e("metric:a", "permission:p"),
  ],
};

const toneOf = (built: ReturnType<typeof buildPlatformMap>, id: string) =>
  built.graph.nodes.find((node) => node.id === id)?.tone;

describe("buildPlatformMap", () => {
  it("passes a source's tone to everything downstream of it", () => {
    const built = buildPlatformMap(dto);
    expect(toneOf(built, "domain:a")).toBe("ok");
    expect(toneOf(built, "metric:a")).toBe("ok");
    expect(toneOf(built, "domain:b")).toBe("warn");
    expect(toneOf(built, "metric:b")).toBe("warn");
  });

  it("gives a derived metric and its dashboards the worst of their inputs", () => {
    const built = buildPlatformMap(dto);
    expect(toneOf(built, "metric:ratio")).toBe("warn");
    expect(toneOf(built, "dashboard:d")).toBe("warn");
  });

  it("orders tones fail > missing > warn", () => {
    const failing = {
      ...dto,
      nodes: dto.nodes.map((node) =>
        node.id === "source:A" ? { ...node, tone: "fail" } : node.id === "source:B" ? { ...node, tone: "missing" } : node,
      ),
    };
    const built = buildPlatformMap(failing);
    expect(toneOf(built, "metric:ratio")).toBe("fail");
    expect(toneOf(built, "metric:b")).toBe("missing");
  });

  it("leaves nodes with no upstream source, and permissions, neutral", () => {
    const built = buildPlatformMap(dto);
    expect(toneOf(built, "metric:orphan")).toBe("neutral");
    expect(toneOf(built, "permission:p")).toBe("neutral");
  });

  it("lists every downstream node once as a source's impact", () => {
    const built = buildPlatformMap(dto);
    expect([...(built.impact.get("source:A") ?? [])].sort()).toEqual(
      ["dashboard:d", "domain:a", "metric:a", "metric:ratio"].sort(),
    );
    expect(built.impact.get("metric:orphan")).toEqual([]);
  });

  it("drops nodes of unknown kind and edges that point at them, and maps unknown tones to neutral", () => {
    const odd: NetworkGraphDto = {
      nodes: [n("source:A", "source", "purple"), n("x:1", "spaceship")],
      edges: [e("source:A", "x:1")],
    };
    const built = buildPlatformMap(odd);
    expect(built.graph.nodes.map((node) => node.id)).toEqual(["source:A"]);
    expect(built.graph.edges).toEqual([]);
    expect(toneOf(built, "source:A")).toBe("neutral");
  });

  it("keeps the API's sizes, subtitles and links", () => {
    const built = buildPlatformMap(demoPlatformMap());
    const ctb = built.graph.nodes.find((node) => node.id === "source:CTB")!;
    expect(ctb).toMatchObject({ size: 6, subtitle: "Last run failed", href: "/logs" });
  });
});

describe("impactSummary", () => {
  it("counts affected nodes by kind in words", () => {
    const built = buildPlatformMap(demoPlatformMap());
    const ctb = built.graph.nodes.find((node) => node.id === "source:CTB")!;
    expect(impactSummary(ctb, built)).toBe(
      "Cooking the Books is failing → 3 domains, 5 metrics, 3 derived metrics, 2 dashboards affected",
    );
  });

  it("says nothing for a healthy node", () => {
    const built = buildPlatformMap(demoPlatformMap());
    const lightspeed = built.graph.nodes.find((node) => node.id === "source:LIGHTSPEED")!;
    expect(impactSummary(lightspeed, built)).toBeNull();
  });
});
