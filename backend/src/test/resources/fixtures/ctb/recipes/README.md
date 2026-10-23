# Real CTB recipe responses

The original JSON fixtures were extracted from the three user-provided HAR captures on 2026-10-23. Response bodies
only: cookies, request headers, tokens, approval/user details, creator names and
irrelevant UI tree properties are excluded. Missing optional fields remain missing.
These are captured source values, including negative quantities and questionable
units, not manufactured discovery data. Folder captures cover only visited branches;
they deliberately cannot be called a complete catalog. Manifest timestamps are the
original request times; replay uses raw-ledger capture times instead.

`optional-survey.sanitised.har.gz` is the later user-supplied 670-recipe survey,
compressed to preserve exact sanitised response text without a 12 MB fixture diff.
It includes 3372 responses covering 3350 unique recipe/endpoint pairs on the five
fully surveyed endpoints, plus sampled recipe facts and lookup responses. Its
business creator names and rich-text method fields are retained to verify display;
it contains no login requests, cookie records or authentication headers. The
parser excludes approval/account objects and permission flags from business display.
This is sanitised discovery/test evidence, not an original production raw record.
