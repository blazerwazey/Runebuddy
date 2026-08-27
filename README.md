# Runebuddy

A RuneLite plugin that answers two questions about the account you are logged into:

- **What should I train, and how?** A ranked list of training methods per skill,
  filtered to what your account can actually do right now.
- **What gear should I be aiming for?** Per equipment slot: what you own, what to buy
  next, and what to work toward.

It reads your levels, quests, account type and bank itself, so there is nothing to type
in and nothing to keep up to date.

## What it does

**Plan** — your combat and total level, then the best use of your next hour, picked
across every skill and weighted toward the ones you have neglected. Attack, Strength and
Defence share their training, so the same activity is not listed three times.

**Skills** — a grid of skill icons. Pick one to see its methods ranked for your account,
each showing the experience rate *at your level*, what it costs or earns, what it needs,
and a link to the wiki guide. Methods just out of reach appear underneath as upcoming
unlocks, so you know what the next few levels buy you.

**Gear** — melee, ranged, magic and skilling tools. Each row shows what you are wearing,
the best item you qualify for and can afford, and the rung above it with the requirement
that is blocking it — "needs 70 Attack", "needs Recipe for Disaster".

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

1. Run **RuneLite (configure)** — on Mac or Linux, launch the launcher with `--configure`.
   You need launcher 2.6.3 or newer.
2. Add `--insecure-write-credentials` to the **Client arguments** field, and save.
3. Launch RuneLite through the Jagex Launcher once. It writes your credentials to
   `.runelite/credentials.properties`.
4. Run `./gradlew run`. The development client finds that file and logs in with it.

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

Everything Runebuddy recommends comes from two JSON files in
`src/main/resources/com/runebuddy/`, not from code. Editing them needs no Java.

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

That last field is the one to get right. Without it, anything gated behind a boss rather
than a level reads as freely available to an ironman, which floats raid drops above an
abyssal whip. Where the gate is a boss, use the stats you would realistically need to go
and kill it, and put the boss itself in `notes`.

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

### Validation

The data is checked when it loads and again by the test suite: ids are unique, every
skill and slot name resolves, experience curves are non-empty and ordered, tiers do not
collide, every skill has methods, every gear entry names a source that is not the Grand
Exchange, and every skill has something an ironman and a free-to-play account can do. A
structural mistake fails the build rather than quietly producing bad advice.

A quest name that this version of the RuneLite API does not know about is the one
exception: it becomes a text note instead of failing, because the data files are
expected to outlive any particular client release.

## Known gaps

- **Sailing** is in RuneLite's skill enum but has no entries here. Making up experience
  rates for methods that are not settled would be worse than saying nothing; the panel
  renders the empty skill with an explanation.
- **Method costs are static.** `gpPerHour` is a number in the data file, not a live
  calculation. Live Grand Exchange prices are used for gear only.
- Runebuddy only reads state and renders advice. It does not automate anything.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
