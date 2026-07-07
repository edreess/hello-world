package com.pixelpress.tycoon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation core: time, economy, jobs, machines, workers, markets.
 * All time is in game hours; 1 day = 24h, 1 month = 30 days, 1 year = 12 months.
 */
public class Game {
    public static final int SLOTS = 6;
    public static final int MAX_WORKERS = 8;
    public static final int MAX_ACTIVE_JOBS = 8;
    public static final int MAX_OFFERS = 6;
    public static final double HOURS_PER_DAY = 24;
    public static final double DAYS_PER_MONTH = 30;
    public static final int MONTHLY_RENT = 1500;
    public static final double[] SPEED_HOURS_PER_SEC = {0, 6, 14, 32};
    public static final String[] SPEED_LABELS = {"II", ">", ">>", ">>>"};

    public final Random rng = new Random();

    public double money = 25000;
    public int rep = 0;
    public double hours = 8;            // start at 08:00 day 1
    public int speedIdx = 1;
    public int lastProcessedMonth = 0;  // absolute month index, for rollover
    public double nextOfferHour = 0;

    public final Machine[] slots = new Machine[SLOTS];
    public final List<Worker> workers = new ArrayList<>();
    public final List<Job> offers = new ArrayList<>();
    public final List<Job> active = new ArrayList<>();
    public final List<Worker> candidates = new ArrayList<>();

    /** Transient UI notifications. */
    public static class Toast {
        public final String text;
        public double ttl = 3.5;
        public Toast(String t) { text = t; }
    }
    public final List<Toast> toasts = new ArrayList<>();

    public boolean dirty; // needs saving

    // ---------------------------------------------------------------- setup

    public void newGame() {
        money = 25000;
        rep = 0;
        hours = 8;
        lastProcessedMonth = 0;
        slots[0] = new Machine("d1", 0);
        slots[1] = new Machine("f1", 1);
        Worker w = Worker.random(rng, 0);
        w.name = "Sam Roe";
        workers.add(w);
        refreshCandidates();
        for (int i = 0; i < 3; i++) generateOffer();
        nextOfferHour = hours + 30;
        toast("Welcome to your print shop!");
        toast("Open JOBS and accept a contract.");
    }

    // ---------------------------------------------------------------- time

    public int day() { return (int) (hours / HOURS_PER_DAY); }
    public int monthAbs() { return (int) (day() / DAYS_PER_MONTH); }
    public int year() { return monthAbs() / 12 + 1; }
    public int month() { return monthAbs() % 12 + 1; }
    public int dayOfMonth() { return (int) (day() % DAYS_PER_MONTH) + 1; }

    public String dateString() {
        return "Y" + year() + " M" + month() + " D" + dayOfMonth();
    }

    /** Seasonal demand multiplier for a market, ~0.55..1.45. */
    public double demand(Catalog.Market m) {
        return 1.0 + 0.45 * Math.sin(2 * Math.PI * (month() - 1 + m.seasonPhase) / 12.0);
    }

    public boolean marketUnlocked(Catalog.Market m) { return rep >= m.repReq; }

    public int companyLevel() { return 1 + rep / 250; }

    // ---------------------------------------------------------------- update

    public void update(double dtSeconds) {
        double dt = dtSeconds * SPEED_HOURS_PER_SEC[speedIdx]; // game hours
        for (int i = toasts.size() - 1; i >= 0; i--) {
            toasts.get(i).ttl -= dtSeconds;
            if (toasts.get(i).ttl <= 0) toasts.remove(i);
        }
        if (dt <= 0) return;
        hours += dt;

        advanceJobs(dt);
        expireOffers();

        if (hours >= nextOfferHour) {
            generateOffer();
            // demand-weighted cadence: offers roughly every 1-3 days
            nextOfferHour = hours + (28 + rng.nextInt(44));
        }

        if (monthAbs() > lastProcessedMonth) {
            lastProcessedMonth = monthAbs();
            monthlyRollover();
        }
    }

    private void advanceJobs(double dt) {
        for (int i = active.size() - 1; i >= 0; i--) {
            Job j = active.get(i);
            if (j.phase == Job.PH_UNASSIGNED || j.machine == null || j.worker == null) continue;
            Catalog.JobTemplate t = j.tpl();
            double remaining = dt;
            while (remaining > 0 && j.phase != -1) {
                if (j.phase == Job.PH_PREPRESS) {
                    double step = Math.min(remaining, j.phaseLeft);
                    j.phaseLeft -= step;
                    remaining -= step;
                    j.worker.gainXpPublic(this, step * 0.5);
                    if (j.phaseLeft <= 0.0001) {
                        j.phase = Job.PH_PRINT;
                        j.phaseLeft = t.sheets;
                    }
                } else if (j.phase == Job.PH_PRINT) {
                    Catalog.MachineType mt = j.machine.type();
                    double speed = mt.speed * (0.6 + 0.8 * j.worker.skillFor(mt.cat) / 100.0);
                    double hoursNeeded = j.phaseLeft / speed;
                    double step = Math.min(remaining, hoursNeeded);
                    j.phaseLeft -= step * speed;
                    money -= step * mt.runCost;
                    remaining -= step;
                    j.worker.gainXpPublic(this, step);
                    if (j.phaseLeft <= 0.5) {
                        if (t.finishLevel > 0) {
                            j.phase = Job.PH_FINISH;
                            j.phaseLeft = t.sheets;
                        } else {
                            completeJob(j);
                            break;
                        }
                    }
                } else if (j.phase == Job.PH_FINISH) {
                    Machine fin = bestFinisher(t.finishLevel);
                    if (fin == null) { // finisher was sold mid-job: stall
                        remaining = 0;
                        break;
                    }
                    Catalog.MachineType ft = fin.type();
                    double speed = ft.speed * (0.6 + 0.8 * j.worker.finishing / 100.0);
                    double hoursNeeded = j.phaseLeft / speed;
                    double step = Math.min(remaining, hoursNeeded);
                    j.phaseLeft -= step * speed;
                    money -= step * ft.runCost;
                    remaining -= step;
                    j.worker.gainXpPublic(this, step * 0.8);
                    if (j.phaseLeft <= 0.5) {
                        completeJob(j);
                        break;
                    }
                }
            }
        }
    }

    private void expireOffers() {
        for (int i = offers.size() - 1; i >= 0; i--) {
            if (hours > offers.get(i).offerExpiresHour) offers.remove(i);
        }
    }

    private void monthlyRollover() {
        int wages = 0;
        for (Worker w : workers) wages += w.salary;
        money -= wages + MONTHLY_RENT;
        toast("Month end: -$" + fmt(wages) + " wages, -$" + fmt(MONTHLY_RENT) + " rent");
        refreshCandidates();
        if (money < 0) toast("WARNING: you are in debt!");
        dirty = true;
    }

    // ---------------------------------------------------------------- offers

    public void generateOffer() {
        if (offers.size() >= MAX_OFFERS) return;
        // pick an unlocked market weighted by demand
        List<Catalog.Market> open = new ArrayList<>();
        for (Catalog.Market m : Catalog.MARKETS) if (marketUnlocked(m)) open.add(m);
        if (open.isEmpty()) return;
        double totalW = 0;
        for (Catalog.Market m : open) totalW += demand(m);
        double pick = rng.nextDouble() * totalW;
        Catalog.Market chosen = open.get(0);
        for (Catalog.Market m : open) {
            pick -= demand(m);
            if (pick <= 0) { chosen = m; break; }
        }
        // clients only bring work the shop can plausibly deliver at its current fame
        int qCap = 45 + rep / 10;
        List<Catalog.JobTemplate> pool = new ArrayList<>();
        for (Catalog.JobTemplate t : Catalog.JOBS)
            if (t.market == chosen.id && t.qReq <= qCap) pool.add(t);
        if (pool.isEmpty())
            for (Catalog.JobTemplate t : Catalog.JOBS) if (t.market == chosen.id) pool.add(t);
        Catalog.JobTemplate t = pool.get(rng.nextInt(pool.size()));

        Job j = new Job();
        j.templateId = t.id;
        double payMul = (0.85 + rng.nextDouble() * 0.3) * (0.8 + 0.2 * demand(chosen));
        j.pay = (int) (t.basePay * payMul);
        j.offerExpiresHour = hours + (72 + rng.nextInt(72));
        offers.add(j);
    }

    /** Why a job offer can't be accepted, or null if it can. */
    public String acceptBlocker(Job j) {
        Catalog.JobTemplate t = j.tpl();
        if (active.size() >= MAX_ACTIVE_JOBS) return "Job queue full";
        if (t.finishLevel > 0 && bestFinisher(t.finishLevel) == null)
            return "Needs finishing Lv" + t.finishLevel;
        if (!hasPressFor(t.catReq))
            return "Needs " + (t.catReq == Catalog.CAT_OFFSET ? "offset" : "digital") + " press";
        return null;
    }

    public boolean acceptOffer(Job j) {
        if (acceptBlocker(j) != null) return false;
        offers.remove(j);
        j.acceptedHour = hours;
        j.deadlineHour = hours + j.tpl().deadlineDays * HOURS_PER_DAY;
        j.phase = Job.PH_UNASSIGNED;
        active.add(j);
        dirty = true;
        return true;
    }

    private boolean hasPressFor(int catReq) {
        for (Machine m : slots) {
            if (m == null) continue;
            int c = m.type().cat;
            boolean isPress = c == Catalog.CAT_DIGITAL || c == Catalog.CAT_OFFSET;
            if (!isPress) continue;
            if (catReq == Catalog.REQ_ANY || c == catReq) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- assignment

    public List<Machine> compatiblePresses(Job j, boolean idleOnly) {
        List<Machine> out = new ArrayList<>();
        int req = j.tpl().catReq;
        for (Machine m : slots) {
            if (m == null) continue;
            int c = m.type().cat;
            if (c != Catalog.CAT_DIGITAL && c != Catalog.CAT_OFFSET) continue;
            if (req != Catalog.REQ_ANY && c != req) continue;
            if (idleOnly && m.busy()) continue;
            out.add(m);
        }
        return out;
    }

    public List<Worker> idleWorkers() {
        List<Worker> out = new ArrayList<>();
        for (Worker w : workers) if (w.job == null) out.add(w);
        return out;
    }

    public void startJob(Job j, Machine m, Worker w) {
        j.machine = m;
        j.worker = w;
        m.job = j;
        w.job = j;
        Catalog.JobTemplate t = j.tpl();
        double setup = t.setupHours;
        if (m.type().cat == Catalog.CAT_OFFSET) {
            Machine ctp = bestCtp();
            int tier = ctp == null ? 0 : ctp.type().tier;
            setup *= (1.0 - 0.18 * tier);
            setup *= (1.0 - 0.3 * w.prepress / 100.0);
            if (ctp == null) setup *= 1.6; // stripping film by hand
        } else {
            setup *= 0.5 * (1.0 - 0.3 * w.prepress / 100.0);
        }
        j.phase = Job.PH_PREPRESS;
        j.phaseLeft = Math.max(0.2, setup);
        dirty = true;
    }

    public Machine bestCtp() {
        Machine best = null;
        for (Machine m : slots)
            if (m != null && m.type().cat == Catalog.CAT_CTP
                    && (best == null || m.type().tier > best.type().tier)) best = m;
        return best;
    }

    public Machine bestFinisher(int minLevel) {
        Machine best = null;
        for (Machine m : slots)
            if (m != null && m.type().cat == Catalog.CAT_FINISH && m.type().tier >= minLevel
                    && (best == null || m.type().tier > best.type().tier)) best = m;
        return best;
    }

    // ---------------------------------------------------------------- completion

    private void completeJob(Job j) {
        Catalog.JobTemplate t = j.tpl();
        Catalog.MachineType mt = j.machine.type();
        double skill = j.worker.skillFor(mt.cat);
        double q = 0.5 * mt.quality + 0.45 * skill + rng.nextInt(17) - 8;
        if (mt.cat == Catalog.CAT_OFFSET) {
            Machine ctp = bestCtp();
            if (ctp != null) q += 3 * ctp.type().tier;
        }
        if (t.finishLevel > 0) q += 0.1 * j.worker.finishing;

        boolean late = hours > j.deadlineHour;
        double payMul;
        int repDelta;
        String grade;
        if (q >= t.qReq + 15) { payMul = 1.3; repDelta = (int) (t.repGain * 1.5); grade = "PERFECT"; }
        else if (q >= t.qReq) { payMul = 1.0; repDelta = t.repGain; grade = "Good"; }
        else if (q >= t.qReq - 12) { payMul = 0.8; repDelta = Math.max(1, t.repGain / 2); grade = "Passable"; }
        else { payMul = 0.4; repDelta = -Math.max(1, t.repGain / 2); grade = "REJECTED"; }
        if (late) { payMul *= 0.6; repDelta -= t.repGain / 2; grade += " (late)"; }

        int paid = (int) (j.pay * payMul);
        money += paid;
        int oldRep = rep;
        rep = Math.max(0, rep + repDelta);
        j.worker.gainXpPublic(this, 10 + t.repGain);

        toast(t.name + ": " + grade + "  +$" + fmt(paid) + (repDelta != 0 ? "  rep " + (repDelta > 0 ? "+" : "") + repDelta : ""));
        checkUnlocks(oldRep);

        j.machine.job = null;
        j.worker.job = null;
        j.machine = null;
        j.worker = null;
        active.remove(j);
        dirty = true;
    }

    private void checkUnlocks(int oldRep) {
        for (Catalog.Market m : Catalog.MARKETS)
            if (oldRep < m.repReq && rep >= m.repReq)
                toast("NEW MARKET UNLOCKED: " + m.name + "!");
        for (Catalog.MachineType mt : Catalog.MACHINES)
            if (oldRep < mt.repReq && rep >= mt.repReq && mt.repReq > 0)
                toast("Shop unlock: " + mt.name);
    }

    // ---------------------------------------------------------------- shop / staff actions

    public String buyBlocker(Catalog.MachineType t) {
        if (rep < t.repReq) return "Need " + t.repReq + " rep";
        if (money < t.price) return "Not enough cash";
        if (freeSlot() < 0) return "No free floor slot";
        return null;
    }

    public int freeSlot() {
        for (int i = 0; i < SLOTS; i++) if (slots[i] == null) return i;
        return -1;
    }

    public boolean buyMachine(Catalog.MachineType t) {
        if (buyBlocker(t) != null) return false;
        int s = freeSlot();
        slots[s] = new Machine(t.id, s);
        money -= t.price;
        toast("Installed " + t.name);
        dirty = true;
        return true;
    }

    public boolean sellMachine(Machine m) {
        if (m.busy()) { toast("Machine is busy!"); return false; }
        // don't allow selling a finisher a running job depends on
        for (Job j : active) {
            Catalog.JobTemplate t = j.tpl();
            if (j.phase != Job.PH_UNASSIGNED && t.finishLevel > 0) {
                Machine fin = bestFinisher(t.finishLevel);
                if (fin == m && countFinishers(t.finishLevel) == 1) {
                    toast("A running job needs it!");
                    return false;
                }
            }
        }
        slots[m.slot] = null;
        int refund = (int) (m.type().price * 0.4);
        money += refund;
        toast("Sold " + m.type().name + " +$" + fmt(refund));
        dirty = true;
        return true;
    }

    private int countFinishers(int minLevel) {
        int n = 0;
        for (Machine m : slots)
            if (m != null && m.type().cat == Catalog.CAT_FINISH && m.type().tier >= minLevel) n++;
        return n;
    }

    public void refreshCandidates() {
        candidates.clear();
        for (int i = 0; i < 3; i++) candidates.add(Worker.random(rng, rep));
    }

    public String hireBlocker(Worker c) {
        if (workers.size() >= MAX_WORKERS) return "Staff is full";
        if (money < c.salary * 2) return "Need $" + fmt(c.salary * 2);
        return null;
    }

    public boolean hire(Worker c) {
        if (hireBlocker(c) != null) return false;
        money -= c.salary * 2; // agency fee
        candidates.remove(c);
        workers.add(c);
        toast("Hired " + c.name + "!");
        dirty = true;
        return true;
    }

    public static final int TRAIN_COST = 2000;

    public boolean train(Worker w, int cat) {
        if (money < TRAIN_COST) { toast("Not enough cash"); return false; }
        if (w.job != null) { toast(w.name + " is working"); return false; }
        money -= TRAIN_COST;
        w.addSkill(cat, 5 + rng.nextInt(4));
        boolean lv = w.gainXp(40, rng);
        toast(w.name + " trained " + Catalog.CAT_NAMES[cat]
                + (lv ? " and leveled up!" : "!"));
        dirty = true;
        return true;
    }

    public boolean fire(Worker w) {
        if (w.job != null) { toast(w.name + " is working"); return false; }
        if (workers.size() <= 1) { toast("You need at least one worker"); return false; }
        workers.remove(w);
        money -= w.salary; // severance
        toast(w.name + " left. -$" + fmt(w.salary) + " severance");
        dirty = true;
        return true;
    }

    // ---------------------------------------------------------------- misc

    public void toast(String msg) {
        toasts.add(new Toast(msg));
        if (toasts.size() > 4) toasts.remove(0);
    }

    public static String fmt(long n) {
        StringBuilder sb = new StringBuilder(Long.toString(Math.abs(n)));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ',');
        if (n < 0) sb.insert(0, '-');
        return sb.toString();
    }
}
