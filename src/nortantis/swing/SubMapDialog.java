package nortantis.swing;

import nortantis.GeneratedDimension;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.SubMapCreator;
import nortantis.WorldGraph;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.platform.awt.AwtBridge;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

import nortantis.swing.translation.Translation;

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
	private int detailMultiplier = 4;

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

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Instructions
		JLabel instrLabel = new JLabel("<html>Drag on the map to select the region for the sub-map.<br>"
				+ "When done, click <b>Next</b> to choose the detail level.</html>");
		instrLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(instrLabel);
		panel.add(Box.createVerticalStrut(8));

		// Aspect ratio buttons
		JPanel ratioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		ratioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		ratioPanel.add(new JLabel("Aspect ratio:"));

		GeneratedDimension[] dims = GeneratedDimension.values();
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
			ratioPanel.add(btn);
		}
		panel.add(ratioPanel);
		panel.add(Box.createVerticalStrut(6));

		// Position and size spinners
		JPanel coordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		coordPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, origSettings.generatedWidth, 1));
		ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, origSettings.generatedHeight, 1));
		widthSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, origSettings.generatedWidth), 1, origSettings.generatedWidth, 1));
		heightSpinner = new JSpinner(new SpinnerNumberModel(Math.min(100, origSettings.generatedHeight), 1, origSettings.generatedHeight, 1));

		Dimension spinnerSize = new Dimension(75, xSpinner.getPreferredSize().height);
		xSpinner.setPreferredSize(spinnerSize);
		ySpinner.setPreferredSize(spinnerSize);
		widthSpinner.setPreferredSize(spinnerSize);
		heightSpinner.setPreferredSize(spinnerSize);

		coordPanel.add(new JLabel("X:"));
		coordPanel.add(xSpinner);
		coordPanel.add(new JLabel("Y:"));
		coordPanel.add(ySpinner);
		coordPanel.add(new JLabel("Width:"));
		coordPanel.add(widthSpinner);
		coordPanel.add(new JLabel("Height:"));
		coordPanel.add(heightSpinner);
		panel.add(coordPanel);
		panel.add(Box.createVerticalStrut(4));

		// Inline error label for spinner validation
		step1ErrorLabel = new JLabel(" ");
		step1ErrorLabel.setForeground(java.awt.Color.RED);
		step1ErrorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel errorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		errorRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		errorRow.add(step1ErrorLabel);
		panel.add(errorRow);
		panel.add(Box.createVerticalStrut(4));

		// Buttons row
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

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

		buttonsPanel.add(cancelButton);
		buttonsPanel.add(step1NextButton);
		panel.add(buttonsPanel);

		step1Dialog.add(panel);
		step1Dialog.pack();
		step1Dialog.setMinimumSize(step1Dialog.getSize());
		java.awt.Point parentLocation = mainWindow.getLocation();
		Dimension parentSize = mainWindow.getSize();
		Dimension dialogSize = step1Dialog.getSize();
		step1Dialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2,
				parentLocation.y + parentSize.height - dialogSize.height - 18);

		// Constrain the selection box to the map bounds.
		mainWindow.mapEditingPanel.setSelectionBoxConstraints(new Rectangle(0, 0, origSettings.generatedWidth, origSettings.generatedHeight));
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
		if (x + w > origSettings.generatedWidth)
		{
			return "X + Width exceeds the map width (" + origSettings.generatedWidth + ").";
		}
		if (y + h > origSettings.generatedHeight)
		{
			return "Y + Height exceeds the map height (" + origSettings.generatedHeight + ").";
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
		newHeight = Math.min(newHeight, origSettings.generatedHeight - box.y);
		newHeight = Math.max(1, newHeight);
		// If height was clamped, back-compute width to maintain ratio.
		double newWidth = newHeight * ratio;
		newWidth = Math.min(newWidth, origSettings.generatedWidth - box.x);
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

		// Detail slider row
		JPanel sliderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		sliderRow.add(new JLabel("Detail level:"));

		JSlider rawSlider = new JSlider(1, 16, detailMultiplier);
		rawSlider.setMajorTickSpacing(4);
		rawSlider.setMinorTickSpacing(1);
		rawSlider.setPaintTicks(true);
		rawSlider.setPaintLabels(true);
		rawSlider.setSnapToTicks(true);

		detailSliderWithValue = new SliderWithDisplayedValue(rawSlider,
				value ->
				{
					int unclamped = calculateUnclampedWorldSizeForMultiplier(value);
					int display = Math.min(unclamped, SettingsGenerator.maxWorldSize);
					return value + "x, \u2248" + display + " polygons";
				},
				() ->
				{
					detailMultiplier = detailSlider.getValue();
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
			mainWindow.mapEditingPanel.setSelectionBoxConstraints(new Rectangle(0, 0, origSettings.generatedWidth, origSettings.generatedHeight));
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
		buttonRow.add(cancelButton);
		buttonRow.add(createButton);
		bottomPanel.add(buttonRow);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		step2Dialog.add(mainPanel);

		// Build the preview MapUpdater.
		createPreviewUpdater();

		// Wire dialog resize to re-trigger preview.
		step2Dialog.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
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
				MapSettings settings = SubMapCreator.createSubMapSettings(origSettings, origGraph, origEdits, selBoundsRI, detailMultiplier, origResolution);
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
			protected void onFailedToDraw()
			{
				SwingUtilities.invokeLater(() -> enableOrDisableProgressBar(false));
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

	private int calculateUnclampedWorldSizeForMultiplier(int multiplier)
	{
		if (selBoundsRI == null || selBoundsRI.width <= 0 || selBoundsRI.height <= 0)
		{
			return 0;
		}
		double origMapArea = origSettings.generatedWidth * (double) origSettings.generatedHeight;
		double selArea = selBoundsRI.width * selBoundsRI.height;
		return (int) Math.round((double) multiplier * origSettings.worldSize * selArea / origMapArea);
	}

	private int calculateUnclampedWorldSize()
	{
		return calculateUnclampedWorldSizeForMultiplier(detailMultiplier);
	}

	private boolean isTooDetailed()
	{
		return calculateUnclampedWorldSize() > SettingsGenerator.maxWorldSize;
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
