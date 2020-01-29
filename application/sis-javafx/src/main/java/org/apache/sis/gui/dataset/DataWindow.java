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
package org.apache.sis.gui.dataset;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import org.apache.sis.internal.gui.Resources;


/**
 * Shows features, sample values, map or coverages in a separated window.
 * The data are initially shown in the "Data" pane of {@link ResourceExplorer},
 * but may be copied in a separated, usually bigger, windows.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
final class DataWindow extends Stage {
    /**
     * The tools bar. Removed from the pane when going in full screen mode,
     * and reinserted when exiting full screen mode.
     */
    private final ToolBar tools;

    DataWindow(final Stage main, final FeatureTable data) {
        final FeatureTable features = new FeatureTable(data);
        Resources localized = Resources.forLocale(data.textLocale);
        /*
         * Build the tools bar. This bar will be hidden in full screen mode.
         */
        final Button mainWindow = new Button("⌂");
        mainWindow.setTooltip(new Tooltip(localized.getString(Resources.Keys.MainWindow)));
        mainWindow.setOnAction((event) -> {main.show(); main.toFront();});

        final Button fullScreen = new Button("⇱");
        fullScreen.setTooltip(new Tooltip(localized.getString(Resources.Keys.FullScreen)));
        fullScreen.setOnAction((event) -> setFullScreen(true));

        tools = new ToolBar(mainWindow, fullScreen);
        fullScreenProperty().addListener((source, oldValue, newValue) -> onFullScreen(newValue));
        /*
         * Main content. We use an initial size covering a large fraction of the screen
         * since this window is typically used for showing image or large tabular data.
         */
        final BorderPane pane = new BorderPane();
        pane.setTop(tools);
        pane.setCenter(features);
        setScene(new Scene(pane));
        final Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        setWidth (0.8 * bounds.getWidth());
        setHeight(0.8 * bounds.getHeight());
    }

    /**
     * Invoked when entering or existing the full screen mode.
     */
    private void onFullScreen(final boolean entering) {
        final BorderPane pane = (BorderPane) getScene().getRoot();
        pane.setTop(entering ? null : tools);
    }
}