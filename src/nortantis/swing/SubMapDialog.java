package nortantis.swing;

import nortantis.*;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;
import nortantis.swing.translation.Translation;

import nortantis.util.Helper;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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

	// Captured state from the current map at construction time.
	private final MapSettings origSettings;
	private final WorldGraph origGraph;
	private final MapEdits origEdits;
	/** The display-quality resolution scale at which origGraph was created. */
	private final double origResolution;

	// Shared state between steps.
	private Rectangle selBoundsRI;
	/** The polygon count for the sub-map; 0 = uninitialized (will default to 1× on first step-2 entry). */
	private int subMapWorldSize = 0;

	// Step 1 dialog state.
	private JDialog step1Dialog;
	private JSpinner xSpinner, ySpinner, widthSpinner, heightSpinner;
	private JLabel step1ErrorLabel;
	private JButton step1NextButton;
	/** Guards against infinite update loops between spinners and the selection box. */
	private boolean updatingSpinnersFromBox = false;
	/** Currently selected aspect ratio (width / height). 0 = no lock. */
	private double selectedAspectRatio = 0.0;

	// Step 2 dialog / preview state.
	private JDialog step2Dialog;
	private MapUpdater previewUpdater;
	private MapEditingPanel previewPanel;
	private JPanel previewContainer;
	private volatile MapSettings lastSubMapSettings;
	private SliderWithDisplayedValue detailSliderWithValue;
	private JLabel errorLabel;
	private JButton createButton;
	private JSlider detailSlider;
	private JProgressBar previewProgressBar;
	private Timer progressBarTimer;
	/** Stable seed for the sub-map graph; generated once per step-2 session so re-draws produce the same Voronoi layout. */
	private long subMapSeed;
	private JTextField seedTextField;
	/** Set to true in windowOpened; guards componentResized from firing the first preview draw before the dialog is fully shown. */
	private boolean step2DialogOpened = false;

	public SubMapDialog(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;
		this.origSettings = mainWindow.getSettingsFromGUI(false);
		this.origGraph = mainWindow.updater.mapParts.graph;
		this.origEdits = mainWindow.edits.deepCopy();
		this.origResolution = mainWindow.displayQualityScale;
	}

	// -------------------------------------------------------------------------
	// Step 1: Selection box
	// -------------------------------------------------------------------------

	public void showStep1()
	{
		step1Dialog = new JDialog(mainWindow, "Create Sub-Map – Select Region", false);
		step1Dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		GridBagOrganizer organizer = new GridBagOrganizer();
		final int topInset = 2;

		// Instructions
		JLabel instructionsLabel = new JLabel("<html>Drag on the map to select the region for the sub-map.<br>"
				+ "When done, click <b>Next</b> to choose the detail level.</html>");
		organizer.addLeftAlignedComponent(instructionsLabel);

		// Aspect ratio buttons
		GeneratedDimension[] dims = GeneratedDimension.presets();
		int numButtons = dims.length + 1; // +1 for "Any"
		double[] ratios = new double[numButtons];
		String[] ratioLabels = new String[numButtons];
		ratios[0] = 0.0;
		ratioLabels[0] = "Any";
		for (int i = 0; i < dims.length; i++)
		{
			ratios[i + 1] = dims[i].aspectRatio();
			ratioLabels[i + 1] = dims[i].displayName();
		}

		ButtonGroup ratioGroup = new ButtonGroup();
		List<JToggleButton> aspectRatioButtons = new ArrayList<>();
		for (int i = 0; i < numButtons; i++)
		{
			final double ratio = ratios[i];
			JToggleButton btn = new JToggleButton(ratioLabels[i]);
			btn.setSelected(Math.abs(ratio - selectedAspectRatio) < 0.001);
			btn.addActionListener(e ->
			{
				selectedAspectRatio = ratio;
				mainWindow.mapEditingPanel.setSelectionBoxLockedAspectRatio(ratio);
				if (ratio > 0 && selBoundsRI != null)
				{
					selBoundsRI = adjustSelectionBoxToAspectRatio(selBoundsRI, ratio);
					mainWindow.mapEditingPanel.setSelectionBoxRI(selBoundsRI);
					updateStep1SpinnersFromBox();
				}
			});
			ratioGroup.add(btn);
			aspectRatioButtons.add(btn);
		}
		SegmentedButtonWidget segmentedButtonWidget = new SegmentedButtonWidget(aspectRatioButtons);
		segmentedButtonWidget.addToOrganizer(organizer, "Aspect ratio:","Constrain the aspect ratio of the selection.", topInset);

		// Position and size spinners (use display dimensions, which are rotated relative to generatedWidth/Height for 90°/270°)
		int mapDisplayW = getMapDisplayWidth();
		int mapDisplayH = getMapDisplayHeight();
		xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, mapDisplayW, 1));
		ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, mapDisplayH, 1));
		widthSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, mapDisplayW), 1, mapDisplayW, 1));
		heightSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, mapDisplayH), 1, mapDisplayH, 1));

		// TODO See if I need to set the preferred sizes of the spinners like the code had here before.
		Dimension spinnerSize = new Dimension(75, xSpinner.getPreferredSize().height);
		xSpinner.setPreferredSize(spinnerSize);
		ySpinner.setPreferredSize(spinnerSize);
		widthSpinner.setPreferredSize(spinnerSize);
		heightSpinner.setPreferredSize(spinnerSize);

		organizer.addLabelAndComponentsHorizontalWithTopInset( "Position:", "", Arrays.asList(new JLabel("X:"), xSpinner, new JLabel("Y:"), ySpinner, new JLabel("Width:"), widthSpinner, new JLabel("Height:"), heightSpinner), topInset);

		organizer.addVerticalFillerRow();

		// Inline error label for spinner validation
		step1ErrorLabel = new JLabel(" ");
		step1ErrorLabel.setForeground(java.awt.Color.RED);

		// Buttons row
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> cancelStep1());

		step1NextButton = new JButton("Next →");
		step1NextButton.setEnabled(false);
		step1NextButton.addActionListener(e ->
		{
			if (selBoundsRI != null && validateStep1Spinners() == null)
			{
				disposeStep1();
				showStep2();
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
		step1Dialog.setPreferredSize(new Dimension(step1Dialog.getPreferredSize().width + 35, step1Dialog.getPreferredSize().height + 15));
		step1Dialog.pack();
		step1Dialog.setMinimumSize(step1Dialog.getSize());
		java.awt.Point parentLocation = mainWindow.getLocation();
		Dimension parentSize = mainWindow.getSize();
		Dimension dialogSize = step1Dialog.getSize();
		step1Dialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2,
				parentLocation.y + parentSize.height - dialogSize.height - 18);

		// Constrain the selection box to the displayed map bounds (accounts for rotation).
		mainWindow.mapEditingPanel.setSelectionBoxConstraints(new Rectangle(0, 0, getMapDisplayWidth(), getMapDisplayHeight()));
		mainWindow.mapEditingPanel.setSelectionBoxLockedAspectRatio(selectedAspectRatio);

		// Register the selection box handler on the main map panel.
		mainWindow.mapEditingPanel.enableSelectionBox(() ->
		{
			selBoundsRI = mainWindow.mapEditingPanel.getSelectionBoxRI();
			updateStep1SpinnersFromBox();
			updateStep1NextButton();
		});

		// Wire spinners: when edited by user, update the selection box.
		ChangeListener spinnerListener = e ->
		{
			if (!updatingSpinnersFromBox)
			{
				applySpinnersToSelectionBox();
			}
		};
		xSpinner.addChangeListener(spinnerListener);
		ySpinner.addChangeListener(spinnerListener);
		widthSpinner.addChangeListener(spinnerListener);
		heightSpinner.addChangeListener(spinnerListener);

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
			return "Width and height must be at least 1.";
		}
		if (x < 0 || y < 0)
		{
			return "X and Y must be at least 0.";
		}
		if (x + w > getMapDisplayWidth())
		{
			return "X + Width exceeds the map width (" + getMapDisplayWidth() + ").";
		}
		if (y + h > getMapDisplayHeight())
		{
			return "Y + Height exceeds the map height (" + getMapDisplayHeight() + ").";
		}
		return null;
	}

	/**
	 * Returns the current validation error for the spinners, or null if valid.
	 */
	private String validateStep1Spinners()
	{
		return validateSpinnerValues(((Number) xSpinner.getValue()).intValue(), ((Number) ySpinner.getValue()).intValue(),
				((Number) widthSpinner.getValue()).intValue(), ((Number) heightSpinner.getValue()).intValue());
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
		updatingSpinnersFromBox = true;
		try
		{
			xSpinner.setValue((int) Math.round(selBoundsRI.x));
			ySpinner.setValue((int) Math.round(selBoundsRI.y));
			widthSpinner.setValue(Math.max(1, (int) Math.round(selBoundsRI.width)));
			heightSpinner.setValue(Math.max(1, (int) Math.round(selBoundsRI.height)));
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
	}

	private void disposeStep1()
	{
		if (step1Dialog != null)
		{
			step1Dialog.dispose();
			step1Dialog = null;
		}
	}

	// -------------------------------------------------------------------------
	// Step 2: Detail level + preview
	// -------------------------------------------------------------------------

	private void showStep2()
	{
		// Generate a stable seed for this step-2 session so repeated redraws produce the same Voronoi graph.
		// Use nextInt so the seed fits in an integer and displays as a readable value in the seed field.
		subMapSeed = Helper.safeAbs(new Random().nextInt());

		step2Dialog = new JDialog(mainWindow, "Create Sub-Map – Detail Level", true);
		step2Dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		step2Dialog.setResizable(true);
		step2Dialog.setSize(900, 700);
		step2Dialog.setMinimumSize(new Dimension(600, 500));

		JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// -- Top control area --
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

		// Compute the 1× polygon count for this selection to use as the default slider value.
		double origMapAreaForDefault = origSettings.generatedWidth * (double) origSettings.generatedHeight;
		double selAreaForDefault = selBoundsRI.width * selBoundsRI.height;
		double oneXWorldSize = origSettings.worldSize * selAreaForDefault / origMapAreaForDefault;
		final int minPolygonsInSubMap = 1000;
		if (subMapWorldSize == 0)
		{
			subMapWorldSize = (int) Math.round(Math.max(minPolygonsInSubMap, Math.min(SettingsGenerator.maxWorldSize, oneXWorldSize)));
		}
		else
		{
			subMapWorldSize = Math.max(minPolygonsInSubMap, Math.min(SettingsGenerator.maxWorldSize, subMapWorldSize));
		}

		// Detail slider row
		JPanel sliderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		JLabel polygonsLabel = new JLabel("Number of polygons:");
		polygonsLabel.setToolTipText("<html>The number of Voronoi polygons in the sub-map, which controls its level of detail.<br>"
				+ "The multiplier shows how many times more polygons the sub-map has relative<br>"
				+ "to the equivalent area of the source map. Values below 1× mean less detail. "
				+ "Values <br>above 1× mean more detail. The number of polygons is must be between " + minPolygonsInSubMap + " <br>and " + SettingsGenerator.maxWorldSize + ".</html>");
		sliderRow.add(polygonsLabel);

		JSlider rawSlider = new JSlider(1000, SettingsGenerator.maxWorldSize, subMapWorldSize);
		rawSlider.setMajorTickSpacing(8000);
		rawSlider.setMinorTickSpacing(1000);
		rawSlider.setPaintTicks(true);
		rawSlider.setPaintLabels(true);
		rawSlider.setSnapToTicks(true);

		detailSliderWithValue = new SliderWithDisplayedValue(rawSlider,
				value ->
				{
					double origMapArea = origSettings.generatedWidth * (double) origSettings.generatedHeight;
					double selArea = selBoundsRI.width * selBoundsRI.height;
					double oneX = origSettings.worldSize * selArea / origMapArea;
					double ratio = (oneX > 0) ? value / oneX : 1.0;
					return String.format("%.1fx, \u2248%d polygons", ratio, value);
				},
				() ->
				{
					subMapWorldSize = detailSlider.getValue();
					updateDetailLevelState();
					if (!isTooDetailed())
					{
						triggerPreviewRedraw();
					}
				},
				null);
		detailSlider = detailSliderWithValue.slider;
		sliderRow.add(detailSlider);
		sliderRow.add(detailSliderWithValue.valueDisplay);
		controlPanel.add(sliderRow);

		// Seed row
		JPanel seedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		seedRow.add(new JLabel("Random seed:"));
		seedTextField = new JTextField(String.valueOf(subMapSeed), 10);
		seedTextField.getDocument().addDocumentListener(new DocumentListener()
		{
			private void handleChange()
			{
				try
				{
					subMapSeed = Long.parseLong(seedTextField.getText());
					if (!isTooDetailed())
					{
						triggerPreviewRedraw();
					}
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
		seedRow.add(seedTextField);
		JButton newSeedButton = new JButton("New Seed");
		newSeedButton.setToolTipText(Translation.get("theme.newSeed.tooltip"));
		newSeedButton.addActionListener(e -> seedTextField.setText(String.valueOf(Helper.safeAbs(new Random().nextInt()))));
		seedRow.add(newSeedButton);
		controlPanel.add(seedRow);

		// Error label row
		errorLabel = new JLabel("Detail level too high – maximum is 32,000 polygons. Reduce the selected area or lower the detail level.");
		errorLabel.setForeground(java.awt.Color.RED);
		errorLabel.setVisible(false);
		JPanel errorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		errorRow.add(errorLabel);
		controlPanel.add(errorRow);

		mainPanel.add(controlPanel, BorderLayout.NORTH);

		// -- Preview area --
		JPanel previewWrapper = new JPanel(new BorderLayout(0, 4));

		JLabel previewLabel = new JLabel("Preview:");
		previewLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		previewWrapper.add(previewLabel, BorderLayout.NORTH);

		BufferedImage placeholder = AwtBridge.toBufferedImage(nortantis.platform.ImageHelper.getInstance().createPlaceholderImage(new String[] { "Drawing sub-map preview..." },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages())));
		previewPanel = new MapEditingPanel(placeholder);

		previewContainer = new JPanel(new BorderLayout());
		previewContainer.add(previewPanel, BorderLayout.CENTER);

		JScrollPane previewScroll = new JScrollPane(previewContainer);
		previewWrapper.add(previewScroll, BorderLayout.CENTER);

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

		progressBarTimer = new Timer(50, e -> previewProgressBar.setVisible(previewUpdater != null && previewUpdater.isMapBeingDrawn()));
		progressBarTimer.setInitialDelay(500);

		bottomPanel.add(Box.createHorizontalGlue());

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));

		JButton backButton = new JButton("← Back");
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

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e ->
		{
			stopPreviewUpdater();
			mainWindow.mapEditingPanel.clearSelectionBox();
			step2Dialog.dispose();
			step2Dialog = null;
		});

		createButton = new JButton("Create");
		createButton.addActionListener(e -> handleCreate());

		buttonRow.add(backButton);
		buttonRow.add(createButton);
		buttonRow.add(Box.createHorizontalStrut(5));
		buttonRow.add(cancelButton);
		bottomPanel.add(buttonRow);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		step2Dialog.add(mainPanel);

		step2DialogOpened = false;

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
				if (!isTooDetailed())
				{
					triggerPreviewRedraw();
				}
			}
		});

		step2Dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowOpened(WindowEvent e)
			{
				step2DialogOpened = true;
				// Trigger the first draw here, after the dialog is visible and its container is sized.
				if (!isTooDetailed())
				{
					triggerPreviewRedraw();
				}
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				stopPreviewUpdater();
				mainWindow.mapEditingPanel.clearSelectionBox();
				step2Dialog.dispose();
				step2Dialog = null;
			}
		});

		step2Dialog.setLocationRelativeTo(mainWindow);

		// Initialize error/button state.
		updateDetailLevelState();

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
				MapSettings settings = SubMapCreator.createSubMapSettings(origSettings, origGraph, origEdits, selBoundsRI, subMapWorldSize, origResolution, subMapSeed);
				settings.resolution = 1.0;
				lastSubMapSettings = settings;
				return settings;
			}

			@Override
			protected void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderPaddingAsDrawn, List<String> warningMessages)
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

					if (!anotherDrawIsQueued)
					{
						enableOrDisableProgressBar(false);
					}

					if (step2Dialog != null)
					{
						step2Dialog.revalidate();
						step2Dialog.repaint();
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
		if (previewUpdater == null)
		{
			return;
		}
		nortantis.geom.Dimension size = getPreviewContainerSize();
		if (size == null)
		{
			return;
		}
		previewUpdater.setMaxMapSize(size);
		enableOrDisableProgressBar(true);
		previewUpdater.createAndShowMapFull();
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

	private nortantis.geom.Dimension getPreviewContainerSize()
	{
		if (previewContainer == null || previewContainer.getWidth() <= 0 || previewContainer.getHeight() <= 0)
		{
			return null;
		}
		double scale = previewPanel != null ? previewPanel.osScale : 1.0;
		return new nortantis.geom.Dimension(previewContainer.getWidth() * scale, previewContainer.getHeight() * scale);
	}

	private boolean isTooDetailed()
	{
		return subMapWorldSize > SettingsGenerator.maxWorldSize;
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

	private void updateDetailLevelState()
	{
		boolean tooDetailed = isTooDetailed();
		if (errorLabel != null)
		{
			errorLabel.setVisible(tooDetailed);
		}
		if (createButton != null)
		{
			createButton.setEnabled(!tooDetailed);
		}
	}

	private void handleCreate()
	{
		MapSettings settings = lastSubMapSettings;
		if (settings == null)
		{
			JOptionPane.showMessageDialog(step2Dialog, "The sub-map preview is not ready yet. Please wait a moment.", "Not Ready", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		stopPreviewUpdater();
		mainWindow.mapEditingPanel.clearSelectionBox();
		step2Dialog.dispose();
		step2Dialog = null;
		mainWindow.loadSettingsIntoGUI(settings);
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
