package nortantis.swing;

import nortantis.*;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;

import nortantis.util.Helper;
import nortantis.util.Logger;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A two-step dialog for creating a higher-detail sub-map from a region of the current map.
 *
 * Step 1 (non-modal): User drags on the map to select a region. Step 2 (modal): User chooses detail level and previews the sub-map before
 * creating it.
 */
public class SubMapDialog
{
	private final MainWindow mainWindow;

	// Captured state from the current map. origSettings is captured at construction for step 1's resolution-independent dimension math, but
	// origGraph, origResolution, and origSettings are all (re)captured when entering step 2 from the then-current main-map state, so a
	// display-quality change made while step 1 is open is reflected in the sub-map (and so the graph is never captured null mid-draw).
	private MapSettings origSettings;
	private WorldGraph origGraph;
	/** The display-quality resolution scale at which origGraph was created. */
	private double origResolution;
	/** File name (not full path) of the original map, or null if it had not been saved. Recorded in the sub-map's provenance info. */
	private final String origFileName;

	// Shared state between steps.
	private Rectangle selBoundsRI;

	// Step 1 dialog state.
	private JDialog step1Dialog;
	private JSpinner xSpinner, ySpinner, widthSpinner, heightSpinner;
	private JLabel step1ErrorLabel;
	private JButton step1NextButton;
	/** Guards against infinite update loops between spinners and the selection box. */
	private boolean updatingSpinnersFromBox = false;
	/** Currently selected effective aspect ratio (width / height), including any 90° rotation. 0 = no lock. */
	private double selectedAspectRatio = 0.0;
	/** The chosen preset aspect ratio before optional 90° rotation (width / height). 0 = no lock. */
	private double selectedBaseAspectRatio = 0.0;
	/** When true, the selected non-square preset aspect ratio is rotated 90° (inverted) to portrait orientation. */
	private boolean rotateAspectRatio90 = false;
	/** Checkbox that rotates the selected aspect ratio 90°. Always visible; disabled for Custom and Square. */
	private JCheckBox rotate90Checkbox;

	// Step 2 dialog / preview state.
	private JDialog step2Dialog;
	private MapUpdater previewUpdater;
	private MapEditingPanel previewPanel;
	private JPanel previewContainer;
	private volatile MapSettings lastSubMapSettings;
	private SliderWithDisplayedValue detailSliderWithValue;
	private JButton createButton;
	private JSlider detailSlider;
	private JProgressBar previewProgressBar;
	/** Inline, wrap-capable warning shown under the preview when cities near the shore disappear onto water in the sub-map. */
	private JTextArea citiesOnWaterWarningArea;
	/** Holds the warning icon and {@link #citiesOnWaterWarningArea}; its visibility is toggled to show or hide the whole warning. */
	private JPanel citiesOnWaterWarningPanel;
	/** The text last shown in {@link #citiesOnWaterWarningArea} ("" when hidden), used to detect when the warning changed and the preview must be redrawn. */
	private String lastCitiesOnWaterWarningText = "";
	/** True while the next preview draw is the one-shot redraw requested because the warning changed the preview height, so it does not request yet another. */
	private boolean isRedrawForWarning = false;
	private Timer progressBarTimer;
	/** Stable seed for the sub-map graph; generated once per step-2 session so re-draws produce the same Voronoi layout. */
	private long subMapSeed;
	/**
	 * True once {@link #subMapSeed} has been generated, so going Back to step 1 and forward again keeps the same seed (and any edit the
	 * user made to it) rather than regenerating it.
	 */
	private boolean subMapSeedGenerated = false;
	private JTextField seedTextField;
	/** Set to true in windowOpened; guards componentResized from firing the first preview draw before the dialog is fully shown. */
	private boolean step2DialogOpened = false;
	/**
	 * The preview container size the last preview draw was kicked off for, or null before the first draw. The window-open and resize
	 * handlers use it to skip a redraw when the size has not actually changed, so the preview is not drawn twice when the dialog fires a
	 * redundant resize while it is first shown.
	 */
	private nortantis.geom.Dimension lastPreviewDrawSize;
	/** The 1× polygon count for the current selection (computed when step 2 opens). */
	private double oneXWorldSize;
	/** Hides/shows the custom polygon count slider row. */
	private RowHider sliderRowHider;
	/** Hides/shows the amber warning shown in Custom detail mode. */
	private RowHider customWarningRowHider;
	/** Radio button for custom polygon count; instance field so createPreviewUpdater can read its state. */
	private JRadioButton customRadio;
	private int clampedOneXWorldSize;
	static final int minPolygonsInSubMap = 1000;
	private static final Color warningMessageColor = new java.awt.Color(160, 90, 0);

	/**
	 * Computes the clamped 1× world size (polygon count) for a sub-map selection. This matches the default "Match source detail" value
	 * shown in the sub-map dialog.
	 */
	public static int computeDefaultWorldSize(MapSettings origSettings, Rectangle selectionBoundsRI)
	{
		double origMapArea = origSettings.generatedWidth * (double) origSettings.generatedHeight;
		double selArea = selectionBoundsRI.width * selectionBoundsRI.height;
		double oneXWorldSize = origSettings.worldSize * selArea / origMapArea;
		return (int) Math.round(Math.min(SettingsGenerator.maxWorldSize, Math.max(minPolygonsInSubMap, oneXWorldSize)));
	}

	public SubMapDialog(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;
		this.origSettings = mainWindow.getSettingsFromGUI(false);
		this.origFileName = mainWindow.getOpenSettingsFilePath() == null ? null : mainWindow.getOpenSettingsFilePath().getFileName().toString();
	}

	// -------------------------------------------------------------------------
	// Step 1: Selection box
	// -------------------------------------------------------------------------

	public void showStep1()
	{
		// Disable editing tools and theme controls for the duration of the sub-map workflow.
		// Call onSwitchingAway() first so each tool clears its internal selection state
		// (e.g. selected icon in IconsTool, selected text in TextTool) before we clear visuals.
		mainWindow.toolsPanel.currentTool.onSwitchingAway();
		mainWindow.mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
		mainWindow.enableOrDisableFieldsThatRequireMap(false, null, true);
		// Lock the whole menu bar for the duration of the sub-map workflow. Re-enabled at each exit point (cancel from step 1 or 2, closing
		// step 2's window, and creating the sub-map).
		mainWindow.setMenuBarEnabled(false);

		step1Dialog = new JDialog(mainWindow, Translation.get("subMapDialog.step1.title"), false);
		step1Dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		GridBagOrganizer organizer = new GridBagOrganizer();
		final int topInset = 2;

		// Instructions
		JLabel instructionsLabel = new JLabel(Translation.get("subMapDialog.step1.instructions"));
		organizer.addLeftAlignedComponent(instructionsLabel);

		// Aspect ratio buttons
		GeneratedDimension[] dims = GeneratedDimension.presets();
		int numButtons = dims.length + 1; // +1 for Custom
		double[] ratios = new double[numButtons];
		String[] ratioLabels = new String[numButtons];
		ratios[0] = 0.0;
		ratioLabels[0] = GeneratedDimension.Custom.displayName();
		for (int i = 0; i < dims.length; i++)
		{
			ratios[i + 1] = dims[i].aspectRatio();
			ratioLabels[i + 1] = dims[i].displayName();
		}

		// Lay the aspect ratio toggle buttons and the Rotate 90° checkbox out in a single horizontal row. A plain BoxLayout row is used
		// (rather than SegmentedButtonWidget) so it reports a correct single-row height in this wide dialog: the widget's preferred-size
		// heuristic is tuned for the narrow side panel and at pack() time would assume a narrow width, wrap the buttons to several rows,
		// and
		// reserve extra vertical space that shows up as a large empty gap once the dialog is displayed at its real (wide) size.
		JPanel aspectRatioRow = new JPanel();
		aspectRatioRow.setLayout(new BoxLayout(aspectRatioRow, BoxLayout.X_AXIS));

		ButtonGroup ratioGroup = new ButtonGroup();
		for (int i = 0; i < numButtons; i++)
		{
			final double ratio = ratios[i];
			JToggleButton btn = new JToggleButton(ratioLabels[i]);
			SwingHelper.reduceHorizontalMargin(btn);
			btn.setAlignmentY(Component.CENTER_ALIGNMENT);
			// Compare against the base (un-rotated) ratio so the correct button stays selected even when a 90° rotation is active.
			btn.setSelected(Math.abs(ratio - selectedBaseAspectRatio) < 0.001);
			btn.addActionListener(e ->
			{
				selectedBaseAspectRatio = ratio;
				updateRotate90CheckboxEnabled();
				applyAspectRatioSelection();
			});
			ratioGroup.add(btn);
			if (i > 0)
			{
				aspectRatioRow.add(Box.createHorizontalStrut(4));
			}
			aspectRatioRow.add(btn);
		}

		rotate90Checkbox = new JCheckBox(Translation.get("subMapDialog.step1.rotate90.label"));
		rotate90Checkbox.setToolTipText(Translation.get("subMapDialog.step1.rotate90.help"));
		rotate90Checkbox.setAlignmentY(Component.CENTER_ALIGNMENT);
		rotate90Checkbox.setSelected(rotateAspectRatio90);
		rotate90Checkbox.addActionListener(e ->
		{
			rotateAspectRatio90 = rotate90Checkbox.isSelected();
			// Rotate the existing selection in place (swap width and height) so its area is preserved, rather than stretching one
			// dimension.
			swapSelectionBoxDimensions();
			applyAspectRatioSelection();
		});
		updateRotate90CheckboxEnabled();

		aspectRatioRow.add(Box.createHorizontalStrut(8));
		aspectRatioRow.add(rotate90Checkbox);
		organizer.addLabelAndComponent(Translation.get("subMapDialog.step1.aspectRatio.label"),
				Translation.get("subMapDialog.step1.aspectRatio.help", GeneratedDimension.Custom.displayName(), GeneratedDimension.MAX_ASPECT_RATIO), aspectRatioRow, topInset);

		// Position and size spinners (use display dimensions, which are rotated relative to generatedWidth/Height for 90°/270°)
		int mapDisplayW = getMapDisplayWidth();
		int mapDisplayH = getMapDisplayHeight();
		xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, mapDisplayW, 1));
		ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, mapDisplayH, 1));
		widthSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, mapDisplayW), 1, mapDisplayW, 1));
		heightSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, mapDisplayH), 1, mapDisplayH, 1));
		SwingHelper.resetSpinnerToMinimumWhenBlank(xSpinner);
		SwingHelper.resetSpinnerToMinimumWhenBlank(ySpinner);

		// TODO See if I need to set the preferred sizes of the spinners like the code had here before.
		Dimension spinnerSize = new Dimension(75, xSpinner.getPreferredSize().height);
		xSpinner.setPreferredSize(spinnerSize);
		ySpinner.setPreferredSize(spinnerSize);
		widthSpinner.setPreferredSize(spinnerSize);
		heightSpinner.setPreferredSize(spinnerSize);

		organizer.addLabelAndComponentsHorizontalWithTopInset(Translation.get("subMapDialog.step1.position.label"), "",
				Arrays.asList(new JLabel(Translation.get("subMapDialog.step1.x")), xSpinner, new JLabel(Translation.get("subMapDialog.step1.y")), ySpinner,
						new JLabel(Translation.get("subMapDialog.step1.width")), widthSpinner, new JLabel(Translation.get("subMapDialog.step1.height")), heightSpinner),
				topInset);

		organizer.addVerticalFillerRow();

		// Inline error label for spinner validation
		step1ErrorLabel = new JLabel(" ");
		step1ErrorLabel.setForeground(java.awt.Color.RED);

		// Buttons row
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> cancelStep1());

		step1NextButton = new JButton(Translation.get("subMapDialog.step1.next"));
		step1NextButton.setEnabled(false);
		step1NextButton.addActionListener(e ->
		{
			if (selBoundsRI != null && validateStep1Spinners() == null)
			{
				// Quantize the selection to whole RI pixels before it is used to build the sub-map. The box is captured from the mouse (and
				// adjusted by the aspect-ratio presets) in sub-pixel RI coordinates, but the spinners only display — and the user only
				// reasons
				// about — integer RI values. Building the sub-map from sub-pixel bounds makes two selections that look identical in the
				// spinners produce slightly different sub-maps: every road and city is offset by ~1px (the sub-pixel shift magnified by the
				// zoom), borderline coastline polygons flip between land and water, and icons appear or disappear as sample points cross
				// center boundaries. Rounding the same way updateStep1SpinnersFromBox does makes the bounds used exactly equal the
				// displayed
				// values, so the same spinner values always reproduce the same sub-map. validateStep1Spinners has already confirmed the
				// rounded values are in bounds.
				selBoundsRI = roundToIntegerBounds(selBoundsRI);
				// Capture the current main-map graph, resolution, and settings now, rather than at construction, so a display-quality change
				// made while step 1 was open is reflected in the sub-map. The guard ensures the graph is fully built and non-null (a full draw
				// leaves mapParts.graph null until it finishes) and that origResolution matches the resolution the shared edits' text bounds
				// were last re-baked to. If a full draw is still running, the action briefly defers until it completes, then proceeds.
				mainWindow.updater.doWhenMapIsReadyForInteractions(() ->
				{
					// If this action was deferred until a running draw finished, step 1 may have been cancelled (or Next clicked again) in
					// the meantime; either disposes step1Dialog. Don't advance to step 2 in that case.
					if (step1Dialog == null)
					{
						return;
					}
					origSettings = mainWindow.getSettingsFromGUI(false);
					origGraph = mainWindow.updater.mapParts.graph;
					origResolution = mainWindow.displayQualityScale;
					disposeStep1();
					showStep2();
				});
			}
		});

		buttonsPanel.add(step1NextButton);
		buttonsPanel.add(Box.createHorizontalStrut(5));
		buttonsPanel.add(cancelButton);

		JPanel bottomRow = new JPanel(new BorderLayout());
		bottomRow.add(step1ErrorLabel, BorderLayout.LINE_START);
		bottomRow.add(buttonsPanel, BorderLayout.LINE_END);
		organizer.addLeftAlignedComponent(bottomRow, topInset, GridBagOrganizer.rowVerticalInset, false);

		step1Dialog.add(organizer.panel);
		// Pack once to learn the content width, then size the multi-line HTML instructions label to that width so its preferred height is
		// computed for the width it is actually displayed at. Otherwise its height is computed for its natural single-line width, and the
		// text is clipped once it wraps at the narrower dialog width.
		step1Dialog.pack();
		fitMultiLineLabelToWidth(instructionsLabel, organizer.panel.getWidth() - 10);
		step1Dialog.setPreferredSize(new Dimension(step1Dialog.getPreferredSize().width + 15, step1Dialog.getPreferredSize().height + 15));
		step1Dialog.pack();
		step1Dialog.setMinimumSize(step1Dialog.getSize());
		java.awt.Point parentLocation = mainWindow.getLocation();
		Dimension parentSize = mainWindow.getSize();
		Dimension dialogSize = step1Dialog.getSize();
		step1Dialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2, parentLocation.y + parentSize.height - dialogSize.height - 18);

		// Constrain the selection box to the displayed map bounds (accounts for rotation).
		mainWindow.mapEditingPanel.setSelectionBoxConstraints(new Rectangle(0, 0, getMapDisplayWidth(), getMapDisplayHeight()));
		mainWindow.mapEditingPanel.setSelectionBoxLockedAspectRatio(selectedAspectRatio);
		mainWindow.mapEditingPanel.setSelectionBoxMaxAspectRatio(selectedAspectRatio == 0.0 ? GeneratedDimension.MAX_ASPECT_RATIO : 0.0);

		// Register the selection box handler on the main map panel.
		mainWindow.mapEditingPanel.enableSelectionBox(() ->
		{
			selBoundsRI = mainWindow.mapEditingPanel.getSelectionBoxRI();
			updateStep1SpinnersFromBox();
			updateStep1NextButton();
		});

		// Wire spinners: when edited by user, update the selection box.
		ChangeListener xYListener = e ->
		{
			if (!updatingSpinnersFromBox)
			{
				applySpinnersToSelectionBox();
			}
		};
		xSpinner.addChangeListener(xYListener);
		ySpinner.addChangeListener(xYListener);

		widthSpinner.addChangeListener(e ->
		{
			if (!updatingSpinnersFromBox)
			{
				if (selectedAspectRatio != 0.0)
				{
					int w = ((Number) widthSpinner.getValue()).intValue();
					int h = (int) Math.round(w / selectedAspectRatio);
					h = Math.max(1, Math.min(getMapDisplayHeight(), h));
					updatingSpinnersFromBox = true;
					try
					{
						heightSpinner.setValue(h);
					}
					finally
					{
						updatingSpinnersFromBox = false;
					}
				}
				applySpinnersToSelectionBox();
			}
		});

		heightSpinner.addChangeListener(e ->
		{
			if (!updatingSpinnersFromBox)
			{
				if (selectedAspectRatio != 0.0)
				{
					int h = ((Number) heightSpinner.getValue()).intValue();
					int w = (int) Math.round(h * selectedAspectRatio);
					w = Math.max(1, Math.min(getMapDisplayWidth(), w));
					updatingSpinnersFromBox = true;
					try
					{
						widthSpinner.setValue(w);
					}
					finally
					{
						updatingSpinnersFromBox = false;
					}
				}
				applySpinnersToSelectionBox();
			}
		});

		step1Dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				cancelStep1();
			}
		});

		// If we already have a selection (e.g. when going Back from step 2), sync the spinners.
		if (selBoundsRI != null)
		{
			updateStep1SpinnersFromBox();
			updateStep1NextButton();
		}

		step1Dialog.setVisible(true);
	}

	/**
	 * Applies the current spinner values as the selection box, validates, and updates the Next button and error label.
	 */
	private void applySpinnersToSelectionBox()
	{
		int x = ((Number) xSpinner.getValue()).intValue();
		int y = ((Number) ySpinner.getValue()).intValue();
		int w = ((Number) widthSpinner.getValue()).intValue();
		int h = ((Number) heightSpinner.getValue()).intValue();

		String error = validateSpinnerValues(x, y, w, h);
		step1ErrorLabel.setText(error != null ? error : " ");

		if (error == null)
		{
			selBoundsRI = new Rectangle(x, y, w, h);
			mainWindow.mapEditingPanel.setSelectionBoxRI(selBoundsRI);
		}
		updateStep1NextButton();
	}

	/**
	 * Validates spinner values. Returns an error message string, or null if valid.
	 */
	private String validateSpinnerValues(int x, int y, int w, int h)
	{
		if (w <= 0 || h <= 0)
		{
			return Translation.get("subMapDialog.error.widthHeightMin");
		}
		if (x < 0 || y < 0)
		{
			return Translation.get("subMapDialog.error.xyMin");
		}
		if (x + w > getMapDisplayWidth())
		{
			return Translation.get("subMapDialog.error.xWidthExceeds", getMapDisplayWidth());
		}
		if (y + h > getMapDisplayHeight())
		{
			return Translation.get("subMapDialog.error.yHeightExceeds", getMapDisplayHeight());
		}
		return null;
	}

	/**
	 * Returns the current validation error for the spinners, or null if valid.
	 */
	private String validateStep1Spinners()
	{
		return validateSpinnerValues(((Number) xSpinner.getValue()).intValue(), ((Number) ySpinner.getValue()).intValue(), ((Number) widthSpinner.getValue()).intValue(),
				((Number) heightSpinner.getValue()).intValue());
	}

	/**
	 * Returns {@code box} with each field rounded to the nearest whole RI pixel and clamped so the result stays within the displayed map
	 * bounds. This matches exactly how {@link #updateStep1SpinnersFromBox} rounds for display (so the snapped bounds equal the values shown in
	 * the spinners). Rounding each field independently can otherwise push {@code x + width} (or {@code y + height}) one pixel past the map
	 * edge, which would fail {@link #validateStep1Spinners} and disable the Next button even though the selection is valid. Width and height
	 * are floored at 1. Returns null if {@code box} is null.
	 */
	private Rectangle roundToIntegerBounds(Rectangle box)
	{
		if (box == null)
		{
			return null;
		}
		long mapWidth = getMapDisplayWidth();
		long mapHeight = getMapDisplayHeight();
		long x = Math.max(0, Math.min(Math.round(box.x), mapWidth - 1));
		long y = Math.max(0, Math.min(Math.round(box.y), mapHeight - 1));
		long width = Math.max(1, Math.min(Math.round(box.width), mapWidth - x));
		long height = Math.max(1, Math.min(Math.round(box.height), mapHeight - y));
		return new Rectangle(x, y, width, height);
	}

	/**
	 * Updates the spinner values to reflect the current selBoundsRI. Guards against recursive updates.
	 */
	private void updateStep1SpinnersFromBox()
	{
		if (xSpinner == null || selBoundsRI == null)
		{
			return;
		}
		Rectangle rounded = roundToIntegerBounds(selBoundsRI);
		updatingSpinnersFromBox = true;
		try
		{
			xSpinner.setValue((int) rounded.x);
			ySpinner.setValue((int) rounded.y);
			widthSpinner.setValue((int) rounded.width);
			heightSpinner.setValue((int) rounded.height);
			step1ErrorLabel.setText(" ");
		}
		finally
		{
			updatingSpinnersFromBox = false;
		}
		updateStep1NextButton();
	}

	private void updateStep1NextButton()
	{
		if (step1NextButton == null)
		{
			return;
		}
		step1NextButton.setEnabled(selBoundsRI != null && validateStep1Spinners() == null);
	}

	/**
	 * Returns the effective aspect ratio (width / height) implied by the selected preset and the 90° rotation toggle. 0 = no lock.
	 */
	private double effectiveAspectRatio()
	{
		if (selectedBaseAspectRatio == 0.0)
		{
			return 0.0;
		}
		return rotateAspectRatio90 ? 1.0 / selectedBaseAspectRatio : selectedBaseAspectRatio;
	}

	/**
	 * Recomputes the effective aspect ratio from the selected preset and rotation toggle, applies it to the selection box constraints, and
	 * reshapes the current selection to match.
	 */
	private void applyAspectRatioSelection()
	{
		selectedAspectRatio = effectiveAspectRatio();
		mainWindow.mapEditingPanel.setSelectionBoxLockedAspectRatio(selectedAspectRatio);
		mainWindow.mapEditingPanel.setSelectionBoxMaxAspectRatio(selectedAspectRatio == 0.0 ? GeneratedDimension.MAX_ASPECT_RATIO : 0.0);
		if (selectedAspectRatio > 0 && selBoundsRI != null)
		{
			selBoundsRI = adjustSelectionBoxToAspectRatio(selBoundsRI, selectedAspectRatio);
			mainWindow.mapEditingPanel.setSelectionBoxRI(selBoundsRI);
			updateStep1SpinnersFromBox();
		}
	}

	/**
	 * Sizes a multi-line HTML label so its preferred height matches the height it needs when laid out at the given display width. This
	 * keeps the text from being clipped when it wraps at a width narrower than its natural single-line width.
	 */
	private void fitMultiLineLabelToWidth(JLabel label, int width)
	{
		if (width <= 0)
		{
			return;
		}
		View view = (View) label.getClientProperty(BasicHTML.propertyKey);
		if (view == null)
		{
			return;
		}
		view.setSize(width, 0);
		int preferredWidth = (int) Math.ceil(view.getPreferredSpan(View.X_AXIS));
		int preferredHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
		label.setPreferredSize(new Dimension(Math.min(preferredWidth, width), preferredHeight));
	}

	/**
	 * Rotates the current selection box 90° in place by swapping its width and height (keeping the top-left corner and clamping to the map
	 * bounds). This preserves the selection's area when the Rotate 90° checkbox is toggled, instead of stretching a single dimension.
	 */
	private void swapSelectionBoxDimensions()
	{
		if (selBoundsRI == null)
		{
			return;
		}
		double newWidth = Math.max(1, Math.min(selBoundsRI.height, getMapDisplayWidth() - selBoundsRI.x));
		double newHeight = Math.max(1, Math.min(selBoundsRI.width, getMapDisplayHeight() - selBoundsRI.y));
		selBoundsRI = new Rectangle(selBoundsRI.x, selBoundsRI.y, newWidth, newHeight);
	}

	/**
	 * Enables the Rotate 90° checkbox only for non-square presets; rotating Custom or Square has no effect, so it stays disabled there.
	 */
	private void updateRotate90CheckboxEnabled()
	{
		if (rotate90Checkbox == null)
		{
			return;
		}
		boolean enabled = selectedBaseAspectRatio != 0.0 && Math.abs(selectedBaseAspectRatio - 1.0) > 0.001;
		// If the newly selected preset can't be rotated (Custom or Square), clear any existing rotation so it doesn't silently carry over.
		if (!enabled && rotateAspectRatio90)
		{
			rotateAspectRatio90 = false;
			rotate90Checkbox.setSelected(false);
		}
		rotate90Checkbox.setEnabled(enabled);
	}

	/**
	 * Adjusts the selection box to match the given aspect ratio (width / height), keeping the top-left corner fixed and clamping to the map
	 * bounds.
	 */
	private Rectangle adjustSelectionBoxToAspectRatio(Rectangle box, double ratio)
	{
		double newHeight = box.width / ratio;
		// Clamp height to map bounds.
		newHeight = Math.min(newHeight, getMapDisplayHeight() - box.y);
		newHeight = Math.max(1, newHeight);
		// If height was clamped, back-compute width to maintain ratio.
		double newWidth = newHeight * ratio;
		newWidth = Math.min(newWidth, getMapDisplayWidth() - box.x);
		newWidth = Math.max(1, newWidth);
		return new Rectangle(box.x, box.y, newWidth, newHeight);
	}

	private void cancelStep1()
	{
		mainWindow.mapEditingPanel.clearSelectionBox();
		if (step1Dialog != null)
		{
			step1Dialog.dispose();
			step1Dialog = null;
		}
		// Restore the menu bar's locked snapshot first, then recompute field states, so the snapshot doesn't overwrite them.
		mainWindow.setMenuBarEnabled(true);
		mainWindow.enableOrDisableFieldsThatRequireMap(true, mainWindow.getSettingsFromGUI(false), true);
		// Re-activate the current tool so its editing visuals (such as the overlay tool's edit highlights), which showStep1 cleared via
		// onSwitchingAway(), are restored.
		mainWindow.toolsPanel.currentTool.onSwitchingTo();
	}

	private void disposeStep1()
	{
		if (step1Dialog != null)
		{
			step1Dialog.dispose();
			step1Dialog = null;
		}
	}

	private void handleCancelStep2()
	{
		String goBackOptionText = Translation.get("subMapDialog.step2.cancelConfirm.goBack");
		String discardOptionText = Translation.get("subMapDialog.step2.cancelConfirm.discard");
		Object[] options = new Object[] { goBackOptionText, discardOptionText };
		int response = SwingHelper.showOptionDialog(step2Dialog, Translation.get("subMapDialog.step2.cancelConfirm.message"), Translation.get("common.cancel"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, goBackOptionText);

		// Discard only on an explicit Discard click. Go Back, or dismissing the dialog with Escape or the window's close button (which
		// returns CLOSED_OPTION), leaves step 2 open.
		if (response == Arrays.asList(options).indexOf(discardOptionText))
		{
			cancelStep2();
		}
	}

	/**
	 * Tears down step 2 and restores the main window. Shared by the Cancel button and the dialog's window-close (X) handler so the two
	 * paths can't drift apart.
	 */
	private void cancelStep2()
	{
		stopPreviewUpdater();
		mainWindow.mapEditingPanel.clearSelectionBox();
		if (step2Dialog != null)
		{
			step2Dialog.dispose();
			step2Dialog = null;
		}
		// Restore the menu bar's locked snapshot first, then recompute field states, so the snapshot doesn't overwrite them.
		mainWindow.setMenuBarEnabled(true);
		mainWindow.enableOrDisableFieldsThatRequireMap(true, mainWindow.getSettingsFromGUI(false), true);
		// Re-activate the current tool so its editing visuals (such as the overlay tool's edit highlights), which showStep1 cleared via
		// onSwitchingAway(), are restored.
		mainWindow.toolsPanel.currentTool.onSwitchingTo();
	}

	// -------------------------------------------------------------------------
	// Step 2: Detail level + preview
	// -------------------------------------------------------------------------

	private void showStep2()
	{
		// Generate a stable seed the first time step 2 opens, so repeated redraws produce the same Voronoi graph. Going Back to step 1
		// and forward again reuses the existing seed (including any value the user typed) rather than regenerating it.
		// Use nextInt so the seed fits in an integer and displays as a readable value in the seed field.
		if (!subMapSeedGenerated)
		{
			subMapSeed = Helper.safeAbs(new Random().nextInt());
			subMapSeedGenerated = true;
		}

		step2Dialog = new JDialog(mainWindow, Translation.get("subMapDialog.step2.title"), true);
		step2Dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		step2Dialog.setResizable(true);
		step2Dialog.setSize(900, 775);
		step2Dialog.setMinimumSize(new Dimension(600, 500));

		JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// -- Top control area (GridBagOrganizer for aligned labels and fields) --
		GridBagOrganizer controlOrganizer = new GridBagOrganizer();
		final int topInset = 2;

		// Compute the 1× polygon count for this selection to use as the default slider value.
		double origMapAreaForDefault = origSettings.generatedWidth * (double) origSettings.generatedHeight;
		double selAreaForDefault = selBoundsRI.width * selBoundsRI.height;
		oneXWorldSize = origSettings.worldSize * selAreaForDefault / origMapAreaForDefault;
		clampedOneXWorldSize = computeDefaultWorldSize(origSettings, selBoundsRI);
		boolean matchDetailPossible = oneXWorldSize >= minPolygonsInSubMap;

		// Advice label explaining key sub-map limitations.
		JLabel adviceLabel = new JLabel(Translation.get("subMapDialog.step2.advice"));
		controlOrganizer.addLeftAlignedComponent(adviceLabel, 0, 8, false);

		// Number of polygons: radio buttons to choose between matching source detail or a custom level.
		JRadioButton matchSourceRadio = new JRadioButton(Translation.get("subMapDialog.step2.matchSourceDetail", clampedOneXWorldSize));
		customRadio = new JRadioButton(Translation.get("subMapDialog.step2.choose"));
		ButtonGroup detailModeGroup = new ButtonGroup();
		detailModeGroup.add(matchSourceRadio);
		detailModeGroup.add(customRadio);
		// Default: match source detail, unless the selected area is too small to reach the minimum polygon count.
		if (matchDetailPossible)
		{
			matchSourceRadio.setSelected(true);
		}
		else
		{
			matchSourceRadio.setEnabled(false);
			customRadio.setSelected(true);
		}

		matchSourceRadio.setToolTipText(Translation.get("subMapDialog.step2.matchSourceHelp"));
		customRadio.setToolTipText(Translation.get("subMapDialog.step2.chooseHelp"));
		controlOrganizer.addLabelAndComponentsHorizontalWithTopInset(Translation.get("subMapDialog.step2.numberOfPolygons.label"), "", Arrays.asList(matchSourceRadio, customRadio), topInset);

		// Explanation shown when the selected area is too small to match the source detail level.
		if (!matchDetailPossible)
		{
			JLabel matchDetailDisabledLabel = new JLabel(Translation.get("subMapDialog.step2.matchDetailDisabled", minPolygonsInSubMap));
			matchDetailDisabledLabel.setForeground(warningMessageColor);
			controlOrganizer.addLeftAlignedComponent(matchDetailDisabledLabel, 0, 4, false);
		}

		// Slider row (shown only in Choose mode).
		JSlider rawSlider = new JSlider(minPolygonsInSubMap, SettingsGenerator.maxWorldSize, clampedOneXWorldSize);
		rawSlider.setMajorTickSpacing(8000);
		rawSlider.setMinorTickSpacing(1000);
		rawSlider.setPaintTicks(true);
		rawSlider.setPaintLabels(true);
		rawSlider.setSnapToTicks(true);

		detailSliderWithValue = new SliderWithDisplayedValue(rawSlider, value ->
		{
			double origMapArea = origSettings.generatedWidth * (double) origSettings.generatedHeight;
			double selArea = selBoundsRI.width * selBoundsRI.height;
			double oneX = origSettings.worldSize * selArea / origMapArea;
			double ratio = (oneX > 0) ? value / oneX : 1.0;
			return Translation.get("subMapDialog.step2.sliderDisplay", String.format("%.1f", ratio), value);
		}, () ->
		{
			triggerPreviewRedraw();
		}, null);
		detailSlider = detailSliderWithValue.slider;
		String polygonsTooltip = Translation.get("subMapDialog.step2.polygons.tooltip", minPolygonsInSubMap, SettingsGenerator.maxWorldSize);
		sliderRowHider = detailSliderWithValue.addToOrganizer(controlOrganizer, "", polygonsTooltip);
		sliderRowHider.setVisible(!matchDetailPossible);

		// Warning shown when Choose mode is selected.
		JLabel customWarningLabel = new JLabel(Translation.get("subMapDialog.step2.customWarning"));
		customWarningLabel.setForeground(warningMessageColor);
		customWarningRowHider = controlOrganizer.addLeftAlignedComponent(customWarningLabel, 2, 8, false);
		customWarningRowHider.setVisible(!matchDetailPossible);

		// Wire radio button listeners.
		matchSourceRadio.addActionListener(e ->
		{
			if (previewUpdater != null)
			{
				previewUpdater.cancel();
			}
			previewPanel.setImage(null);
			sliderRowHider.setVisible(false);
			customWarningRowHider.setVisible(false);
			step2Dialog.validate();
			triggerPreviewRedraw();
		});
		customRadio.addActionListener(e ->
		{
			if (previewUpdater != null)
			{
				previewUpdater.cancel();
			}
			previewPanel.setImage(null);
			sliderRowHider.setVisible(true);
			customWarningRowHider.setVisible(true);
			step2Dialog.validate();
			triggerPreviewRedraw();
		});

		// Random seed.
		seedTextField = new JTextField(String.valueOf(subMapSeed), 10);
		seedTextField.setMaximumSize(new Dimension(seedTextField.getPreferredSize().width, seedTextField.getPreferredSize().height));
		seedTextField.getDocument().addDocumentListener(new DocumentListener()
		{
			private void handleChange()
			{
				try
				{
					subMapSeed = Long.parseLong(seedTextField.getText());
					triggerPreviewRedraw();
				}
				catch (NumberFormatException ex)
				{
					// Ignore invalid input; don't redraw.
				}
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				handleChange();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				if (!seedTextField.getText().isEmpty())
				{
					handleChange();
				}
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				handleChange();
			}
		});
		JButton newSeedButton = new JButton(Translation.get("theme.newSeed"));
		newSeedButton.setToolTipText(Translation.get("theme.newSeed.tooltip"));
		newSeedButton.addActionListener(e -> seedTextField.setText(String.valueOf(Helper.safeAbs(new Random().nextInt()))));
		controlOrganizer.addLabelAndComponentsHorizontalWithTopInset(Translation.get("subMapDialog.step2.randomSeed.label"), "", Arrays.asList(seedTextField, newSeedButton), topInset);

		mainPanel.add(controlOrganizer.panel, BorderLayout.NORTH);

		// -- Preview area --
		JPanel previewWrapper = new JPanel(new BorderLayout(0, 4));

		JLabel previewLabel = new JLabel(Translation.get("subMapDialog.step2.preview.label"));
		previewLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		previewWrapper.add(previewLabel, BorderLayout.NORTH);

		BufferedImage placeholder = AwtBridge.toBufferedImage(nortantis.platform.ImageHelper.getInstance().createPlaceholderImage(new String[] { Translation.get("subMapDialog.step2.drawingPreview") },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages())));
		previewPanel = new MapEditingPanel(placeholder);

		previewContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		previewContainer.add(previewPanel);

		previewWrapper.add(previewContainer, BorderLayout.CENTER);

		// Inline warning shown under the preview when shore-side cities disappear onto water in the sub-map. A JTextArea (styled to look like
		// a label) is used so a long list of city file names wraps, with the look-and-feel's standard warning icon to its left. The icon is an
		// image (not a glyph), so it renders the same across languages, fonts, and operating systems. The whole row lives in the preview area's
		// bottom slot and starts hidden. When it appears or disappears it takes height from (or returns it to) the preview, so
		// onFinishedDrawingFull redraws the preview once at the new size; that does not loop because the redraw produces the same warning,
		// leaving its size unchanged (see updateCitiesOnWaterWarning).
		lastCitiesOnWaterWarningText = "";
		isRedrawForWarning = false;
		citiesOnWaterWarningArea = new JTextArea();
		citiesOnWaterWarningArea.setEditable(false);
		citiesOnWaterWarningArea.setFocusable(false);
		citiesOnWaterWarningArea.setLineWrap(true);
		citiesOnWaterWarningArea.setWrapStyleWord(true);
		citiesOnWaterWarningArea.setOpaque(false);
		citiesOnWaterWarningArea.setForeground(warningMessageColor);
		citiesOnWaterWarningArea.setFont(UIManager.getFont("Label.font"));

		citiesOnWaterWarningPanel = new JPanel(new BorderLayout());
		citiesOnWaterWarningPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		Icon warningIcon = UIManager.getIcon("OptionPane.warningIcon");
		if (warningIcon != null)
		{
			JLabel warningIconLabel = new JLabel(warningIcon);
			// Align the icon with the first line of text and leave a gap before the text.
			warningIconLabel.setVerticalAlignment(SwingConstants.TOP);
			warningIconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
			citiesOnWaterWarningPanel.add(warningIconLabel, BorderLayout.WEST);
		}
		citiesOnWaterWarningPanel.add(citiesOnWaterWarningArea, BorderLayout.CENTER);
		citiesOnWaterWarningPanel.setVisible(false);
		previewWrapper.add(citiesOnWaterWarningPanel, BorderLayout.SOUTH);

		mainPanel.add(previewWrapper, BorderLayout.CENTER);

		// -- Bottom: progress bar + buttons --
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));

		previewProgressBar = new JProgressBar();
		previewProgressBar.setStringPainted(true);
		previewProgressBar.setString(Translation.get("newSettingsDialog.drawing"));
		previewProgressBar.setIndeterminate(true);
		previewProgressBar.setVisible(false);
		bottomPanel.add(previewProgressBar);

		progressBarTimer = new Timer(50, e ->
		{
			if (previewUpdater != null)
			{
				SwingHelper.updateMapDrawingProgressBar(previewProgressBar, previewUpdater);
			}
			else
			{
				previewProgressBar.setVisible(false);
			}
		});
		progressBarTimer.setInitialDelay(500);

		bottomPanel.add(Box.createHorizontalGlue());

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));

		JButton backButton = new JButton(Translation.get("subMapDialog.step2.back"));
		backButton.addActionListener(e ->
		{
			stopPreviewUpdater();
			step2Dialog.dispose();
			step2Dialog = null;
			// Restore constraints and aspect ratio when going back.
			mainWindow.mapEditingPanel.setSelectionBoxConstraints(new Rectangle(0, 0, getMapDisplayWidth(), getMapDisplayHeight()));
			mainWindow.mapEditingPanel.setSelectionBoxLockedAspectRatio(selectedAspectRatio);
			mainWindow.mapEditingPanel.setSelectionBoxRI(selBoundsRI);
			showStep1();
		});

		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> handleCancelStep2());

		// Reuse NewSettingsDialog's Create label so both Create buttons stay consistent. The label is HTML that underlines the
		// mnemonic letter for each language. Alt+C triggers Create and, unlike Ctrl+C, does not collide with the Copy binding of
		// the spinners and seed text field.
		createButton = new JButton(Translation.get("newSettingsDialog.create"));
		SwingHelper.bindAltMnemonic(createButton, KeyEvent.VK_C);
		createButton.addActionListener(e -> handleCreate());

		buttonRow.add(backButton);
		buttonRow.add(createButton);
		buttonRow.add(Box.createHorizontalStrut(5));
		buttonRow.add(cancelButton);
		bottomPanel.add(buttonRow);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		step2Dialog.add(mainPanel);

		step2DialogOpened = false;
		lastPreviewDrawSize = null;

		// Build the preview MapUpdater.
		createPreviewUpdater();

		// Wire dialog resize to re-trigger preview.
		// Guard against the spurious componentResized that fires during initial layout (before windowOpened).
		step2Dialog.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				if (!step2DialogOpened)
				{
					return;
				}
				triggerPreviewRedrawIfSizeChanged();
			}
		});

		step2Dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowOpened(WindowEvent e)
			{
				step2DialogOpened = true;
				// Trigger the first draw here, after the dialog is visible and its container is sized.
				triggerPreviewRedrawIfSizeChanged();
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				// Intentionally not showing the discard warning here.
				cancelStep2();
			}
		});

		step2Dialog.setLocationRelativeTo(mainWindow);

		step2Dialog.setVisible(true);
	}

	private void createPreviewUpdater()
	{
		previewUpdater = new MapUpdater(false)
		{
			@Override
			protected void onBeginDraw()
			{
			}

			@Override
			public MapSettings getSettingsFromGUI()
			{
				// Called on background thread by MapUpdater.
				try
				{
					boolean redistributeIconsAndRivers = customRadio != null && customRadio.isSelected();
					MapSettings settings = SubMapCreator.createSubMapSettings(origSettings, origGraph, selBoundsRI, getSubmapWorldSize(), origResolution, subMapSeed, redistributeIconsAndRivers,
							origFileName);
					// Set resolution to the new-map default as a baseline; MapCreator.createMap will override it via
					// Background.calcMapBoundsAndAdjustResolutionIfNeeded to fit the maxMapSize passed to the updater.
					settings.resolution = MapSettings.defaultResolution;
					lastSubMapSettings = settings;
					return settings;
				}
				catch (Exception e)
				{
					SwingHelper.handleException(e, step2Dialog, false);
					throw e;
				}
			}

			@Override
			protected void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn, List<String> warningMessages,
					List<IconDrawer.CityIconRemovedForWater> citiesRemovedForWater, boolean wasTriggeredByUndoRedo)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (previewPanel.mapFromMapCreator != null && previewPanel.mapFromMapCreator != map)
					{
						previewPanel.mapFromMapCreator.close();
					}
					previewPanel.mapFromMapCreator = map;
					previewPanel.setImage(AwtBridge.toBufferedImage(map));
					previewPanel.setBorderPadding(borderPaddingAsDrawn);

					boolean warningChanged = false;
					if (!anotherDrawIsQueued)
					{
						enableOrDisableProgressBar(false);
						// Only update the warning once the latest draw has settled, so it reflects what is actually shown.
						warningChanged = updateCitiesOnWaterWarning(citiesRemovedForWater);
					}

					if (step2Dialog != null)
					{
						step2Dialog.revalidate();
						step2Dialog.repaint();
					}

					// Showing or hiding the warning changes the preview area's height, so the map just drawn no longer fits it (its bottom
					// would be covered by the warning, or leave a gap). Redraw once at the new size, the same way a window resize does. The
					// isRedrawForWarning latch makes this provably loop-free: a warning-triggered redraw is never allowed to trigger another,
					// so at most one extra draw happens. (In practice the extra draw reproduces the same warning and would not re-trigger
					// anyway, because whether a city lands on water is detected resolution-independently.)
					boolean wasRedrawForWarning = isRedrawForWarning;
					isRedrawForWarning = false;
					if (warningChanged && !wasRedrawForWarning)
					{
						isRedrawForWarning = true;
						triggerPreviewRedraw();
					}
				});
			}

			@Override
			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderPaddingAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
				// Preview only does full redraws.
			}

			@Override
			protected void onFailedToDraw(Exception exception)
			{
				SwingUtilities.invokeLater(() -> enableOrDisableProgressBar(false));
				if (exception != null)
				{
					SwingHelper.handleException(exception, step2Dialog, false);
				}
			}

			@Override
			protected MapEdits getEdits()
			{
				MapSettings s = lastSubMapSettings;
				return s != null ? s.edits : null;
			}

			@Override
			protected Image getCurrentMapForIncrementalUpdate()
			{
				return previewPanel.mapFromMapCreator;
			}
		};
		previewUpdater.setEnabled(true);
	}

	private void triggerPreviewRedraw()
	{
		triggerPreviewRedraw(false);
	}

	/**
	 * Redraws the preview if the preview container has changed size since the last draw. Used by the window-open and resize handlers, which
	 * can fire redundant events with an unchanged size and would otherwise draw the preview a second time on top of the first.
	 */
	private void triggerPreviewRedrawIfSizeChanged()
	{
		triggerPreviewRedraw(true);
	}

	private void triggerPreviewRedraw(boolean onlyIfSizeChanged)
	{
		if (previewUpdater == null)
		{
			return;
		}
		// Defer to the next EDT cycle, then force a second validate() pass. HTML labels have a
		// two-pass layout problem: their preferred height depends on their width, which is only
		// known after the first layout pass. Callers (radio button listeners) already call
		// validate() once before reaching here; the second pass here settles the correct height.
		SwingUtilities.invokeLater(() ->
		{
			if (previewUpdater == null)
			{
				return;
			}
			if (step2Dialog != null)
			{
				step2Dialog.validate();
			}
			nortantis.geom.Dimension size = getPreviewContainerSize();
			if (size == null)
			{
				return;
			}
			if (onlyIfSizeChanged && size.equals(lastPreviewDrawSize))
			{
				return;
			}
			lastPreviewDrawSize = size;
			previewUpdater.setMaxMapSize(size);
			enableOrDisableProgressBar(true);
			previewUpdater.createAndShowMapFull();
		});
	}

	private void enableOrDisableProgressBar(boolean enable)
	{
		if (progressBarTimer == null || previewProgressBar == null)
		{
			return;
		}
		if (enable)
		{
			progressBarTimer.start();
		}
		else
		{
			progressBarTimer.stop();
			previewProgressBar.setVisible(false);
		}
	}

	/**
	 * Shows or hides the inline warning listing cities that disappeared onto water in the sub-map. {@code removedCities} is the dropped
	 * cities (duplicates kept, so its size is the number of cities lost); the warning reports that count and the distinct cities — each as
	 * its quoted file name with its group and art pack — worded singular when only one city was lost. An empty or null list hides the
	 * warning.
	 *
	 * @return true if the displayed warning text changed (shown, hidden, or different text) from the previous call. The caller uses this to
	 *         redraw the preview once at the new size, since showing or hiding the warning changes the preview area's height.
	 */
	private boolean updateCitiesOnWaterWarning(List<IconDrawer.CityIconRemovedForWater> removedCities)
	{
		if (citiesOnWaterWarningArea == null)
		{
			return false;
		}
		String newText;
		if (removedCities == null || removedCities.isEmpty())
		{
			newText = "";
		}
		else
		{
			// Distinct entries (a sub-map can drop several cities that use the same icon), each formatted as its quoted name plus its group
			// and art pack. Sorted for a stable order.
			java.util.SortedSet<String> distinctEntries = new java.util.TreeSet<>();
			for (IconDrawer.CityIconRemovedForWater city : removedCities)
			{
				distinctEntries.add(Translation.get("subMapDialog.step2.cityOnWaterListEntry", city.fileName, city.groupId, city.artPack));
			}
			String cityList = String.join(", ", distinctEntries);
			if (removedCities.size() == 1)
			{
				newText = Translation.get("subMapDialog.step2.cityOnWaterWarning", cityList);
			}
			else
			{
				// Pass the count as a string so MessageFormat does not apply locale digit grouping to it.
				newText = Translation.get("subMapDialog.step2.citiesOnWaterWarning", String.valueOf(removedCities.size()), cityList);
			}
		}

		if (newText.equals(lastCitiesOnWaterWarningText))
		{
			return false;
		}
		lastCitiesOnWaterWarningText = newText;
		citiesOnWaterWarningArea.setText(newText);
		// Toggle the whole row (icon + text), not just the text, so the icon disappears with the message.
		citiesOnWaterWarningPanel.setVisible(!newText.isEmpty());
		if (!newText.isEmpty())
		{
			citiesOnWaterWarningArea.setCaretPosition(0);
		}
		return true;
	}

	private nortantis.geom.Dimension getPreviewContainerSize()
	{
		if (previewContainer == null)
		{
			return null;
		}
		int width = previewContainer.getWidth();
		int height = previewContainer.getHeight();
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		double scale = previewPanel != null ? previewPanel.osScale : 1.0;
		return new nortantis.geom.Dimension(width * scale, height * scale);
	}

	private int getSubmapWorldSize()
	{
		if (customRadio.isSelected())
		{
			return detailSlider.getValue();
		}
		return clampedOneXWorldSize;
	}

	/**
	 * Returns the displayed map width in RI units, accounting for rotation (90°/270° swaps generatedWidth and generatedHeight).
	 */
	private int getMapDisplayWidth()
	{
		return (origSettings.rightRotationCount == 1 || origSettings.rightRotationCount == 3) ? origSettings.generatedHeight : origSettings.generatedWidth;
	}

	/**
	 * Returns the displayed map height in RI units, accounting for rotation (90°/270° swaps generatedWidth and generatedHeight).
	 */
	private int getMapDisplayHeight()
	{
		return (origSettings.rightRotationCount == 1 || origSettings.rightRotationCount == 3) ? origSettings.generatedWidth : origSettings.generatedHeight;
	}

	private void handleCreate()
	{
		MapSettings settings = lastSubMapSettings;
		if (settings == null)
		{
			SwingHelper.showMessageDialog(step2Dialog, Translation.get("subMapDialog.step2.notReady"), Translation.get("subMapDialog.step2.notReady.title"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		stopPreviewUpdater();
		mainWindow.mapEditingPanel.clearSelectionBox();
		step2Dialog.dispose();
		step2Dialog = null;
		// The preview draw shrank settings.resolution to fit the preview area (calcMapBoundsAndAdjustResolutionIfNeeded mutates the settings it
		// is given). loadSettingsIntoGUI captures settings.resolution as the export resolution, so restore the new-map default here; otherwise
		// the created sub-map would inherit the shrunken preview resolution as its export/display baseline.
		settings.resolution = MapSettings.defaultResolution;
		// Re-enable the menu bar that was locked while the dialog was open. The map-related field state is left to loadSettingsIntoGUI
		// below,
		// which decides it authoritatively based on whether the sub-map's edits are initialized (they always are).
		mainWindow.setMenuBarEnabled(true);
		mainWindow.clearOpenSettingsFilePath();
		mainWindow.loadSettingsIntoGUI(settings);

		// Restore focus to the main window so WHEN_IN_FOCUSED_WINDOW keybindings (Delete, Ctrl+C,
		// Ctrl+V) fire on the first action after the dialog closes. See the matching call in
		// NewSettingsDialog.onCreateMap for the full explanation.
		SwingUtilities.invokeLater(() -> mainWindow.requestFocus());
	}

	private void stopPreviewUpdater()
	{
		enableOrDisableProgressBar(false);
		if (previewUpdater != null)
		{
			previewUpdater.cancel();
			previewUpdater.setEnabled(false);
		}
	}
}
