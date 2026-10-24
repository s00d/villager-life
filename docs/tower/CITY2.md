# After the spire: heroes, finer gear, a second age of the town

The spire is the endgame, not the point of the mod: the point is a town that lives on its own. What the spire gives
has to show in the town, where the player spends most of the time. Three things, built on what the mod already has
(minimum new systems, the same code paths):

1. **Gear that looks like its work** — spire armour and weapons change their look as they are honed, not only their
   numbers.
2. **Heroes** — villagers who have been up the spire come back armed: they wear its gear and fight off monsters.
3. **The second age** — coins earned in the spire let families restyle their houses: the same plans, finer
   materials and dressing, built by hand like everything else.

## References

- *Guard Villagers* (seymourimadeit): guards are villagers with a sword or crossbow, armour shown on them, shield
  blocks, food when low; patrol a point. Recruiting = giving a weapon. → heroes are ordinary villagers given gear,
  not a new mob. <https://www.curseforge.com/minecraft/mc-mods/guard-villagers>
- *MineColonies*: guard towers and barracks, knights fight close, archers keep range; "a guard's equipment changes
  its appearance"; huts have levels 1–5 and style packs move "from simple wooden structures to solid stone
  buildings" as they level. → the second age is a level of finish over the same plan.
  <https://minecolonies.com/wiki/buildings/guardtower>, <https://minecolonies.com/wiki/systems/research>
- *Millénaire*: villages upgrade their buildings themselves when they can; walls go from palisade to stone. →
  restyling is the villagers' own decision, paid from their purse, never the player's chore.
- *Fantasy 3D Armors* (resource pack): one geometry a set, layered plates, shoulder armour, helmet ornaments in a
  blocky style. → spire armour gets 3D shoulders, crest and plates of its own, growing with the hone.

## 1. Gear that changes with its hone

Hone stages: **0** (+0…+4), **1** (+5…+14), **2** (+15…+29), **3** (+30 and up) — `SpireGear.stage(stack)`.

- Item textures: every tier × piece × stage (`tools/art/paint_spire_gear.py`): stage 1 adds a brass inlay along the
  blade/plates, stage 2 a glowing rune of the Core and a gem, stage 3 a halo of light round the outline.
  Selected by `custom_model_data` set when the hone changes (one vanilla mechanism on every version: model
  overrides up to 1.21.3, `range_dispatch` client items from 1.21.4).
- Armour on the body: the same stages for the armour layers (1.21.1: `getArmorTexture` by stack; from 1.21.2 the
  stack's `equippable` asset is the stage's). On 1.21.1 a 3D model of its own (`IClientItemExtensions`): shoulder
  plates, a crest on the helm, knee and boot plates, larger and more ornate stage by stage.
- Held at stage 3: motes of the tier's colour round the hand.

## 2. Heroes

- A villager back from the spire with its expedition pay may buy a piece of spire gear of the town's chapter at the
  shop (the expedition's floor sets the tier; the shop never stocks it — it is ordered from the Builders, like a
  pair of animals). Gear is worn: the sword in hand (drawn in the hand, not across the arms), the armour as an
  outfit layer with 3D shoulders.
- `HeroBehavior` (brain core package, like `Doors`): a hero sees a monster within 16 inside its town's land, goes
  for it and strikes with its sword's damage; never chases past the town's land, never fights at night outside the
  walls (it guards doors), flees under a third of its health. Heroes do not panic (`VillagerHostilesSensorMixin`).
- Fallen heroes leave the gear to the family (in the house chest), never to the ground.

## 3. The second age

- A town with a risen spire is in its second age. A family with a house of the mod, the coins for it in its own
  purse (`Housing.Purpose.GROWTH`: own money and loan, never the town's), and a house finished at its top level
  restyles it: `HouseLevels.restyle` — the same plan, level and rotation, the palette `<biome>_fine` (dressed stone
  plinths, dark timber, slate or copper roofs, glass panes, lanterns), the blocks that differ taken down and set
  anew by hand (the same take-down/rebuild as a level). Then dressing of the second age: banners, flower boxes,
  Builders' sconces at the door.
- One restyle a town a day, the dearest houses last; the shop stocks the fine materials (`Market.building` from the
  new palettes).

## Economy checks (GameTests)

- Restyling spends only the family's purse and loan: `office.minted` does not move, the treasury does not pay.
- Expedition pay stays between a wage and two silver; a hero's gear costs more than a trip pays (no hero from one
  trip).
- A town run for 20 days with a spire (`sweep.py`): `minted` per day stays under the existing bound.
