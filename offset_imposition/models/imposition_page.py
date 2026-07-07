from odoo import api, fields, models


class ImpositionPage(models.Model):
    """One page of the imported job PDF.

    XJDF mapping: one logical page of the content RunList; placements
    reference it through their Ord. The thumbnail is a low-resolution
    raster proxy of the page content used by the plate editor.
    """

    _name = "imposition.page"
    _description = "Imposition Job PDF Page"
    _order = "job_id, page_number"

    job_id = fields.Many2one(
        "imposition.job", required=True, ondelete="cascade", index=True)
    page_number = fields.Integer(
        required=True, help="1-based page number in the source PDF.")
    width_mm = fields.Float(string="Trim Width (mm)")
    height_mm = fields.Float(string="Trim Height (mm)")
    thumbnail = fields.Image(
        max_width=256, max_height=256, attachment=True,
        help="Low-resolution PNG preview rendered from the PDF page.")

    _page_number_unique = models.Constraint(
        "UNIQUE (job_id, page_number)",
        "A PDF page can only be registered once per job.",
    )

    @api.depends("page_number", "job_id.name")
    def _compute_display_name(self):
        for page in self:
            page.display_name = self.env._(
                "Page %(num)s (%(job)s)",
                num=page.page_number, job=page.job_id.name or "?")
