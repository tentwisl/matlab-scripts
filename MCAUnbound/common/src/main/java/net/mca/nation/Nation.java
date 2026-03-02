package net.mca.nation;

import net.mca.nation.cabinet.CabinetData;
import net.mca.nation.congress.CongressData;
import net.mca.nation.heir.HeirData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents one nation in the world — player-founded or AI-controlled.
 * Serialized to NBT and stored inside NationManager (PersistentState).
 */
public class Nation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // ── Identity ──────────────────────────────────────────────────────────────
    private final UUID nationId;
    private String name;
    /** Hex RGB, e.g. "FF4400" — base flag color; also used as first layer color. */
    private String flagColorHex = "AAAAAA";
    /** Ordered flag layers (bottom → top), max 4. */
    private final List<FlagLayer> flagLayers = new ArrayList<>();

    // ── Government ────────────────────────────────────────────────────────────
    private GovernmentType governmentType = GovernmentType.MONARCHY;
    /** NPC UUID of the current leader (null = player is leader). */
    private UUID leaderEntityId;
    /** UUID of the player or NPC who originally founded the nation. */
    private final UUID founderEntityId;
    /** Player UUID who *owns* this nation (may be different if player was elected to a foreign nation). */
    private UUID ownerPlayerId;

    // ── Territory ─────────────────────────────────────────────────────────────
    /** VillageId of the designated capital city. */
    private int capitalCityVillageId = -1;
    /** District UUIDs that belong to this nation. */
    private final List<UUID> districtIds = new ArrayList<>();

    // ── Sub-systems ───────────────────────────────────────────────────────────
    private NationPolicy  policy   = new NationPolicy();
    private NationStats   stats    = new NationStats();
    private HeirData      heir     = new HeirData();
    private CabinetData   cabinet  = new CabinetData();
    private CongressData  congress = new CongressData();

    // ── AI behaviour (for non-player nations) ─────────────────────────────────
    private NationPersonality personality = NationPersonality.DIPLOMATIC;
    /** Whether this is an AI-controlled nation. */
    private boolean isAiControlled = false;

    // ── Puppet / Vassal ───────────────────────────────────────────────────────
    /** True if this nation is a puppet under another nation. */
    private boolean isPuppet = false;
    /** The nation that controls this one (null if independent). */
    private UUID puppetMasterNationId;

    // ── Event log ─────────────────────────────────────────────────────────────
    private final List<String> recentEvents = new ArrayList<>();
    private static final int MAX_EVENTS = 30;

    // ── Founding ──────────────────────────────────────────────────────────────
    /** In-game day when the nation was founded. */
    private long foundedDay = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public Nation(UUID nationId, String name, UUID founderEntityId, GovernmentType governmentType) {
        this.nationId        = nationId;
        this.name            = name;
        this.founderEntityId = founderEntityId;
        this.governmentType  = governmentType;
    }

    public Nation(NbtCompound nbt) {
        nationId       = nbt.getUuid("nationId");
        name           = nbt.getString("name");
        flagColorHex   = nbt.getString("flagColorHex");
        governmentType = GovernmentType.valueOf(nbt.getString("governmentType"));
        founderEntityId = nbt.getUuid("founderEntityId");
        if (nbt.contains("ownerPlayerId"))        ownerPlayerId        = nbt.getUuid("ownerPlayerId");
        if (nbt.contains("leaderEntityId"))       leaderEntityId       = nbt.getUuid("leaderEntityId");
        if (nbt.contains("puppetMasterNationId")) puppetMasterNationId = nbt.getUuid("puppetMasterNationId");
        capitalCityVillageId = nbt.getInt("capitalCityVillageId");
        personality    = NationPersonality.valueOf(nbt.getString("personality"));
        isAiControlled = nbt.getBoolean("isAiControlled");
        isPuppet       = nbt.getBoolean("isPuppet");
        foundedDay     = nbt.getLong("foundedDay");

        NbtList dStrList = nbt.getList("districtIds", NbtElement.STRING_TYPE);
        for (int i = 0; i < dStrList.size(); i++) districtIds.add(UUID.fromString(dStrList.getString(i)));

        NbtList eList = nbt.getList("recentEvents", NbtElement.STRING_TYPE);
        for (int i = 0; i < eList.size(); i++) recentEvents.add(eList.getString(i));

        NbtList fList = nbt.getList("flagLayers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < fList.size(); i++) flagLayers.add(new FlagLayer(fList.getCompound(i)));

        policy   = new NationPolicy(nbt.getCompound("policy"));
        stats    = new NationStats(nbt.getCompound("stats"));
        heir     = new HeirData(nbt.getCompound("heir"));
        cabinet  = new CabinetData(nbt.getCompound("cabinet"));
        congress = new CongressData(nbt.getCompound("congress"));
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("nationId",           nationId);
        nbt.putString("name",             name);
        nbt.putString("flagColorHex",     flagColorHex);
        nbt.putString("governmentType",   governmentType.name());
        nbt.putUuid("founderEntityId",    founderEntityId);
        if (ownerPlayerId        != null) nbt.putUuid("ownerPlayerId",        ownerPlayerId);
        if (leaderEntityId       != null) nbt.putUuid("leaderEntityId",       leaderEntityId);
        if (puppetMasterNationId != null) nbt.putUuid("puppetMasterNationId", puppetMasterNationId);
        nbt.putInt("capitalCityVillageId", capitalCityVillageId);
        nbt.putString("personality",      personality.name());
        nbt.putBoolean("isAiControlled",  isAiControlled);
        nbt.putBoolean("isPuppet",        isPuppet);
        nbt.putLong("foundedDay",         foundedDay);

        NbtList dList = new NbtList();
        districtIds.forEach(id -> dList.add(NbtString.of(id.toString())));
        nbt.put("districtIds", dList);

        NbtList eList = new NbtList();
        recentEvents.forEach(e -> eList.add(NbtString.of(e)));
        nbt.put("recentEvents", eList);

        NbtList fList = new NbtList();
        flagLayers.forEach(fl -> fList.add(fl.save()));
        nbt.put("flagLayers", fList);

        nbt.put("policy",   policy.save());
        nbt.put("stats",    stats.save());
        nbt.put("heir",     heir.save());
        nbt.put("cabinet",  cabinet.save());
        nbt.put("congress", congress.save());
        return nbt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID   getNationId()                              { return nationId; }
    public String getName()                                  { return name; }
    public void   setName(String name)                       { this.name = name; }
    public String getFlagColorHex()                          { return flagColorHex; }
    public void   setFlagColorHex(String hex)                { this.flagColorHex = hex; }

    /** Unmodifiable view of flag layers; use {@link #setFlagLayers} to replace. */
    public List<FlagLayer> getFlagLayers()                   { return Collections.unmodifiableList(flagLayers); }
    public void setFlagLayers(List<FlagLayer> layers) {
        flagLayers.clear();
        for (int i = 0; i < Math.min(4, layers.size()); i++) flagLayers.add(layers.get(i));
    }

    public GovernmentType getGovernmentType()                { return governmentType; }
    public void   setGovernmentType(GovernmentType t)        { this.governmentType = t; }
    public UUID   getFounderEntityId()                       { return founderEntityId; }
    public Optional<UUID> getOwnerPlayerId()                 { return Optional.ofNullable(ownerPlayerId); }
    public void   setOwnerPlayerId(UUID id)                  { this.ownerPlayerId = id; }
    public Optional<UUID> getLeaderEntityId()                { return Optional.ofNullable(leaderEntityId); }
    public void   setLeaderEntityId(UUID id)                 { this.leaderEntityId = id; }
    public int    getCapitalCityVillageId()                  { return capitalCityVillageId; }
    public void   setCapitalCityVillageId(int id)            { this.capitalCityVillageId = id; }
    public List<UUID> getDistrictIds()                       { return districtIds; }
    public void   addDistrict(UUID id)                       { if (!districtIds.contains(id)) districtIds.add(id); }
    public void   removeDistrict(UUID id)                    { districtIds.remove(id); }
    public NationPolicy  getPolicy()                         { return policy; }
    public NationStats   getStats()                          { return stats; }
    public HeirData      getHeir()                           { return heir; }
    public CabinetData   getCabinet()                        { return cabinet; }
    public CongressData  getCongress()                       { return congress; }
    public NationPersonality getPersonality()                { return personality; }
    public void   setPersonality(NationPersonality p)        { this.personality = p; }
    public boolean isAiControlled()                          { return isAiControlled; }
    public void   setAiControlled(boolean v)                 { this.isAiControlled = v; }
    public boolean isPuppet()                                { return isPuppet; }
    public Optional<UUID> getPuppetMasterNationId()          { return Optional.ofNullable(puppetMasterNationId); }
    public void   setPuppetMaster(UUID masterId)             { this.puppetMasterNationId = masterId; this.isPuppet = (masterId != null); }
    public long   getFoundedDay()                            { return foundedDay; }
    public void   setFoundedDay(long day)                    { this.foundedDay = day; }
    public List<String> getRecentEvents()                    { return recentEvents; }

    public void logEvent(String msg) {
        recentEvents.add(0, msg);
        if (recentEvents.size() > MAX_EVENTS) recentEvents.remove(recentEvents.size() - 1);
    }

    /** True if a player currently leads this nation (either as founder or elected). */
    public boolean isPlayerLed() {
        return ownerPlayerId != null && leaderEntityId == null;
    }

    /** Returns the display title for the current leader based on government type. */
    public String getLeaderTitle() {
        return governmentType.getLeaderTitle();
    }
}
