# Offset Imposition Planner

Odoo 19 Community Edition module for imposition planning in offset print
shops.

## Features

- **PDF import & analysis** — upload the job PDF; page count, trim size
  (TrimBox with MediaBox fallback) and orientation are extracted with
  PyMuPDF, and every page is rasterized to a low-resolution thumbnail.
- **Press configuration** — model your offset presses: plate size, min/max
  sheet, printing units, perfecting capability, gripper / side-lay / tail /
  plate-bend margins.
- **Signature generation** — pick an imposition template (1/2/4/8-up,
  sheetwise, work-and-turn, work-and-tumble, perfecting) and the planner
  computes the layout, signatures, press passes, sheets and plates needed.
- **Visual plate editor** — an OWL/SVG widget shows the plate, sheet,
  gripper zone and every placed page with its real thumbnail; drag pages
  (1 mm snapping), rotate them 90° (`R` key), switch signatures and sides.
- **XJDF-aligned data model** — presses, templates, jobs, pages and
  placements map to CIP4 XJDF `Device`/`Media`, `BinderySignature`,
  `Layout`, `RunList` and `ContentObject` concepts (see
  [ARCHITECTURE.md](ARCHITECTURE.md) for the field-by-field mapping).
- **Downstream-ready** — planning results are plain stored fields plus a
  `get_production_data()` snapshot, so future quotation / sales / MRP
  modules consume them through standard ORM relations.

## Requirements

- Odoo 19 Community Edition
- PyMuPDF: `pip install PyMuPDF`

## Usage

1. *Imposition ▸ Configuration ▸ Presses*: configure your presses (demo
   data ships a GTO 52, an SM 74 and an XL 106).
2. *Imposition ▸ Jobs*: create a job, upload the PDF, **Parse PDF**.
3. Select press + template, adjust sheet/bleed/gutters, **Generate
   Imposition**.
4. Fine-tune the layout in the *Plate Layout* tab, then **Lock** the job to
   freeze the production spec.
