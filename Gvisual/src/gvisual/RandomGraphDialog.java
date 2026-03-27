package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog for generating random graphs interactively.
 *
 * <p>Presents a drop-down of available random graph models from
 * {@link RandomGraphGenerator} and dynamically shows relevant parameter
 * inputs. When the user clicks "Generate", a new graph is built and
 * returned via {@link #getGeneratedGraph()}.</p>
 *
 * <h3>Usage from Main or ToolbarBuilder:</h3>
 * <pre>
 *   RandomGraphDialog dlg = new RandomGraphDialog(parentFrame);
 *   dlg.setVisible(true);
 *   Graph&lt;String, Edge&gt; result = dlg.getGeneratedGraph();
 *   if (result != null) { /* use it */ }
 * </pre>
 *
 * @author GraphVisual Feature Builder
 * @see RandomGraphGenerator
 */
public class RandomGraphDialog extends JDialog {

    private Graph<String, Edge> generatedGraph = null;

    private final JComboBox<String> modelCombo;
    private final JSpinner nSpinner;
    private final JSpinner pSpinner;
    private final JSpinner mSpinner;
    private final JSpinner kSpinner;
    private final JSpinner betaSpinner;
    private final JSpinner rowsSpinner;
    private final JSpinner colsSpinner;

    private final JLabel pLabel, mLabel, kLabel, betaLabel, rowsLabel, colsLabel;

    public RandomGraphDialog(Frame owner) {
        super(owner, "Random Graph Generator", true);
        setLayout(new BorderLayout(8, 8));
        setResizable(false);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Model selector
        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Model:"), c);
        c.gridx = 1;
        String[] models = RandomGraphGenerator.catalog().keySet().toArray(new String[0]);
        modelCombo = new JComboBox<>(models);
        modelCombo.addActionListener(e -> updateVisibility());
        form.add(modelCombo, c);

        // n (vertices)
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Vertices (n):"), c);
        c.gridx = 1;
        nSpinner = new JSpinner(new SpinnerNumberModel(20, 2, 500, 1));
        form.add(nSpinner, c);

        // p (edge probability)
        c.gridx = 0; c.gridy = 2;
        pLabel = new JLabel("Edge prob (p):");
        form.add(pLabel, c);
        c.gridx = 1;
        pSpinner = new JSpinner(new SpinnerNumberModel(0.15, 0.01, 1.0, 0.01));
        form.add(pSpinner, c);

        // m (edges per new vertex)
        c.gridx = 0; c.gridy = 3;
        mLabel = new JLabel("Edges/vertex (m):");
        form.add(mLabel, c);
        c.gridx = 1;
        mSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));
        form.add(mSpinner, c);

        // k (degree / neighbors)
        c.gridx = 0; c.gridy = 4;
        kLabel = new JLabel("Degree (k):");
        form.add(kLabel, c);
        c.gridx = 1;
        kSpinner = new JSpinner(new SpinnerNumberModel(4, 2, 50, 1));
        form.add(kSpinner, c);

        // beta (rewiring)
        c.gridx = 0; c.gridy = 5;
        betaLabel = new JLabel("Rewire (β):");
        form.add(betaLabel, c);
        c.gridx = 1;
        betaSpinner = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05));
        form.add(betaSpinner, c);

        // rows
        c.gridx = 0; c.gridy = 6;
        rowsLabel = new JLabel("Rows:");
        form.add(rowsLabel, c);
        c.gridx = 1;
        rowsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        form.add(rowsSpinner, c);

        // cols
        c.gridx = 0; c.gridy = 7;
        colsLabel = new JLabel("Columns:");
        form.add(colsLabel, c);
        c.gridx = 1;
        colsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        form.add(colsSpinner, c);

        add(form, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton generateBtn = new JButton("Generate");
        generateBtn.addActionListener(e -> {
            generatedGraph = buildGraph();
            if (generatedGraph != null) {
                JOptionPane.showMessageDialog(this,
                        "Generated graph: " + generatedGraph.getVertexCount() + " vertices, "
                                + generatedGraph.getEdgeCount() + " edges",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            }
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(generateBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // Description label
        JLabel desc = new JLabel();
        desc.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        desc.setFont(desc.getFont().deriveFont(Font.ITALIC, 11f));
        Map<String, String> catalog = RandomGraphGenerator.catalog();
        modelCombo.addActionListener(e -> {
            String sel = (String) modelCombo.getSelectedItem();
            desc.setText("<html>" + catalog.getOrDefault(sel, "") + "</html>");
        });
        desc.setText("<html>" + catalog.getOrDefault(models[0], "") + "</html>");
        add(desc, BorderLayout.NORTH);

        updateVisibility();
        pack();
        setLocationRelativeTo(owner);
    }

    private void updateVisibility() {
        String model = (String) modelCombo.getSelectedItem();
        boolean showP = "erdos-renyi".equals(model);
        boolean showM = "barabasi-albert".equals(model);
        boolean showK = "watts-strogatz".equals(model) || "random-regular".equals(model);
        boolean showBeta = "watts-strogatz".equals(model);
        boolean showGrid = "grid".equals(model);
        boolean showN = !showGrid;

        pLabel.setVisible(showP); pSpinner.setVisible(showP);
        mLabel.setVisible(showM); mSpinner.setVisible(showM);
        kLabel.setVisible(showK); kSpinner.setVisible(showK);
        betaLabel.setVisible(showBeta); betaSpinner.setVisible(showBeta);
        rowsLabel.setVisible(showGrid); rowsSpinner.setVisible(showGrid);
        colsLabel.setVisible(showGrid); colsSpinner.setVisible(showGrid);
        nSpinner.setVisible(showN);

        // Update k label text
        if ("random-regular".equals(model)) {
            kLabel.setText("Degree (k):");
        } else {
            kLabel.setText("Neighbors (k):");
        }
    }

    private Graph<String, Edge> buildGraph() {
        String model = (String) modelCombo.getSelectedItem();
        Map<String, Number> params = new HashMap<>();
        params.put("n", (Number) nSpinner.getValue());
        params.put("p", (Number) pSpinner.getValue());
        params.put("m", (Number) mSpinner.getValue());
        params.put("k", (Number) kSpinner.getValue());
        params.put("beta", (Number) betaSpinner.getValue());
        params.put("rows", (Number) rowsSpinner.getValue());
        params.put("cols", (Number) colsSpinner.getValue());
        return RandomGraphGenerator.byName(model, params);
    }

    /** Returns the generated graph, or null if the dialog was cancelled. */
    public Graph<String, Edge> getGeneratedGraph() {
        return generatedGraph;
    }
}
