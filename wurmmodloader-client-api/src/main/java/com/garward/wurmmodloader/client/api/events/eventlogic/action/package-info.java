/**
 * Reusable client-side action-dispatch utilities. Call
 * {@link com.garward.wurmmodloader.client.api.events.eventlogic.action.PlayerActionDispatcher#dispatch}
 * from any mod that needs to send a {@code PlayerAction} against a target
 * keyword (hover, tile, tool, body, selected, area, tile_n…sw, toolbelt,
 * @tbN, @eqN, @nearbyR). The original implementation lived in
 * {@code com.garward.wurmmodloader.mods.action.ActionClientMod#parseAct} and
 * was lifted to the api so other mods (automine, future autochop, …) can
 * dispatch without depending on the action mod.
 *
 * @since 0.4.0
 */
package com.garward.wurmmodloader.client.api.events.eventlogic.action;
