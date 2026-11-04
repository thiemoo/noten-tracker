package de.notentracker.ui;

import javafx.scene.layout.Region;

/**
 * Schlichte Icons als SVG-Pfade (24×24-Raster). Die Farbe kommt aus der CSS ({@code .icon}),
 * damit sie im hellen und dunklen Modus passt.
 */
final class Icons {

    static final String OVERVIEW = "M3 3h8v8H3z M13 3h8v5h-8z M13 10h8v11h-8z M3 13h8v8H3z";
    static final String CHARTS = "M4 20V11h4v9z M10 20V4h4v16z M16 20v-7h4v7z";
    static final String TARGET = "M12 2a10 10 0 1 1 0 20a10 10 0 1 1 0-20z M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z "
            + "M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z";
    static final String STAR = "M12 2l3 7h7l-5.5 4.5 2 7.5-6.5-4.5-6.5 4.5 2-7.5L2 9h7z";
    static final String LAYERS = "M12 2l10 5-10 5L2 7z M2 11.5l2.2-1.1L12 14.3l7.8-3.9 2.2 1.1-10 5z "
            + "M2 16.5l2.2-1.1L12 19.3l7.8-3.9 2.2 1.1-10 5z";
    static final String CHECK = "M9 16.2l-3.5-3.5L4 14.2l5 5 11-11-1.4-1.4z";
    static final String UPLOAD = "M12 3l5 5h-3v6h-4V8H7z M5 17h14v4H5z";
    static final String PLUS = "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z";

    private Icons() {
    }

    static Region icon(String svgPath) {
        Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + svgPath + "\";");
        icon.getStyleClass().add("icon");
        return icon;
    }
}
