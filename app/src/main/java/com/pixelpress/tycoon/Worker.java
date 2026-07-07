package com.pixelpress.tycoon;

import java.util.Random;

/** An employee with per-discipline skills (0-100). */
public class Worker {
    public String name;
    public int press, digital, prepress, finishing;
    public int level = 1;
    public double xp;
    public int salary;       // per month
    public int spriteVariant; // shirt color
    public Job job;          // currently assigned job, or null

    // wander animation state (not saved)
    public double wx, wy, tx, ty;
    public double animT;

    public Worker() {}

    public static Worker random(Random rng, int rep) {
        Worker w = new Worker();
        w.name = Catalog.WORKER_NAMES[rng.nextInt(Catalog.WORKER_NAMES.length)];
        int base = 15 + Math.min(45, rep / 20);
        w.press = clamp(base + rng.nextInt(25) - 8);
        w.digital = clamp(base + rng.nextInt(25) - 8);
        w.prepress = clamp(base + rng.nextInt(25) - 8);
        w.finishing = clamp(base + rng.nextInt(25) - 8);
        w.level = 1 + rng.nextInt(2);
        w.salary = w.computeSalary();
        w.spriteVariant = rng.nextInt(4);
        return w;
    }

    private static int clamp(int v) { return Math.max(5, Math.min(100, v)); }

    public int computeSalary() {
        return 700 + (press + digital + prepress + finishing) * 3 + level * 120;
    }

    public int skillSum() { return press + digital + prepress + finishing; }

    public String title() { return Catalog.titleFor(level); }

    /** Skill relevant to running a machine of the given category. */
    public int skillFor(int cat) {
        switch (cat) {
            case Catalog.CAT_DIGITAL: return digital;
            case Catalog.CAT_OFFSET: return press;
            case Catalog.CAT_CTP: return prepress;
            case Catalog.CAT_FINISH: return finishing;
            default: return 30;
        }
    }

    public void addSkill(int cat, int amount) {
        switch (cat) {
            case Catalog.CAT_DIGITAL: digital = clamp(digital + amount); break;
            case Catalog.CAT_OFFSET: press = clamp(press + amount); break;
            case Catalog.CAT_CTP: prepress = clamp(prepress + amount); break;
            case Catalog.CAT_FINISH: finishing = clamp(finishing + amount); break;
        }
    }

    /** Grants xp and announces level-ups via the game's toast queue. */
    public void gainXpPublic(Game g, double amount) {
        if (gainXp(amount, g.rng)) {
            g.toast(name + " leveled up! " + title() + " Lv" + level);
        }
    }

    /** Grants xp; returns true if the worker leveled up. */
    public boolean gainXp(double amount, Random rng) {
        xp += amount;
        boolean leveled = false;
        while (xp >= level * 100.0) {
            xp -= level * 100.0;
            level++;
            leveled = true;
            // level-up: +2 to two random skills, salary bump
            addSkill(rng.nextInt(4), 2);
            addSkill(rng.nextInt(4), 2);
            salary = computeSalary();
        }
        return leveled;
    }
}
