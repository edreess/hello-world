import base64
import json
import math

from odoo.exceptions import UserError
from odoo.tests import TransactionCase, tagged

try:
    import fitz
except ImportError:
    fitz = None


def _make_pdf(pages=8, width_mm=210.0, height_mm=297.0):
    """Build a small PDF with numbered pages, returned base64-encoded."""
    document = fitz.open()
    width_pt = width_mm * 72.0 / 25.4
    height_pt = height_mm * 72.0 / 25.4
    for number in range(1, pages + 1):
        page = document.new_page(width=width_pt, height=height_pt)
        page.insert_text((width_pt / 2, height_pt / 2), str(number),
                         fontsize=72)
    data = document.tobytes()
    document.close()
    return base64.b64encode(data)


@tagged("post_install", "-at_install")
class TestImposition(TransactionCase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.press = cls.env["press.config"].create({
            "name": "Test Press",
            "plate_width": 605,
            "plate_height": 745,
            "max_sheet_width": 530,
            "max_sheet_height": 740,
            "num_units": 4,
            "gripper_margin": 10,
            "side_lay_margin": 5,
            "tail_margin": 5,
            "plate_bend_margin": 34,
        })
        cls.template_wb = cls.env.ref(
            "offset_imposition.template_4up_sheetwise")
        cls.template_wt = cls.env.ref(
            "offset_imposition.template_4up_work_and_turn")

    def _make_job(self, template, pages=8, quantity=1000):
        job = self.env["imposition.job"].create({
            "quantity": quantity,
            "press_id": self.press.id,
            "template_id": template.id,
        })
        if fitz is not None:
            job.pdf_file = _make_pdf(pages=pages)
            job.pdf_filename = "test.pdf"
        return job

    def test_template_pages_per_signature(self):
        self.assertEqual(self.template_wb.n_up, 4)
        self.assertEqual(self.template_wb.pages_per_signature, 8)
        self.assertEqual(self.template_wt.pages_per_signature, 4)

    def test_parse_pdf(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        job = self._make_job(self.template_wb, pages=8)
        job.action_parse_pdf()
        self.assertEqual(job.state, "parsed")
        self.assertEqual(job.page_count, 8)
        self.assertEqual(len(job.page_ids), 8)
        self.assertAlmostEqual(job.trim_width, 210.0, delta=0.5)
        self.assertAlmostEqual(job.trim_height, 297.0, delta=0.5)
        self.assertEqual(job.orientation, "portrait")
        self.assertTrue(all(p.thumbnail for p in job.page_ids))

    def test_generate_sheetwise(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        job = self._make_job(self.template_wb, pages=8, quantity=1000)
        job.action_parse_pdf()
        job.action_generate_imposition()
        self.assertEqual(job.state, "planned")
        self.assertEqual(job.n_up, 4)
        self.assertEqual(job.signature_count, 1)
        self.assertEqual(job.press_passes, 2)
        self.assertEqual(job.sheets_per_signature, 1000)
        self.assertEqual(job.sheets_needed,
                         math.ceil(1000 * 1.05))
        # 4 front + 4 back separations on one signature
        self.assertEqual(job.plates_needed, 8)
        placements = job.placement_ids
        self.assertEqual(len(placements), 8)
        self.assertEqual(
            set(placements.mapped("surface")), {"front", "back"})
        self.assertEqual(
            sorted(placements.mapped("ord")), list(range(8)))
        # every placed page stays inside the sheet
        sheet_x, sheet_y = job._sheet_offset_on_plate()
        for placement in placements:
            self.assertGreaterEqual(placement.pos_x, sheet_x)
            self.assertGreaterEqual(placement.pos_y, sheet_y)
            self.assertLessEqual(
                placement.pos_x + placement.width_mm,
                sheet_x + job.sheet_width + 1e-6)
            self.assertLessEqual(
                placement.pos_y + placement.height_mm,
                sheet_y + job.sheet_height + 1e-6)

    def test_generate_work_and_turn(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        job = self._make_job(self.template_wt, pages=8, quantity=1000)
        job.action_parse_pdf()
        job.action_generate_imposition()
        self.assertEqual(job.signature_count, 2)
        # one plate set per signature, half sheets (2 copies per sheet)
        self.assertEqual(job.plates_needed, 2 * job.colors_front)
        self.assertEqual(job.sheets_per_signature, 500)
        # all placements on the single (front) plate
        self.assertEqual(
            set(job.placement_ids.mapped("surface")), {"front"})

    def test_layout_json_roundtrip(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        job = self._make_job(self.template_wb, pages=8)
        job.action_parse_pdf()
        job.action_generate_imposition()
        layout = json.loads(job.layout_json)
        self.assertEqual(layout["plate"]["width"], 605)
        self.assertEqual(len(layout["placements"]), 8)
        # simulate the editor moving and rotating the first placement
        moved = layout["placements"][0]
        moved["x"] += 5
        moved["y"] += 7
        moved["rotation"] = (moved["rotation"] + 90) % 360
        job.layout_json = json.dumps(layout)
        placement = job.placement_ids.filtered(
            lambda p: p.id == moved["id"])
        self.assertEqual(placement.pos_x, moved["x"])
        self.assertEqual(placement.pos_y, moved["y"])
        self.assertEqual(int(placement.rotation), moved["rotation"])

    def test_block_does_not_fit(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        small_press = self.env["press.config"].create({
            "name": "Tiny Press",
            "plate_width": 200, "plate_height": 200,
            "max_sheet_width": 180, "max_sheet_height": 180,
            "gripper_margin": 10,
        })
        job = self._make_job(self.template_wb, pages=8)
        job.press_id = small_press
        job.action_parse_pdf()
        with self.assertRaises(UserError):
            job.action_generate_imposition()

    def test_production_data_snapshot(self):
        if fitz is None:
            self.skipTest("PyMuPDF not installed")
        job = self._make_job(self.template_wb, pages=8)
        job.action_parse_pdf()
        job.action_generate_imposition()
        job.action_lock()
        data = job.get_production_data()
        self.assertEqual(data["state"], "locked")
        self.assertEqual(data["n_up"], 4)
        self.assertEqual(data["press_id"], self.press.id)
        self.assertEqual(data["sheet_size_mm"],
                         (job.sheet_width, job.sheet_height))
        # locked jobs refuse layout edits
        with self.assertRaises(UserError):
            job.layout_json = job.layout_json or "{}"
