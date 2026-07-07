from odoo import api, fields, models

# XJDF Layout/@WorkStyle values (CIP4 XJDF 2.x)
WORK_STYLES = [
    ("simplex", "Simplex (single sided)"),
    ("work_and_back", "Work & Back (sheetwise)"),
    ("perfecting", "Perfecting"),
    ("work_and_turn", "Work & Turn"),
    ("work_and_tumble", "Work & Tumble"),
]

# work styles where front and back share the same plate set
SINGLE_PLATE_SET_STYLES = ("simplex", "work_and_turn", "work_and_tumble")


class ImpositionTemplate(models.Model):
    """Reusable signature pattern.

    XJDF mapping: BinderySignature (@FoldCatalog, @NumberUp = "cols rows")
    with default stripping values from StripCellParams (bleed, cut lanes)
    and the Layout/@WorkStyle the pattern is meant for.
    """

    _name = "imposition.template"
    _description = "Imposition Template (Signature Pattern)"
    _order = "sequence, name"

    name = fields.Char(required=True)
    code = fields.Char()
    sequence = fields.Integer(default=10)
    active = fields.Boolean(default=True)

    fold_catalog = fields.Char(
        string="Fold Catalog",
        help="CIP4 fold catalog code of the intended folding scheme, "
             "e.g. F4-1, F8-7, F16-6. XJDF: BinderySignature/@FoldCatalog.")
    rows = fields.Integer(
        required=True, default=1,
        help="Cell rows on one plate surface. "
             "XJDF: second component of BinderySignature/@NumberUp.")
    cols = fields.Integer(
        string="Columns", required=True, default=1,
        help="Cell columns on one plate surface. "
             "XJDF: first component of BinderySignature/@NumberUp.")
    work_style = fields.Selection(
        WORK_STYLES, required=True, default="simplex",
        help="How front and back of the sheet are produced. "
             "XJDF: Layout/@WorkStyle.")

    bleed_default = fields.Float(
        string="Default Bleed", default=3.0,
        help="Default bleed in mm on all page edges. "
             "XJDF: StripCellParams/@BleedFace/Foot/Head/Spine.")
    gutter_x = fields.Float(
        string="Horizontal Gutter", default=4.0,
        help="Horizontal cut lane between columns, in mm. "
             "XJDF: StripCellParams/@TrimFace.")
    gutter_y = fields.Float(
        string="Vertical Gutter", default=4.0,
        help="Vertical cut lane between rows, in mm. "
             "XJDF: StripCellParams/@TrimFoot.")
    allow_rotation = fields.Boolean(
        default=True,
        help="Allow the generator to rotate pages 90° when the upright "
             "block does not fit the sheet.")

    n_up = fields.Integer(
        string="N-Up", compute="_compute_n_up", store=True,
        help="Cells per plate surface (rows × columns).")
    pages_per_signature = fields.Integer(
        compute="_compute_n_up", store=True,
        help="Distinct PDF pages consumed by one signature, depending on "
             "the work style.")
    description = fields.Text()

    _grid_positive = models.Constraint(
        "CHECK (rows > 0 AND cols > 0)",
        "Rows and columns must be positive.",
    )

    @api.depends("rows", "cols", "work_style")
    def _compute_n_up(self):
        for template in self:
            cells = template.rows * template.cols
            template.n_up = cells
            if template.work_style in ("work_and_back", "perfecting"):
                # distinct front content + distinct back content
                template.pages_per_signature = 2 * cells
            else:
                # simplex: one side only; work & turn/tumble: the plate
                # carries both front and back halves of the same pages
                template.pages_per_signature = cells

    @api.depends("rows", "cols", "work_style")
    def _compute_display_name(self):
        style = dict(self._fields["work_style"].selection)
        for template in self:
            template.display_name = (
                f"{template.name} ({template.cols}×{template.rows}, "
                f"{style.get(template.work_style, template.work_style)})")
