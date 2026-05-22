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
 *   if (result != null) { // use it ... }
 * </pre>
 *
 * @author GraphVisual Feature Builder
 * @see RandomGraphGenerator
 */
public class RandomGraphDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    // ── Model name constants (avoid magic strings) ────────────────────────
    static final String MODEL_ERDOS_RENYI    = "erdos-renyi";
    static final String MODEL_BARABASI       = "barabasi-albert";
    static final String MODEL_WATTS_STROGATZ = "watts-strogatz";
    static final String MODEL_RANDOM_REGULAR = "random-regular";
    static final String MODEL_GRID           = "grid";

    private Graph<String, Edge> generatedGraph = null;

    private final JComboBox<String> modelCombo;
    private final JSpinner nSpinner;
    private final JSpinner pSpinner;
    private final JSpinner mSpinner;
    private final JSpinner kSpinner;
    private final JSpinner betaSpinner;
    private final JSpinner rowsSpinner;
    private final JSpinner colsSpinner;

    private final JLabel nLabel, pLabel, mLabel, kLabel, betaLabel, rowsLabel, colsLabel;
    private final JLabel descLabel;

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
        form.add(modelCombo, c);

        // Parameter rows — each builds (label, spinner) pair and returns them.
        nSpinner    = new JSpinner(new SpinnerNumberModel(20,   2, 500,  1));
        pSpinner    = new JSpinner(new SpinnerNumberModel(0.15, 0.01, 1.0, 0.01));
        mSpinner    = new JSpinner(new SpinnerNumberModel(2,    1, 50,   1));
        kSpinner    = new JSpinner(new SpinnerNumberModel(4,    2, 50,   1));
        betaSpinner = new JSpinner(new SpinnerNumberModel(0.3,  0.0, 1.0, 0.05));
        rowsSpinner = new JSpinner(new SpinnerNumberModel(5,    1, 50,   1));
        colsSpinner = new JSpinner(new SpinnerNumberModel(5,    1, 50,   1));

        nLabel    = addSpinnerRow(form, c, 1, "Vertices (n):",   nSpinner);
        pLabel    = addSpinnerRow(form, c, 2, "Edge prob (p):",  pSpinner);
        mLabel    = addSpinnerRow(form, c, 3, "Edges/vertex (m):", mSpinner);
        kLabel    = addSpinnerRow(form, c, 4, "Neighbors (k):",  kSpinner);
        betaLabel = addSpinnerRow(form, c, 5, "Rewire (\u03B2):", betaSpinner);
        rowsLabel = addSpinnerRow(form, c, 6, "Rows:",            rowsSpinner);
        colsLabel = addSpinnerRow(form, c, 7, "Columns:",         colsSpinner);

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

        // Description label (driven by the same listener as visibility)
        descLabel = new JLabel();
        descLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        descLabel.setFont(descLabel.getFont().deriveFont(Font.ITALIC, 11f));
        add(descLabel, BorderLayout.NORTH);

        // Single listener: previously the dialog registered two separate
        // ActionListeners on modelCombo (one for visibility, one for the
        // description label). Consolidated into one to keep update logic
        // atomic and avoid ordering surprises if either ever throws.
        modelCombo.addActionListener(e -> onModelChanged());

        onModelChanged();   // initial sync
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Adds a (label, spinner) row to {@code form} at the given grid row and
     * returns the created label so callers can toggle its visibility.
     *
     * <p>Extracted to remove the seven nearly-identical
     * "set gridx/y, new JLabel(text), set gridx, add(spinner)" blocks in
     * the original constructor.</p>
     */
    private static JLabel addSpinnerRow(JPanel form, GridBagConstraints c,
                                        int gridY, String text, JSpinner spinner) {
        JLabel label = new JLabel(text);
        c.gridx = 0; c.gridy = gridY;
        form.add(label, c);
        c.gridx = 1;
        form.add(spinner, c);
        return label;
    }

    /**
     * Reacts to a model-selection change: updates which parameter rows are
     * visible and refreshes the description text. Package-private so unit
     * tests can drive it directly without firing Swing events.
     */
    void onModelChanged() {
        String model = (String) modelCombo.getSelectedItem();
        applyVisibility(model);
        applyDescription(model);
    }

    private void applyVisibility(String model) {
        boolean showP    = MODEL_ERDOS_RENYI.equals(model);
        boolean showM    = MODEL_BARABASI.equals(model);
        boolean showK    = MODEL_WATTS_STROGATZ.equals(model) || MODEL_RANDOM_REGULAR.equals(model);
        boolean showBeta = MODEL_WATTS_STROGATZ.equals(model);
        boolean showGrid = MODEL_GRID.equals(model);
        boolean showN    = !showGrid;

        setRowVisible(nLabel,    nSpinner,    showN);
        setRowVisible(pLabel,    pSpinner,    showP);
        setRowVisible(mLabel,    mSpinner,    showM);
        setRowVisible(kLabel,    kSpinner,    showK);
        setRowVisible(betaLabel, betaSpinner, showBeta);
        setRowVisible(rowsLabel, rowsSpinner, showGrid);
        setRowVisible(colsLabel, colsSpinner, showGrid);

        // k-row label depends on which model is showing it
        kLabel.setText(MODEL_RANDOM_REGULAR.equals(model) ? "Degree (k):" : "Neighbors (k):");
    }

    private static void setRowVisible(JLabel label, JSpinner spinner, boolean visible) {
        label.setVisible(visible);
        spinner.setVisible(visible);
    }

    private void applyDescription(String model) {
        String desc = RandomGraphGenerator.catalog().getOrDefault(model, "");
        descLabel.setText("<html>" + desc + "</html>");
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

    // ── Package-private hooks for tests ───────────────────────────────────

    /** @return the currently selected model name */
    String getSelectedModel() {
        return (String) modelCombo.getSelectedItem();
    }

    /** Selects a model by name; intended for tests. */
    void setSelectedModel(String model) {
        modelCombo.setSelectedItem(model);
    }

    /** @return the parameter spinner for grid rows (test-only access). */
    JSpinner getRowsSpinner() { return rowsSpinner; }
    /** @return the parameter spinner for grid columns (test-only access). */
    JSpinner getColsSpinner() { return colsSpinner; }
    /** @return the n (vertex count) label (test-only access). */
    JLabel   getNLabel()      { return nLabel; }
    /** @return the p (edge probability) label (test-only access). */
    JLabel   getPLabel()      { return pLabel; }
    /** @return the m (edges per vertex) label (test-only access). */
    JLabel   getMLabel()      { return mLabel; }
    /** @return the k (degree / neighbors) label (test-only access). */
    JLabel   getKLabel()      { return kLabel; }
    /** @return the beta (rewiring probability) label (test-only access). */
    JLabel   getBetaLabel()   { return betaLabel; }
    /** @return the rows label (test-only access). */
    JLabel   getRowsLabel()   { return rowsLabel; }
    /** @return the cols label (test-only access). */
    JLabel   getColsLabel()   { return colsLabel; }
}
