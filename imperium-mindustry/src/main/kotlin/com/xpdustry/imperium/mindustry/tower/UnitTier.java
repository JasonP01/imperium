package imperiumV2.gamemode.TowerDefence;

import imperiumV2.gamemode.TowerDefence.CustomAI.CustomGroundAI;
import mindustry.content.StatusEffects;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import java.util.Objects;

//units
public final class UnitTier {
    public final int tier;
    public final UnitType downgrade;
    public final short credits;

    public UnitTier(short tier, UnitType downgrade, short credits) {
        this.tier = tier;
        this.downgrade = downgrade;
        this.credits = credits;
    }

    public void died(Unit u) {
        if (downgrade != null) {
            for (int i = 0; i < tier; i++) {
                Unit out = downgrade.create(u.team);
                if (u.tileOn() == null) {
                    return;
                }
                Tile spawn = TD.closestCenter(u.tileOn(), u.type.naval ? TD.FlowFieldType.Naval : TD.FlowFieldType.Ground);
                if (spawn == null)
                    spawn = u.tileOn();

                out.set(spawn.x * 8f + TD.rand.nextFloat(2) - 1f, spawn.y * 8f + TD.rand.nextFloat(2) - 1f);
                out.controller(new CustomGroundAI());
                out.apply(StatusEffects.invincible, 15);
                out.add();
            }
        }
        Call.label(Short.toString(credits), 0.75f, u.x, u.y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (UnitTier) obj;
        return this.tier == that.tier &&
                Objects.equals(this.downgrade, that.downgrade) &&
                this.credits == that.credits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tier, downgrade, credits);
    }

    @Override
    public String toString() {
        return "UnitTier[" +
                "tier=" + tier + ", " +
                "downgrade=" + downgrade + ", " +
                "credits=" + credits + ']';
    }

}
