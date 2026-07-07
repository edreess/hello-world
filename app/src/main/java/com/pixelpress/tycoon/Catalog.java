package com.pixelpress.tycoon;

/** Static game data: machine types, markets, job templates, worker names. */
public final class Catalog {

    // Machine categories
    public static final int CAT_DIGITAL = 0;
    public static final int CAT_OFFSET = 1;
    public static final int CAT_CTP = 2;
    public static final int CAT_FINISH = 3;
    public static final String[] CAT_NAMES = {"Digital", "Offset", "Prepress", "Finishing"};

    // Job category requirement
    public static final int REQ_ANY = -1;

    public static final class MachineType {
        public final String id, name, desc;
        public final int cat, tier, price, speed, quality, runCost, repReq;

        MachineType(String id, int cat, int tier, String name, int price, int speed,
                    int quality, int runCost, int repReq, String desc) {
            this.id = id; this.cat = cat; this.tier = tier; this.name = name;
            this.price = price; this.speed = speed; this.quality = quality;
            this.runCost = runCost; this.repReq = repReq; this.desc = desc;
        }
    }

    public static final MachineType[] MACHINES = {
        // id, cat, tier, name, price, speed(sheets/h), quality, runCost($/h), repReq
        new MachineType("d1", CAT_DIGITAL, 1, "InkJet Desk 100", 3500, 350, 42, 6, 0,
                "Entry desktop digital press. Slow but cheap to run."),
        new MachineType("d2", CAT_DIGITAL, 2, "ProDigit 2400", 16000, 1000, 62, 14, 100,
                "Production digital press. Solid speed and quality."),
        new MachineType("d3", CAT_DIGITAL, 3, "UltraJet X", 48000, 2600, 80, 30, 500,
                "High-end inkjet line. Superb quality short runs."),
        new MachineType("o1", CAT_OFFSET, 1, "Relic 1C Offset", 14000, 3000, 55, 22, 50,
                "Old single-color offset. Long setup, fast runs."),
        new MachineType("o2", CAT_OFFSET, 2, "Sheetfed Quad 4C", 45000, 7000, 74, 45, 300,
                "Four-color sheetfed workhorse."),
        new MachineType("o3", CAT_OFFSET, 3, "Web Titan 5000", 130000, 16000, 88, 90, 800,
                "Heatset web press. Massive throughput."),
        new MachineType("c1", CAT_CTP, 1, "PlateMate Manual", 6000, 0, 0, 0, 50,
                "Manual platesetter. -18% offset setup, +3 quality."),
        new MachineType("c2", CAT_CTP, 2, "CTP Sprint", 22000, 0, 0, 0, 300,
                "Thermal CTP. -36% offset setup, +6 quality."),
        new MachineType("c3", CAT_CTP, 3, "CTP Quantum", 55000, 0, 0, 0, 700,
                "Violet laser CTP. -54% offset setup, +9 quality."),
        new MachineType("f1", CAT_FINISH, 1, "Guillotine Cutter", 4000, 2000, 0, 4, 0,
                "Cutting only. Handles simple finishing (Lv1)."),
        new MachineType("f2", CAT_FINISH, 2, "FoldStitch Line", 20000, 5000, 0, 10, 150,
                "Folding + saddle stitch (finishing Lv2)."),
        new MachineType("f3", CAT_FINISH, 3, "BindMaster Pro", 52000, 9000, 0, 20, 600,
                "Perfect binding + die cut (finishing Lv3)."),
    };

    public static MachineType machine(String id) {
        for (MachineType m : MACHINES) if (m.id.equals(id)) return m;
        return null;
    }

    public static final class Market {
        public final int id, repReq;
        public final String name;
        public final double seasonPhase; // shifts the demand sine per market

        Market(int id, String name, int repReq, double seasonPhase) {
            this.id = id; this.name = name; this.repReq = repReq; this.seasonPhase = seasonPhase;
        }
    }

    public static final Market[] MARKETS = {
        new Market(0, "Commercial", 0, 0.0),
        new Market(1, "Publishing", 150, 3.0),
        new Market(2, "Packaging", 400, 6.0),
        new Market(3, "Labels", 800, 9.0),
    };

    public static final class JobTemplate {
        public final String id, name;
        public final int market, sheets, basePay, qReq, finishLevel, catReq, deadlineDays, repGain;
        public final double setupHours;

        JobTemplate(String id, int market, String name, int sheets, int basePay, int qReq,
                    double setupHours, int finishLevel, int catReq, int deadlineDays, int repGain) {
            this.id = id; this.market = market; this.name = name; this.sheets = sheets;
            this.basePay = basePay; this.qReq = qReq; this.setupHours = setupHours;
            this.finishLevel = finishLevel; this.catReq = catReq;
            this.deadlineDays = deadlineDays; this.repGain = repGain;
        }
    }

    public static final JobTemplate[] JOBS = {
        // Commercial
        new JobTemplate("bc", 0, "Business Cards", 500, 650, 30, 1, 1, REQ_ANY, 6, 4),
        new JobTemplate("fl", 0, "Flyers A5", 2000, 1400, 35, 1, 0, REQ_ANY, 7, 5),
        new JobTemplate("br", 0, "Brochures", 5000, 3200, 50, 2, 1, REQ_ANY, 8, 8),
        new JobTemplate("po", 0, "Posters B2", 1500, 2200, 55, 2, 0, REQ_ANY, 6, 7),
        // Publishing
        new JobTemplate("zi", 1, "Zine Run", 8000, 5200, 45, 3, 2, REQ_ANY, 10, 10),
        new JobTemplate("nv", 1, "Novel Paperback", 20000, 11000, 55, 5, 2, CAT_OFFSET, 14, 14),
        new JobTemplate("mg", 1, "Glossy Magazine", 35000, 20000, 70, 6, 2, CAT_OFFSET, 14, 18),
        new JobTemplate("tb", 1, "Textbook", 60000, 34000, 65, 7, 3, CAT_OFFSET, 20, 22),
        // Packaging
        new JobTemplate("cs", 2, "Carton Sleeves", 12000, 9500, 60, 5, 2, CAT_OFFSET, 10, 14),
        new JobTemplate("fb", 2, "Folding Boxes", 25000, 19000, 70, 6, 3, CAT_OFFSET, 14, 20),
        new JobTemplate("lx", 2, "Luxury Boxes", 40000, 36000, 82, 8, 3, CAT_OFFSET, 18, 28),
        // Labels
        new JobTemplate("st", 3, "Sticker Rolls", 6000, 7500, 65, 2, 1, CAT_DIGITAL, 8, 14),
        new JobTemplate("wl", 3, "Wine Labels", 10000, 14000, 80, 3, 2, CAT_DIGITAL, 10, 20),
        new JobTemplate("hl", 3, "Holo Labels", 15000, 26000, 88, 4, 2, CAT_DIGITAL, 12, 28),
    };

    public static JobTemplate job(String id) {
        for (JobTemplate j : JOBS) if (j.id.equals(id)) return j;
        return null;
    }

    public static final String[] WORKER_NAMES = {
        "Sam Roe", "Ada Kim", "Leo Font", "Mia Serif", "Rex Bold", "Ivy Chan",
        "Gus Platen", "Nora Web", "Tom Duplex", "Zoe Raster", "Kai Offset", "Fay Trim",
        "Ben Crop", "Lia Bleed", "Max Emboss", "Sue Kern", "Dov Folio", "Ana Gloss",
    };

    public static final String[] TITLES = {"Trainee", "Operator", "Senior Op", "Press Master", "Print Legend"};

    public static String titleFor(int level) {
        if (level >= 15) return TITLES[4];
        if (level >= 10) return TITLES[3];
        if (level >= 6) return TITLES[2];
        if (level >= 3) return TITLES[1];
        return TITLES[0];
    }

    private Catalog() {}
}
