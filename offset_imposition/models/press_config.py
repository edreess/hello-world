from odoo import api, fields, models
from odoo.exceptions import ValidationError


class PressConfig(models.Model):
    """Physical description of an offset press.

    XJDF mapping: Device (identification, non-printable margins) plus the
    plate Media resource (Media/@MediaType="Plate", @Dimension) and the
    paper sheet bounds (Media/@MediaType="Paper").
    All dimensions are millimeters.
    """

    _name = "press.config"
    _description = "Offset Press Configuration"
    _order = "name"

    name = fields.Char(required=True)
    code = fields.Char(help="Short identifier, e.g. SM74. XJDF: Device/@DeviceID.")
    manufacturer = fields.Char()
    active = fields.Boolean(default=True)

    # Plate (XJDF: Media[@MediaType="Plate"]/@Dimension)
    plate_width = fields.Float(
        required=True, help="Plate width in mm (across the cylinder).")
    plate_height = fields.Float(
        required=True, help="Plate height in mm (around the cylinder).")

    # Sheet bounds (XJDF: Media[@MediaType="Paper"]/@Dimension limits)
    max_sheet_width = fields.Float(required=True, help="Maximum sheet width in mm.")
    max_sheet_height = fields.Float(required=True, help="Maximum sheet height in mm.")
    min_sheet_width = fields.Float(help="Minimum sheet width in mm.")
    min_sheet_height = fields.Float(help="Minimum sheet height in mm.")

    # Units / perfecting
    num_units = fields.Integer(
        string="Printing Units", default=4,
        help="Number of printing units, i.e. colors printable in one pass.")
    is_perfecting = fields.Boolean(
        string="Perfecting Press",
        help="Press can print both sides of the sheet in a single pass.")

    # Non-printable margins (XJDF: Device/@NonPrintableMargin*)
    gripper_margin = fields.Float(
        default=10.0,
        help="Gripper edge in mm at the leading (bottom) sheet edge. "
             "XJDF: Device/@NonPrintableMarginBottom.")
    side_lay_margin = fields.Float(
        default=5.0,
        help="Side lay / non-printable margin in mm on the sheet sides. "
             "XJDF: Device/@NonPrintableMarginLeft/Right.")
    tail_margin = fields.Float(
        default=5.0,
        help="Non-printable margin in mm at the tail (top) sheet edge. "
             "XJDF: Device/@NonPrintableMarginTop.")
    plate_bend_margin = fields.Float(
        default=0.0,
        help="Extra plate clamp/bend allowance in mm at the gripper edge, "
             "in addition to the gripper margin.")

    notes = fields.Text()

    _plate_size_positive = models.Constraint(
        "CHECK (plate_width > 0 AND plate_height > 0)",
        "Plate dimensions must be positive.",
    )
    _max_sheet_positive = models.Constraint(
        "CHECK (max_sheet_width > 0 AND max_sheet_height > 0)",
        "Maximum sheet dimensions must be positive.",
    )

    @api.constrains("max_sheet_width", "max_sheet_height",
                    "min_sheet_width", "min_sheet_height")
    def _check_sheet_bounds(self):
        for press in self:
            if (press.min_sheet_width > press.max_sheet_width
                    or press.min_sheet_height > press.max_sheet_height):
                raise ValidationError(self.env._(
                    "Minimum sheet size cannot exceed maximum sheet size."))
