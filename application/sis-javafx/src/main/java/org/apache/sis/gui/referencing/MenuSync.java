/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.gui.referencing;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import org.opengis.referencing.ReferenceSystem;
import org.apache.sis.internal.gui.GUIUtilities;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.util.resources.Vocabulary;


/**
 * Keep a list of menu items up to date with an {@code ObservableList<ReferenceSystem>}.
 * The selected {@link MenuItem} is given by {@link ToggleGroup#selectedToggleProperty()}
 * but for the purpose of {@link RecentReferenceSystems} we rather need a property giving
 * the selected reference system directly.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
final class MenuSync extends SimpleObjectProperty<ReferenceSystem> implements EventHandler<ActionEvent> {
    /**
     * Keys where to store the reference system in {@link MenuItem}.
     */
    private static final String REFERENCE_SYSTEM_KEY = "ReferenceSystem";

    /**
     * Sentinel value associated to {@link #REFERENCE_SYSTEM_KEY} for requesting the {@link CRSChooser}.
     */
    private static final String CHOOSER = "CHOOSER";

    /**
     * The manager of reference systems to synchronize with.
     */
    private final RecentReferenceSystems owner;

    /**
     * The list of menu items to keep up-to-date with an {@code ObservableList<ReferenceSystem>}.
     */
    private final ObservableList<MenuItem> menus;

    /**
     * The group of menus.
     */
    private final ToggleGroup group;

    /**
     * The action to execute when a reference system is selected.
     */
    private final ChangeListener<ReferenceSystem> action;

    /**
     * Creates a new synchronization for the given list of menu items.
     *
     * @param  owner    the manager of reference systems to synchronize with.
     * @param  systems  the reference systems for which to build menu items.
     * @param  bean     the menu to keep synchronized with the list of reference systems.
     * @param  action   the user-specified action to execute when a reference system is selected.
     */
    MenuSync(final RecentReferenceSystems owner, final ObservableList<ReferenceSystem> systems,
             final Menu bean, final ChangeListener<ReferenceSystem> action)
    {
        super(bean, "value");
        this.owner  = owner;
        this.menus  = bean.getItems();
        this.group  = new ToggleGroup();
        this.action = action;
        /*
         * We do not register listener for `systems` list.
         * Instead `notifyChanges(…)` will be invoked directly by RecentReferenceSystems.
         */
        final MenuItem[] items = new MenuItem[systems.size()];
        for (int i=0; i<items.length; i++) {
            items[i] = createItem(systems.get(i));
        }
        menus.setAll(items);
        initialize(systems);
    }

    /**
     * Sets the initial value to the first item in the {@code systems} list, if any.
     * This method is invoked in JavaFX thread at construction time or, if it didn't
     * work at some later time when the systems list may contain an element.
     * This method should not be invoked anymore after initialization succeeded.
     */
    private void initialize(final ObservableList<? extends ReferenceSystem> systems) {
        for (final ReferenceSystem system : systems) {
            if (system != RecentReferenceSystems.OTHER) {
                set(system);
                break;
            }
        }
    }

    /**
     * Creates a new menu item for the given reference system.
     */
    private MenuItem createItem(final ReferenceSystem system) {
        if (system != RecentReferenceSystems.OTHER) {
            final RadioMenuItem item = new RadioMenuItem(IdentifiedObjects.getDisplayName(system, owner.locale));
            item.getProperties().put(REFERENCE_SYSTEM_KEY, system);
            item.setToggleGroup(group);
            item.setOnAction(this);
            return item;
        } else {
            final MenuItem item = new MenuItem(Vocabulary.getResources(owner.locale).getString(Vocabulary.Keys.Others) + '…');
            item.getProperties().put(REFERENCE_SYSTEM_KEY, CHOOSER);
            item.setOnAction(this);
            return item;
        }
    }

    /**
     * Must be invoked after removing a menu item for avoiding memory leak.
     */
    private static void dispose(final MenuItem item) {
        item.setOnAction(null);
        if (item instanceof RadioMenuItem) {
            ((RadioMenuItem) item).setToggleGroup(null);
        }
    }

    /**
     * Invoked when the list of reference systems changed. While it would be possible to trace the permutations,
     * additions, removals and replacements done on the list, it is easier to recreate the menu items list from
     * scratch (with recycling of existing items) and inspect the differences.
     */
    final void notifyChanges(final ObservableList<? extends ReferenceSystem> systems) {
        final Map<Object,MenuItem> mapping = new IdentityHashMap<>();
        for (final Iterator<MenuItem> it = menus.iterator(); it.hasNext();) {
            final MenuItem item = it.next();
            if (mapping.putIfAbsent(item.getProperties().get(REFERENCE_SYSTEM_KEY), item) != null) {
                it.remove();    // Remove duplicated item. Should never happen, but we are paranoiac.
                dispose(item);
            }
        }
        final MenuItem[] items = new MenuItem[systems.size()];
        for (int i=0; i<items.length; i++) {
            items[i] = mapping.remove(systems.get(i));
        }
        /*
         * Previous loop copied all items that could be reused as-is. Now search for all items that are new.
         * If there is some menu items available, recycle them.
         */
        final Iterator<MenuItem> recycle = mapping.values().iterator();
        for (int i=0; i<items.length; i++) {
            if (items[i] == null) {
                final ReferenceSystem system = systems.get(i);
                if (system != RecentReferenceSystems.OTHER && recycle.hasNext()) {
                    final MenuItem item = recycle.next();
                    recycle.remove();
                    if (item instanceof RadioMenuItem) {
                        item.setText(IdentifiedObjects.getDisplayName(system, owner.locale));
                        item.getProperties().put(REFERENCE_SYSTEM_KEY, system);
                        items[i] = item;
                        continue;
                    }
                }
                items[i] = createItem(system);
            }
        }
        /*
         * If there is any item left, we must remove them from the ToggleGroup for avoiding memory leak.
         */
        while (recycle.hasNext()) {
            dispose(recycle.next());
        }
        GUIUtilities.copyAsDiff(Arrays.asList(items), menus);
        /*
         * If we had no previously selected item, selects it now.
         */
        if (get() == null) {
            initialize(systems);
        }
    }

    /**
     * Invoked when user selects a menu item. This method gets the old and new values and sends them
     * to {@link org.apache.sis.gui.referencing.RecentReferenceSystems.Listener} as a change event.
     * That {@code Listener} will update the list of reference systems, which may result in a callback
     * to {@link #notifyChanges(ObservableList)}. If the selected menu item is the "Other…" choice,
     * then {@code Listener} will popup {@link CRSChooser} and callback {@link #set(ReferenceSystem)}
     * for storing the result. Otherwise we need to invoke  {@link #set(ReferenceSystem)} ourselves.
     */
    @Override
    public void handle(final ActionEvent event) {
        // ClassCastException should not happen because this listener is registered only on MenuItem.
        final Object value = ((MenuItem) event.getSource()).getProperties().get(REFERENCE_SYSTEM_KEY);
        if (value == CHOOSER) {
            action.changed(this, get(), RecentReferenceSystems.OTHER);
        } else {
            set((ReferenceSystem) value);
        }
    }

    /**
     * Selects the specified reference system. This method is invoked by {@link RecentReferenceSystems} when the
     * selected CRS changed, either programmatically or by user action. User-specified {@link #action} is invoked,
     * which will typically start a background thread for transforming data. This method does nothing if the given
     * reference system is same as current one; this is important both for avoiding infinite loop and for avoiding
     * to invoke the potentially costly {@link #action}.
     */
    @Override
    public void set(final ReferenceSystem system) {
        final ReferenceSystem old = get();
        if (old != system) {
            super.set(system);
            for (final MenuItem item : menus) {
                if (item instanceof RadioMenuItem && item.getProperties().get(REFERENCE_SYSTEM_KEY) == system) {
                    ((RadioMenuItem) item).setSelected(true);
                    action.changed(this, old, system);
                    return;
                }
            }
            group.selectToggle(null);
            action.changed(this, old, null);
        }
    }
}
