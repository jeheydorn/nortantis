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

import nortantis.MapSettings;
import nortantis.editor.MapUpdater;
import nortantis.util.Assets;
import nortantis.util.FileHelper;

public class OverlayTool extends EditorTool
{
	private JTextField overlayImagePath;
	private JSlider overlayImageTransparencySlider;
	private JCheckBox drawOverlayImageCheckbox;
	private JButton btnsBrowseOverlayImage;

	public OverlayTool(MainWindow parent, ToolsPanel toolsPanel, MapUpdater mapUpdater)
	{
		super(parent, toolsPanel, mapUpdater);
	}

	@Override
	public String getToolbarName()
	{
		// TODO Auto-generated method stub
		return "Overlay";
	}

	@Override
	public int getMnemonic()
	{
		// TODO Auto-generated method stub
		return KeyEvent.VK_V;
	}

	@Override
	public String getKeyboardShortcutText()
	{
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub

	}

	@Override
	public void onSwitchingAway()
	{
		// TODO Auto-generated method stub

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
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleMousePressedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleMouseReleasedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleMouseMovedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleMouseDraggedOnMap(MouseEvent e)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void handleMouseExitedMap(MouseEvent e)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void onBeforeShowMap()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void onAfterUndoRedo()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void onBeforeUndoRedo()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImagesRefresh)
	{
		drawOverlayImageCheckbox.setSelected(settings.drawOverlayImage);
		overlayImagePath.setText(FileHelper.replaceHomeFolderPlaceholder(settings.overlayImagePath));
		overlayImageTransparencySlider.setValue(settings.overlayImageTransparency);
		// TODO 
	}

	@Override
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.drawOverlayImage = drawOverlayImageCheckbox.isSelected();
		settings.overlayImagePath =  FileHelper.replaceHomeFolderWithPlaceholder(overlayImagePath.getText());
		settings.overlayImageTransparency = overlayImageTransparencySlider.getValue();
		// TODO
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
		// TODO Auto-generated method stub

	}

	private void handleOverlayImageChange()
	{
		mainWindow.undoer.setUndoPoint(UpdateType.OverlayImage, null);
		mainWindow.updater.createAndShowMapOverlayImage();
	}

}
