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
	private Point editStartLastUpdate; // TODO Decide what to do with this if I use it.

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
	}

	private void showOrHideEditorTools()
	{
		updater.doWhenMapIsReadyForInteractions(() -> 
		{
			if (drawOverlayImageCheckbox.isSelected() && !StringUtils.isEmpty(overlayImagePath.getText()) && updater != null
					&& updater.mapParts != null && updater.mapParts.background != null)
			{
				IntDimension mapSize = updater.mapParts.background.getMapBoundsIncludingBorder().toIntDimension();
				try
				{
					Tuple2<IntRectangle, Image> tuple = MapCreator.getOverlayPositionAndImage(overlayImagePath.getText(), overlayScale,
							getOverlayOffsetResolutionInvariant(), mainWindow.displayQualityScale, mapSize);
					if (tuple != null)
					{
						IntRectangle overlayPosition = tuple.getFirst();
						// Reduce width by 1 pixel so that right side draws inside the map when the overlay is the size of the map.
						IntRectangle adjusted = new IntRectangle(overlayPosition.x, overlayPosition.y, overlayPosition.width - 1,
								overlayPosition.height);
						mapEditingPanel.showIconEditToolsAt(adjusted.toRectangle(), true, IconEditToolsMode.Overlay, true);
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
		isMoving = false;
		isScaling = false;
		editStart = null;
		editStartLastUpdate = null;
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
			editStartLastUpdate = new Point(overlayOffset);
		}
		else
		{
			editStart = null;
			editStartLastUpdate = null;
		}
	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		isMoving = false;
		isScaling = false;
		editStart = null;
		editStartLastUpdate = null;
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
			Point graphPointMouseLocation = getPointOnGraph(e.getPoint());
			Point graphPointMousePressedLocation = getPointOnGraph(editStart);

			if (isMoving)
			{
				double deltaX = (int) (graphPointMouseLocation.x - graphPointMousePressedLocation.x);
				double deltaY = (int) (graphPointMouseLocation.y - graphPointMousePressedLocation.y);
				overlayOffset = overlayOffset.add(deltaX, deltaY);
				editStart = e.getPoint();
			}
			else if (isScaling)
			{
				double scale = calcScale(graphPointMouseLocation, graphPointMousePressedLocation);
				overlayScale *= scale;
				editStart = e.getPoint();
			}
			
			showOrHideEditorTools();
			undoer.setUndoPoint(UpdateType.OverlayImage, this);
			handleOverlayImageChange();
		}
	}
	
	private double calcScale(Point graphPointMouseLocation, Point graphPointMousePressedLocation)
	{
		IntDimension mapSize = updater.mapParts.background.getMapBoundsIncludingBorder().toIntDimension();
		Tuple2<IntRectangle, Image> tuple = MapCreator.getOverlayPositionAndImage(overlayImagePath.getText(), overlayScale,
				getOverlayOffsetResolutionInvariant(), mainWindow.displayQualityScale, mapSize);
		if (tuple != null)
		{
			Rectangle overlayPosition = tuple.getFirst().toRectangle();
			double scale = graphPointMouseLocation.distanceTo(overlayPosition.getCenter())
					/ graphPointMousePressedLocation.distanceTo(overlayPosition.getCenter());

			final double minSize = 50; // TODO Make sure this is big enough to keep icon edit tools from crossing.
			double minSideLength = Math.min(overlayPosition.width, overlayPosition.height);
			double minScale = minSize / minSideLength;
			return Math.max(scale, minScale);
		}
		// Shouldn't happen since the user shouldn't be interacting with the overlay edit tools if they aren't being drawn.
		assert false; 
		return 1.0;
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
		
		showOrHideEditorTools();
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
	}

	@Override
	public void onBeforeLoadingNewMap()
	{
	}

	private void handleOverlayImageChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.OverlayImage, null);
		mainWindow.updater.createAndShowMapOverlayImage();
	}

}
