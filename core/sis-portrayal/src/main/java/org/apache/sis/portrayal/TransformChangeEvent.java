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
package org.apache.sis.portrayal;

import java.util.Optional;
import java.util.logging.Logger;
import java.awt.geom.AffineTransform;
import java.beans.PropertyChangeEvent;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.apache.sis.referencing.operation.matrix.AffineTransforms2D;
import org.apache.sis.referencing.operation.transform.LinearTransform;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.internal.system.Modules;
import org.apache.sis.util.logging.Logging;


/**
 * A change in the "objective to display" transform that {@code Canvas} uses for rendering data.
 * That transform is updated frequently following gestures events such as zoom, translation or rotation.
 * All events fired by {@link Canvas} for the {@value Canvas#OBJECTIVE_TO_DISPLAY_PROPERTY} property
 * are instances of this class.
 * This specialization provides methods for computing the difference between the old and new state.
 *
 * <h2>Multi-threading</h2>
 * This class is <strong>not</strong> thread-safe.
 * All listeners should process this event in the same thread.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 *
 * @see Canvas#OBJECTIVE_TO_DISPLAY_PROPERTY
 *
 * @since 1.3
 * @module
 */
public class TransformChangeEvent extends PropertyChangeEvent {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = 4444752056666264066L;

    /**
     * The change from old coordinates to new coordinates, computed when first needed.
     *
     * @see #getDisplayChange()
     * @see #getObjectiveChange()
     */
    private transient LinearTransform displayChange, objectiveChange;

    /**
     * Value of {@link #displayChange} or {@link #objectiveChange} precomputed by the code that fired this event.
     */
    AffineTransform displayChange2D, objectiveChange2D;

    /**
     * Non-null if {@link #canNotCompute(String, NoninvertibleTransformException)} already reported an error.
     * This is used for avoiding to report many times the same error.
     */
    private transient Exception error;

    /**
     * Creates a new event for a change of the "objective to display" property.
     * The old and new transforms should not be null, except for lazy computation:
     * a null {@code newValue} means to take the value from {@link Canvas#getObjectiveToDisplay()} when needed.
     *
     * @param  source    the canvas that fired the event.
     * @param  oldValue  the old "objective to display" transform.
     * @param  newValue  the new transform, or {@code null} for lazy computation.
     * @throws IllegalArgumentException if {@code source} is {@code null}.
     */
    public TransformChangeEvent(final Canvas source, final LinearTransform oldValue, final LinearTransform newValue) {
        super(source, Canvas.OBJECTIVE_TO_DISPLAY_PROPERTY, oldValue, newValue);
    }

    /**
     * Returns the canvas on which this event initially occurred.
     *
     * @return the canvas on which this event initially occurred.
     */
    @Override
    public Canvas getSource() {
        return (Canvas) source;
    }

    /**
     * Gets the old "objective to display" transform.
     *
     * @return the old "objective to display" transform.
     */
    @Override
    public LinearTransform getOldValue() {
        return (LinearTransform) super.getOldValue();
    }

    /**
     * Gets the new "objective to display" transform.
     * It should be the current value of {@link Canvas#getObjectiveToDisplay()}.
     *
     * @return the new "objective to display" transform.
     */
    @Override
    public LinearTransform getNewValue() {
        LinearTransform value = (LinearTransform) super.getNewValue();
        if (value == null) {
            value = getSource().getObjectiveToDisplay();
        }
        return value;
    }

    /**
     * Returns the change from old objective coordinates to new objective coordinates.
     * When the "objective to display" transform changed (e.g. because the user did a zoom, translation or rotation),
     * this method expresses how the "real world" coordinates (typically in metres) of any point on the screen changed.
     *
     * <div class="note"><b>Example:</b>
     * if the map is shifted 10 metres toward the right side of the canvas, then (assuming no rotation or axis flip)
     * the <var>x</var> translation coefficient of the change is +10 (same sign than {@link #getDisplayChange()}).
     * Note that it may correspond to any amount of pixels, depending on the zoom factor.</div>
     *
     * The {@link #getObjectiveChange2D()} method gives the same transform as a Java2D object.
     * That change can be replicated on another canvas by giving the transform to
     * {@link PlanarCanvas#transformObjectiveCoordinates(AffineTransform)}.
     *
     * @return the change in objective coordinates. Usually not {@code null},
     *         unless one of the canvas is initializing or has a non-invertible transform.
     */
    public LinearTransform getObjectiveChange() {
        if (objectiveChange == null) {
            if (objectiveChange2D != null) {
                objectiveChange = AffineTransforms2D.toMathTransform(objectiveChange2D);
            } else {
                final LinearTransform oldValue = getOldValue();
                if (oldValue != null) {
                    final LinearTransform newValue = getNewValue();
                    if (newValue != null) try {
                        objectiveChange = (LinearTransform) MathTransforms.concatenate(newValue, oldValue.inverse());
                    } catch (NoninvertibleTransformException e) {
                        canNotCompute("getObjectiveChange", e);
                    }
                }
            }
        }
        return objectiveChange;
    }

    /**
     * Returns the change from old display coordinates to new display coordinates.
     * When the "objective to display" transform changed (e.g. because the user did a zoom, translation or rotation),
     * this method expresses how the display coordinates (typically pixels) of any given point on the map changed.
     *
     * <div class="note"><b>Example:</b>
     * if the map is shifted 10 pixels toward the right side of the canvas, then (assuming no rotation or axis flip)
     * the <var>x</var> translation coefficient of the change is +10: the points on the map which were located at
     * <var>x</var>=0 pixel before the change are now located at <var>x</var>=10 pixels after the change.</div>
     *
     * The {@link #getDisplayChange2D()} method gives the same transform as a Java2D object.
     * That change can be replicated on another canvas by giving the transform to
     * {@link PlanarCanvas#transformDisplayCoordinates(AffineTransform)}.
     *
     * @return the change in display coordinates. Usually not {@code null},
     *         unless one of the canvas is initializing or has a non-invertible transform.
     */
    public LinearTransform getDisplayChange() {
        if (displayChange == null) {
            if (displayChange2D != null) {
                displayChange = AffineTransforms2D.toMathTransform(displayChange2D);
            } else {
                final LinearTransform oldValue = getOldValue();
                if (oldValue != null) {
                    final LinearTransform newValue = getNewValue();
                    if (newValue != null) try {
                        displayChange = (LinearTransform) MathTransforms.concatenate(oldValue.inverse(), newValue);
                    } catch (NoninvertibleTransformException e) {
                        canNotCompute("getDisplayChange", e);
                    }
                }
            }
        }
        return displayChange;
    }

    /**
     * Returns the change in objective coordinates as a Java2D affine transform.
     * This method is suitable for two-dimensional canvas only.
     * For performance reason, it does not clone the returned transform.
     *
     * @return the change in objective coordinates. <strong>Do not modify.</strong>
     *
     * @see #getObjectiveChange()
     */
    public Optional<AffineTransform> getObjectiveChange2D() {
        if (objectiveChange2D == null) try {
            objectiveChange2D = AffineTransforms2D.castOrCopy(getObjectiveChange());
        } catch (IllegalArgumentException e) {
            canNotCompute("getObjectiveChange2D", e);
        }
        return Optional.ofNullable(objectiveChange2D);
    }

    /**
     * Returns the change in display coordinates as a Java2D affine transform.
     * This method is suitable for two-dimensional canvas only.
     * For performance reason, it does not clone the returned transform.
     *
     * @return the change in display coordinates. <strong>Do not modify.</strong>
     *
     * @see #getDisplayChange()
     */
    public Optional<AffineTransform> getDisplayChange2D() {
        if (displayChange2D == null) try {
            displayChange2D = AffineTransforms2D.castOrCopy(getDisplayChange());
        } catch (IllegalArgumentException e) {
            canNotCompute("getDisplayChange2D", e);
        }
        return Optional.ofNullable(displayChange2D);
    }

    /**
     * Invoked when a change can not be computed. It should never happen because "objective to display"
     * transforms should always be invertible. If this error nevertheless happens, consider the change
     * as a missing optional information.
     */
    private void canNotCompute(final String method, final Exception e) {
        if (error == null) {
            error = e;
            Logging.recoverableException(Logger.getLogger(Modules.PORTRAYAL), TransformChangeEvent.class, method, e);
        } else {
            error.addSuppressed(e);
        }
    }
}