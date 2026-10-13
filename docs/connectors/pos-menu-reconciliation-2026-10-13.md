# POS menu reconciliation — 2026-10-13

Reconciled the POS "currently sold" export (655 products) against the production DB's
product records. This is the seed data for the automated availability determination
(see the accompanying design spec).

## Still active — renamed / rebranded (NOT retired)

| DB name | Actually in POS as |
|---|---|
| `Eggplant Dip` | `Baba Ganoush` |
| `Corn Estiques` | `Charred Corn Salad` |
| `G - Havilah Gewurztraminer` | `B - Havilah Gewurztraminer` (bottle) |
| `G - Spinifex Rose 2025` | `B - Spinifex Rose 2025` |
| `G - Deepwoods Estate Chardonnay` | `G/B - Deep Woods Estate Chardonnay` (spacing) |
| `G - Lighthand, Pinot Noir` | `B - Lighthand, Pinot Noir` |
| `Kiddies slider` | `Kids slider` |
| `Mustard Gravy` | `Mustard Gravy - Side` |
| `Turkish bread` | `Half Turkish bread - side` |
| `Aperol / Campari / Limoncello / Montenegro Spritz (NV Continental)` | `… (NV Three Pillars)` (base-wine rebrand) |
| `NV Continental Platter Prosecco` (B & G) | `NV Three Pillars Sparkling Brut Cuvee` |
| `Jamsheed Mataro (Chilled)` (.5L & G) | `Jamsheed Mataro (Tap)` |
| `Gnocchi - Pumpkin` | `Gnocchi - Pumpkin Butter Sage` |
| `Katsaros Brothers Ouzo - 30ml` | `Ouzo - 30ml` (name shortened) |
| `Ruben Croquettes` / `+ 1 Ruben Croquette` | `F - Ruben Croquette` / `+1 Croquette` |
| `Kids Gnocchi` | `Kids Gnocchi Bolognese` / `Kids Gnocchi Napoli` |

## Still active — size/format differs only

`Keeper Pilsner`, `Goldy's 'An Old One' … Shandy`, `Philter XPA`, `Loveshack Mid Red-Ale`
(each `- Jug/Pint/Pot` ↔ `Jug/Pint/Pot - …`), `Bridge Road Brewers 'Beechworth' Pale Ale`
(`Can -`), `Dolin Dry Vermouth - 45ml` (`- 30ml`), `G - Denton Shed Chardonnay`
(`B - … (2023)`).

## Retired — confirmed, with last receipt date

| Item | Last sold | Receipts |
|---|---|---|
| `Coke Zero - Mixer` | 2026-09-19 | 1,236 |
| `B - Uva Non Grata, Gamay (2020)` | 2026-09-26 | 109 |
| `Nonna's Spritz` | 2026-09-26 | 260 |
| `B - Domaine Gandines Macon-Burgy Gamay Noir` | 2026-09-25 | 13 |
| `B - Juxtaposed 'Old Vine' Shiraz` | 2026-09-25 | 9 |
| `B/G - Luke Lambert 'Crudo' Shiraz` | 2026-09-24 | 31 |
| `B - Minim 'Simone' Cabernet Rosé` | 2026-09-13 | 12 |
| `B - Ravensworth 'Regional' GSM` | 2026-09-05 | 10 |
| `B/G - Monsalto 'La Commenda' Chianti DOCG (2021)` | 2026-08-29 | 95 |
| `B/G - Jamsheed Apricity Mataro` | 2026-08-29 | 12 |
| `Ravensworth GSM` | 2026-08-06 | 133 |
| `Satay Schnitty` | 2026-07-10 | 373 |
| `Vegan Satay Schnitty` | 2026-07-06 | 38 |
| `$20 Eggplant Satay / Satay Schnitty` | 2026-06-30 | 13 / 80 |
| `B/G - Hedonist Light Red` | 2026-06-19 | 12 |
| `B - Minim 'Hitch' Sangiovese` | 2026-06-19 | 24 |
| `B/G - Côtier, Pinot Gris` | 2026-05-22 | 10 |
| `B - Express Winemakers 'Drinking Wine Red'` | 2026-05-21 | 14 |
| `Falafel` | 2026-05-19 | 570 |
| `B - Express Winemakers 'L'Orange'` | 2026-05-18 | 6 |
| `Goldy's! Fried Chicken` / `Fish Crudo` | 2026-05-13 | 1,210 / 317 |
| `Whipped Hummus` | 2026-05-13 | 759 |
| `Pannacotta` | 2026-05-12 | 7 |
| `Oysters 1/2 Doz` | 2026-05-11 | 192 |
| `B - Ravensworth Sangiovese` | 2026-05-10 | 6 |
| `$20 Eggplant Shroomy / Shroomy Schnitty` | 2026-05-05 | 23 / 73 |
| `Shroomy / Eggplant Shroomy Schnitty` | 2026-05-04 | 257 / 27 |
| `B - Two Tonne Chardonnay` | 2026-05-02 | 15 |
| `Vegan Roast (May 2026)` / `Sunday Roast (May 2026)` | 2026-05-31 | 27 / 277 |
| `Sunday Roast (April 2026)` / `Vegan Roast (April 2026)` | 2026-04-26 | 224 / 23 |
| `Kids Roast (April 2026)` | 2026-04-26 | 8 |
| `Greenhouse Martini` | 2026-04-18 | 68 |
| `Jug/Pint/Pot - NZ IPA` | 2026-04-09 | 9 |
| `Chicken Spice Bag / Eggplant Spice Bag Schnitty` | 2026-04-06 | 608 / 74 |
| `Hot Cross Bun - Easter` | 2026-04-05 | 11 |
| `Grilled Barramundi - Easter` | 2026-04-03 | 12 |
| `Altina Sparkling Sansgria` | 2026-04-02 | 224 |
| `WOTW XPA` (Jug/Pint/Pot) | 2026-03-22 | 558 |
| `F - Chicken karaage` | 2024-06-05 | 1,321 |
| `Pot - Love Shack Red Ale` | 2024-02-16 | 2 |

("never sold in the ledger" for a few recipe-link-only names omitted; those are CTB links
with no matching receipt line, i.e. retired before/without sales.)
