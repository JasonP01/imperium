package imperiumV2.gamemode.TowerDefence;

import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import imperiumV2.ConsoleColor;
import imperiumV2.TimeKeeper;
import imperiumV2.gamemode.TowerDefence.CustomAI.CustomGroundAI;
import imperiumV2.gamemode.TowerDefence.CustomAI.CustomNavalAI;
import imperiumV2.handlers.ContentHandler;
import imperiumV2.libs.Sql;
import imperiumV2.misc.DevKit;
import imperiumV2.security.PacketHandling.RequestPayloadHandler;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Administration;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static mindustry.Vars.pathfinder;
import static mindustry.Vars.state;

public class TD {
    //final
    public static final Random rand = new Random();
    public static final float multiIncreasePer15S = 1f / (600 / 15f); //600seconds = 10 minutes
    public static final int PassiveIncome = 25;
    //path
    public static final ObjectMap<Tile, Tile> closestCenterCache = new ObjectMap<>();

    public static final ObjectMap<UnitType, UnitTier> unitTiers = new ObjectMap<>();
    public static float healthMulti = 1;
    public static float[] origHealth = new float[0];
    //items
    public static CreditHandler[] CH = new CreditHandler[0];
    public static int ItemCount = 0;
    public static final Seq<Item> buildItems = new Seq<>();
    public static float[] itemCost = new float[0];
    public static TechType tt = TechType.Serpulo;

    public static int[] coreItems;
    public static long missionTimeout = System.currentTimeMillis();

    public enum TechType {
        Serpulo,
        Erekir,
        MixTech;

        public final Seq<Item> hiddenItems = new Seq<>();

        private static boolean init = false;

        public static void init() {
            if (!init) {
                init = true;
                //setup items
                TechType.Serpulo.hiddenItems.addAll(Items.erekirItems).sort(i -> i.id).removeAll(Items.serpuloItems);
                System.out.println("Serpulo hi: " + TechType.Serpulo.hiddenItems);
                TechType.Erekir.hiddenItems.add(Items.serpuloItems).sort(i -> i.id).removeAll(Items.erekirItems);
                System.out.println("Erekir hi: " + TechType.Erekir.hiddenItems);
            }
        }
    }

    public static void RegisterEvents() {
        Vars.pathfinder = new TDPathfinder();
        Pathfinder.costTypes.clear().add(FlowFieldType.Ground.getPatCost(), FlowFieldType.Ground.getPatCost(), FlowFieldType.Naval.getPatCost());

        Events.on(EventType.ServerLoadEvent.class, event -> {
            //setup unit health
            var unitTypes = Vars.content.units();
            origHealth = new float[unitTypes.size];
            for (var ut : unitTypes)
                origHealth[ut.id] = ut.health;
            //don't allow on path tiles
            Vars.netServer.admins.addActionFilter(af -> {
                if (af.tile == null) return true;
                boolean path = FlowFieldType.Ground.pathTileCosts[af.tile.array()] != 0 || FlowFieldType.Naval.pathTileCosts[af.tile.array()] != 0;
                if (path) {
                    Call.label(af.player.con, "[scarlet]" + Iconc.cancel, 1, af.tile.x * 8f, af.tile.y * 8f);
                } else if (af.type == Administration.ActionType.configure) {
                    if (af.tile.build instanceof LogicBlock.LogicBuild && af.config instanceof byte[]) {
                        String code = ContentHandler.readCompressedLogicCode((byte[]) af.config);
                        if (code != null && code.contains("ubind ")) {
                            af.player.sendMessage("[scarlet]> Unit Bind is disabled in td.");
                            return false;
                        }
                    }
                }
                return !path;
            });
            //get all unit credits
            HashMap<UnitType, Short> unitValues = new HashMap<>();
            Object[][] o = Sql.get_MM(new Sql.DataType[]{Sql.DataType.String, Sql.DataType.Short}, "SELECT `unit`, `credits` FROM Mindustry.tdUnitValue");
            if (o == null) {
                ConsoleColor.error.sout("\n\ntdUnitValue null\n\n");
                return;
            } else {
                for (Object[] o2 : o)
                    unitValues.put(Vars.content.unit((String) o2[0]), (short) o2[1]);
                for (var u : Vars.content.units()) {
                    unitValues.computeIfAbsent(u, u2 -> {
                        ConsoleColor.warning.sout("No unit value for " + u2.name);
                        Sql.run("INSERT INTO Mindustry.tdUnitValue (`unit`, `credits`) VALUES (?, ?)", u2.name, 0);
                        return (short) 0;
                    });
                }
            }
            o = Sql.get_MM(new Sql.DataType[]{Sql.DataType.String, Sql.DataType.Short, Sql.DataType.String, Sql.DataType.Short}, "SELECT name, tier, downgrade, credits FROM TD.unittier");
            if (o == null) {
                ConsoleColor.error.sout("\n\ntdUnitTier null\n\n");
            } else {
                for (Object[] o2 : o) {
                    String s = (String) o2[2];
                    unitTiers.put(Vars.content.unit((String) o2[0]), new UnitTier((short) o2[1], s.isEmpty() ? null : Vars.content.unit(s), (short) o2[3]));
                }
            }
            /*
            //get all unit factories / upgrades
            HashMap<UnitType, UnitType> upgrades = new HashMap<>();
            HashMap<UnitType, UnitType> downgrades = new HashMap<>();
            for (var b : Vars.content.blocks())
                switch (b) {
                    case Reconstructor r -> {
                        for (var uta : r.upgrades) {
                            upgrades.put(uta[0], uta[1]);
                            downgrades.put(uta[1], uta[0]);
                        }
                    }
                    case UnitAssembler ua -> {
                        for (var uap : ua.plans) {
                            for (PayloadStack ps : uap.requirements) {
                                if (ps.item instanceof UnitType ut) {
                                    System.out.println(uap.unit + " made by " + ut);
                                    upgrades.put(ut, uap.unit);
                                    downgrades.put(uap.unit, ut);
                                    break;
                                }
                            }
                        }
                    }
                    default -> {
                    }
                }
            //find missing t1's
            for (var a : downgrades.entrySet()) {
                if (upgrades.get(a.getKey()) == null) {//if ut has no upgrades
                    int tier = 5;//start at tier 5
                    var currentlyChecking = a.getKey();
                    var downgrade = a.getValue();
                    var ut = unitTiers.get(currentlyChecking);
                    if (ut == null) {
                        unitTiers.put(currentlyChecking, new UnitTier(tier--, downgrade, unitValues.get(currentlyChecking)));
                    } else {
                        unitTiers.put(currentlyChecking, new UnitTier(Math.min(tier--, ut.tier), downgrade, unitValues.get(currentlyChecking)));
                    }
                    while (downgrade != null) {
                        currentlyChecking = downgrade;
                        downgrade = downgrades.get(currentlyChecking);
                        ut = unitTiers.get(currentlyChecking);
                        if (ut == null) {
                            unitTiers.put(currentlyChecking, new UnitTier(tier--, downgrade, unitValues.get(currentlyChecking)));
                        } else {
                            unitTiers.put(currentlyChecking, new UnitTier(Math.max(tier--, ut.tier), downgrade, unitValues.get(currentlyChecking)));
                        }
                    }
                }
            }
            //check all units found
            for (var u : Vars.content.units()) {
                var ut = unitTiers.get(u);
                if (ut != null)
                    Sql.run("INSERT INTO TD.unitTier (`name`, `tier`, `downgrade`, `credits`) VALUES (?, ?, ?, ?)", u.name, ut.tier, ut.downgrade == null ? "" : ut.downgrade.name, ut.tier * 5);
            }
             */
            /*
            if (DevKit.dev)
                for (var u : Vars.content.units()) {
                    var ut = unitTiers.get(u);
                    if (ut == null) {
                        ConsoleColor.warning.sout("Unit Type " + u.name + " not found in unitTiers!");
                    } else {
                        Sql.run("UPDATE Mindustry.tdUnitValue set `credits` = ? WHERE `unit` = ? LIMIT 1", ut.tier * 5, u.name);
                    }
                }
             */
            //drop filter
            RequestPayloadHandler.addDropPayloadFilter((p, t) -> FlowFieldType.Ground.pathTileCosts[t.array()] == 0 && FlowFieldType.Naval.pathTileCosts[t.array()] == 0);
        });

        Events.on(EventType.WorldLoadEndEvent.class, ignore -> {
            System.out.println("registering ff");
            for (var fft : FlowFieldType.values())
                fft.init();
            //Pathfinder.costTypes.clear().add(FlowFieldType.Ground.getPatCost(), FlowFieldType.Ground.getPatCost(), FlowFieldType.Naval.getPatCost());
        });
        Events.on(EventType.SaveLoadEvent.class, ignore -> {
            //setup
            setup();
            //get approx unit health multi
            float wtSeconds = state.rules.waveSpacing / 60f;
            float s = (state.wave * wtSeconds)//seconds passed due to wave count
                    + wtSeconds //first wave is 2x, so add this to compensate
                    + ((state.rules.waveSpacing - state.wavetime) / 60f);//increase wt for seconds passed between waves
            float increases = s / 15;
            healthMulti += multiIncreasePer15S * increases;
            ConsoleColor.debug.sout("--- fix unit multi ---");
            ConsoleColor.debug.sout("waves: " + state.wave);
            ConsoleColor.debug.sout("Seconds passed: " + s);
            ConsoleColor.debug.sout("Increments: " + increases);
            ConsoleColor.debug.sout("--- end ---");
        });
        Events.on(EventType.PlayEvent.class, ignore -> setup());

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking || event.unit == null || event.unit.getPlayer() == null) return;
            final Player p = event.unit.getPlayer();
            if (event.tile.build instanceof final LogicBlock.LogicBuild lb) {
                lb.configure(event.config);
                //check if draws to display
                if (lb.code.contains("ubind ")) {
                    lb.updateCode("");
                    Call.tileConfig(null, lb, lb.config());
                    p.sendMessage("[scarlet]Unit Bind is disabled in td.");
                }
            }
        });
        Events.on(EventType.TapEvent.class, event -> {
            if (DevKit.dev && event.player.admin) {
                Tile t = Vars.pathfinder.getTargetTile(event.tile, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore));
                if (t == null) {
                    event.player.sendMessage("null");
                } else {
                    System.out.println("f: " + event.tile.x + ", " + event.tile.y);
                    Call.label(event.player.con, "[red][[]", 1, event.tile.x * 8f, event.tile.y * 8f);
                    System.out.println("t: " + t.x + ", " + t.y);
                    Call.label(event.player.con, "[green][[]", 1, t.x * 8f, t.y * 8f);

                    /*
                    IntMap<AtomicInteger> a = new IntMap<>();
                    for (int i : FlowFieldType.Ground.pathTileCosts) {
                        var b = a.get(i);
                        if (b == null) {
                            b = new AtomicInteger(0);
                            a.put(i, b);
                        }
                        b.incrementAndGet();
                    }
                    System.out.println(a);
                     */
                    System.out.println(FlowFieldType.Ground.getPatCost().getCost(Team.sharded.id, event.tile.array()));
                }
            }
        });

        Events.on(EventType.UnitSpawnEvent.class, event -> fixAI(event.unit));
        Events.on(EventType.UnitCreateEvent.class, event -> {
            if (event.unit.team == state.rules.defaultTeam) {
                if (event.unit.type == UnitTypes.mono) {
                    Call.label("Monos are not allowed in TD due to mining!", 10, event.unit.x, event.unit.y);
                    Call.unitDestroy(event.unit.id);
                    event.unit.kill();
                } else {
                    event.unit.apply(StatusEffects.disarmed, 9999999999f);
                    ConsoleColor.debug.sout("disarmed u:" + event.unit.id + " type:" + event.unit.type.name);
                }
            }
        });
        Events.on(EventType.UnitDestroyEvent.class, event -> {
            var u = event.unit;
            if (u.team() == Vars.state.rules.waveTeam) {
                for (CreditHandler ch : CH) {
                    ch.unitDeath(u);
                }
            }
        });
//        Events.on(EventType.WorldLoadEvent.class, event -> Vars.state.rules.teams.get(Vars.state.rules.defaultTeam).cheat = true);

        Events.run(TimeKeeper.S1, () -> {
            for (var c : CH) c.addPassiveIncome();
        });
        Events.run(TimeKeeper.S15, () -> {
            healthMulti += multiIncreasePer15S;
            for (var ut : Vars.content.units())
                ut.health = origHealth[ut.id] * healthMulti;
        });

        Events.run(EventType.Trigger.update, () -> {
            for (CreditHandler ch : CH)
                ch.tick();
            long cur = System.currentTimeMillis();
            if (TD.missionTimeout < cur)
                TD.missionTimeout = cur + 250L;
        });
    }

    public static void setup() {
        //item stuff
        System.out.println("registering item stuff");
        ItemCount = Vars.content.items().size;
        itemCost = new float[ItemCount];
        coreItems = new int[ItemCount];
        healthMulti = 1f;

        var active = Arrays.stream(Team.all).filter(t -> t.core() != null).toList();
        CH = new CreditHandler[active.size()];
        if (active.size() == 1) {
            CH[0] = new CreditHandler();
        } else {
            for (int i = 0; i < active.size(); i++)
                CH[i] = new CreditHandler(active.get(i));
        }

        tt = mapType();
        ConsoleColor.info.sout("Map type is " + tt.name());
        switch (tt) {
            case Serpulo, Erekir -> {
                //get all blocks that don't use hidden
                System.out.println(tt.hiddenItems);
                buildItems.clear();
                for (var b : Vars.content.blocks()) {
                    if (b.isVisible()) {
                        if (Arrays.stream(b.requirements).noneMatch(is -> tt.hiddenItems.contains(is.item))) {
                            Arrays.stream(b.requirements).forEach(is -> {
                                if (buildItems.addUnique(is.item))
                                    ConsoleColor.debug.sout("Added " + is.item.name);
                            });
                        } else {
                            ConsoleColor.debug.sout(b.name + " uses hidden items");
                        }
                    }
                }
                //get items from sql db
                var o = Sql.get_MM(new Sql.DataType[]{Sql.DataType.String, Sql.DataType.Float}, "SELECT `item`, `cost` FROM td.itemCost WHERE tt = ?", tt.ordinal());
                if (o == null) {
                    ConsoleColor.error.sout("item cost null!");
                    return;
                }
                for (Object[] o2 : o) {
                    float cost = (float) o2[1];
                    var a = Vars.content.item((String) o2[0]);
                    itemCost[a.id] = cost;
                }
                //if not loaded
                ArrayList<Sql.bulkData> queue = new ArrayList<>();
                for (Item i : buildItems) {
                    if (itemCost[i.id] == 0) {
                        queue.add(new Sql.bulkData(i.name, tt.ordinal()));
                        itemCost[i.id] = 1;
                    }
                }
                if (!queue.isEmpty())
                    ConsoleColor.warning.sout("Adding missing items to td.itemCost: " + Arrays.toString(Sql.runBulk("INSERT INTO td.itemCost (`item`, `tt`, `cost`) VALUES (?, ?, 1)", queue)));
            }
            case MixTech -> {
                ConsoleColor.error.sout("Refusing to load mix tech map!");
                Events.fire(new EventType.GameOverEvent(Team.derelict));
                return;
            }
        }
        //fix
        System.out.println("fixing unit ai");
        Groups.unit.each(TD::fixAI);
    }

    public enum FlowFieldType {
        Ground(0),
        Naval(1);

        public final int y;
        public int size = 0;
        public int[] pathTileCosts = new int[0];
        public Block centerPathFloor = null;

        FlowFieldType(int y) {
            this.y = y;
        }

        public void init() {
            //find which tiles are path
            centerPathFloor = Vars.world.tile(0, y).floor();
            var pathFloor = Vars.world.tile(1, y).floor();
            System.out.println(name() + " path center floor is " + centerPathFloor.name);
            System.out.println(name() + " path floor is " + pathFloor.name);
            size = Vars.world.width() * Vars.world.height();
            pathTileCosts = new int[size];

            int i = 0;
            for (var t : Vars.world.tiles) {
                int a = t.array();
                if (t.floor() == centerPathFloor) {
                    pathTileCosts[a] = 1;
                    i++;
                } else if (t.floor() == pathFloor) {
                    pathTileCosts[a] = 2;
                    i++;
                }
            }
            System.out.println("paths for " + this.name() + ": " + i);
        }

        public Pathfinder.PathCost getPatCost() {
            return (team, tile) -> {
                if (tile >= size) return 1;//should only happen during save loads
                return switch (pathTileCosts[tile]) {
                    case 1 -> 1;
                    case 2 -> 5;
                    default -> -1;
                };
            };
        }
    }

    public static TechType mapType() {
        //init tt
        TechType.init();
        //
        Seq<Item> now = Vars.state.map.rules().hiddenBuildItems.toSeq().sort(i -> i.id);

        if (now.equals(TechType.Erekir.hiddenItems)) {
            return TechType.Erekir;
        } else if (now.equals(TechType.Serpulo.hiddenItems)) {
            return TechType.Serpulo;
        } else {
            return TechType.MixTech;
        }
    }

    public static void fixAI(Unit u) {
        if (u.team() == Vars.state.rules.waveTeam) {
            if (u.type.naval) {
                u.controller(new CustomNavalAI());
            } else {
                u.controller(new CustomGroundAI());
            }
        }
    }

    public static Tile closestCenter(Tile t, FlowFieldType fft) {
        Tile out = closestCenterCache.get(t);
        if (out != null) return out;

        int wwidth = Vars.world.width(), wheight = Vars.world.height();

        Seq<Tile> avail = new Seq<>();

        for (int x = 0; x < 21; x++) {
            for (int y = 0; y < 21; y++) {
                int dx = x - 10 + t.x, dy = y - 10 + t.y;
                if (dx < 0 || dy < 0 || dx >= wwidth || dy >= wheight) continue;
                Tile tile = Vars.world.tile(dx, dy);
                if (tile.floor() == fft.centerPathFloor)
                    avail.add(tile);
            }
        }

        switch (avail.size) {
            case 0:
                var a = Point2.unpack(Vars.waves.get().get(0).spawn);
                out = Vars.world.tile(a.x, a.y);
                closestCenterCache.put(t, out);
                return out;
            default:
                avail.sort(nt -> Mathf.dst2(t.x, t.y, nt.x, nt.y));
            case 1:
                out = avail.get(0);
                closestCenterCache.put(t, out);
                return out;
        }
    }
}
