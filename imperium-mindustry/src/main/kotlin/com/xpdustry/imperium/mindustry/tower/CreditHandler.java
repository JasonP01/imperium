package imperiumV2.gamemode.TowerDefence;

import arc.Events;
import imperiumV2.ConsoleColor;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.Item;

import static mindustry.Vars.state;

public class CreditHandler {
    public final Team team;

    public final float centerX;
    public final boolean left;

    public float credits = 0;

    public CreditHandler() {
        this.team = Vars.state.rules.defaultTeam;
        centerX = Vars.world.width() * 8f;
        left = true;
    }

    public CreditHandler(Team team) {
        this.team = team;
        centerX = Vars.world.width() * 4f;
        if (!team.active()) {
            left = true;
            return;
        }

        Boolean test = null;
        for (var c : team.cores()) {
            boolean left = c.x <= centerX;
            if (test == null) {
                test = left;
            } else if (test != left) {
                ConsoleColor.error.sout("Core between left and right!");
                Events.fire(new EventType.GameOverEvent(Team.derelict));
            }
        }
        left = Boolean.TRUE.equals(test);
    }

    public void addPassiveIncome() {
        credits += TD.PassiveIncome;
    }

    public void unitDeath(Unit u) {
        boolean udLeft = u.x <= centerX;
        if (udLeft == left) {//if team left and unit died left, or team right and unit died right
            var ut = TD.unitTiers.get(u.type);
            if (ut == null) {
                ConsoleColor.debug.sout("unit tiers null for " + u.type);
            } else {
                credits += ut.credits;
                ut.died(u);
            }
        }
    }

    public void tick() {
        var c = team.core();
        if (c == null) return;//gameover
        var ci = c.items;
        //
        for (Item i : TD.buildItems) {
            //find how much was used
            int used = TD.coreItems[i.id] - ci.get(i);//always positive
            credits -= used * TD.itemCost[i.id];
            //calculate item amount, can be lower than previously even without usage
            float creditsAvail = credits / TD.buildItems.size;
            int items = (int) (creditsAvail / TD.itemCost[i.id]);
            ci.set(i, items);
            //save item count
            TD.coreItems[i.id] = items;
        }
        //
        long cur = System.currentTimeMillis();
        if (TD.missionTimeout < cur) {
            Vars.state.rules.mission = "[white]$$$: [lightgray]" + (int) credits +
                    "\n[white]Wave [lightgray]" + state.wave + "[white]: [lightgray]" + getWaveTime() +
                    "\n[white]" + Iconc.units + "[scarlet]" + Iconc.add + "[white]: [lightgray]" + String.format("%.2fx", TD.healthMulti);
            for (Player p : Groups.player) {
                if (p.team() == team)
                    Call.setRules(p.con, state.rules);
            }
        }
    }

    private static final StringBuilder ibuild = new StringBuilder();

    public static String getWaveTime() {
        ibuild.setLength(0);
        int i = (int) state.wavetime / 60;
        int m = i / 60;
        int s = i % 60;
        if (m > 0) {
            ibuild.append(m);
            ibuild.append(":");
            if (s < 10)
                ibuild.append("0");
        }
        ibuild.append(s);
        return ibuild.toString();
    }
}