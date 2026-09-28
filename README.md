# Runebuddy

A RuneLite plugin that answers one question about the account you are logged into:
**what should I do next, and what is the best way to do it?** With the alternatives
laid side by side, so you can see the trade-off rather than take one answer on trust.

- **What next?** One feed: your Slayer task, the next step toward each of your goals,
  quick wins you can take right now, and the best training for your time.
- **How, and how long?** Routes to any level, with hours and gold: fastest, cheapest,
  most AFK and most profitable.
- **What gear?** Per equipment slot: what you own, what to buy next, and what to work
  toward.
- **What content?** Bosses, raids, minigames, quests and unlocks you are ready for, and
  what stands between you and the rest.

It reads your levels, quests, account type and bank itself, so there is nothing to type
in and nothing to keep up to date.

## What it does

**Plan** — your combat and total level, then **Next up**: one feed of what to do, in
priority order.

1. **Your Slayer task**, read straight from the game.
2. **The next step of each goal.** Two goals needing the same levels share one entry.
3. **Quick wins**: a gear upgrade you can use and afford now, content whose requirement
   you crossed in the last couple of levels, a combat achievement tier within reach.
4. **Training**: the best use of your time across every skill, weighted toward the ones
   you have neglected, with how soon the next level comes.

These are buckets, not one blended score: no honest number weighs "equip this shield"
against "train Agility". Click any entry to open the tab with its detail and
alternatives. Your goals sit underneath, each with progress, hours left and every step.

**Goals** — a level ("85 Slayer"), a piece of gear ("Bandos chestplate") or an activity
("The fire cape"). Set a level goal from a skill's route panel, or right-click any gear
row or activity. Runebuddy works each goal backwards: every missing level becomes a
timed training route, and quests, items and notes are listed but not timed, because
there is no honest estimate for them. Hitpoints is credited with what the goal's other
combat training will give it, so the same hours are not counted twice. Ironman
requirements apply. Goals are saved per RuneScape account.

**Skills** — a grid of skill icons. Pick one and its page opens with **ways to level**:
routes from your level to a target (ten levels on by default, or your goal), recommended,
fastest, cheapest, most AFK and most profitable, each with hours and total gold. Routes
switch method as better ones unlock ("willows to 60, then teaks"), estimates count only
what is left of your current level, and styles that land on the same route share a row.
Below that, its methods ranked for your account,
each showing the experience rate *at your level*, what it costs or earns, what it needs,
and a link to the wiki guide. Methods just out of reach appear underneath as upcoming
unlocks, so you know what the next few levels buy you.

**Gear** — melee, ranged, magic and skilling tools. Each row shows what you are wearing,
the best item you qualify for and can afford, and the rung above it with the requirement
that is blocking it — "needs 70 Attack", "needs Recipe for Disaster".

**Do** — what to actually go and do: bosses, raids, minigames, skilling activities,
quests, diaries and unlocks, split into what you are ready for now, what is nearly in
reach, and what to aim at. Each entry leads with the reason to go. It opens with your
**combat achievement** points and how far the next reward tier is; the points and the
tier thresholds both come from the game.

**Slayer** — your current task, read from the game's own tables the way RuneLite's
Slayer plugin does, so the name is always right, boss tasks and Konar's areas included.
Advice from `slayer.json` adds where to go, the style it wants, whether a cannon works,
and whether it is usually done, skipped or blocked. Skipping is only suggested when you
have the points. A task the file does not cover still shows, without advice.

## How the ranking works

Three things are weighed against each other, and you decide how much each matters with
sliders in the plugin config:

| Slider | What it favours |
| --- | --- |
| **Value XP rate** | Raw experience per hour, at your current level |
| **Value gold** | Methods that pay, or at least ones you can afford |
| **Value AFK-ness** | Methods that do not need constant clicking |

The experience term is measured against the other options *you* have for that skill, so
"fast" always means "fast compared to your alternatives" rather than against some
absolute ceiling. The gold term compares what a method costs against what you can
actually pay: set a **Budget** in hours and anything you cannot sustain for that long
gets ranked down. That is what stops the panel telling an account with 1,000 coins to go
and do Nightmare Zone.

Ironman accounts and free-to-play accounts are detected automatically and never shown
methods that depend on buying supplies or on membership. Both can be overridden in the
config if the detection is wrong or you are planning ahead.

## Gear and your bank

The bank can only be read while it is open, so Runebuddy remembers what it saw and
stores it against your RuneScape account. Open your bank once and the gear tab can tell
what you already own from then on, including in later sessions. Until it has seen a
bank, it says so rather than guessing.

## Building and running

Requires a JDK 11 or newer.

```
./gradlew build     # compile and run the tests
./gradlew run       # launch a RuneLite client with the plugin side-loaded
```

`./gradlew run` starts a real client, so you will need a display and an account to log in
with.

### Logging in with a Jagex account

A development client is not launched by the Jagex Launcher, so it has no session to log
in with. RuneLite supports this directly: the launcher can write its credentials out for
a development client to pick up.

1. Open the RuneLite launcher's configuration window. On Windows that is the
   **RuneLite (configure)** Start Menu entry; on Mac or Linux, pass `--configure` to the
   launcher. You need launcher 2.6.3 or newer.
2. Add `--insecure-write-credentials` to the **Client arguments** field, and save.
3. Launch RuneLite through the Jagex Launcher once. It writes your credentials to
   `.runelite/credentials.properties`.
4. Run `./gradlew run`. The development client finds that file and logs in with it.

This works when RuneLite was installed *through* the Jagex Launcher too — the launcher
configuration is deliberately stored so that it still applies when the Jagex Launcher
starts RuneLite. If the Start Menu entry is missing, run the configuration window
straight off the executable instead:

```powershell
# find it
Get-ChildItem $env:LOCALAPPDATA, "C:\Program Files" -Filter RuneLite.exe -Recurse -ErrorAction SilentlyContinue |
  Select-Object -ExpandProperty FullName

# then
& "<path>\RuneLite.exe" --configure
```

**That file grants access to your account without a password.** Do not share it, do not
commit it. Delete it when you have finished testing, and remove the client argument
again. If you think it has been exposed, use **End sessions** in your account settings on
runescape.com to invalidate it.

### If the build fails

**`ExceptionInInitializerError` during `:compileJava`** means Lombok does not understand
your JDK. Lombok reaches into compiler internals, so it has to be new enough for
whatever Java you are compiling with, and it fails this way rather than saying so. Check
your version with `java -version` and, if it is newer than the `lombokVersion` in
`build.gradle` supports, raise that version — the
[Lombok changelog](https://projectlombok.org/changelog) lists which release added support
for each JDK.

The plugin itself always targets Java 11 bytecode via `options.release.set(11)`,
regardless of which JDK builds it, so a newer JDK is fine as long as Lombok agrees.

## The data files

Everything Runebuddy recommends comes from JSON files in
`src/main/resources/com/runebuddy/`, not from code: `training_methods.json`,
`gear.json`, `content.json` and `slayer.json`, plus `manifest.json` describing them.
Editing them needs no Java.

### Data updates

The data can be fixed without a plugin release. At startup and every six hours,
Runebuddy fetches `manifest.json` from this repository's `main` branch on
`raw.githubusercontent.com`. If it describes a newer version in the schema this build
understands, the four data files are fetched and put through exactly the same validation
as the bundled copy. Only a complete set that passes every check is used; anything else
keeps the current data. The last good copy is cached in `.runelite/runebuddy/` for
offline starts.

- **Nothing about you or your account is sent.** It is a plain download of public files.
- **Links are restricted** to `https://oldschool.runescape.wiki/`, since they open in
  your browser when clicked.
- **Switch it off** under *Data* in the plugin settings to use only the bundled data.

To publish a data fix: edit the files, bump `version` in `manifest.json` (a sortable
date such as `2026-09-28`, or `2026-09-28.2` for a second change that day), run the
tests, and merge to `main`. Bump `schema` only when a file changes shape; installs
built for the old schema then ignore the new data until they update.

### Adding Slayer advice

`slayer.json` entries are keyed by the task name the game uses, matched ignoring case:

```json
{
  "task": "Abyssal demons",
  "locations": ["Catacombs of Kourend", "Abyssal Sire (counts as the task)"],
  "style": "MELEE",
  "cannonable": false,
  "verdict": "DO",
  "why": "Steady XP and whip drops",
  "notes": "Cannons cannot be used in the Catacombs.",
  "wikiUrl": "https://oldschool.runescape.wiki/w/Abyssal_demon"
}
```

`verdict` is `DO`, `SKIP` or `BLOCK`; `style` is `MELEE`, `RANGED` or `MAGIC`, or left
out when there is no clear one.

### Adding a training method

Append an object to `training_methods.json`:

```json
{
  "id": "mining_iron_powermine",
  "skill": "MINING",
  "name": "Power-mine iron ore",
  "minLevel": 15,
  "recommendedUntil": 75,
  "xpCurve": [
    {"level": 15, "xpPerHour": 20000},
    {"level": 60, "xpPerHour": 42000},
    {"level": 99, "xpPerHour": 52000}
  ],
  "gpPerHour": 0,
  "effort": "HIGH",
  "members": false,
  "ironmanFriendly": true,
  "requirements": {
    "skills": {"MINING": 15},
    "quests": ["DORICS_QUEST"],
    "items": [1275],
    "notes": ["A three-rock cluster for the best rates"]
  },
  "location": "Ardougne east mine",
  "notes": "Drop the ore as you go.",
  "wikiUrl": "https://oldschool.runescape.wiki/w/Mining_training"
}
```

- `id` — unique across the file; used in tests and log messages.
- `xpCurve` — rates at a few levels, interpolated in between and clamped at the ends. A
  method with a flat rate needs only one point. This is what lets the ranking use your
  real level instead of one number for 1–99.
- `recommendedUntil` — the level past which better options exist. The method stays
  listed above this, marked as out-levelled and ranked down.
- `gpPerHour` — negative is a cost, positive is a profit.
- `effort` — `AFK`, `LOW`, `MEDIUM` or `HIGH`.
- `ironmanFriendly` — set `false` only when the method depends on *buying* its inputs.
- `requirements.skills` keys are `Skill` enum names; `requirements.quests` are `Quest`
  enum names. `requirements.notes` is free text for anything that cannot be checked
  automatically, such as diary tiers or minigame access — it is shown but never blocks.

### Adding a piece of gear

Append an object to `gear.json`:

```json
{
  "itemId": 4151,
  "name": "Abyssal whip",
  "slot": "WEAPON",
  "category": "MELEE",
  "tier": 70,
  "members": true,
  "tradeable": true,
  "source": "Abyssal demons",
  "requirements": {"skills": {"ATTACK": 70}},
  "ironmanRequirements": {"skills": {"SLAYER": 85}},
  "notes": "The standard melee training weapon."
}
```

Items form a ladder per `(category, slot)`, ordered by `tier`. Tiers must be unique
within a ladder; the number itself is arbitrary, so using the level requirement is a
convenient convention. `category` is `MELEE`, `RANGED`, `MAGIC` or `SKILLING`; skilling
entries use `"slot": "TOOL"` and must name the skill they serve with `"toolFor"`.

- `source` — how you get one. Required on every entry, and it may not be
  "Grand Exchange": that is the one source an ironman cannot use, so every item needs a
  way to obtain it yourself.
- `tradeable` — whether it can be bought at all. Untradeables are never filtered out on
  price and never show one.
- `ironmanRequirements` — extra requirements that apply **only** to ironman accounts,
  using the same shape as `requirements`. This is where the real cost of self-obtaining
  goes.
- `situational` — set `true` for gear that is only strong against particular targets: the
  demonbane weapons, the dragon hunter crossbow. A ladder means "generally better as you
  go up", and these break that, so they stay in the data but out of the ranking.
  Without it the panel tells a level 70 account to aim for a demonbane sword as its
  everyday weapon.

That last field is the one to get right. Without it, anything gated behind a boss rather
than a level reads as freely available to an ironman, which floats raid drops above an
abyssal whip. Where the gate is a boss, use the stats you would realistically need to go
and kill it, and put the boss itself in `notes`.

### How gear is judged

There are two halves to this, and they age very differently.

**What you own** is ranked from the client's own live equipment data — RuneLite serves
stats for every item in the game, so a shield added last week ranks correctly against one
from 2005. Melee sorts on strength, ranged on ranged strength, magic on magic damage,
with accuracy and then defence breaking ties for the slots that carry no damage bonus.
None of this depends on the item appearing in any data file.

**What to aim for** comes from the curated ladders in `gear.json`, because level
requirements are the one thing the client does not expose. That half can fall behind the
game, and will; the difference is that it now does so visibly, in a file anyone can edit,
rather than silently deciding your gear does not exist.

### Item variants

Players hold whichever charge or condition they happen to have, so ids are folded onto
one per family before anything is compared — an amulet of glory(6) counts as the glory
the data names, and a degraded or broken armour piece counts as the pristine one. This
uses RuneLite's own variation table, so a data entry only ever needs the one id.

## Ironman accounts

The account type is read from the client, covering standard, hardcore, ultimate and both
group variants, with an override in the config.

On the **Skills** and **Plan** tabs, methods flagged `ironmanFriendly: false` — the ones
that only work if you can buy the inputs, such as Nightmare Zone, Blast Furnace or dart
fletching — are dropped entirely. Every skill still has at least one option left, and
there is a test enforcing that.

On the **Gear** tab, coins buy nothing, so the affordability filter is skipped and the
best item you qualify for is simply the answer. What holds an ironman back is levels, so
`ironmanRequirements` are applied on top of the ordinary ones, prices are not shown, and
every row names where the item comes from.

### Adding an activity

Append an object to `content.json`:

```json
{
  "id": "fight_caves",
  "name": "Fight Caves",
  "category": "MINIGAME",
  "members": true,
  "ironmanFriendly": true,
  "requirements": {"skills": {"RANGED": 61, "DEFENCE": 43, "PRAYER": 43}},
  "recommendedGear": {"style": "RANGED", "bonus": 70},
  "rewards": "The fire cape, best in slot for melee training for years",
  "notes": "A rite of passage. Bring a crossbow and learn the prayer switches.",
  "wikiUrl": "https://oldschool.runescape.wiki/w/Fight_Caves"
}
```

- `category` — `BOSS`, `RAID`, `MINIGAME`, `SKILLING`, `QUEST`, `DIARY` or `UNLOCK`.
  Each has its own switch in the plugin settings.
- `rewards` — required, because it is the reason to go and the first thing the card says.
- `recommendedGear` — a style and an offensive bonus worth having, checked against the
  player's real equipment. Levels alone are a poor guide: a maxed account in rune meets
  the stated requirements for a great deal it should not attempt. Omit it when the
  activity does not care.
- `ironmanRequirements` works exactly as it does for gear.

Free-to-play entries need `"members": false`, and are worth adding deliberately — nearly
everything in the file is members-only, so without them a free account opens the tab to
nothing.

### Validation

The data is checked when it loads and again by the test suite: ids are unique, every
skill and slot name resolves, experience curves are non-empty and ordered, tiers do not
collide, every skill has methods, every gear entry names a source that is not the Grand
Exchange, and every skill has something an ironman and a free-to-play account can do, Slayer tasks
are unique, and every link points at the wiki. A structural mistake fails the build
rather than quietly producing bad advice, and fetched updates go through the same checks.

A quest name that this version of the RuneLite API does not know about is the one
exception: it becomes a text note instead of failing, because the data files are
expected to outlive any particular client release.

## Known gaps

- **Sailing** is in RuneLite's skill enum but has no entries here. Making up experience
  rates for methods that are not settled would be worse than saying nothing; the panel
  renders the empty skill with an explanation.
- **Method costs are static.** `gpPerHour` is a number in the data file, not a live
  calculation. Live Grand Exchange prices are used for gear only.
- **Time estimates are only as good as the rates.** Hours come from the experience
  rates in the data, which are typical figures; yours will differ.
- **Quests and item hunts inside a goal are listed, not timed.**
- **Combat achievements show tier progress only**, not individual tasks.
- **Slayer advice** covers the common tasks. The task itself is always read correctly.
- Runebuddy only reads state and renders advice. It does not automate anything.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
