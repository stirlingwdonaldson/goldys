"use client";

import { useId, useState, type ReactNode } from "react";
import { Plus, X } from "lucide-react";
import { humanizeKey } from "@/lib/format";
import type {
  QueryAggregate,
  QueryField,
  QueryFilterNode,
  QueryFilterOp,
  QueryGroupByItem,
  QueryOrderByItem,
  QueryPredicate,
  QuerySelectItem,
} from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const AGGREGATES: Record<QueryAggregate, string> = {
  SUM: "Sum — add values",
  AVG: "Average — mean value",
  MIN: "Minimum — lowest value",
  MAX: "Maximum — highest value",
  COUNT: "Count — non-empty values",
  COUNT_DISTINCT: "Unique count — different values",
};
const OPS: Record<QueryFilterOp, string> = {
  EQ: "Equals",
  NE: "Does not equal",
  GT: "Greater than",
  GTE: "At least",
  LT: "Less than",
  LTE: "At most",
  BETWEEN: "Between (inclusive)",
  IN: "Is one of",
  IS_NULL: "Is empty",
};

/** Repeated query controls share a visible label and associated help text. */
export function QuerySelect({
  label,
  description,
  value,
  onChange,
  placeholder = "Choose…",
  disabled,
  children,
}: {
  label: string;
  description?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  children: ReactNode;
}) {
  const id = useId();
  return (
    <div className="min-w-0 space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value} onValueChange={onChange} disabled={disabled}>
        <SelectTrigger
          id={id}
          aria-describedby={description ? `${id}-help` : undefined}
        >
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>{children}</SelectContent>
      </Select>
      {description ? (
        <p id={`${id}-help`} className="text-xs text-muted-foreground">
          {description}
        </p>
      ) : null}
    </div>
  );
}

export function FieldSelector({
  fields,
  select,
  onChange,
}: {
  fields: QueryField[];
  select: QuerySelectItem[];
  onChange: (select: QuerySelectItem[]) => void;
}) {
  return (
    <div className="grid gap-2 lg:grid-cols-2">
      {fields.map((field) => {
        const selected = select.find((item) => item.field === field.name);
        const numeric = field.type === "DECIMAL" || field.type === "INTEGER";
        return (
          <div
            key={field.name}
            className={`min-w-0 rounded-lg border p-3 ${selected ? "border-primary/30 bg-muted/30" : ""}`}
          >
            <label className="flex cursor-pointer items-start gap-3">
              <Checkbox
                aria-label={`Select ${field.name}`}
                checked={!!selected}
                onCheckedChange={(checked) =>
                  onChange(
                    checked
                      ? [...select, { field: field.name, aggregate: null }]
                      : select.filter((item) => item.field !== field.name),
                  )
                }
              />
              <span className="min-w-0 text-sm font-medium">
                <span className="break-words">{humanizeKey(field.name)}</span>
                <span className="mt-1 block text-xs font-normal text-muted-foreground">
                  {humanizeKey(field.type)}
                  {field.unit ? ` · ${field.unit}` : ""} ·{" "}
                  {field.nullable ? "May be empty" : "Required by schema"}
                </span>
              </span>
            </label>
            {selected ? (
              <div className="mt-3 pl-7">
                <QuerySelect
                  label={`Calculation for ${humanizeKey(field.name)}`}
                  value={selected.aggregate ?? "NONE"}
                  onChange={(aggregate) =>
                    onChange(
                      select.map((item) =>
                        item.field === field.name
                          ? {
                              ...item,
                              aggregate:
                                aggregate === "NONE"
                                  ? null
                                  : (aggregate as QueryAggregate),
                              distinct: false,
                            }
                          : item,
                      ),
                    )
                  }
                >
                  <SelectItem value="NONE">
                    Original value — no summary
                  </SelectItem>
                  {Object.entries(AGGREGATES)
                    .filter(
                      ([aggregate]) =>
                        numeric || (aggregate !== "SUM" && aggregate !== "AVG"),
                    )
                    .map(([aggregate, label]) => (
                      <SelectItem key={aggregate} value={aggregate}>
                        {label}
                      </SelectItem>
                    ))}
                </QuerySelect>
                {selected.distinct ? (
                  <p className="mt-1 text-xs text-muted-foreground">
                    Saved calculation uses distinct values. Choosing a
                    calculation resets this.
                  </p>
                ) : null}
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

/** Preserve saved OR/NOT trees rather than silently flattening their meaning. */
export function simplePredicates(
  filter: QueryFilterNode | null | undefined,
): QueryPredicate[] | null {
  if (!filter) return [];
  if (filter.predicate && !filter.logic) return [filter.predicate];
  if (
    filter.logic === "AND" &&
    filter.children?.every((child) => child.predicate && !child.logic)
  )
    return filter.children.map((child) => child.predicate!);
  return null;
}

export function FilterBuilder({
  fields,
  filter,
  onChange,
}: {
  fields: QueryField[];
  filter: QueryFilterNode | null | undefined;
  onChange: (filter: QueryFilterNode | null) => void;
}) {
  const [field, setField] = useState("");
  const [op, setOp] = useState<QueryFilterOp>("EQ");
  const [value, setValue] = useState("");
  const [value2, setValue2] = useState("");
  const predicates = simplePredicates(filter);
  const selected = fields.find((item) => item.name === field);
  const boolean = selected?.type === "BOOLEAN";
  const inputType =
    selected?.type === "DATE"
      ? "date"
      : selected?.type === "TIMESTAMP"
        ? "datetime-local"
        : selected?.type === "DECIMAL" || selected?.type === "INTEGER"
          ? "number"
          : "text";
  const operators: QueryFilterOp[] = boolean
    ? ["EQ", "NE", "IS_NULL"]
    : (Object.keys(OPS) as QueryFilterOp[]);
  const values =
    op === "BETWEEN"
      ? [value, value2]
      : op === "IN"
        ? value.split(",").map((item) => item.trim())
        : [value];
  const validTimestamp =
    selected?.type !== "TIMESTAMP" ||
    values.every(
      (item) => item.trim() !== "" && Number.isFinite(new Date(item).getTime()),
    );
  const ready =
    !!selected &&
    (op === "IS_NULL" ||
      (validTimestamp &&
        value.trim() !== "" &&
        (op !== "BETWEEN" || value2.trim() !== "")));

  function update(items: QueryPredicate[]) {
    onChange(
      items.length
        ? { logic: "AND", children: items.map((predicate) => ({ predicate })) }
        : null,
    );
  }
  function add() {
    if (!ready || !predicates) return;
    let typedValue: QueryPredicate["value"] = value;
    if (op === "BETWEEN") typedValue = [value, value2];
    if (op === "IN") typedValue = value.split(",").map((item) => item.trim());
    if (selected?.type === "TIMESTAMP" && op !== "IS_NULL") {
      const instants = values.map((item) => new Date(item).toISOString());
      typedValue = op === "BETWEEN" || op === "IN" ? instants : instants[0];
    }
    if (boolean) typedValue = value === "true";
    if (op === "IS_NULL") typedValue = null;
    update([...predicates, { field, op, value: typedValue }]);
    setValue("");
    setValue2("");
  }

  if (!predicates)
    return (
      <div className="space-y-2 text-sm">
        <p>
          This saved query uses advanced filter groups. They are preserved when
          running or saving. Clear them to build new AND filters here.
        </p>
        <Button variant="outline" onClick={() => onChange(null)}>
          Clear filters
        </Button>
      </div>
    );

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {predicates.map((predicate, index) => (
          <div
            key={index}
            className="flex max-w-full items-center gap-2 rounded-md border bg-muted/30 px-3 py-1 text-sm"
          >
            <span className="break-all">
              {humanizeKey(predicate.field)} {OPS[predicate.op].toLowerCase()}{" "}
              {predicate.op === "IS_NULL"
                ? ""
                : JSON.stringify(predicate.value)}
            </span>
            <Button
              variant="ghost"
              size="icon"
              className="size-7 shrink-0"
              aria-label={`Remove filter ${index + 1}`}
              onClick={() => update(predicates.filter((_, i) => i !== index))}
            >
              <X className="size-3.5" />
            </Button>
          </div>
        ))}
      </div>
      <div className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <QuerySelect
          label="Filter field"
          value={field}
          onChange={(value) => {
            setField(value);
            setOp("EQ");
            setValue("");
            setValue2("");
          }}
        >
          {fields.map((field) => (
            <SelectItem key={field.name} value={field.name}>
              {humanizeKey(field.name)}
            </SelectItem>
          ))}
        </QuerySelect>
        <QuerySelect
          label="Condition"
          description="How the field must match your value."
          value={op}
          onChange={(value) => {
            setOp(value as QueryFilterOp);
            setValue("");
            setValue2("");
          }}
        >
          {operators.map((op) => (
            <SelectItem key={op} value={op}>
              {OPS[op]}
            </SelectItem>
          ))}
        </QuerySelect>
        {op !== "IS_NULL" ? (
          boolean ? (
            <QuerySelect label="Filter value" value={value} onChange={setValue}>
              <SelectItem value="true">True</SelectItem>
              <SelectItem value="false">False</SelectItem>
            </QuerySelect>
          ) : (
            <label className="space-y-1.5 text-sm font-medium">
              <span>{op === "BETWEEN" ? "From (inclusive)" : "Value"}</span>
              <Input
                aria-label="Filter value"
                type={op === "IN" ? "text" : inputType}
                step="any"
                placeholder={
                  op === "IN" ? "Value one, value two" : "Enter a value"
                }
                value={value}
                onChange={(event) => setValue(event.target.value)}
              />
              {op === "IN" ? (
                <span className="block text-xs font-normal text-muted-foreground">
                  Separate values with commas.
                </span>
              ) : null}
            </label>
          )
        ) : (
          <p className="pt-7 text-xs text-muted-foreground">
            Matches missing values. No value needed.
          </p>
        )}
        {op === "BETWEEN" ? (
          <label className="space-y-1.5 text-sm font-medium">
            To (inclusive)
            <Input
              aria-label="Filter value 2"
              type={inputType}
              step="any"
              value={value2}
              onChange={(event) => setValue2(event.target.value)}
            />
          </label>
        ) : null}
      </div>
      {selected?.type === "TIMESTAMP" && op !== "IS_NULL" ? (
        <p className="text-xs text-muted-foreground">
          Times use your browser’s local timezone unless an offset is supplied.
        </p>
      ) : null}
      <Button variant="outline" size="sm" onClick={add} disabled={!ready}>
        <Plus className="mr-1.5 size-4" />
        Add filter
      </Button>
    </div>
  );
}

export function GroupByPicker({
  fields,
  groupBy,
  onChange,
}: {
  fields: QueryField[];
  groupBy: QueryGroupByItem[];
  onChange: (items: QueryGroupByItem[]) => void;
}) {
  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">
        Combine rows with the same values into one summary. Choose a calculation
        on a column first; every original-value column must be grouped.
      </p>
      {fields.map((field) => {
        const group = groupBy.find((item) => item.field === field.name);
        return (
          <div key={field.name} className="space-y-2">
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <Checkbox
                aria-label={`Group by ${field.name}`}
                checked={!!group}
                onCheckedChange={(checked) =>
                  onChange(
                    checked
                      ? [
                          ...groupBy,
                          {
                            field: field.name,
                            grain: field.type === "DATE" ? "DAY" : null,
                          },
                        ]
                      : groupBy.filter((item) => item.field !== field.name),
                  )
                }
              />
              {humanizeKey(field.name)}
            </label>
            {group && field.type === "DATE" ? (
              <QuerySelect
                label={`Date grouping for ${humanizeKey(field.name)}`}
                description="Day keeps dates separate; month combines dates in the same calendar month."
                value={group.grain ?? "DAY"}
                onChange={(grain) =>
                  onChange(
                    groupBy.map((item) =>
                      item.field === field.name
                        ? { ...item, grain: grain as "DAY" | "MONTH" }
                        : item,
                    ),
                  )
                }
              >
                <SelectItem value="DAY">Day</SelectItem>
                <SelectItem value="MONTH">Month</SelectItem>
              </QuerySelect>
            ) : null}
          </div>
        );
      })}
      {!fields.length ? (
        <p className="text-sm text-muted-foreground">
          No grouping fields available.
        </p>
      ) : null}
    </div>
  );
}

export function OrderByPicker({
  fields,
  orderBy,
  onChange,
}: {
  fields: QueryField[];
  orderBy: QueryOrderByItem[];
  onChange: (items: QueryOrderByItem[]) => void;
}) {
  const [field, setField] = useState("");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");
  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">
        Sort selected columns before applying the row limit. Ascending puts
        smallest or earliest first; descending puts largest or latest first.
      </p>
      {orderBy.map((item, index) => (
        <div
          key={item.field}
          className="flex items-center justify-between gap-2 text-sm"
        >
          <span>
            {humanizeKey(item.field)} ·{" "}
            {item.direction === "ASC" ? "Ascending" : "Descending"}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            aria-label={`Remove sort ${index + 1}`}
            onClick={() => onChange(orderBy.filter((_, i) => i !== index))}
          >
            <X className="size-3.5" />
          </Button>
        </div>
      ))}
      <QuerySelect
        label="Sort field"
        value={fields.some((item) => item.name === field) ? field : ""}
        onChange={setField}
      >
        {fields
          .filter((field) => !orderBy.some((item) => item.field === field.name))
          .map((field) => (
            <SelectItem key={field.name} value={field.name}>
              {humanizeKey(field.name)}
            </SelectItem>
          ))}
      </QuerySelect>
      <QuerySelect
        label="Sort direction"
        value={direction}
        onChange={(value) => setDirection(value as "ASC" | "DESC")}
      >
        <SelectItem value="DESC">Descending</SelectItem>
        <SelectItem value="ASC">Ascending</SelectItem>
      </QuerySelect>
      <Button
        variant="outline"
        size="sm"
        disabled={
          !fields.some((item) => item.name === field) ||
          orderBy.some((item) => item.field === field)
        }
        onClick={() => {
          onChange([...orderBy, { field, direction }]);
          setField("");
        }}
      >
        <Plus className="mr-1.5 size-4" />
        Add sort
      </Button>
    </div>
  );
}
