package gvisual;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reusable legend panel that displays a color-coded key for edge types.
 *
 * <p>Extracted from {@link Main#initializeLegendSpace()} to separate UI
 * component construction from the main frame, making it independently
 * testable and reusable in other views (e.g., headless report generation
 * or secondary analysis windows).</p>
 *
 * @author zalenix
 */
public class LegendPanel extends JPanel {

    private static final Map<String, String> LEGEND_ENTRIES = new LinkedHashMap<>();
    static {
        LEGEND_ENTRIES.put("Friend", "images/green.jpg");
        LEGEND_ENTRIES.put("Familiar Stranger", "images/gray.jpg");
        LEGEND_ENTRIES.put("Stranger", "images/red.jpg");
        LEGEND_ENTRIES.put("Classmate", "images/blue.jpg");
        LEGEND_ENTRIES.put("Study Group", "images/yellow.jpg");
    }

    /**
     * Creates a legend panel with the default edge-type colour mapping.
     */
    public LegendPanel() {
        JLabel heading = new JLabel("Legend for the Graph");
        Box vbox = Box.createVerticalBox();

        for (Map.Entry<String, String> entry : LEGEND_ENTRIES.entrySet()) {
            Box row = Box.createHorizontalBox();
            row.add(new JLabel(new ImageIcon(entry.getValue())));
            row.add(new JLabel(entry.getKey()));
            vbox.add(row);
        }

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                heading, vbox);
        add(splitPane);
    }
}
