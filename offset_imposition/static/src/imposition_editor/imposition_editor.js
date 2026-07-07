import { Component, useRef, useState } from "@odoo/owl";
import { registry } from "@web/core/registry";
import { standardFieldProps } from "@web/views/fields/standard_field_props";

const SNAP_MM = 1;

/**
 * Interactive plate layout editor for imposition.job.layout_json.
 *
 * Renders the press plate as an SVG (viewBox in millimeters): plate
 * outline, sheet, gripper / side-lay / tail margin zones and one cell per
 * placement, filled with the PDF page thumbnail. Placements can be
 * dragged (1 mm snapping, kept inside the sheet) and rotated by 90°.
 * Every change is serialized back into the Text field through
 * record.update(), so the regular form save/discard flow persists it via
 * the field's Python inverse.
 */
export class ImpositionEditor extends Component {
    static template = "offset_imposition.ImpositionEditor";
    static props = { ...standardFieldProps };

    setup() {
        this.svgRef = useRef("svg");
        this.state = useState({
            signature: 1,
            surface: "front",
            selectedId: null,
            zoom: 1.0,
            // transient position of the placement being dragged (mm)
            drag: null,
        });
        this.onWindowPointerMove = this.onPointerMove.bind(this);
        this.onWindowPointerUp = this.onPointerUp.bind(this);
    }

    // ------------------------------------------------------------------
    // Layout data
    // ------------------------------------------------------------------
    get layout() {
        const raw = this.props.record.data[this.props.name];
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch {
            return null;
        }
    }

    get isEditable() {
        return !this.props.readonly && this.layout?.state !== "locked";
    }

    get signatures() {
        return this.layout?.signatures || [1];
    }

    get surfaces() {
        const surfaces = new Set(
            (this.layout?.placements || [])
                .filter((p) => p.signature === this.state.signature)
                .map((p) => p.surface)
        );
        return ["front", "back"].filter((s) => surfaces.has(s));
    }

    get visiblePlacements() {
        return (this.layout?.placements || []).filter(
            (p) =>
                p.signature === this.state.signature &&
                p.surface === this.state.surface
        );
    }

    /** Placements converted to SVG (top-down) coordinates for rendering. */
    get renderPlacements() {
        const layout = this.layout;
        if (!layout) {
            return [];
        }
        const plateH = layout.plate.height;
        return this.visiblePlacements.map((p) => {
            const drag =
                this.state.drag && this.state.drag.id === p.id
                    ? this.state.drag
                    : null;
            const x = drag ? drag.x : p.x;
            const y = drag ? drag.y : p.y;
            const sideways = p.rotation % 180 !== 0;
            return {
                ...p,
                svgX: x,
                svgY: plateH - y - p.h,
                cx: x + p.w / 2,
                cy: plateH - y - p.h / 2,
                imgW: sideways ? p.h : p.w,
                imgH: sideways ? p.w : p.h,
                thumbUrl: p.page_id
                    ? `/web/image/imposition.page/${p.page_id}/thumbnail`
                    : null,
                selected: this.state.selectedId === p.id,
            };
        });
    }

    get selectedPlacement() {
        return this.visiblePlacements.find(
            (p) => p.id === this.state.selectedId
        );
    }

    /** Margin overlay rectangles in SVG coordinates. */
    get marginZones() {
        const layout = this.layout;
        if (!layout) {
            return [];
        }
        const { sheet, margins, plate } = layout;
        const top = plate.height - sheet.y - sheet.height;
        return [
            // gripper (bottom edge of the sheet)
            {
                x: sheet.x,
                y: plate.height - sheet.y - margins.gripper,
                w: sheet.width,
                h: margins.gripper,
                kind: "gripper",
                label: "GRIPPER",
            },
            // tail
            { x: sheet.x, y: top, w: sheet.width, h: margins.tail, kind: "margin" },
            // sides
            { x: sheet.x, y: top, w: margins.side, h: sheet.height, kind: "margin" },
            {
                x: sheet.x + sheet.width - margins.side,
                y: top,
                w: margins.side,
                h: sheet.height,
                kind: "margin",
            },
        ];
    }

    get sheetRect() {
        const { sheet, plate } = this.layout;
        return {
            x: sheet.x,
            y: plate.height - sheet.y - sheet.height,
            w: sheet.width,
            h: sheet.height,
        };
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------
    commitLayout(mutate) {
        const layout = this.layout;
        if (!layout || !this.isEditable) {
            return;
        }
        mutate(layout);
        this.props.record.update({
            [this.props.name]: JSON.stringify(layout),
        });
    }

    rotateSelected() {
        const id = this.state.selectedId;
        if (!id) {
            return;
        }
        this.commitLayout((layout) => {
            const p = layout.placements.find((pl) => pl.id === id);
            if (!p) {
                return;
            }
            // rotate 90° around the cell center: swap footprint w/h
            const cx = p.x + p.w / 2;
            const cy = p.y + p.h / 2;
            [p.w, p.h] = [p.h, p.w];
            p.x = Math.round((cx - p.w / 2) / SNAP_MM) * SNAP_MM;
            p.y = Math.round((cy - p.h / 2) / SNAP_MM) * SNAP_MM;
            p.rotation = (p.rotation + 90) % 360;
        });
    }

    // ------------------------------------------------------------------
    // Pointer interaction
    // ------------------------------------------------------------------
    clientToMm(event) {
        const svg = this.svgRef.el;
        const point = new DOMPoint(event.clientX, event.clientY);
        const inverse = svg.getScreenCTM().inverse();
        const local = point.matrixTransform(inverse);
        // SVG y is top-down; plate coordinates are bottom-up
        return { x: local.x, y: this.layout.plate.height - local.y };
    }

    onPlacementPointerDown(placement, event) {
        this.state.selectedId = placement.id;
        if (!this.isEditable) {
            return;
        }
        event.preventDefault();
        const start = this.clientToMm(event);
        this.state.drag = {
            id: placement.id,
            x: placement.x,
            y: placement.y,
            offsetX: start.x - placement.x,
            offsetY: start.y - placement.y,
        };
        window.addEventListener("pointermove", this.onWindowPointerMove);
        window.addEventListener("pointerup", this.onWindowPointerUp);
    }

    onPointerMove(event) {
        const drag = this.state.drag;
        if (!drag) {
            return;
        }
        const placement = this.visiblePlacements.find(
            (p) => p.id === drag.id
        );
        if (!placement) {
            return;
        }
        const point = this.clientToMm(event);
        const { sheet } = this.layout;
        const snap = (v) => Math.round(v / SNAP_MM) * SNAP_MM;
        // clamp the cell inside the sheet
        drag.x = snap(
            Math.min(
                Math.max(point.x - drag.offsetX, sheet.x),
                sheet.x + sheet.width - placement.w
            )
        );
        drag.y = snap(
            Math.min(
                Math.max(point.y - drag.offsetY, sheet.y),
                sheet.y + sheet.height - placement.h
            )
        );
    }

    onPointerUp() {
        window.removeEventListener("pointermove", this.onWindowPointerMove);
        window.removeEventListener("pointerup", this.onWindowPointerUp);
        const drag = this.state.drag;
        if (!drag) {
            return;
        }
        this.state.drag = null;
        this.commitLayout((layout) => {
            const p = layout.placements.find((pl) => pl.id === drag.id);
            if (p) {
                p.x = drag.x;
                p.y = drag.y;
            }
        });
    }

    onSvgPointerDown(event) {
        // click on empty plate area: deselect
        if (event.target === this.svgRef.el || event.target.dataset.bg) {
            this.state.selectedId = null;
        }
    }

    onKeyDown(event) {
        if (event.key === "r" || event.key === "R") {
            event.preventDefault();
            this.rotateSelected();
        }
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------
    setSignature(signature) {
        this.state.signature = signature;
        this.state.selectedId = null;
        if (!this.surfaces.includes(this.state.surface)) {
            this.state.surface = this.surfaces[0] || "front";
        }
    }

    setSurface(surface) {
        this.state.surface = surface;
        this.state.selectedId = null;
    }

    zoomIn() {
        this.state.zoom = Math.min(4, this.state.zoom * 1.25);
    }

    zoomOut() {
        this.state.zoom = Math.max(0.25, this.state.zoom / 1.25);
    }

    get svgWidthPx() {
        return this.layout.plate.width * this.state.zoom * 1.6;
    }
}

export const impositionEditor = {
    component: ImpositionEditor,
    displayName: "Imposition Plate Editor",
    supportedTypes: ["text"],
};

registry.category("fields").add("imposition_editor", impositionEditor);
