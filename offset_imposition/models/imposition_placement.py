from odoo import api, fields, models


class ImpositionPlacement(models.Model):
    """One placed page cell on a plate surface.

    Persistent source of truth for the plate layout; the OWL editor's JSON
    is derived from (and written back to) these records.

    XJDF mapping: Layout/Surface/ContentObject —
    * signature      -> index of the signature's Layout entry
    * surface        -> Surface/@Side (Front/Back)
    * ord            -> ContentObject/@Ord (0-based index into the RunList)
    * pos_x/pos_y    -> translation component of ContentObject/@CTM
                        (plate coordinate origin: bottom-left, millimeters)
    * rotation       -> rotation component of ContentObject/@CTM
    * width/height   -> ContentObject/@ClipBox extents (trim size incl. none
                        of the bleed; bleed is kept on the job)
    """

    _name = "imposition.placement"
    _description = "Imposition Placement (Placed Page)"
    _order = "job_id, signature, surface desc, sequence"

    job_id = fields.Many2one(
        "imposition.job", required=True, ondelete="cascade", index=True)
    sequence = fields.Integer(default=10)
    signature = fields.Integer(
        required=True, default=1, help="1-based signature number.")
    surface = fields.Selection(
        [("front", "Front"), ("back", "Back")],
        required=True, default="front",
        help="Plate/sheet side. XJDF: Surface/@Side.")
    ord = fields.Integer(
        string="Ord", default=-1,
        help="0-based index into the job's page run list. -1 for a blank "
             "cell. XJDF: ContentObject/@Ord.")
    page_id = fields.Many2one(
        "imposition.page", ondelete="set null",
        help="Placed PDF page; empty for a blank cell.")
    pos_x = fields.Float(
        string="X (mm)", help="Cell left edge from the plate's left edge.")
    pos_y = fields.Float(
        string="Y (mm)",
        help="Cell bottom edge from the plate's bottom (gripper) edge.")
    rotation = fields.Selection(
        [("0", "0°"), ("90", "90°"), ("180", "180°"), ("270", "270°")],
        required=True, default="0",
        help="Counter-clockwise page rotation on the plate.")
    width_mm = fields.Float(
        string="Width (mm)", help="Placed trim width (after rotation).")
    height_mm = fields.Float(
        string="Height (mm)", help="Placed trim height (after rotation).")

    page_number = fields.Integer(
        related="page_id.page_number", string="Page")

    @api.depends("signature", "surface", "page_id.page_number")
    def _compute_display_name(self):
        for placement in self:
            page = (str(placement.page_id.page_number)
                    if placement.page_id else self.env._("blank"))
            placement.display_name = self.env._(
                "Sig %(sig)s / %(surface)s / page %(page)s",
                sig=placement.signature, surface=placement.surface, page=page)
