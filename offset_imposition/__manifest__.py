{
    "name": "Offset Imposition Planner",
    "summary": "Imposition planning for offset printing: PDF analysis, "
               "press configuration and a visual plate layout editor",
    "description": """
Offset Imposition Planner
=========================
Plan offset printing impositions:

* Import a job PDF and extract page count, trim size and orientation.
* Configure offset presses (plate size, sheet limits, gripper margins).
* Generate imposition signatures (n-up, work-and-turn, work-and-tumble, ...)
  aligned with CIP4 XJDF prepress concepts (BinderySignature, Layout,
  ContentObject, RunList).
* Adjust the plate layout in a visual drag-and-drop editor with real page
  thumbnails.
* Expose plates / sheets / passes results for future quotation, sales and
  manufacturing modules.
""",
    "category": "Manufacturing",
    "version": "19.0.1.0.0",
    "license": "LGPL-3",
    "author": "Volubilix",
    "website": "https://github.com/edreess/hello-world",
    "depends": ["mail", "web"],
    "external_dependencies": {
        # PyMuPDF (imported as "fitz") is used for PDF page analysis and
        # thumbnail rasterization: pip install PyMuPDF
        "python": ["fitz"],
    },
    "data": [
        "security/imposition_security.xml",
        "security/ir.model.access.csv",
        "data/imposition_template_data.xml",
        "views/press_config_views.xml",
        "views/imposition_template_views.xml",
        "views/imposition_job_views.xml",
        "views/imposition_menus.xml",
    ],
    "demo": [
        "demo/press_config_demo.xml",
    ],
    "assets": {
        "web.assets_backend": [
            "offset_imposition/static/src/imposition_editor/**/*",
        ],
    },
    "application": True,
    "installable": True,
}
