package nortantis.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import nortantis.Background;
import nortantis.DebugFlags;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.platform.Image;
import nortantis.swing.MapEditingPanel.IconEditToolsMode;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.Tuple2;

public class OverlayTool extends EditorTool
{
	private JTextField overlayImagePath;
	private JSlider overlayImageTransparencySlider;
	private JCheckBox drawOverlayImageCheckbox;
	private JButton btnsBrowseOverlayImage;
	private Point overlayOffset;
	private double overlayScale;

	private java.awt.Point editStart;
	private boolean isMoving;
	private boolean isScaling;
	private Point overlayOffsetBeforeEdit;
	private double overlayScaleBeforeEdit;
	private JButton fitToMapButton;
	private JButton fitInsideBorderButton;

	public OverlayTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
	}

	@Override
	public String getToolbarName()
	{
		return "Overlay";
	}

	@Override
	public int getMnemonic()
	{
		return KeyEvent.VK_V;
	}

	@Override
	public String getKeyboardShortcutText()
	{
		return "(Alt+V)";
	}

	@Override
	public String getImageIconFilePath()
	{
		return Paths.get(Assets.getAssetsPath(), "internal/Overlay tool.png").toString();
	}

	@Override
	public void onBeforeSaving()
	{
	}

	@Override
	public void onSwitchingTo()
	{
		showOrHideEditorTools();
		super.onSwitchingTo();
	}

	private void showOrHideEditorTools()
	{
		updater.doWhenMapIsReadyForInteractions(() ->
		{
			if (drawOverlayImageCheckbox.isSelected() && !StringUtils.isEmpty(overlayImagePath.getText()))
			{
				try
				{
					IntRectangle overlayPosition = calcOverlayPositionForScale(overlayScale);
					if (overlayPosition != null)
					{
						// Reduce width by 1 pixel so that right side draws inside the map when the overlay is the size of the map.
						IntRectangle adjusted = new IntRectangle(overlayPosition.x, overlayPosition.y, overlayPosition.width - 1,
								overlayPosition.height);
						mapEditingPanel.showIconEditToolsAt(adjusted.toRectangle(), true, IconEditToolsMode.Overlay, true);
					}
					else
					{
						mapEditingPanel.clearIconEditTools();
					}
				}
				catch (Exception ex)
				{
					mapEditingPanel.clearIconEditTools();
				}
			}
			else
			{
				mapEditingPanel.clearIconEditTools();
			}
			mapEditingPanel.repaint();
		});

	}

	private Point getOverlayOffsetResolutionInvariant()
	{
		if (overlayOffset != null)
		{
			return overlayOffset.mult(1.0 / mainWindow.displayQualityScale);
		}
		return new Point(0, 0);
	}

	@Override
	public void onSwitchingAway()
	{
		mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
		clearEditFields();
		mapEditingPanel.repaint();
	}

	@Override
	protected JPanel createToolOptionsPanel()
	{
		GridBagOrganizer organizer = new GridBagOrganizer();

		JPanel toolOptionsPanel = organizer.panel;
		toolOptionsPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));


		{
			drawOverlayImageCheckbox = new JCheckBox("Enable overlay image");
			drawOverlayImageCheckbox.setToolTipText("Show or hide the selected overlay image, if any.");
			drawOverlayImageCheckbox.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleEnablingAndDisabling(null);
					handleOverlayImageChange();
				}
			});
			organizer.addLeftAlignedComponent(drawOverlayImageCheckbox);
		}
		{
			overlayImagePath = new JTextField();
			overlayImagePath.getDocument().addDocumentListener(new DocumentListener()
			{
				public void changedUpdate(DocumentEvent e)
				{
					if (FileHelper.isFile(overlayImagePath.getText()))
					{
						handleOverlayImageChange();
					}
				}

				public void removeUpdate(DocumentEvent e)
				{
					if (FileHelper.isFile(overlayImagePath.getText()))
					{
						handleOverlayImageChange();
					}
				}

				public void insertUpdate(DocumentEvent e)
				{
					if (FileHelper.isFile(overlayImagePath.getText()))
					{
						handleOverlayImageChange();
					}
				}
			});

			btnsBrowseOverlayImage = new JButton("Browse");
			btnsBrowseOverlayImage.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					String filename = SwingHelper.chooseImageFile(toolOptionsPanel, FilenameUtils.getFullPath(overlayImagePath.getText()));
					if (filename != null)
					{
						overlayImagePath.setText(filename);
					}
				}
			});

			JPanel overlayImageChooseButtonPanel = new JPanel();
			overlayImageChooseButtonPanel.setLayout(new BoxLayout(overlayImageChooseButtonPanel, BoxLayout.X_AXIS));
			overlayImageChooseButtonPanel.add(btnsBrowseOverlayImage);
			overlayImageChooseButtonPanel.add(Box.createHorizontalGlue());

			organizer.addLabelAndComponentsVertical("Image:",
					"Image to draw over the map (not including borders). This is useful when drawing a map from a reference image.",
					Arrays.asList(overlayImagePath, Box.createVerticalStrut(5), overlayImageChooseButtonPanel));
		}

		{
			overlayImageTransparencySlider = new JSlider();
			overlayImageTransparencySlider.setPaintLabels(false);
			overlayImageTransparencySlider.setValue(50);
			overlayImageTransparencySlider.setMaximum(100);
			overlayImageTransparencySlider.setMinimum(0);
			SwingHelper.addListener(overlayImageTransparencySlider, () -> handleOverlayImageChange());
			SwingHelper.setSliderWidthForSidePanel(overlayImageTransparencySlider);
			SliderWithDisplayedValue sliderWithDisplay = new SliderWithDisplayedValue(overlayImageTransparencySlider,
					(value) -> String.format("%s%%", value), null, 30);
			sliderWithDisplay.addToOrganizer(organizer, "Transparency:",
					"Transparency to add to the overlay image to help with seeing the map underneath it.");
		}

		{
			fitToMapButton = new JButton("Fit to Map");
			fitToMapButton.setToolTipText("Resize and position the overlay image to fit the entire map, including borders.");
			fitToMapButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					overlayOffset = new Point(0.0, 0.0);
					overlayScale = 1.0;
					handleOverlayImageChange();
				}
			});


			fitInsideBorderButton = new JButton("Fit Inside Border");
			fitInsideBorderButton.setToolTipText(
					"Resize and position the overlay image to fit the drawable space on the map (the ocean/land), not including the border.");
			fitInsideBorderButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					updater.doWhenMapIsReadyForInteractions(() ->
					{
						if (updater.mapParts == null || updater.mapParts.background == null)
						{
							// Should not happen because we just checked that the map is ready for user interactions, so it has been drawn.
							assert false;
							return;
						}
						double scaledBorderWidth = updater.mapParts.background.getBorderWidthScaledByResolution();
						double mapWidthWithBorder = updater.mapParts.background.getMapBoundsIncludingBorder().width;
						overlayOffset = new Point(0.0, 0.0);
						overlayScale = (mapWidthWithBorder - (scaledBorderWidth * 2.0)) / mapWidthWithBorder;
						handleOverlayImageChange();
					});
				}
			});

			organizer.addLeftAlignedComponents(Arrays.asList(fitToMapButton, fitInsideBorderButton));
		}

		organizer.addHorizontalSpacerRowToHelpComponentAlignment(0.666);
		organizer.addVerticalFillerRow();

		return toolOptionsPanel;
	}

	@Override
	protected void handleMouseClickOnMap(MouseEvent e)
	{
	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		isMoving = mapEditingPanel.isInMoveTool(e.getPoint());
		isScaling = mapEditingPanel.isInScaleTool(e.getPoint());
		if (isMoving || isScaling)
		{
			editStart = e.getPoint();
			overlayOffsetBeforeEdit = new Point(overlayOffset);
			overlayScaleBeforeEdit = overlayScale;
		}
		else
		{
			clearEditFields();
		}
	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		if (editStart != null && (isMoving || isScaling) && overlayOffset != null)
		{
			updateOveralyOffsetOrScaleForEdit(e);

			showOrHideEditorTools();
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		if (editStart != null && (isMoving || isScaling) && overlayOffset != null)
		{
			updateOveralyOffsetOrScaleForEdit(e);

			overlayOffsetBeforeEdit = null;
			overlayScaleBeforeEdit = 0.0;

			showOrHideEditorTools();
			handleOverlayImageChange();

		}

		clearEditFields();
	}

	private void updateOveralyOffsetOrScaleForEdit(MouseEvent e)
	{
		Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
		Point graphPointMousePressedLocation = getPointOnGraph(editStart);

		if (isMoving)
		{
			double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
			double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);
			overlayOffset = overlayOffsetBeforeEdit.add(deltaX, deltaY);
		}
		else if (isScaling)
		{
			double scaleDelta = calcScale(graphPointMouseLocation, graphPointMousePressedLocation);
			final double minScale = calcMinScale();
			if (overlayScaleBeforeEdit * scaleDelta < minScale)
			{
				overlayScale = minScale;
			}
			else
			{
				overlayScale = overlayScaleBeforeEdit * scaleDelta;
			}
		}
	}

	private double calcMinScale()
	{
		try
		{
			IntRectangle overlaySizeAt1Scale = calcOverlayPositionForScale(1.0);
			if (overlaySizeAt1Scale != null)
			{
				final double minWidth = 375 * mainWindow.displayQualityScale;
				return minWidth / overlaySizeAt1Scale.width;
			}
			else
			{
				return 0.0;
			}
		}
		catch (Exception ex)
		{
			return 0.0;
		}
	}

	private void clearEditFields()
	{
		isMoving = false;
		isScaling = false;
		editStart = null;
		overlayOffsetBeforeEdit = null;
		overlayScaleBeforeEdit = 0.0;
	}

	private double calcScale(Point graphPointMouseLocation, Point graphPointMousePressedLocation)
	{
		IntRectangle overlayPosition = calcOverlayPositionForScale(overlayScale);
		if (overlayPosition != null)
		{
			double scale = graphPointMouseLocation.distanceTo(overlayPosition.toRectangle().getCenter())
					/ graphPointMousePressedLocation.distanceTo(overlayPosition.toRectangle().getCenter());

			return scale;
		}
		// Shouldn't happen since the user shouldn't be interacting with the overlay edit tools if they aren't being drawn.
		assert false;
		return 1.0;
	}

	private IntRectangle calcOverlayPositionForScale(double scale)
	{
		if (updater != null && updater.mapParts != null && updater.mapParts.background != null)
		{
			IntDimension mapSize = updater.mapParts.background.getMapBoundsIncludingBorder().toIntDimension();
			Tuple2<IntRectangle, Image> tuple = MapCreator.getOverlayPositionAndImage(overlayImagePath.getText(), scale,
					getOverlayOffsetResolutionInvariant(), mainWindow.displayQualityScale, mapSize);
			if (tuple != null)
			{
				return tuple.getFirst();
			}
		}
		return null;
	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
	}

	@Override
	protected void onBeforeShowMap()
	{
		showOrHideEditorTools();
	}

	@Override
	protected void onAfterUndoRedo()
	{
		showOrHideEditorTools();
	}

	@Override
	protected void onBeforeUndoRedo()
	{
	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImagesRefresh)
	{
		drawOverlayImageCheckbox.setSelected(settings.drawOverlayImage);
		overlayImagePath.setText(FileHelper.replaceHomeFolderPlaceholder(settings.overlayImagePath));
		overlayImageTransparencySlider.setValue(settings.overlayImageTransparency);
		overlayOffset = settings.overlayOffsetResolutionInvariant == null ? null
				: settings.overlayOffsetResolutionInvariant.mult(mainWindow.displayQualityScale);
		overlayScale = settings.overlayScale;
		
		if (isSelected())
		{
			showOrHideEditorTools();
		}
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.drawOverlayImage = drawOverlayImageCheckbox.isSelected();
		settings.overlayImagePath = FileHelper.replaceHomeFolderWithPlaceholder(overlayImagePath.getText());
		settings.overlayImageTransparency = overlayImageTransparencySlider.getValue();
		settings.overlayOffsetResolutionInvariant = overlayOffset == null ? new Point(0, 0)
				: overlayOffset.mult(1.0 / mainWindow.displayQualityScale);
		settings.overlayScale = overlayScale;
	}

	@Override
	public void handleEnablingAndDisabling(MapSettings settings)
	{
		if (overlayImagePath != null)
		{
			overlayImagePath.setEnabled(drawOverlayImageCheckbox.isSelected());
		}
		if (overlayImageTransparencySlider != null)
		{
			overlayImageTransparencySlider.setEnabled(drawOverlayImageCheckbox.isSelected());
		}
		if (btnsBrowseOverlayImage != null)
		{
			btnsBrowseOverlayImage.setEnabled(drawOverlayImageCheckbox.isSelected());
		}
		if (fitToMapButton != null)
		{
			fitToMapButton.setEnabled(drawOverlayImageCheckbox.isSelected());
		}
		if (fitInsideBorderButton != null)
		{
			fitInsideBorderButton.setEnabled(drawOverlayImageCheckbox.isSelected());
		}
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
		onSwitchingAway();
	}

	private void handleOverlayImageChange()
	{
		if (overlayOffset != null)
		{
			double minScale = calcMinScale();
			if (overlayScale < minScale)
			{
				overlayScale = minScale;
			}
		}

		mainWindow.undoer.setUndoPoint(UpdateType.OverlayImage, null);
		mainWindow.updater.createAndShowMapOverlayImage();
	}

}
