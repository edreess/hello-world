import base64
import json
import logging
import math

from odoo import api, fields, models
from odoo.exceptions import UserError

_logger = logging.getLogger(__name__)

try:
    import fitz  # PyMuPDF
except ImportError:
    fitz = None
    _logger.warning(
        "PyMuPDF is not installed; PDF parsing for offset_imposition "
        "is disabled (pip install PyMuPDF).")

PT_TO_MM = 25.4 / 72.0
THUMBNAIL_TARGET_PX = 200.0


class ImpositionJob(models.Model):
    """One imposition plan for one print job.

    XJDF mapping: the XJDF job ticket root — the imported PDF is the
    content RunList, the plan corresponds to the Layout resource set and
    the planning outputs to the amounts of the sheet Component and plate
    ExposedMedia resources.
    """

    _name = "imposition.job"
    _description = "Imposition Job"
    _inherit = ["mail.thread", "mail.activity.mixin"]
    _order = "id desc"

    name = fields.Char(
        required=True, copy=False, readonly=True, default=lambda self: self.env._("New"))
    reference = fields.Char(
        string="Customer Reference", tracking=True)
    partner_id = fields.Many2one("res.partner", string="Customer")
    state = fields.Selection(
        [
            ("draft", "Draft"),
            ("parsed", "PDF Parsed"),
            ("planned", "Planned"),
            ("locked", "Locked"),
        ],
        default="draft", required=True, copy=False, tracking=True)

    # ------------------------------------------------------------------
    # Inputs
    # ------------------------------------------------------------------
    pdf_file = fields.Binary(
        string="Job PDF", attachment=True, copy=False,
        help="Print content PDF. XJDF: RunList/FileSpec.")
    pdf_filename = fields.Char(copy=False)
    quantity = fields.Integer(
        string="Run Length", default=1000, tracking=True,
        help="Number of finished copies to produce.")
    press_id = fields.Many2one(
        "press.config", string="Press", tracking=True,
        help="Press that will run the job. XJDF: Device.")
    template_id = fields.Many2one(
        "imposition.template", string="Imposition Template", tracking=True,
        help="Signature pattern. XJDF: BinderySignature.")
    sheet_width = fields.Float(
        compute="_compute_sheet_size", store=True, readonly=False,
        help="Press sheet width in mm. XJDF: Media[Paper]/@Dimension.")
    sheet_height = fields.Float(
        compute="_compute_sheet_size", store=True, readonly=False,
        help="Press sheet height in mm.")
    bleed = fields.Float(
        compute="_compute_strip_defaults", store=True, readonly=False,
        help="Bleed in mm around each page. XJDF: StripCellParams/@Bleed*.")
    gutter_x = fields.Float(
        compute="_compute_strip_defaults", store=True, readonly=False,
        string="Horizontal Gutter", help="Cut lane between columns, mm.")
    gutter_y = fields.Float(
        compute="_compute_strip_defaults", store=True, readonly=False,
        string="Vertical Gutter", help="Cut lane between rows, mm.")
    colors_front = fields.Integer(
        default=4, help="Ink separations on the front, e.g. 4 for CMYK.")
    colors_back = fields.Integer(
        default=4, help="Ink separations on the back (0 for single sided).")
    overage_percent = fields.Float(
        string="Overage %", default=5.0,
        help="Extra sheets for makeready and waste, in percent.")

    # ------------------------------------------------------------------
    # Parsed from the PDF
    # ------------------------------------------------------------------
    page_count = fields.Integer(readonly=True, copy=False, tracking=True)
    trim_width = fields.Float(
        string="Trim Width (mm)", readonly=True, copy=False,
        help="Page trim width from the PDF TrimBox (MediaBox fallback).")
    trim_height = fields.Float(
        string="Trim Height (mm)", readonly=True, copy=False)
    orientation = fields.Selection(
        [("portrait", "Portrait"), ("landscape", "Landscape"),
         ("square", "Square")],
        compute="_compute_orientation", store=True)
    page_ids = fields.One2many(
        "imposition.page", "job_id", string="Pages", copy=False)
    placement_ids = fields.One2many(
        "imposition.placement", "job_id", string="Placements", copy=False)

    # ------------------------------------------------------------------
    # Planning outputs (stored: consumed by quotation / sales / MRP later)
    # ------------------------------------------------------------------
    n_up = fields.Integer(
        string="N-Up", readonly=True, copy=False,
        help="Pages up per plate surface. XJDF: BinderySignature/@NumberUp.")
    signature_count = fields.Integer(
        readonly=True, copy=False,
        help="Number of distinct signatures (plate sets).")
    press_passes = fields.Integer(
        readonly=True, copy=False,
        help="Total passes through the press (all signatures).")
    sheets_per_signature = fields.Integer(
        readonly=True, copy=False,
        help="Net press sheets per signature for the run length.")
    sheets_needed = fields.Integer(
        readonly=True, copy=False, tracking=True,
        help="Gross press sheets including overage. "
             "XJDF: sheet Component/@Amount.")
    plates_needed = fields.Integer(
        readonly=True, copy=False, tracking=True,
        help="Total plates over all signatures and separations. "
             "XJDF: ExposedMedia[Plate] amount.")
    pages_rotated = fields.Boolean(
        readonly=True, copy=False,
        help="Generator rotated pages 90° to fit the sheet.")

    layout_json = fields.Text(
        compute="_compute_layout_json", inverse="_inverse_layout_json",
        help="Plate layout serialization consumed by the visual editor. "
             "Derived from the placement records; not stored.")

    # ==================================================================
    # Defaults / computes
    # ==================================================================
    @api.model_create_multi
    def create(self, vals_list):
        for vals in vals_list:
            if vals.get("name", self.env._("New")) == self.env._("New"):
                vals["name"] = (self.env["ir.sequence"]
                                .next_by_code("imposition.job")
                                or self.env._("New"))
        return super().create(vals_list)

    @api.depends("press_id")
    def _compute_sheet_size(self):
        for job in self:
            if job.press_id:
                job.sheet_width = job.press_id.max_sheet_width
                job.sheet_height = job.press_id.max_sheet_height

    @api.depends("template_id")
    def _compute_strip_defaults(self):
        for job in self:
            if job.template_id:
                job.bleed = job.template_id.bleed_default
                job.gutter_x = job.template_id.gutter_x
                job.gutter_y = job.template_id.gutter_y

    @api.depends("trim_width", "trim_height")
    def _compute_orientation(self):
        for job in self:
            if not job.trim_width or not job.trim_height:
                job.orientation = False
            elif abs(job.trim_width - job.trim_height) < 0.01:
                job.orientation = "square"
            elif job.trim_width > job.trim_height:
                job.orientation = "landscape"
            else:
                job.orientation = "portrait"

    # ==================================================================
    # Workflow: PDF parsing
    # ==================================================================
    def action_parse_pdf(self):
        self.ensure_one()
        if not self.pdf_file:
            raise UserError(self.env._("Upload a job PDF first."))
        if fitz is None:
            raise UserError(self.env._(
                "PyMuPDF is not installed on the server. "
                "Install it with: pip install PyMuPDF"))

        try:
            document = fitz.open(
                stream=base64.b64decode(self.pdf_file), filetype="pdf")
        except Exception as error:
            raise UserError(self.env._(
                "The uploaded file could not be read as a PDF: %(error)s",
                error=error)) from error

        if document.page_count == 0:
            raise UserError(self.env._("The PDF contains no pages."))

        self.page_ids.unlink()
        self.placement_ids.unlink()

        page_vals = []
        first_size = None
        for index, page in enumerate(document):
            width_mm, height_mm = self._page_trim_size_mm(page)
            if first_size is None:
                first_size = (width_mm, height_mm)
            page_vals.append({
                "job_id": self.id,
                "page_number": index + 1,
                "width_mm": width_mm,
                "height_mm": height_mm,
                "thumbnail": self._render_thumbnail(page),
            })
        self.env["imposition.page"].create(page_vals)

        self.write({
            "page_count": document.page_count,
            "trim_width": first_size[0],
            "trim_height": first_size[1],
            "state": "parsed",
            # planning outputs are stale now
            "n_up": 0, "signature_count": 0, "press_passes": 0,
            "sheets_per_signature": 0, "sheets_needed": 0,
            "plates_needed": 0, "pages_rotated": False,
        })
        document.close()
        return True

    @staticmethod
    def _page_trim_size_mm(page):
        """Trim size of a PyMuPDF page in mm, honoring page rotation."""
        box = page.trimbox if page.trimbox else page.mediabox
        width, height = box.width, box.height
        if page.rotation in (90, 270):
            width, height = height, width
        return round(width * PT_TO_MM, 2), round(height * PT_TO_MM, 2)

    @staticmethod
    def _render_thumbnail(page):
        """Render a page to a small base64 PNG."""
        max_pt = max(page.rect.width, page.rect.height) or 1.0
        zoom = THUMBNAIL_TARGET_PX / max_pt
        pixmap = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom), alpha=False)
        return base64.b64encode(pixmap.tobytes("png"))

    # ==================================================================
    # Workflow: imposition generation
    # ==================================================================
    def action_generate_imposition(self):
        self.ensure_one()
        if self.state == "locked":
            raise UserError(self.env._("The job is locked."))
        if not self.page_ids:
            raise UserError(self.env._("Parse the PDF first."))
        if not self.press_id or not self.template_id:
            raise UserError(self.env._(
                "Select a press and an imposition template first."))
        if self.quantity <= 0:
            raise UserError(self.env._("The run length must be positive."))

        press, template = self.press_id, self.template_id
        self._check_sheet_against_press()

        geometry = self._fit_page_block()
        placements = self._build_placements(geometry)
        self.placement_ids.unlink()
        self.env["imposition.placement"].create(placements)

        self._compute_planning_outputs()
        self.write({"state": "planned", "pages_rotated": geometry["rotated"]})
        self.message_post(body=self.env._(
            "Imposition generated: %(nup)s-up, %(sigs)s signature(s) on "
            "%(press)s (%(template)s).",
            nup=template.n_up, sigs=self.signature_count,
            press=press.name, template=template.name))
        return True

    def _check_sheet_against_press(self):
        press = self.press_id
        if (self.sheet_width > press.max_sheet_width
                or self.sheet_height > press.max_sheet_height):
            raise UserError(self.env._(
                "Sheet %(w).0f×%(h).0f mm exceeds the press maximum "
                "%(mw).0f×%(mh).0f mm.",
                w=self.sheet_width, h=self.sheet_height,
                mw=press.max_sheet_width, mh=press.max_sheet_height))
        if (self.sheet_width < press.min_sheet_width
                or self.sheet_height < press.min_sheet_height):
            raise UserError(self.env._(
                "Sheet %(w).0f×%(h).0f mm is below the press minimum "
                "%(mw).0f×%(mh).0f mm.",
                w=self.sheet_width, h=self.sheet_height,
                mw=press.min_sheet_width, mh=press.min_sheet_height))
        if (self.sheet_width > press.plate_width
                or self.sheet_height > press.plate_height):
            raise UserError(self.env._(
                "Sheet %(w).0f×%(h).0f mm does not fit the plate "
                "%(pw).0f×%(ph).0f mm.",
                w=self.sheet_width, h=self.sheet_height,
                pw=press.plate_width, ph=press.plate_height))

    def _usable_area(self):
        """Printable rectangle on the sheet, in sheet coordinates (mm,
        origin bottom-left; the gripper edge is the bottom edge)."""
        press = self.press_id
        x0 = press.side_lay_margin
        y0 = press.gripper_margin + press.plate_bend_margin
        x1 = self.sheet_width - press.side_lay_margin
        y1 = self.sheet_height - press.tail_margin
        if x1 <= x0 or y1 <= y0:
            raise UserError(self.env._(
                "The press margins leave no printable area on the sheet."))
        return x0, y0, x1, y1

    def _fit_page_block(self):
        """Compute cell geometry; try upright, then rotated 90° if allowed.

        Returns a dict with the cell size, block origin (sheet coords) and
        whether pages were rotated.
        """
        template = self.template_id
        x0, y0, x1, y1 = self._usable_area()
        usable_w, usable_h = x1 - x0, y1 - y0

        for rotated in ([False, True] if template.allow_rotation
                        else [False]):
            page_w = self.trim_height if rotated else self.trim_width
            page_h = self.trim_width if rotated else self.trim_height
            cell_w = page_w + 2 * self.bleed
            cell_h = page_h + 2 * self.bleed
            block_w = (template.cols * cell_w
                       + (template.cols - 1) * self.gutter_x)
            block_h = (template.rows * cell_h
                       + (template.rows - 1) * self.gutter_y)
            if block_w <= usable_w + 1e-6 and block_h <= usable_h + 1e-6:
                return {
                    "rotated": rotated,
                    "page_w": page_w, "page_h": page_h,
                    "cell_w": cell_w, "cell_h": cell_h,
                    # center the block in the usable area
                    "origin_x": x0 + (usable_w - block_w) / 2.0,
                    "origin_y": y0 + (usable_h - block_h) / 2.0,
                }
        raise UserError(self.env._(
            "A %(cols)s×%(rows)s block of %(w).1f×%(h).1f mm pages "
            "(bleed and gutters included) does not fit the usable sheet "
            "area of %(uw).1f×%(uh).1f mm. Choose a smaller template, a "
            "bigger sheet or another press.",
            cols=template.cols, rows=template.rows,
            w=self.trim_width, h=self.trim_height,
            uw=usable_w, uh=usable_h))

    def _sheet_offset_on_plate(self):
        """Position of the sheet's bottom-left corner on the plate:
        centered horizontally, gripper edge at the plate bend (clamped so
        the sheet never overhangs the plate top)."""
        press = self.press_id
        offset_x = max(0.0, (press.plate_width - self.sheet_width) / 2.0)
        offset_y = max(0.0, min(press.plate_bend_margin,
                                press.plate_height - self.sheet_height))
        return offset_x, offset_y

    def _cell_position(self, geometry, row, col):
        """Bottom-left of a cell's page (bleed excluded), plate coords.

        `row` counts from the top of the sheet, matching reading order.
        The sheet is centered horizontally on the plate and its gripper
        edge sits at the plate bend margin.
        """
        sheet_offset_x, sheet_offset_y = self._sheet_offset_on_plate()
        rows = self.template_id.rows
        x = (geometry["origin_x"] + col * (geometry["cell_w"] + self.gutter_x)
             + self.bleed)
        y = (geometry["origin_y"]
             + (rows - 1 - row) * (geometry["cell_h"] + self.gutter_y)
             + self.bleed)
        return round(sheet_offset_x + x, 2), round(sheet_offset_y + y, 2)

    def _build_placements(self, geometry):
        """Generate placement values for all signatures and surfaces.

        Default page-assignment schemes (see ARCHITECTURE.md §5); the
        operator can rearrange pages in the visual editor afterwards.
        """
        template = self.template_id
        rows, cols = template.rows, template.cols
        cells = rows * cols
        style = template.work_style
        pages_per_sig = template.pages_per_signature
        signature_count = max(
            1, math.ceil(self.page_count / pages_per_sig))
        pages_by_index = {p.page_number - 1: p.id for p in self.page_ids}
        rotation = "90" if geometry["rotated"] else "0"

        def cell_iter():
            for row in range(rows):
                for col in range(cols):
                    yield row, col

        def make(sig, surface, row, col, ord_index, sequence):
            page_id = pages_by_index.get(ord_index)
            pos_x, pos_y = self._cell_position(geometry, row, col)
            return {
                "job_id": self.id,
                "sequence": sequence,
                "signature": sig,
                "surface": surface,
                "ord": ord_index if page_id else -1,
                "page_id": page_id or False,
                "pos_x": pos_x,
                "pos_y": pos_y,
                "rotation": rotation,
                "width_mm": geometry["page_w"],
                "height_mm": geometry["page_h"],
            }

        values = []
        for sig in range(1, signature_count + 1):
            base = (sig - 1) * pages_per_sig
            sequence = 0
            if style in ("simplex",):
                for index, (row, col) in enumerate(cell_iter()):
                    sequence += 1
                    values.append(
                        make(sig, "front", row, col, base + index, sequence))
            elif style in ("work_and_back", "perfecting"):
                # front: first half of the signature's pages; back: second
                # half with mirrored columns so the sheet backs up.
                for index, (row, col) in enumerate(cell_iter()):
                    sequence += 1
                    values.append(
                        make(sig, "front", row, col, base + index, sequence))
                for index, (row, col) in enumerate(cell_iter()):
                    sequence += 1
                    values.append(make(
                        sig, "back", row, cols - 1 - col,
                        base + cells + index, sequence))
            elif style == "work_and_turn":
                # single plate: left columns carry front content, right
                # columns the column-mirrored backing pages.
                half_cols = max(1, cols // 2)
                per_half = rows * half_cols
                for row in range(rows):
                    for col in range(half_cols):
                        sequence += 1
                        ord_index = base + row * half_cols + col
                        values.append(
                            make(sig, "front", row, col, ord_index, sequence))
                        sequence += 1
                        values.append(make(
                            sig, "front", row, cols - 1 - col,
                            ord_index + per_half, sequence))
            elif style == "work_and_tumble":
                # single plate: top rows carry front content, bottom rows
                # the row-mirrored backing pages.
                half_rows = max(1, rows // 2)
                per_half = half_rows * cols
                for row in range(half_rows):
                    for col in range(cols):
                        sequence += 1
                        ord_index = base + row * cols + col
                        values.append(
                            make(sig, "front", row, col, ord_index, sequence))
                        sequence += 1
                        values.append(make(
                            sig, "front", rows - 1 - row, col,
                            ord_index + per_half, sequence))
        return values

    def _compute_planning_outputs(self):
        template, press = self.template_id, self.press_id
        style = template.work_style
        signature_count = max(
            1, math.ceil(self.page_count / template.pages_per_signature))

        if style == "simplex":
            passes_per_sig = 1
            sheets_per_sig = self.quantity
            plates_per_sig = self.colors_front
        elif style == "perfecting":
            passes_per_sig = 1 if press.is_perfecting else 2
            sheets_per_sig = self.quantity
            plates_per_sig = self.colors_front + self.colors_back
        elif style == "work_and_back":
            passes_per_sig = 2
            sheets_per_sig = self.quantity
            plates_per_sig = self.colors_front + self.colors_back
        else:  # work_and_turn / work_and_tumble: same plates, 2 passes,
            #    each sheet yields two copies once cut apart
            passes_per_sig = 2
            sheets_per_sig = math.ceil(self.quantity / 2)
            plates_per_sig = self.colors_front

        net_sheets = signature_count * sheets_per_sig
        self.write({
            "n_up": template.n_up,
            "signature_count": signature_count,
            "press_passes": signature_count * passes_per_sig,
            "sheets_per_signature": sheets_per_sig,
            "sheets_needed": math.ceil(
                net_sheets * (1 + self.overage_percent / 100.0)),
            "plates_needed": signature_count * plates_per_sig,
        })

    def action_lock(self):
        for job in self:
            if job.state != "planned":
                raise UserError(self.env._("Only planned jobs can be locked."))
            job.state = "locked"

    def action_reset_draft(self):
        for job in self:
            job.state = "draft"

    # ==================================================================
    # Layout JSON <-> placements (bridge to the OWL editor)
    # ==================================================================
    @api.depends(
        "placement_ids.pos_x", "placement_ids.pos_y",
        "placement_ids.rotation", "placement_ids.page_id",
        "placement_ids.signature", "placement_ids.surface",
        "press_id", "template_id", "sheet_width", "sheet_height", "bleed")
    def _compute_layout_json(self):
        for job in self:
            press = job.press_id
            if not press or not job.placement_ids:
                job.layout_json = ""
                continue
            sheet_x, sheet_y = job._sheet_offset_on_plate()
            job.layout_json = json.dumps({
                "unit": "mm",
                "state": job.state,
                "plate": {"width": press.plate_width,
                          "height": press.plate_height},
                "sheet": {
                    "width": job.sheet_width,
                    "height": job.sheet_height,
                    "x": sheet_x,
                    "y": sheet_y,
                },
                "margins": {
                    "gripper": press.gripper_margin,
                    "side": press.side_lay_margin,
                    "tail": press.tail_margin,
                    "plate_bend": press.plate_bend_margin,
                },
                "bleed": job.bleed,
                "work_style": job.template_id.work_style or "simplex",
                "signatures": sorted(set(
                    job.placement_ids.mapped("signature"))),
                "placements": [{
                    "id": placement.id,
                    "signature": placement.signature,
                    "surface": placement.surface,
                    "ord": placement.ord,
                    "page_id": placement.page_id.id or None,
                    "page_number": placement.page_id.page_number or 0,
                    "x": placement.pos_x,
                    "y": placement.pos_y,
                    "w": placement.width_mm,
                    "h": placement.height_mm,
                    "rotation": int(placement.rotation),
                } for placement in job.placement_ids],
            })

    def _inverse_layout_json(self):
        """Persist editor changes (position, rotation, page swap) back to
        the placement records."""
        for job in self:
            if not job.layout_json:
                continue
            if job.state == "locked":
                raise UserError(self.env._(
                    "The layout of a locked job cannot be modified."))
            try:
                data = json.loads(job.layout_json)
            except (ValueError, TypeError) as error:
                raise UserError(self.env._(
                    "Invalid layout data: %(error)s", error=error)) from error
            placements = {p.id: p for p in job.placement_ids}
            pages = {p.id: p for p in job.page_ids}
            for item in data.get("placements", []):
                placement = placements.get(item.get("id"))
                if not placement:
                    continue
                page = pages.get(item.get("page_id"))
                placement.write({
                    "pos_x": item.get("x", placement.pos_x),
                    "pos_y": item.get("y", placement.pos_y),
                    "rotation": str(int(item.get("rotation", 0)) % 360),
                    "page_id": page.id if page else False,
                    "ord": page.page_number - 1 if page else -1,
                })

    # ==================================================================
    # Public API for downstream modules (quotation / sales / MRP)
    # ==================================================================
    def get_production_data(self):
        """Serialized production snapshot for quotation, sales order or
        manufacturing modules — no PDF access or layout re-derivation
        needed on the consumer side."""
        self.ensure_one()
        return {
            "job_id": self.id,
            "name": self.name,
            "state": self.state,
            "quantity": self.quantity,
            "page_count": self.page_count,
            "trim_size_mm": (self.trim_width, self.trim_height),
            "press_id": self.press_id.id,
            "press_name": self.press_id.name,
            "sheet_size_mm": (self.sheet_width, self.sheet_height),
            "work_style": self.template_id.work_style,
            "fold_catalog": self.template_id.fold_catalog,
            "n_up": self.n_up,
            "signature_count": self.signature_count,
            "press_passes": self.press_passes,
            "sheets_per_signature": self.sheets_per_signature,
            "sheets_needed": self.sheets_needed,
            "plates_needed": self.plates_needed,
            "colors": (self.colors_front, self.colors_back),
            "pages_rotated": self.pages_rotated,
        }
