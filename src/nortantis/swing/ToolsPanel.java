package nortantis.swing;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import nortantis.MapSettings;
import nortantis.editor.DisplayQuality;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class ToolsPanel extends JPanel
{
	EditorTool currentTool;
	List<EditorTool> tools;
	private JScrollPane toolsOptionsPanelContainer;
	private JPanel currentToolOptionsPanel;
	JComboBox<String> zoomComboBox;
	List<String> zoomLevels;
	JComboBox<DisplayQuality> displayQualityComboBox;
	List<String> displayQualityLevels;
	private TitledBorder toolOptionsPanelBorder;
	private JProgressBar progressBar;
	private JPanel bottomPanel;
	static final String fitToWindowZoomLevel = "Fit to Window";
	private final String defaultZoomLevel = fitToWindowZoomLevel;
	private Timer progressBarTimer;
	MainWindow mainWindow;
	MapUpdater updater;

	public ToolsPanel(MainWindow mainWindow, MapEditingPanel mapEditingPanel, MapUpdater updater)
	{
		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, getPreferredSize().height));
		setMinimumSize(new Dimension(SwingHelper.sidePanelMinimumWidth, getMinimumSize().height));

		this.mainWindow = mainWindow;
		this.updater = updater;

		// Setup tools
		tools = Arrays.asList(new LandWaterTool(mainWindow, this, updater), new IconsTool(mainWindow, this, updater),
				new TextTool(mainWindow, this, updater));
		currentTool = tools.get(2);

		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, mainWindow.getContentPane().getHeight()));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolSelectPanel.getMaximumSize().width, 20));
		toolSelectPanel
				.setBorder(BorderFactory.createTitledBorder(new LineBorder(UIManager.getColor("controlShadow"), 1), "Editing Tools"));
		add(toolSelectPanel);
		for (EditorTool tool : tools)
		{
			JToggleButton toolButton = new JToggleButton();
			try
			{
				toolButton.setIcon(new ImageIcon(tool.getImageIconFilePath()));
			}
			catch (Exception e)
			{
				e.printStackTrace();
				Logger.printError("Error while setting an image for a tool: ", e);
			}
			toolButton.setToolTipText(tool.getToolbarName());
			toolButton.setMaximumSize(new Dimension(50, 50));
			tool.setToggleButton(toolButton);
			toolButton.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					handleToolSelected(tool);
				}
			});
			tool.updateBorder();
			toolSelectPanel.add(toolButton);
		}

		currentTool.setToggled(true);

		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer = new JScrollPane(currentToolOptionsPanel);

		add(toolsOptionsPanelContainer);
		toolOptionsPanelBorder = BorderFactory.createTitledBorder(new LineBorder(UIManager.getColor("controlShadow"), 1),
				currentTool.getToolbarName() + " Options");
		toolsOptionsPanelContainer.setBorder(toolOptionsPanelBorder);

		JPanel progressAndBottomPanel = new JPanel();
		progressAndBottomPanel.setLayout(new BoxLayout(progressAndBottomPanel, BoxLayout.Y_AXIS));
		// Progress bar
		JPanel progressBarPanel = new JPanel();
		progressBarPanel.setLayout(new BoxLayout(progressBarPanel, BoxLayout.X_AXIS));
		progressBarPanel.setBorder(BorderFactory.createEmptyBorder(0, SwingHelper.borderWidthBetweenComponents - 2, 0,
				SwingHelper.borderWidthBetweenComponents));
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Drawing...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		progressBarPanel.add(progressBar);
		progressAndBottomPanel.add(progressBarPanel);

		// Setup bottom panel
		bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.setBorder(
				BorderFactory.createEmptyBorder(SwingHelper.borderWidthBetweenComponents, SwingHelper.borderWidthBetweenComponents,
						SwingHelper.borderWidthBetweenComponents, SwingHelper.borderWidthBetweenComponents));

		JLabel lblZoom = new JLabel("Zoom");
		lblZoom.setToolTipText("Zoom the map in or out (mouse wheel). To view more details at higher zoom levels,"
				+ " adjust the 'Display quality'.");

		zoomLevels = Arrays.asList(new String[] { fitToWindowZoomLevel, "50%", "75%", "100%", "150%", "200%", "275%" });
		zoomComboBox = new JComboBoxFixed<>();
		for (String level : zoomLevels)
		{
			zoomComboBox.addItem(level);
		}
		zoomComboBox.setSelectedItem(defaultZoomLevel);
		zoomComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				mainWindow.updateDisplayedMapFromGeneratedMap(true, null);
			}
		});
		
		bottomPanel.add(SwingHelper.stackLabelAndComponent(lblZoom, zoomComboBox));
		
		
		
		//bottomPanel.add(Box.createHorizontalGlue());
		bottomPanel.add(Box.createRigidArea(new Dimension(12, 4)));

		JLabel lblDisplayQuality = new JLabel("Display Quality");
		lblDisplayQuality.setToolTipText("Change the quality of the map displayed in the editor. Does not apply when exporting the map to an image. Higher values make the editor slower.");

		displayQualityComboBox = new JComboBoxFixed<>();
		for (DisplayQuality quality : DisplayQuality.values())
		{
			displayQualityComboBox.addItem(quality);
		}
		
		// Default display quality
		displayQualityComboBox.setSelectedItem(UserPreferences.getInstance().editorImageQuality);
		
		mainWindow.updateImageQualityScale(UserPreferences.getInstance().editorImageQuality);
		
		bottomPanel.add(displayQualityComboBox);
		displayQualityComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				DisplayQuality quality = (DisplayQuality) displayQualityComboBox.getSelectedItem();
				UserPreferences.getInstance().editorImageQuality = quality;
				mainWindow.handleImageQualityChange(quality);
			}
		});

		bottomPanel.add(SwingHelper.stackLabelAndComponent(lblDisplayQuality, displayQualityComboBox));

		


		progressAndBottomPanel.add(bottomPanel);
		add(progressAndBottomPanel);

		ActionListener listener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				progressBar.setVisible(updater.isMapBeingDrawn());
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);
	}

	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages, boolean willDoImageRefresh)
	{
		for (EditorTool tool : tools)
		{
			tool.loadSettingsIntoGUI(settings, isUndoRedoOrAutomaticChange, changeEffectsBackgroundImages, willDoImageRefresh);
		}
	}

	public void resetToolsForNewMap()
	{
		for (EditorTool tool : tools)
		{
			tool.onBeforeLoadingNewMap();
		}
	}

	public void getSettingsFromGUI(MapSettings settings)
	{
		for (EditorTool tool : tools)
		{
			tool.getSettingsFromGUI(settings);
		}
	}
	
	public TextTool getTextTool()
	{
		for (EditorTool tool : tools)
		{
			if (tool instanceof TextTool)
			{
				return (TextTool) tool;
			}
		}
		
		assert false;
		return null;
	}

	public void handleToolSelected(EditorTool selectedTool)
	{
		showAsDrawing(true);

		mainWindow.mapEditingPanel.clearHighlightedCenters();
		mainWindow.mapEditingPanel.clearTextBox();
		mainWindow.mapEditingPanel.clearSelectedCenters();
		mainWindow.mapEditingPanel.clearHighlightedEdges();
		mainWindow.mapEditingPanel.hideBrush();
		EditorTool prevTool = currentTool;
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		// I'm calling onSwitchingAway after setting currentTool because the place EditorTool.shouldShowTextWhenTextIsEnabled
		// in MainWindow.createMapUpdater depends on it.
		prevTool.onSwitchingAway();
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.setViewportView(currentToolOptionsPanel);
		toolsOptionsPanelContainer.revalidate();
		toolsOptionsPanelContainer.repaint();
		currentTool.onActivate();
		mainWindow.themePanel.showOrHideTextHiddenMessage();

		if (!updater.isMapBeingDrawn())
		{
			showAsDrawing(false);
		}
	}

	public void handleImagesRefresh(MapSettings settings)
	{
		// Cause the Icons tool to update its image radio buttons
		for (EditorTool tool : tools)
		{
			tool.handleImagesRefresh(settings);
		}
	}

	public String getZoomString()
	{
		return (String) zoomComboBox.getSelectedItem();
	}
	
	public void resetZoomToDefault()
	{
		zoomComboBox.setSelectedItem(defaultZoomLevel);
	}

	public void showAsDrawing(boolean isDrawing)
	{
		zoomComboBox.setEnabled(!isDrawing);

		if (isDrawing)
		{
			progressBarTimer.start();
		}
		else
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}

	}

	void enableOrDisableEverything(boolean enable, MapSettings settings)
	{
		SwingHelper.setEnabled(this, enable);

		if (enable)
		{
			if (!mainWindow.updater.isMapBeingDrawn())
			{
				zoomComboBox.setEnabled(enable);
			}

			if (settings != null)
			{
				for (EditorTool tool : tools)
				{
					tool.handleEnablingAndDisabling(settings);
				}
			}
		}
	}
}
