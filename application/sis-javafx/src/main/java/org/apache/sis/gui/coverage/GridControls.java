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
package org.apache.sis.gui.coverage;

import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.util.resources.Vocabulary;


/**
 * A {@link GridView} with associated controls to show in a {@link CoverageExplorer}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
final class GridControls extends Controls {
    /**
     * The component for showing sample values.
     */
    private final GridView view;

    /**
     * The controls for changing {@link #view}.
     */
    private final Accordion controls;

    /**
     * The control showing sample dimensions for the current coverage.
     */
    private final TableView<SampleDimension> sampleDimensions;

    /**
     * Creates a new set of grid controls.
     *
     * @param  vocabulary  localized set of words, provided in argument before often known by the caller.
     */
    GridControls(final Vocabulary vocabulary) {
        view = new GridView();
        sampleDimensions = new CategoryCellFactory(view.cellFormat).createSampleDimensionTable(vocabulary);
        sampleDimensions.getSelectionModel().selectedIndexProperty().addListener(new BandSelectionListener(view.bandProperty));
        view.bandProperty.addListener(this::onBandSpecified);
        /*
         * "Coverage" section with the following controls:
         *    - Coverage domain as a list of CRS dimensions with two of them selected (TODO).
         *    - Coverage range as a list of sample dimensions with at least one selected.
         */
        final VBox coveragePane;
        {   // Block for making variables locale to this scope.
            final Label label = new Label(vocabulary.getLabel(Vocabulary.Keys.SampleDimensions));
            label.setPadding(CAPTION_MARGIN);
            label.setLabelFor(sampleDimensions);
            coveragePane = new VBox(label, sampleDimensions);
        }
        /*
         * "Display" section with the following controls:
         *    - Number format as a localized pattern.
         *    - Cell width as a slider.
         */
        final VBox displayPane;
        {   // Block for making variables locale to this scope.
            final GridPane gp = createControlGrid(
                label(vocabulary, Vocabulary.Keys.Width,  createSlider(view.cellWidth,  30, 200)),
                label(vocabulary, Vocabulary.Keys.Height, createSlider(view.cellHeight, 10,  50)),
                label(vocabulary, Vocabulary.Keys.Format, view.cellFormat.createEditor())
            );
            final Label label = new Label(vocabulary.getLabel(Vocabulary.Keys.Cells));
            label.setPadding(CAPTION_MARGIN);
            label.setLabelFor(gp);
            displayPane = new VBox(label, gp);
        }
        /*
         * Put all sections together and have the first one expanded by default.
         */
        controls = new Accordion(
            new TitledPane(vocabulary.getString(Vocabulary.Keys.Coverage), coveragePane),
            new TitledPane(vocabulary.getString(Vocabulary.Keys.Display),  displayPane)
            // TODO: more controls to be added in a future version.
        );
        controls.setExpandedPane(controls.getPanes().get(0));
    }

    /**
     * Creates a new slider for the given range of values and bound to the specified properties.
     * This is used for creating the sliders to shown in the "Display" pane.
     */
    private static Slider createSlider(final DoubleProperty property, final double min, final double max) {
        final Slider slider = new Slider(min, max, property.getValue());
        property.bind(slider.valueProperty());
        slider.setShowTickMarks(false);
        return slider;
    }

    /**
     * Invoked when the band property changed. This method ensures that the selected row
     * in the sample dimension table matches the band which is shown in the grid view.
     */
    private void onBandSpecified(final ObservableValue<? extends Number> property,
                                 final Number previous, final Number band)
    {
        sampleDimensions.getSelectionModel().clearAndSelect(band.intValue());
    }

    /**
     * Invoked after {@link CoverageExplorer#setCoverage(ImageRequest)} for updating the table of
     * sample dimensions with information become available. This method is invoked in JavaFX thread.
     *
     * @param  data  the new coverage, or {@code null} if none.
     */
    @Override
    final void updateBandTable(final GridCoverage data) {
        final ObservableList<SampleDimension> items = sampleDimensions.getItems();
        if (data != null) {
            items.setAll(data.getSampleDimensions());
            sampleDimensions.getSelectionModel().clearAndSelect(view.getBand());
        } else {
            items.clear();
        }
    }

    /**
     * Returns the main component, which is showing coverage tabular data.
     */
    @Override
    final Region view() {
        return view;
    }

    /**
     * Returns the controls for controlling the view of tabular data.
     */
    @Override
    final Control controls() {
        return controls;
    }
}