package nortantis.swing;

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
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

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

	// Shared state between steps.
	private Rectangle selBoundsRI;
	private int detailMultiplier = 4;

	// Step 1 dialog state.
	private JDialog step1Dialog;

	// Step 2 dialog / preview state.
	private JDialog step2Dialog;
	private MapUpdater previewUpdater;
	private MapEditingPanel previewPanel;
	private JPanel previewContainer;
	private volatile MapSettings lastSubMapSettings;
	private JLabel polygonCountLabel;
	private JLabel errorLabel;
	private JButton createButton;
	private JSlider detailSlider;

	public SubMapDialog(MainWindow mainWindow)
	{
		this.mainWindow = mainWindow;
		this.origSettings = mainWindow.getSettingsFromGUI(false);
		this.origGraph = mainWindow.updater.mapParts.graph;
		this.origEdits = mainWindow.edits.deepCopy();
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

		JLabel instrLabel = new JLabel("<html>Drag on the map to select the region for the sub-map.<br>"
				+ "When done, click <b>Next</b> to choose the detail level.</html>");
		instrLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(instrLabel);
		panel.add(Box.createVerticalStrut(12));

		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> cancelStep1());

		JButton nextButton = new JButton("Next →");
		nextButton.setEnabled(false);
		nextButton.addActionListener(e ->
		{
			if (selBoundsRI != null)
			{
				disposeStep1();
				showStep2();
			}
		});

		buttonsPanel.add(cancelButton);
		buttonsPanel.add(nextButton);
		panel.add(buttonsPanel);

		step1Dialog.add(panel);
		step1Dialog.pack();
		step1Dialog.setMinimumSize(step1Dialog.getSize());
		java.awt.Point parentLocation = mainWindow.getLocation();
		Dimension parentSize = mainWindow.getSize();
		Dimension dialogSize = step1Dialog.getSize();
		step1Dialog.setLocation(parentLocation.x + parentSize.width / 2 - dialogSize.width / 2,
				parentLocation.y + parentSize.height - dialogSize.height - 18);

		// Register the selection box handler on the main map panel.
		mainWindow.mapEditingPanel.enableSelectionBox(() ->
		{
			selBoundsRI = mainWindow.mapEditingPanel.getSelectionBoxRI();
			nextButton.setEnabled(selBoundsRI != null);
		});

		step1Dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				cancelStep1();
			}
		});

		step1Dialog.setVisible(true);
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

		detailSlider = new JSlider(1, 16, detailMultiplier);
		detailSlider.setMajorTickSpacing(4);
		detailSlider.setMinorTickSpacing(1);
		detailSlider.setPaintTicks(true);
		detailSlider.setPaintLabels(true);
		detailSlider.setSnapToTicks(true);
		sliderRow.add(detailSlider);

		polygonCountLabel = new JLabel("≈ ? polygons");
		sliderRow.add(polygonCountLabel);
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
		BufferedImage placeholder = AwtBridge.toBufferedImage(nortantis.platform.ImageHelper.getInstance().createPlaceholderImage(new String[] { "Drawing sub-map preview..." },
				AwtBridge.fromAwtColor(SwingHelper.getTextColorForPlaceholderImages())));
		previewPanel = new MapEditingPanel(placeholder);

		previewContainer = new JPanel(new BorderLayout());
		previewContainer.add(previewPanel, BorderLayout.CENTER);

		JScrollPane previewScroll = new JScrollPane(previewContainer);
		mainPanel.add(previewScroll, BorderLayout.CENTER);

		// -- Bottom buttons --
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));

		JButton backButton = new JButton("← Back");
		backButton.addActionListener(e ->
		{
			stopPreviewUpdater();
			step2Dialog.dispose();
			step2Dialog = null;
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

		bottomPanel.add(backButton);
		bottomPanel.add(cancelButton);
		bottomPanel.add(createButton);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		step2Dialog.add(mainPanel);

		// Build the preview MapUpdater.
		createPreviewUpdater();

		// Wire slider changes.
		detailSlider.addChangeListener(e ->
		{
			if (!detailSlider.getValueIsAdjusting())
			{
				detailMultiplier = detailSlider.getValue();
				updatePolygonCountLabel();
				if (!isTooDetailed())
				{
					triggerPreviewRedraw();
				}
			}
		});

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
			public void windowClosing(WindowEvent e)
			{
				stopPreviewUpdater();
				mainWindow.mapEditingPanel.clearSelectionBox();
				step2Dialog.dispose();
				step2Dialog = null;
			}
		});

		step2Dialog.setLocationRelativeTo(mainWindow);

		// Initial label update and first preview draw.
		updatePolygonCountLabel();
		if (!isTooDetailed())
		{
			triggerPreviewRedraw();
		}

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
				MapSettings settings = SubMapCreator.createSubMapSettings(origSettings, origGraph, origEdits, selBoundsRI, detailMultiplier);
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
		if (size != null && size.width > 0 && size.height > 0)
		{
			previewUpdater.setMaxMapSize(size);
		}
		previewUpdater.createAndShowMapFull();
	}

	private nortantis.geom.Dimension getPreviewContainerSize()
	{
		if (previewContainer == null || previewContainer.getWidth() <= 0 || previewContainer.getHeight() <= 0)
		{
			return new nortantis.geom.Dimension(500, 400);
		}
		double scale = previewPanel != null ? previewPanel.osScale : 1.0;
		return new nortantis.geom.Dimension(previewContainer.getWidth() * scale, previewContainer.getHeight() * scale);
	}

	private int calculateUnclampedWorldSize()
	{
		if (selBoundsRI == null || selBoundsRI.width <= 0 || selBoundsRI.height <= 0)
		{
			return 0;
		}
		double origMapArea = origSettings.generatedWidth * (double) origSettings.generatedHeight;
		double selArea = selBoundsRI.width * selBoundsRI.height;
		return (int) Math.round((double) detailMultiplier * origSettings.worldSize * selArea / origMapArea);
	}

	private boolean isTooDetailed()
	{
		return calculateUnclampedWorldSize() > SettingsGenerator.maxWorldSize;
	}

	private void updatePolygonCountLabel()
	{
		int unclamped = calculateUnclampedWorldSize();
		int display = Math.min(unclamped, SettingsGenerator.maxWorldSize);
		boolean tooDetailed = unclamped > SettingsGenerator.maxWorldSize;

		if (polygonCountLabel != null)
		{
			polygonCountLabel.setText("≈ " + display + " polygons");
		}
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
		if (previewUpdater != null)
		{
			previewUpdater.cancel();
			previewUpdater.setEnabled(false);
		}
	}
}
