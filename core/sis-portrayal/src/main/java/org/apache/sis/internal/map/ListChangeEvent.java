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
package org.apache.sis.internal.map;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.sis.measure.NumberRange;

/**
 * Event generated by modified list properties.
 *
 * @author Johann Sorel (Geomatys)
 * @version 1.2
 * @since   1.2
 */
public final class ListChangeEvent<T> extends PropertyChangeEvent {

    public enum Type {
        ADDED,
        REMOVED,
        CHANGED
    }

    private final NumberRange<Integer> range;
    private final Type type;
    private final List<T> items;

    private ListChangeEvent(final Object source, String propertyName, List<T> originalList, final List<? extends T> items, final NumberRange<Integer> range, final Type type){
        super(source, propertyName, originalList, originalList);
        this.range = range;
        this.type = type;
        this.items = (items != null) ? Collections.unmodifiableList(new ArrayList<>(items)) : null;
    }

    /**
     * Returns the range index of the affected items.
     * If the event type is Added, the range correspond to the index range after insertion.
     * If the event type is Removed, the range correspond to the index before deletion.
     * @return NumberRange added or removed range.
     */
    public NumberRange<Integer> getRange(){
        return range;
    }

    /**
     * Returns event type.
     */
    public Type getType(){
        return type;
    }

    /**
     * Returns the affected items of this event.
     * This property is set if event is of type added or removed.
     *
     * @return List
     */
    public List<T> getItems(){
        return items;
    }

    public static <T> ListChangeEvent<T> added(Object source, String propertyName, List<T> originalList, T newItem, final int index) {
        return added(source, propertyName, originalList, Arrays.asList(newItem),
                NumberRange.create(index, true, index, true));
    }

    public static <T> ListChangeEvent<T> added(Object source, String propertyName, List<T> originalList, List<T> newItems, final NumberRange<Integer> range) {
        return new ListChangeEvent<>(source, propertyName, originalList,  newItems, range, Type.ADDED);
    }

    public static <T> ListChangeEvent<T> removed(Object source, String propertyName, List<T> originalList, T newItem, final int index) {
        return removed(source, propertyName, originalList, Arrays.asList(newItem),
                NumberRange.create(index, true, index, true));
    }

    public static <T> ListChangeEvent<T> removed(Object source, String propertyName, List<T> originalList, List<T> oldItems, final NumberRange<Integer> range) {
        return new ListChangeEvent<>(source, propertyName, originalList, oldItems, range, Type.REMOVED);
    }

    public static <T> ListChangeEvent<T> changed(Object source, String propertyName, List<T> originalList) {
        return new ListChangeEvent<>(source, propertyName, originalList, null, null, Type.CHANGED);
    }
}
