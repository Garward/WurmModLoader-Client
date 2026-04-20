/**
 * Event logic helpers for complex event processing.
 *
 * <p>This package contains helper classes and utilities for events that require
 * complex logic or calculations. Event classes themselves should remain simple POJOs,
 * with complex logic delegated to classes in this package.
 *
 * <h2>Organization</h2>
 * <p>Eventlogic classes are organized in subpackages that mirror the event package
 * structure:
 * <ul>
 *   <li>{@code eventlogic.lifecycle} - Lifecycle event helpers</li>
 *   <li>{@code eventlogic.input} - Input event helpers (future)</li>
 *   <li>{@code eventlogic.entity} - Entity event helpers (future)</li>
 * </ul>
 *
 * @since 0.1.0
 */
package com.garward.wurmmodloader.client.api.events.eventlogic;
