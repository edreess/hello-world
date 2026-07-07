# Offset Imposition Planner — Architecture

Odoo 19 Community Edition module for imposition planning of offset printing
jobs. This document is the design proposal the implementation follows.

## 1. Scope

The module covers imposition **planning** only:

1. PDF import and page analysis (page count, trim size, orientation).
2. Offset press configuration (physical constraints).
3. Automatic signature generation + a visual, interactive plate editor (OWL).
4. Low-resolution page thumbnails rendered onto the plate preview.
5. A data model aligned with CIP4 **XJDF 2.x** prepress/imposition resources.
6. A clean, relational output surface for future quotation / sales / MRP
   modules (no costing or manufacturing logic lives here).

## 2. Models

All models live in the `offset_imposition` addon.

```
press.config            1 ──< imposition.job >── 1  imposition.template
                                   │ 1
                     ┌─────────────┴─────────────┐
                     │ *                         │ *
              imposition.page             imposition.placement
              (one per PDF page,          (one per placed page per
               holds thumbnail)            signature/surface; x, y,
                     ▲                     rotation on the plate)
                     └───────── page_id ──────────┘
```

### 2.1 `press.config` — offset press definition

Physical constraints of one press. XJDF analogue: `Device` plus the plate
`Media` resource.

| Field | Type | XJDF mapping |
|---|---|---|
| `name`, `code`, `manufacturer` | Char | `Device/@DescriptiveName`, `@DeviceID` |
| `plate_width`, `plate_height` (mm) | Float | `Media[@MediaType="Plate"]/@Dimension` |
| `max_sheet_width/height`, `min_sheet_width/height` (mm) | Float | `Media[@MediaType="Paper"]/@Dimension` bounds |
| `num_units` | Integer | number of printing units (colors per pass) |
| `gripper_margin` (mm) | Float | JDF/XJDF `Device/@NonPrintableMarginBottom` (gripper edge) |
| `side_lay_margin`, `tail_margin` (mm) | Float | `NonPrintableMarginLeft/Right/Top` |
| `plate_bend_margin` (mm) | Float | plate clamp/bend allowance at gripper edge |
| `is_perfecting` | Boolean | press can print both sides in one pass |

### 2.2 `imposition.template` — signature pattern

A reusable imposition scheme. XJDF analogue: `BinderySignature` (+ default
`StripCellParams`).

| Field | Type | XJDF mapping |
|---|---|---|
| `name`, `code` | Char | `BinderySignature/@DescriptiveName` |
| `fold_catalog` | Char | `BinderySignature/@FoldCatalog` (e.g. `F4-1`, `F8-7`, `F16-6`) |
| `rows`, `cols` | Integer | `BinderySignature/@NumberUp` = "`cols rows`" |
| `work_style` | Selection | `Layout/@WorkStyle`: `Simplex`, `WorkAndBack` (sheetwise), `Perfecting`, `WorkAndTurn`, `WorkAndTumble` |
| `bleed_default` (mm) | Float | `StripCellParams/@BleedFace/Foot/Head/Spine` (uniform default) |
| `gutter_x`, `gutter_y` (mm) | Float | `StripCellParams/@TrimFace` / `@TrimFoot` (cut lane widths) |
| `allow_rotation` | Boolean | generator may rotate pages 90° to fit |

`pages_per_signature` is computed from `rows × cols` and the work style
(sheetwise carries `2 × rows × cols` distinct pages per signature;
work-and-turn/tumble carry `rows × cols`).

### 2.3 `imposition.job` — the planning record

One imposition plan for one print job. XJDF analogue: the `XJDF` job ticket
root with its `Layout` and `RunList` resource sets. Uses `mail.thread`
chatter. Workflow: `draft → parsed → planned → locked`.

Inputs: `pdf_file` (Binary attachment), `quantity` (run length),
`press_id`, `template_id`, `sheet_width/height` (defaulted from press),
`bleed`, `gutter_x/y`, `colors_front`, `colors_back`, `overage_percent`.

Parsed from PDF (`action_parse_pdf`, PyMuPDF): `page_count`,
`trim_width`, `trim_height` (mm, from TrimBox falling back to MediaBox),
`orientation`.

Computed planning outputs (stored, for downstream modules):

| Field | Meaning | XJDF mapping |
|---|---|---|
| `n_up` | pages up per plate surface | `BinderySignature/@NumberUp` |
| `signature_count` | number of distinct signatures/plate sets | count of `Layout/Surface` pairs |
| `press_passes` | passes through the press per signature | derived from `@WorkStyle` |
| `sheets_per_signature`, `sheets_needed` | net + gross (with overage) paper | `Component/@Amount` on the sheet resource |
| `plates_needed` | total plates over all signatures & separations | `ExposedMedia[@MediaType="Plate"]` amount |
| `layout_json` | non-stored compute/inverse Text feeding the OWL editor | serialization of `Layout` |

Public API for future quotation/sale/MRP modules:
`get_production_data()` returns a plain dict (press, sheet size, n-up,
signatures, sheets, plates, passes, work style) so consumers never touch the
PDF or the widget state. All numeric outputs are also plain stored fields
reachable through standard ORM relations.

### 2.4 `imposition.page` — one PDF page

XJDF analogue: one logical page of the content `RunList`.

| Field | Type | XJDF mapping |
|---|---|---|
| `page_number` | Integer (1-based) | `RunList` page index (`Ord` refers to this) |
| `width_mm`, `height_mm` | Float | page `TrimBox` |
| `thumbnail` | Image (PNG, ~200 px) | low-res proxy of `FileSpec` content |

### 2.5 `imposition.placement` — one placed page on a surface

The persistent source of truth for the layout; the editor JSON is derived
from these rows. XJDF analogue: `Layout/Surface/ContentObject`.

| Field | Type | XJDF mapping |
|---|---|---|
| `signature` | Integer | index of the `Layout` signature |
| `surface` | Selection front/back | `Surface/@Side` = `Front`/`Back` |
| `ord` | Integer | `ContentObject/@Ord` (0-based index into RunList) |
| `page_id` | M2O `imposition.page` | resolved `Ord` target (empty = blank cell) |
| `pos_x`, `pos_y` (mm, plate origin bottom-left) | Float | translation part of `ContentObject/@CTM` |
| `rotation` | Selection 0/90/180/270 | rotation part of `@CTM` |
| `width_mm`, `height_mm` | Float | `ContentObject/@ClipBox` extents |

## 3. Views & menus

- Root menu **Imposition**: Jobs / Configuration (Presses, Templates).
- `press.config`, `imposition.template`: list + form.
- `imposition.job` form: statusbar workflow buttons (*Parse PDF*,
  *Generate Imposition*, *Lock*, *Reset*), input groups, results group,
  notebook tabs: **Plate Layout** (OWL widget on `layout_json`),
  **Pages** (kanban with thumbnails), **Placements** (list, for audit),
  chatter.

## 4. OWL widget (`imposition_editor`)

`static/src/imposition_editor/` — field widget registered in the `fields`
registry for Text fields, used only on `imposition.job.layout_json`.

- **Rendering**: SVG whose viewBox is the plate in mm — plate outline,
  sheet rectangle, gripper/side-lay/tail margin zones, and one `<g>` per
  placement containing the page `<image>`
  (`/web/image/imposition.page/<id>/thumbnail`), trim frame and page label.
- **Interaction**: click to select; pointer-event drag with 1 mm snapping
  (client px → mm via inverse screen CTM); rotate 90° button/`r` key;
  front/back and signature switchers; zoom.
- **Data flow**: parse `layout_json` into local `useState`; every mutation
  re-serializes and calls `record.update()`, so standard form save/discard
  semantics apply. The field's Python `inverse` writes the placements back
  to `imposition.placement` rows — the widget never owns persistent state.

## 5. Auto-imposition algorithm (server side)

`action_generate_imposition()`:

1. Usable area = sheet minus gripper+plate-bend (bottom), side-lay (left/right)
   and tail (top) margins, validated against plate and press sheet bounds.
2. Cell = trim size + 2×bleed; block = `cols × rows` cells + gutters. If the
   block does not fit and the template allows rotation, retry with pages
   rotated 90°; otherwise raise a blocking `UserError`.
3. The block is centered in the usable area; positions are generated
   row-major, top row first, in plate coordinates (origin bottom-left).
4. Page assignment (default scheme — the operator can rearrange visually):
   - `simplex` / `perfecting`: consecutive pages per surface.
   - `work_and_back`: front gets the first half of the signature's pages,
     back gets the second half with mirrored columns (so backup aligns).
   - `work_and_turn`: one plate; left half columns carry "front" content,
     right half the backing pages, column-mirrored (sheet turns on the
     vertical axis, gripper edge kept). Each sheet yields 2 copies.
   - `work_and_tumble`: same idea mirrored across the horizontal axis
     (sheet tumbles, gripper edge changes) — 2 copies per sheet.
5. Trailing cells with no page remain blank placements (`page_id` empty).

Note: fold-catalog-driven page ordering (true folding dummies for saddle
stitch / perfect binding) is intentionally out of scope for the generator's
first version; `fold_catalog` is stored per template so a later version (or
an operator, via the editor) can apply the exact folding scheme.

## 6. Module layout

```
offset_imposition/
├── __manifest__.py                  depends: base, mail, web; PyMuPDF ext. dep.
├── models/                          press_config, imposition_template,
│                                    imposition_job, imposition_page,
│                                    imposition_placement
├── security/                        groups (user/manager) + ir.model.access.csv
├── data/imposition_template_data.xml   standard templates (1/2/4/8-up, WT, WTumble)
├── demo/press_config_demo.xml          demo presses (GTO 52, SM 74, XL 106)
├── views/                           press, template, job views + menus
└── static/src/imposition_editor/    OWL component (js/xml/scss)
```

## 7. Downstream integration contract

Future modules depend on `offset_imposition` and read:

- `imposition.job` stored fields: `press_id`, `sheet_width/height`, `n_up`,
  `signature_count`, `sheets_needed`, `plates_needed`, `press_passes`,
  `quantity`, `colors_front/back`, `state` (`locked` = confirmed spec).
- `imposition.job.get_production_data()` for a serialized snapshot
  (e.g. to freeze specs onto a quotation line).
- `imposition.placement` for anything geometric (e.g. cutting programs).

Nothing downstream needs the PDF, the thumbnails, or the widget.
