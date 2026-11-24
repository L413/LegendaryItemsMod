# LegendaryItemsMod

## Lifesteal heart texture

Heart counts are stored as half-heart health points: `20` is normal health and
`40` is double normal health. The Heart of Life uses the custom item model
`legendary:life_heart`. To customize its appearance, replace
`assets/legendary/textures/item/life_heart.png` inside `LegendaryItemsPack.zip`
with your PNG texture, then reload the resource pack.

## Admin commands and legendary item handling

- `/lifesteal settings` opens Lifesteal settings; `/lifesteal give` gives a Heart of Life; `/lifesteal set <player> <half-hearts|reset>` changes a player's health.
- `/legendary give <item_id>` gives a registered legendary item; `/legendary destroyall` removes all legendary items; `/legendary settings` opens its settings menu, including the carry rule (`one armor + one tool anywhere in inventory` or `one overall`).
- Legendary items cannot be stored in containers or item frames. Attempts to store them eject the item instead of damaging the container.
- The Legendary settings menu controls logout behavior (`beacon`, `drop`, or `inventory`) and tracker coordinate rounding. `beacon` keeps items in an offline player's inventory and leaves a stationary beam at logout; the item is not location-tracked until the player returns.