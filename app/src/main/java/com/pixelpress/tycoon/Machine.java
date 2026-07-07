package com.pixelpress.tycoon;

/** A machine instance installed in a floor slot. */
public class Machine {
    public String typeId;
    public int slot;      // 0..Game.SLOTS-1
    public Job job;       // job currently printing on it, or null
    public double animT;  // animation clock (not saved)

    public Machine() {}

    public Machine(String typeId, int slot) {
        this.typeId = typeId;
        this.slot = slot;
    }

    public Catalog.MachineType type() { return Catalog.machine(typeId); }

    public boolean busy() { return job != null; }
}
