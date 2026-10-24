# NeoForge / vanilla events vs VNAP expand

Wired before expand: tick, spawn finalize, join/leave, break, right-click block/item, entity interact, attack entity, wake, damage, death.

## Easy NeoForge hooks to add (Phase D)

| Event | Proposed title(s) | Notes |
|-------|-------------------|--------|
| `ItemFishedEvent` | Catch a Fish (nearby) | distinct from ambient `Fish` |
| `AnimalTameEvent` | Tame an Animal | wolf/cat/parrot/horse |
| `LivingUseTotemEvent` | Use a Totem of Undying | player |
| `LivingShieldBlockEvent` | Block with a Shield | player blocks |
| `ArrowNockEvent` | Draw a Bow | hold drawn |
| `BonemealEvent` | Use Bone Meal | crops/sapling |
| `AnvilRepairEvent` | Repair on an Anvil | |
| `PlayerEnchantItemEvent` | Enchant an Item | |
| `PlayerXpEvent.PickupXp` | Pick Up XP | near villager |
| `PlayerEvent.PlayerChangedDimensionEvent` | Change Dimension | |
| `PlayerEvent.PlayerRespawnEvent` | Respawn After Death | |
| `EntityMountEvent` | Mount a Horse / Mount a Boat | path-based |
| `NoteBlockEvent.Play` | Play a Note Block | |
| `ItemTossEvent` | Throw an Item | |
| `PlayerDestroyItemEvent` | Tool Breaks | |
| `PlayerBrewedPotionEvent` | Brew a Potion | |
| `CriticalHitEvent` | Land a Critical Hit | |
| `SweepAttackEvent` | Sweep Attack | |
| `EntityStruckByLightningEvent` | Lightning Hits Nearby | villager/player witness |
| `BabyEntitySpawnEvent` | Animals Have a Baby | non-villager |

## playerContext hold/env (no new event)

Spyglass, totem, shield, map/filled_map, on fire, swimming, thunderstorm, diamond ore break (in break handler), fill bucket / milk / ender eye / torch place.

## Bugs fixed in this pass

- Steal replaced Harvest Near Farmer always → split chance
- `isFdFood` matched any FD item → food-ish only
- CBC shell detect via title substring → namespace helper
- Nested `Map` helper in ModFlavorMatchers → `java.util.Map.entry`
- `EventBusSubscriber.Bus` deprecated → drop `bus=`
- Explosion nearest player → players() scan (avoid deprecated overload)
