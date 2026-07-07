package com.pixelpress.tycoon;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** JSON save/load via SharedPreferences. */
public final class SaveGame {
    private static final String PREFS = "pixelpress";
    private static final String KEY = "save_v1";

    public static Game loadOrNew(Context ctx) {
        Game g = new Game();
        try {
            String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw != null) {
                load(g, new JSONObject(raw));
                g.toast("Game loaded.");
                return g;
            }
        } catch (Exception e) {
            g = new Game(); // corrupt save: start over
        }
        g.newGame();
        return g;
    }

    public static void save(Context ctx, Game g) {
        try {
            SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            ed.putString(KEY, toJson(g).toString());
            ed.apply();
        } catch (Exception ignored) {
        }
    }

    private static JSONObject toJson(Game g) throws Exception {
        JSONObject o = new JSONObject();
        o.put("money", g.money);
        o.put("rep", g.rep);
        o.put("hours", g.hours);
        o.put("month", g.lastProcessedMonth);
        o.put("nextOffer", g.nextOfferHour);

        JSONArray ms = new JSONArray();
        for (Machine m : g.slots) {
            if (m == null) continue;
            JSONObject mo = new JSONObject();
            mo.put("type", m.typeId);
            mo.put("slot", m.slot);
            ms.put(mo);
        }
        o.put("machines", ms);

        JSONArray ws = new JSONArray();
        for (Worker w : g.workers) ws.put(workerJson(w));
        o.put("workers", ws);

        JSONArray cs = new JSONArray();
        for (Worker w : g.candidates) cs.put(workerJson(w));
        o.put("candidates", cs);

        JSONArray as = new JSONArray();
        for (Job j : g.active) {
            JSONObject jo = new JSONObject();
            jo.put("tpl", j.templateId);
            jo.put("pay", j.pay);
            jo.put("phase", j.phase);
            jo.put("left", j.phaseLeft);
            jo.put("deadline", j.deadlineHour);
            jo.put("mslot", j.machine == null ? -1 : j.machine.slot);
            jo.put("widx", j.worker == null ? -1 : g.workers.indexOf(j.worker));
            as.put(jo);
        }
        o.put("active", as);

        JSONArray os = new JSONArray();
        for (Job j : g.offers) {
            JSONObject jo = new JSONObject();
            jo.put("tpl", j.templateId);
            jo.put("pay", j.pay);
            jo.put("expires", j.offerExpiresHour);
            os.put(jo);
        }
        o.put("offers", os);
        return o;
    }

    private static JSONObject workerJson(Worker w) throws Exception {
        JSONObject wo = new JSONObject();
        wo.put("name", w.name);
        wo.put("press", w.press);
        wo.put("digital", w.digital);
        wo.put("prepress", w.prepress);
        wo.put("finishing", w.finishing);
        wo.put("level", w.level);
        wo.put("xp", w.xp);
        wo.put("salary", w.salary);
        wo.put("variant", w.spriteVariant);
        return wo;
    }

    private static Worker workerFrom(JSONObject wo) throws Exception {
        Worker w = new Worker();
        w.name = wo.getString("name");
        w.press = wo.getInt("press");
        w.digital = wo.getInt("digital");
        w.prepress = wo.getInt("prepress");
        w.finishing = wo.getInt("finishing");
        w.level = wo.getInt("level");
        w.xp = wo.getDouble("xp");
        w.salary = wo.getInt("salary");
        w.spriteVariant = wo.getInt("variant");
        return w;
    }

    private static void load(Game g, JSONObject o) throws Exception {
        g.money = o.getDouble("money");
        g.rep = o.getInt("rep");
        g.hours = o.getDouble("hours");
        g.lastProcessedMonth = o.getInt("month");
        g.nextOfferHour = o.getDouble("nextOffer");

        JSONArray ms = o.getJSONArray("machines");
        for (int i = 0; i < ms.length(); i++) {
            JSONObject mo = ms.getJSONObject(i);
            int slot = mo.getInt("slot");
            g.slots[slot] = new Machine(mo.getString("type"), slot);
        }

        JSONArray ws = o.getJSONArray("workers");
        for (int i = 0; i < ws.length(); i++) g.workers.add(workerFrom(ws.getJSONObject(i)));

        JSONArray cs = o.getJSONArray("candidates");
        for (int i = 0; i < cs.length(); i++) g.candidates.add(workerFrom(cs.getJSONObject(i)));

        JSONArray as = o.getJSONArray("active");
        for (int i = 0; i < as.length(); i++) {
            JSONObject jo = as.getJSONObject(i);
            Job j = new Job();
            j.templateId = jo.getString("tpl");
            j.pay = jo.getInt("pay");
            j.phase = jo.getInt("phase");
            j.phaseLeft = jo.getDouble("left");
            j.deadlineHour = jo.getDouble("deadline");
            int mslot = jo.getInt("mslot");
            int widx = jo.getInt("widx");
            if (j.phase != Job.PH_UNASSIGNED && mslot >= 0 && g.slots[mslot] != null
                    && widx >= 0 && widx < g.workers.size()) {
                j.machine = g.slots[mslot];
                j.worker = g.workers.get(widx);
                j.machine.job = j;
                j.worker.job = j;
            } else {
                j.phase = Job.PH_UNASSIGNED;
            }
            g.active.add(j);
        }

        JSONArray os = o.getJSONArray("offers");
        for (int i = 0; i < os.length(); i++) {
            JSONObject jo = os.getJSONObject(i);
            Job j = new Job();
            j.templateId = jo.getString("tpl");
            j.pay = jo.getInt("pay");
            j.offerExpiresHour = jo.getDouble("expires");
            g.offers.add(j);
        }
    }

    private SaveGame() {}
}
