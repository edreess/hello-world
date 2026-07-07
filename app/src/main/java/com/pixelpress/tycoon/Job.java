package com.pixelpress.tycoon;

/** A print job: either an offer on the market, or an accepted (possibly running) job. */
public class Job {
    public static final int PH_UNASSIGNED = 0;
    public static final int PH_PREPRESS = 1;
    public static final int PH_PRINT = 2;
    public static final int PH_FINISH = 3;

    public String templateId;
    public int pay;                 // negotiated pay for this instance
    public int phase = PH_UNASSIGNED;
    public double phaseLeft;        // hours left in prepress, or sheets left in print/finish
    public double acceptedHour = -1;
    public double deadlineHour;     // absolute game hour
    public double offerExpiresHour; // only while an offer
    public Machine machine;         // press assigned (transient link, rebuilt on load)
    public Worker worker;

    public Job() {}

    public Catalog.JobTemplate tpl() { return Catalog.job(templateId); }

    /** Total 0..1 progress across all phases, for the progress bar. */
    public double progress() {
        Catalog.JobTemplate t = tpl();
        double setupW = 0.15, printW = t.finishLevel > 0 ? 0.65 : 0.85;
        double finishW = t.finishLevel > 0 ? 0.20 : 0.0;
        switch (phase) {
            case PH_PREPRESS: {
                double d = t.setupHours <= 0 ? 1 : 1 - phaseLeft / Math.max(0.01, t.setupHours);
                return setupW * Math.max(0, Math.min(1, d));
            }
            case PH_PRINT:
                return setupW + printW * (1 - phaseLeft / t.sheets);
            case PH_FINISH:
                return setupW + printW + finishW * (1 - phaseLeft / t.sheets);
            default:
                return 0;
        }
    }

    public String phaseName() {
        switch (phase) {
            case PH_PREPRESS: return "Prepress";
            case PH_PRINT: return "Printing";
            case PH_FINISH: return "Finishing";
            default: return "Assign!";
        }
    }
}
