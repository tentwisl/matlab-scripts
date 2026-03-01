# MCAUnbound — Nation Simulation Design Document

## Overview & Design Philosophy

This document outlines the full design for the nation simulation layer built on top of MCA (Minecraft Comes Alive Reborn).
The goal is an emergent, organic world where:

- The player starts as a nobody and can grow into the ruler of an empire
- Rival nations exist from day one with their own goals, personalities, and territorial behavior
- Villages are living communities with opinions, economies, and political allegiances
- War, trade, diplomacy, and culture are meaningful long-term gameplay loops
- MCA provides the social/human layer; AW2 provides the military/production layer; the Nation system ties them together
- Unloaded regions continue to simulate at low cost — results are materialized when the player returns

---

## Part 1 — Expanded Rank System (13 Tiers, Slower Pacing)

Replaces the existing 6-rank MCA system (`Outlaw → Peasant → Merchant → Noble → Mayor → Monarch`).
Each rank from Magistrate onward represents weeks of real playtime.
Reputation is cumulative across all villages, not just one.

| # | Rank Enum | Display Name | Key Requirements |
|---|---|---|---|
| 1 | `WANDERER` | Wanderer | Default spawn |
| 2 | `OUTSIDER` | Outsider | Met 5 villagers; total reputation ≥ 0 |
| 3 | `SETTLER` | Settler | Recognized home (bed + door structure); reputation ≥ 100 in one village |
| 4 | `FREEMAN` | Freeman | Reputation ≥ 300; at least 1 AW2 worker employed |
| 5 | `MERCHANT` | Merchant | Active trade route (Courier NPC between 2 towns); reputation ≥ 600 |
| 6 | `GUILDMASTER` | Guildmaster | 3+ trader NPCs employed; warehouse built; reputation ≥ 900 |
| 7 | `MAGISTRATE` | Magistrate | Controlled village pop ≥ 15; barracks built; reputation ≥ 1400 |
| 8 | `MAYOR` | Mayor | Pop ≥ 30; library + armory + engineering table in town; reputation ≥ 2000 |
| 9 | `NOBLE` | Noble | 2+ towns under influence; 10+ military NPCs; District Charter crafted |
| 10 | `DUKE` | Duke | 4+ towns; 25+ military; 3 AW2 automation machines operational |
| 11 | `ARCHDUKE` | Archduke | 8+ towns; won at least one military conflict; Capital City designated |
| 12 | `MONARCH` | King / Queen | Nation formally founded; recognized by ≥ 1 rival nation |
| 13 | `EMPEROR` | Emperor / Empress | Nation controls 3+ regions; 2+ rivals subjugated or allied; Council seat held |

**Existing `Rank.java` changes:**
- Replace the 6 enum values with the 13 above
- `promote()` / `degrade()` methods remain the same pattern
- `getTranslationKey()` continues using `"gui.village.rank." + name().toLowerCase()`
- All task JSON files in `resources/data/tasks/` must be updated with new thresholds

---

## Part 2 — Government Types

Each nation (player and AI) has one of the following government types.
Government type affects available policies, leader dialogue flavor, and how decisions are made.

### 2.1 — Government Enum

```java
public enum GovernmentType {
    MONARCHY,       // Single absolute ruler (default for aggressive/militarist AI)
    REPUBLIC,       // Elected president + congress of citizens
    OLIGARCHY,      // Council of nobles/merchants; no election
    THEOCRACY,      // Religious leader appointed by faith score
    TRIBAL          // Council of chiefs; decisions by majority vote of city governors
}
```

### 2.2 — Democratic / Republic Nations

When a nation's government type is `REPUBLIC`:

**Elections:**
- Held every 30 in-game days (configurable)
- Candidates: MCA villagers with high `Charisma` stat emerge automatically as Politician-profession NPCs
- MCA villagers have a `PoliticalLean` value: `MILITARIST | ECONOMIC | CULTURAL | ISOLATIONIST | EXPANSIONIST`
- Each citizen's lean is influenced by:
  - Personal happiness (food, safety, prosperity)
  - Conversations with other MCA NPCs (social propagation — 10% chance per NPC interaction to shift lean 1 point toward the other NPC's lean)
  - Current leader's approval rating (poor performance shifts citizens away from the incumbent's lean)
- Citizens vote for the candidate closest to their lean
- Congress: top-5 highest vote-getters beyond the president become Congress members (stored as UUIDs in `DemocraticNationData`)
- Congress leans affect which policy proposals pass (majority lean of congress must align)

**Player Influence on Elections:**
- Talk to citizens and nudge their lean (dialogue option: discuss politics)
- Fund a candidate (spend gold → candidate gets +5% vote weight)
- Send Bard NPCs to perform propaganda (shifts 20 citizens' lean over 7 days)
- Bribery (high risk; if discovered, player loses 30 opinion with that entire nation)

**Congress Decisions:**
- AI congress can pass or block edicts if their lean opposes it
- Player can petition congress (spend reputation to push a policy through even with opposing congress)

---

## Part 3 — Nation Data Model

All nation data is stored in `NationManager` (extends `PersistentState`, saved in overworld data).

### 3.1 — Core Classes

```
NationManager (PersistentState)
├── Map<UUID, Nation>
├── Map<UUID, District>
├── Map<UUID, CityData>              // wraps existing Village IDs
├── Map<UUID, DiplomacyRelation>     // key: pair hash of two nation UUIDs
├── List<WorldEvent>                 // active organic events
├── CouncilOfNations                 // singleton
└── ResourceWorldCache               // biome→resource mappings (lazy-loaded)
```

**`Nation`:**
```java
UUID nationId
String name
String flagColorHex
GovernmentType governmentType
UUID leaderEntityId          // player UUID or AI leader NPC UUID
UUID capitalCityId
List<UUID> districtIds
NationPolicy policies
NationStats stats
NationPersonality personality  // AI only; null for player nation
boolean isPuppet               // true if controlled by another nation
UUID puppetMasterId            // the nation UUID that controls this puppet
```

**`District`:**
```java
UUID districtId
String name
UUID governorId              // MCA villager UUID
UUID parentNationId
List<UUID> cityIds
DistrictPolicy localPolicies // overrides parent nation policy for this district
```

**`CityData`:**
```java
UUID villageId               // matches existing Village.id cast to UUID
UUID districtId              // null = independent city-state
Map<UUID, Integer> playerOpinion    // -100 to +100 per player
int baseOpinion              // average of all resident moods
CityTier tier                // HAMLET, VILLAGE, TOWN, CITY, METROPOLIS
ResourceProfile resources    // what this city produces and consumes
List<TradeOffer> townMarketOffers   // refreshed every 7 in-game days
long lastMarketRefresh
boolean isIndependent        // independent city-states: annexable but costly
int independenceScore        // how strongly the city resists annexation (0–100)
```

**`DiplomacyRelation`:**
```java
UUID nationA
UUID nationB
DiplomacyStatus status       // AT_WAR, HOSTILE, NEUTRAL, FRIENDLY, TRADE_AGREEMENT,
                             //   NON_AGGRESSION, ALLIANCE, VASSAL, OVERLORD
int trustValue               // -100 to +100
List<DiplomacyEvent> history
List<ActiveAgreement> agreements
```

**`NationPolicy`:**
```java
int taxRate                  // 0–50%; negatively affects town opinion if > 20%
int militaryLevy             // % of adults eligible for conscription (0–100)
ProductionFocus focus        // MILITARY, ECONOMIC, CULTURAL, SCIENTIFIC, BALANCED
Map<String, Integer> productionQuotas  // AW2 item/block type → target per week
boolean openBorders
boolean conscriptionEnabled
boolean freeTradeEnabled
```

**`NationStats`:**
```java
int totalPopulation
int militaryStrength         // weighted unit count × level
int economicOutput           // sum of all city surplus production values
int culturalInfluence        // drives cultural spread radius
int scienceLevel             // 0–10; unlocks research tiers
Map<ResourceType, Integer> weeklyProduction   // totals across all cities
Map<ResourceType, Integer> weeklyConsumption
Map<ResourceType, Integer> stockpile          // national reserve
```

### 3.2 — Player Restrictions

- A player may found **exactly one nation** (one-time, irreversible)
- A player **can** control puppet nations after capitulation/surrender (see Part 5.3)
- `PlayerSaveData` gains: `Optional<UUID> ownedNationId` and `List<UUID> puppetNationIds`

---

## Part 4 — Unloaded Chunk Simulation

All nation simulation runs on the server thread via `NationTickManager`, registered as a `ServerTickEvents.END_SERVER_TICK` listener.
No chunk loading is required for simulation — all data is purely in `NationManager`.

### 4.1 — Tick Frequency

| System | Tick Interval | Notes |
|---|---|---|
| Resource production/consumption | Every 24,000 ticks (1 in-game day) | Per-city, data only |
| Opinion drift | Every 24,000 ticks | Applied to all cities |
| AI nation decisions | Every 72,000 ticks (3 in-game days) | Per AI nation |
| World events | Every 48,000 ticks (2 in-game days) | Probability roll |
| Trade route processing | Every 24,000 ticks | Timer countdown |
| Election cycle check | Every 72,000 ticks | Only for REPUBLIC nations |
| Cultural spread | Every 120,000 ticks (5 days) | Radius check |

### 4.2 — Materialization (Region Loads)

When a chunk containing a village loads:
1. `NationTickManager.onChunkLoad(ChunkPos)` fires
2. Simulation delta since last materialization is computed
3. Results are applied to live MCA entities:
   - Population delta → spawn/despawn MCA villagers
   - New guard assignments → spawn guard NPCs at town hall
   - Courier routes → spawn courier NPCs at source city
   - Siege events that fired → apply structural damage, flip allegiance if applicable
4. "Aftermath" messages displayed to any player in range: `"[City Name]: A bandit raid occurred 2 days ago. 3 residents were lost."`

### 4.3 — Performance Budget

- `NationManager` serializes to NBT; total size should be kept under 2MB for typical worlds
- No per-block or per-chunk data — all data is per-city (village bounding box level)
- AI nation decision loop is O(n × m) where n = AI nations, m = known neighbors; capped at 50ms per tick cycle
- `ResourceWorldCache` lazily scans the `BiomeSource` around city centers once, then caches results

---

## Part 5 — Nation Founding, Puppets & AI Nations

### 5.1 — Founding a Nation

Unlocked at **Duke** rank. Requires:
- A designated Capital City
- Nation name input (text field in Diplomacy Table GUI)
- Flag color selection
- Consumed: District Charter item × 3

After founding:
- All known AI nations receive a `DiplomacyEvent.NATION_FOUNDED` notification
- Initial relation with all AI nations: `NEUTRAL` (trust = 0)
- First-contact dialogue fires the next time the player approaches an AI capital or envoy

### 5.2 — AI Nation Generation

`NationSpawner` runs on first world tick (via `ServerWorldEvents.LOAD`).
Number of AI nations: configurable (default 3–6, scales with world seed complexity).

Each AI nation:
- Gets a personality (rolled from `NationPersonality` pool)
- Gets a leader NPC spawned at their designated capital
- Gets 2–4 starting villages linked to their nation
- Gets a starting `NationStats` baseline matching their personality (militarist → higher military, lower economy, etc.)

### 5.3 — Puppet Nations

When a nation capitulates (all military destroyed + capital captured):
- Player chooses: **Annex**, **Vassal**, or **Puppet**
- If **Puppet**: player selects any MCA NPC from their nation as installed Puppet Leader
  - Puppet leader is physically transported to enemy capital (teleported if unloaded)
  - Player can open `PuppetDirectiveScreen` from Diplomacy Table to issue orders:
    - "Focus Military Build-up"
    - "Focus Economic Output"
    - "Declare War on [Nation]"
    - "Sign Trade Agreement with [Nation]"
    - "Increase/Decrease Tax Rate"
  - Puppet leader's MCA mood affects compliance: unhappy leader ignores directives 30% of the time
  - If puppet nation's citizenry opinion of the puppet leader drops below -50, a `Revolt` world event fires → puppet leader is killed, nation regains independence
- Puppet nations still simulate independently; player simply has directive influence

---

## Part 6 — AI Nation Personalities & Leader Dialogue

### 6.1 — Personality Profiles

| Personality | War Tendency | Preferred Expansion | Diplomatic Style | Peace Trigger |
|---|---|---|---|---|
| `AGGRESSIVE` | Declares war readily | Military conquest | Threatening | Only when severely losing |
| `EXPANSIONIST` | Wars for territory | Fast annexation | Assertive | Once objectives met |
| `MILITARIST` | Loves conflict; huge army | Siege campaigns | Blunt | Never surrenders first |
| `MERCANTILE` | Avoids war; prefers trade wars | Economic influence | Cordial | Quickly if offered reparations |
| `ISOLATIONIST` | Never attacks; defends fiercely | None | Cold, distant | When border restored |
| `DIPLOMATIC` | Builds alliances first; proxy wars | Alliance webs | Warm, formal | Eager to negotiate |
| `SCIENTIFIC` | Slow start, overwhelming late | Research-driven expansion | Intellectual | Offers research sharing |
| `CULTURAL` | Wins through influence | Cultural spread | Artistic, philosophical | Offers cultural exchange |

### 6.2 — Leader Dialogue System

Leader NPCs are special `VillagerEntityMCA` instances with `NationLeader` tag in NBT.
They retain all standard MCA mood/emotion systems. Their `diplomaticMood` is a separate field:
`WELCOMING | NEUTRAL | SUSPICIOUS | HOSTILE | FEARFUL | ARROGANT | RESPECTFUL | DESPERATE`

Diplomatic mood is determined by:
- Current war/peace status
- Trust value with the player's nation
- Recent events (player helped/hurt them)
- Leader's MCA mood (tired, sad leaders are less confrontational; happy, energetic leaders are bolder)
- Nation's current power relative to player's nation (power ratio affects arrogance/fear)

Dialogue branches per personality × mood combination:

**Aggressive leader, trust = 0, no war:**
> "You stand in my hall unannounced. State your purpose quickly — I have campaigns to plan."

**Aggressive leader, trust = 0, at war:**
> "The nerve. You come here while your soldiers burn my villages? Give me one reason I shouldn't have you seized."

**Aggressive leader, trust = 0, desperate (losing war):**
> "Fine. You want terms. I'll hear them. But know this is temporary — the [NationName] people do not forget."

**Diplomatic leader, first meeting:**
> "Ah, a visitor of some standing! Word of your deeds has traveled. Come, sit — there is much we might offer each other."

**Diplomatic leader, trade agreement active:**
> "Our merchants speak well of you. I believe we are building something lasting here. What more can we do together?"

**Mercantile leader, war declared against them:**
> "This is... regrettable. Entirely unnecessary. I trust there is a price that makes this misunderstanding go away?"

**Cultural leader, when player's culture is high:**
> "You have cultivated something beautiful in your lands. Rare, in this age of iron and fire. We should exchange more than goods."

**Isolationist leader, player approaches border:**
> "This is the edge of [NationName] sovereignty. Beyond this line, we ask only to be left in peace. Please honor that."

All dialogue is stored in the existing `assets/mca/api/dialogues/` JSON format with added `leader_*` dialogue keys.
MCA mood modifiers apply on top (tired leader speaks shorter, angry leader raises formality).

---

## Part 7 — Blocks & GUIs

### 7.1 — Diplomacy Table

**Block:** `DiplomacyTableBlock` — crafted; uses placeholder texture (reskinned enchanting table).
**Block Entity:** `DiplomacyTableBlockEntity` — stores owning player UUID.

**GUI Tabs:**

#### Nation Overview Tab
- Nation name, rank, flag color swatch
- Population, military strength, economic output bars
- Territory summary: X cities, X districts, X regions
- Buttons: "Manage Districts" → `DistrictManagerScreen`
- Buttons: "Set Nation Policies" → inline policy panel (tax slider, focus selector, levy toggle)
- Button: "Set Capital City" (only before founding)

#### Diplomacy Tab
- Scrollable list of all known nations with status icon + trust bar
- Per-nation action buttons based on current status:
  - Neutral → `Propose Trade Agreement` / `Propose Non-Aggression Pact` / `Declare War`
  - Friendly → `Propose Alliance` / `Propose Open Borders` / `Request Military Access`
  - Allied → `Coordinate Attack` / `Share Research` / `Propose Vassal Status`
  - At War → `Propose Peace` / `Offer Tribute` / `Demand Surrender`
- "Send Envoy" button: dispatches a Courier NPC physically to the target capital
  - If Envoy arrives safely: +10 to offer outcome
  - If Envoy is killed en route: proposal cancelled, relations -5

#### Puppet Directive Tab (only visible if player has puppet nations)
- List of puppet nations
- Per-puppet: current leader name, compliance rate, directive queue
- Dropdown to issue new directive
- "Remove Puppet Leader" (triggers independence restoration)

#### Edicts Tab (unlocks at Monarch rank)
- Global nation-wide edicts (toggleable):
  - **Military Conscription** — boosts army by 15%; -5 happiness/day in all cities
  - **Free Trade** — removes internal tariffs; +10% economic output; +3 citizen happiness
  - **Fortification Order** — all garrisoned towns gain AW2 wall structures (generated on next chunk load)
  - **Research Mandate** — researcher NPC output ×2; all other workers -10%
  - **Cultural Festival** — +10 happiness for 7 days; attracts 1-3 immigrants per city

#### Council of Nations Tab (unlocks when 3+ nations exist and player is Monarch+)
- Meeting schedule (next meeting in X days)
- Active resolutions list with vote status
- Button: "Propose Resolution" (spend 1 Diplomacy Point, earned via trust with multiple nations)
- Resolution types:
  - Global Trade Agreement
  - War Crimes Declaration
  - Border Freeze
  - World Wonder Designation
  - Embargo on a specific nation

### 7.2 — Town Hall Block

**Block:** `TownHallBlock` — auto-spawns at the geographic center of every generated vanilla village
via `VillageStructureProcessor` hook. Placeholder texture (decorated chiseled stone bricks + banner).
**Block Entity:** `TownHallBlockEntity` — stores `villageId`, town market `ItemHandler` (6 slots), `opinionMap`.

**Auto-spawn logic:**
```java
// In VillageStructureProcessor.process() or via StructurePlacementEvent:
// After village structure pieces are placed, find center BlockPos,
// replace the top non-air block with TownHallBlock.
// TownHallBlockEntity.villageId = nearest Village from VillageManager.
```

**GUI Tabs:**

#### Overview Tab
- Town name (from `API.getVillagePool()`, same as existing `Village.name`)
- City tier badge (HAMLET → VILLAGE → TOWN → CITY → METROPOLIS)
- Population: live count of `VillagerEntityMCA` registered to this village
- Nation affiliation: "Independent" or "[NationName] — [DistrictName]"
- Town opinion bar: -100 (Hostile) to +100 (Allied); color-coded
- Recent events log (last 5 `WorldEvent` entries for this city)
- Resources panel: icons showing this city's `ResourceProfile` outputs

#### Market Tab
- **Town Market** (left panel): 4–6 `TradeOffer` slots; "Refresh" costs 1 Emerald before 7-day timer
  - Scales with city tier (Metropolis has 6 slots with rare goods)
- **Villager Trades** (right panel): scrollable list of all residents with profession icons
  - Click a villager entry → opens their standard MCA trade GUI (same as right-clicking them)

#### Garrison Tab (visible at Magistrate+ rank)
- List of military NPCs stationed here with their level
- "Assign Commander" button → promote highest-level soldier to Commander role
- "Requisition Defense" → spend iron/food to spawn additional guard NPCs (1-day delay)

#### Town Projects Tab (visible when city is under player control)
- Queue AW2 automation buildings (workers will path and construct)
- Town production focus override (dropdown; defaults to district policy)
- Tax override toggle (this city only; useful for managing unrest in newly annexed cities)

### 7.3 — Trade Depot (Import/Export Block)

**Block:** `TradeDepotBlock` — crafted multi-block structure (2×1×2 minimum; placeholder texture).
**Block Entity:** `TradeDepotBlockEntity` — handles import orders, export listings, and resource routing.

**GUI Tabs:**

#### Import Tab
- Dropdown: select a nation (only nations with `TRADE_AGREEMENT` or `ALLIANCE` status shown)
- Their available exports are listed: item icon, quantity available per week, price per unit
  - Availability is computed from their `NationStats.weeklyProduction - weeklyConsumption - existingExports`
  - Resource availability gated by: what biome/chunk types the source nation owns + their science level
- Per item: input field for quantity requested
- "Submit Order" button → calculates:
  - **Preparation time**: `Math.ceil(quantityRequested / sourceNation.productionRate)` in days
  - **Transit time**: `distance(capitalA, capitalB) / TRADE_SPEED_CONSTANT` in days (configurable)
  - Modifiers: Trade Agreement active (-15%), Open Borders (-10%), Active War In Region (+50% or cancel)
- "Route To" dropdown: select which of your cities receives this shipment
- Order tracker: list of pending orders with ETAs and current status (`PREPARING | IN_TRANSIT | ARRIVED`)

#### Export Tab
- "Add Export Listing" button → opens item picker:
  - Drag item from JEI into the slot (JEI integration via `IJeiPlugin`)
  - Set quantity per shipment (number field)
  - Set frequency: Daily / Every 3 Days / Weekly / On Demand
  - Set minimum stockpile reserve (won't export below this amount)
  - Set price: what you want in return (item + quantity, or gold value)
- Active listings list: shows what foreign nations are currently buying
- "Remove Listing" per entry

#### Distribution Tab
- Visual list of your cities with their current stockpile levels for each resource
- Drag-and-drop interface to set priority routing: "Incoming iron goes to [City A] first, then [City B]"
- Auto-balance toggle: evenly distribute surplus across all cities in district

### 7.4 — Internal Trade & Courier System

Internal resource distribution (within your nation) is performed by Courier NPCs:

**Courier Entity:** `CourierEntityMCA` (extends `VillagerEntityMCA`)
- Assigned a `CourierRoute`: source city → destination city, carrying `ItemStack list`
- Pathfinds between cities using existing MCA `WalkToTargetTask` logic
- Spawns at source `TownHallBlock` position, despawns at destination and updates `CityData.stockpile`
- Rides a horse if one is available at source city (2× speed)
- Can be intercepted:
  - Bandits (world event): courier is killed, cargo lost, `CourierLostEvent` fires → notification to player
  - Rival military during border tensions: cargo seized, relations -5

**Route Setup:** configured from the Distribution Tab or from the District Manager.
Player sets: source city, destination city, resource type, quantity per trip, frequency.
`CourierManager` (server-side) spawns the courier entity when the source city chunk is loaded;
if unloaded, the route processes in simulation (resources transferred directly, no entity spawned).

International trade (between nations) is entirely code-side — no entities rendered.
The `TradeDepotBlockEntity` simply processes timers and fires `ImportArrivedEvent` when the ETA elapses.

---

## Part 8 — Resource System

### 8.1 — Biome-to-Resource Mapping

Each city's `ResourceProfile` is determined by scanning the biome distribution within its `BlockBoxExtended` and a configurable radius beyond it (default: 64 blocks from village edge).

Biome → primary resources:

| Biome Category | Resources |
|---|---|
| Plains / Meadow | Wheat, Livestock (leather, meat), Wool |
| Forest / Taiga | Timber, Furs, Game (meat), Herbs |
| Birch / Dark Forest | Fine Timber, Dyes, Mushrooms |
| Mountains / Stony | Stone, Iron Ore, Coal |
| Badlands / Eroded | Gold Ore, Terracotta Clay, Copper |
| Desert | Sand (glass), Gold, Exotic Spices |
| Jungle | Tropical Timber, Rubber (slimeballs), Exotic Dyes |
| Swamp | Clay, Peat (coal substitute), Alchemical Ingredients |
| Ocean / Beach | Fish, Salt, Coral, Ship Materials (prismarine) |
| Snowy / Tundra | Furs, Ice, Sparse Timber |
| Deep Underground (caves) | Deepslate Stone, Diamonds, Amethyst |
| Nether-adjacent (fortress) | Blaze Powder, Nether Brick, Quartz |

Scanning is done once per city and cached in `ResourceWorldCache`. Re-scanned if city tier increases
(higher tiers can exploit deeper/farther resources).

### 8.2 — Science Level Gating

A nation's `scienceLevel` (0–10) gates what resources can be extracted:

| Science Level | Extraction Unlocks |
|---|---|
| 0–1 | Surface farming, basic logging, surface stone |
| 2–3 | Shallow mining (Y > 0), crop irrigation (+25% food) |
| 4–5 | Deep mining (Y -64 to 0), animal husbandry optimization (+25% livestock) |
| 6–7 | Automated AW2 extraction machines, offshore fishing platforms |
| 8–9 | Deep vein extraction (diamonds, ancient debris), Nether resource access |
| 10 | Full automation: all resources at 2× yield; Nether portal trade routes |

Science level increases when:
- A Research Station building is active in a city
- A Great Scientist Great Person is present
- Research Sharing agreement is active with a higher-science nation
- Research Mandate edict is active

### 8.3 — Resource Flow Summary

```
Biome scan → ResourceProfile (what the land CAN produce)
     ↓
Science level × AW2 machines → actual weeklyProduction
     ↓
weeklyProduction - weeklyConsumption → surplus/deficit
     ↓ (surplus)                          ↓ (deficit)
Available for trade/export            Opinion penalty (-2/day if starving)
Stockpile increases                   Triggers import demand from Trade Depot
Courier routes redistribute to        AI nations seek trade/conquest to
cities with deficits                  resolve chronic deficits
```

---

## Part 9 — Great People System

Special named MCA villagers that occasionally spawn from high-activity cities.

| Type | Spawn Trigger | Ability |
|---|---|---|
| Great Merchant | High weekly trade volume (> 3 active routes) | +50% trade income for home city permanently |
| Great General | 10+ military victories in district | +20% combat strength for all units in their district |
| Great Engineer | 5+ AW2 machines operating | Doubles production output of one linked machine |
| Great Scientist | Research station active 14+ days | Instantly advances science level by 1 |
| Great Diplomat | Trust ≥ 60 with 3+ nations | Can negotiate a treaty that would otherwise be refused |
| Great Artist | Bard NPC reaches level 10 | Home city gets permanent +10 opinion, passive immigration |

Great People are `VillagerEntityMCA` with `greatPerson: true` NBT tag and a named `GreatPersonType`.
They participate in all normal MCA systems (can die, marry, have children).
Losing one is a meaningful setback — their bonus persists for 7 in-game days after death, then fades.

---

## Part 10 — World Wonders

Structures buildable by a city once. Only one of each exists globally.
Built by queueing them in the Town Projects Tab (requires city tier + resources).
First nation to finish construction claims it — if another nation's city is further along, a race event fires.

| Wonder | Requirements | Effect |
|---|---|---|
| Grand Market | City tier TOWN+; 5 Trader NPCs | All trade routes to this city +25% yield |
| Great Citadel | Barracks + 20 military NPCs | City cannot be captured while the Citadel stands |
| Academy of Sciences | Engineering Table + 3 Researcher NPCs level 5+ | Research speed ×2 for owning nation |
| Colosseum | City pop ≥ 30 | All cities in district get permanent +10 opinion |
| Grand Cathedral | Priest NPC level 8+ | Nullifies happiness penalty from high taxes |
| Royal Treasury | Guildmaster rank + 3 active trade routes | Passive gold income from all trade in territory |

Wonder structures use AW2 Structure module templates (placeholder templates initially).

---

## Part 11 — Organic Village Events

`WorldEventManager` fires periodic events that play out without requiring the player:

| Event | Trigger | Effect |
|---|---|---|
| Bandit Raid | Random; higher freq if city has no garrison | Population -1d4; buildings damaged; opinion drops if undefended |
| Trade Caravan | Active trade route between AI cities | Courier column spawns; player can intercept/tax |
| Migration | Pop > beds × 1.5 in one city AND another city has free beds | 1d6 villagers move between cities |
| Drought/Famine | No food production building; 5 days without food surplus | Pop -1/day; opinion -3/day until resolved |
| Plague | Random; higher freq in overcrowded cities | Pop -1d4 over 7 days; quarantine cuts trade income |
| Cultural Spread | High-culture nation with influence radius touching independent city | City opinion of that nation +2/day (may eventually flip allegiance) |
| Coup | AI nation happiness < -40 for 14+ days | Leader NPC replaced; new leader gets random personality |
| Exploration Event | AI nation city with active scout NPC | Scout finds player's settlement; first-contact dialogue queued |
| Election | REPUBLIC government, 30-day cycle | New president/congress computed; possible policy shift |
| Revolt | Puppet nation opinion of puppet leader < -50 | Puppet leader killed; nation regains independence; player trust -20 |
| City-State Uprising | Independent city annexed with opinion < -30 | City re-declares independence; garrison defects; open war with annexing nation |

### City-State Annexation (Diplomatic Penalty Detail)
Independent city-states can still be annexed. However:
- Nations with `DIPLOMATIC` or `CULTURAL` personality immediately lower trust by 15
- Nations with `MERCANTILE` personality lower trust by 10 and may suspend trade agreements
- `AGGRESSIVE` and `MILITARIST` nations are indifferent or may view it favorably (trust +5)
- The annexed city gets `independenceScore` added as a flat opinion penalty that decays 2 points/day

---

## Part 12 — War System

### 12.1 — Military Structure

Each nation's military is tracked in `NationStats` (not per-entity; entity count is a derived value).
When a loaded region is involved in war, entities are materialized to match the stats.

Unit types (AW2 integration):
- Soldier (sword + shield)
- Archer (bow/crossbow)
- Commander (grants +15% combat bonus to nearby units via AW2 officer mechanic)
- Siege Engineer (operates AW2 vehicles: Catapult, Ballista, Trebuchet, Hwacha)

### 12.2 — War Objectives

Wars have a declared objective (Civ-style):
- **Territorial War** — claim specific villages; war ends when those villages change hands
- **Punitive War** — force a reparations payment; war ends when paid
- **Conquest War** — absorb all enemy territory; war ends only on total capitulation
- **Defensive War** — triggered when attacked; war ends when invaders leave your territory
- **War of Independence** — a district breaks away and declares itself a new nation

### 12.3 — Peace Terms

Negotiated at the Diplomacy Table:
- White Peace (status quo)
- Cede Territory (loser gives specific villages)
- Reparations (loser pays items/resources from stockpile)
- Vassal Status (semi-independent; pays tribute; follows overlord's war declarations)
- Puppet Status (full control; see Part 5.3)
- Open Borders Concession

### 12.4 — Supply Chain

AW2 vehicles require resources from the national stockpile:
- Catapult/Trebuchet: stone balls (from quarry)
- Ballista: bolts (from craftsman)
- Hwacha: rockets (requires science level ≥ 5 + craftsman)

If a city's supply route (courier chain) is cut by enemy action, vehicles run dry after
`config.siegeAmmoBuffer` shots. Soldiers without food for 3 days go on strike (wander, refuse tasks).

---

## Part 13 — Technical Architecture Summary

### New Files

```
common/src/main/java/net/mca/
├── nation/
│   ├── Nation.java
│   ├── NationManager.java              // PersistentState
│   ├── NationTickManager.java          // server tick handler
│   ├── NationSpawner.java              // world gen hook
│   ├── District.java
│   ├── CityData.java
│   ├── DiplomacyRelation.java
│   ├── DiplomacyStatus.java            // enum
│   ├── DiplomacyEvent.java
│   ├── ActiveAgreement.java
│   ├── GovernmentType.java             // enum
│   ├── NationPersonality.java          // enum
│   ├── NationPolicy.java
│   ├── NationStats.java
│   ├── ProductionFocus.java            // enum
│   ├── CityTier.java                   // enum
│   ├── ResourceProfile.java
│   ├── ResourceType.java               // enum
│   ├── ResourceWorldCache.java
│   ├── trade/
│   │   ├── TradeOrder.java
│   │   ├── TradeOrderStatus.java       // enum
│   │   ├── ExportListing.java
│   │   └── CourierRoute.java
│   ├── events/
│   │   ├── WorldEventManager.java
│   │   ├── WorldEventType.java         // enum
│   │   └── WorldEvent.java
│   ├── democracy/
│   │   ├── DemocraticNationData.java
│   │   ├── PoliticalLean.java          // enum
│   │   └── ElectionManager.java
│   ├── ai/
│   │   ├── AINationBrain.java
│   │   └── AIDiplomacyAdvisor.java
│   └── wonder/
│       ├── WonderType.java             // enum
│       └── WonderManager.java
├── block/
│   ├── DiplomacyTableBlock.java
│   ├── DiplomacyTableBlockEntity.java
│   ├── TownHallBlock.java
│   ├── TownHallBlockEntity.java
│   ├── TradeDepotBlock.java
│   └── TradeDepotBlockEntity.java
├── entity/
│   └── CourierEntityMCA.java
├── network/
│   ├── c2s/
│   │   ├── OpenDiplomacyTableRequest.java
│   │   ├── OpenTownHallRequest.java
│   │   ├── OpenTradeDepotRequest.java
│   │   ├── FoundNationPacket.java
│   │   ├── SetNationPolicyPacket.java
│   │   ├── DeclareWarPacket.java
│   │   ├── ProposePeacePacket.java
│   │   ├── ProposeTradeAgreementPacket.java
│   │   ├── SendEnvoyPacket.java
│   │   ├── AnnexTownPacket.java
│   │   ├── SetDistrictPolicyPacket.java
│   │   ├── SubmitImportOrderPacket.java
│   │   ├── SetExportListingPacket.java
│   │   ├── SetResourceDistributionPacket.java
│   │   └── IssuePuppetDirectivePacket.java
│   └── s2c/
│       ├── DiplomacyTableDataResponse.java
│       ├── TownHallDataResponse.java
│       ├── TradeDepotDataResponse.java
│       ├── NationEventNotification.java
│       ├── DiplomacyOfferReceived.java
│       ├── WarDeclarationAlert.java
│       └── CouncilMeetingAlert.java
└── client/
    └── gui/
        ├── DiplomacyTableScreen.java
        ├── TownHallScreen.java
        ├── TradeDepotScreen.java
        └── NationMapOverlayScreen.java
```

### Modified Files

```
resources/Rank.java                    // Replace 6 enums with 13
server/world/data/PlayerSaveData.java  // Add ownedNationId, puppetNationIds
server/world/data/Village.java         // Add cityDataId linkage
block/BlocksMCA.java                   // Register 3 new blocks
block/BlockEntityTypesMCA.java         // Register 3 new block entities
entity/EntitiesMCA.java                // Register CourierEntityMCA
resources/data/tasks/*.json            // Update all task thresholds for new rank tiers
```

---

## Implementation Phases

| Phase | Scope | Depends On |
|---|---|---|
| **A** | Rank system expansion (13 tiers, updated task JSON) | None |
| **B** | `CityData` + `TownHallBlock` + Town Hall GUI (overview + market tabs) | Existing `Village` |
| **C** | `NationManager` data model + NBT serialization | B |
| **D** | `Diplomacy Table` block + Nation Overview + founding flow | C |
| **E** | AI nation generation (`NationSpawner`) + tick behavior | C |
| **F** | `NationTickManager` unloaded simulation loop | C + E |
| **G** | Diplomacy Tab + war/peace system | D + E |
| **H** | `TradeDepotBlock` + import/export GUI + `CourierEntityMCA` | C + D |
| **I** | Resource system (biome scan, science gating, production flow) | C + F |
| **J** | District system + district policy GUI | D |
| **K** | AW2 production → nation economy integration | I + AW2 |
| **L** | Democratic government type + election system | E + F |
| **M** | Leader NPC full dialogue trees (personality × mood branches) | E + G |
| **N** | Puppet nation system | G |
| **O** | Great People + Wonders | I + J |
| **P** | Council of Nations + Edicts tab | G + J |
| **Q** | Nation map overlay GUI | C + F |
| **R** | World Events system | F + G |
| **S** | AW2 vehicle + siege system | K + G |
| **T** | AW2 research tree (full port) | K |

Phases A, B can start immediately.
C is the central blocker for D–Q.
K, S, T require AW2 source to be available.
