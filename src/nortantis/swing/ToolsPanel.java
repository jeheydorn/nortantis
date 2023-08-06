package nortantis.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import nortantis.IconType;
import nortantis.ImageCache;
import nortantis.MapSettings;
import nortantis.UserPreferences;
import nortantis.util.JComboBoxFixed;
import nortantis.util.SwingHelper;

@SuppressWarnings("serial")
public class ToolsPanel extends JPanel
{
	EditorTool currentTool;
	List<EditorTool> tools;
	private JPanel toolsOptionsPanelContainer;
	private JPanel currentToolOptionsPanel;
	JComboBox<String> zoomComboBox;
	private JComboBox<String> cityIconsSetComboBox;
	public MapEditingPanel mapEditingPanel;
	private List<String> zoomLevels;
	private TitledBorder toolOptionsPanelBorder;
	private JProgressBar progressBar;
	private JPanel bottomPanel;
	static final String fitToWindowZoomLevel = "Fit to Window";
	private Timer progressBarTimer;
	MainWindow mainWindow;
	private JSlider hueSlider;
	private JSlider brightnessSlider;
	private JSlider saturationSlider;
	private JPanel booksPanel;


	
	public ToolsPanel(MainWindow mainWindow, MapEditingPanel mapEditingPanel)
	{
		this.mainWindow = mainWindow;
		
		// Setup tools
		tools = Arrays.asList(new LandWaterTool(mainWindow), new IconTool(mainWindow), new TextTool(mainWindow));
		if (UserPreferences.getInstance().lastEditorTool != "")
		{
			for (EditorTool tool : tools)
			{
				if (tool.getToolbarName().equals(UserPreferences.getInstance().lastEditorTool))
				{
					currentTool = tool;
				}
			}
		}
		if (currentTool == null)
		{
			currentTool = tools.get(2);
		}

		setPreferredSize(new Dimension(SwingHelper.sidePanelWidth, mainWindow.getContentPane().getHeight()));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(SwingHelper.sidePanelWidth, 20));
		toolSelectPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Tools"));
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
			}
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
			toolSelectPanel.add(toolButton);
		}

		toolsOptionsPanelContainer = new JPanel();
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		add(toolsOptionsPanelContainer);
		toolOptionsPanelBorder = BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
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
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(
				SwingHelper.borderWidthBetweenComponents, 
				SwingHelper.borderWidthBetweenComponents,
				SwingHelper.borderWidthBetweenComponents, 
				SwingHelper.borderWidthBetweenComponents));

		JLabel lblZoom = new JLabel("Zoom:");
		bottomPanel.add(lblZoom);
		lblZoom.setToolTipText("Zoom the map in or out (CTRL + mouse wheel). To view more details at higher zoom levels,"
				+ " adjust View > Image Quality.");

		zoomLevels = Arrays.asList(new String[] { fitToWindowZoomLevel, "50%", "75%", "100%", "125%" });
		zoomComboBox = new JComboBoxFixed<>();
		for (String level : zoomLevels)
		{
			zoomComboBox.addItem(level);
		}
		if (UserPreferences.getInstance().zoomLevel != "" && zoomLevels.contains(UserPreferences.getInstance().zoomLevel))
		{
			zoomComboBox.setSelectedItem(UserPreferences.getInstance().zoomLevel);
		}
		else
		{
			final String defaultZoomLevel = "50%";
			zoomComboBox.setSelectedItem(defaultZoomLevel);
			UserPreferences.getInstance().zoomLevel = defaultZoomLevel;
		}

		bottomPanel.add(zoomComboBox);
		zoomComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				UserPreferences.getInstance().zoomLevel = (String) zoomComboBox.getSelectedItem();
				mainWindow.updateDisplayedMapFromGeneratedMap(true);
			}
		});

		bottomPanel.add(Box.createHorizontalGlue());

		progressAndBottomPanel.add(bottomPanel);
		add(progressAndBottomPanel);

		ActionListener listener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				progressBar.setVisible(mainWindow.isMapBeingDrawn);
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);
		

	}
	
	public void loadSettingsIntoGUI(MapSettings settings)
	{
		hueSlider.setValue(settings.hueRange);
		saturationSlider.setValue(settings.saturationRange);
		brightnessSlider.setValue(settings.brightnessRange);
		
		SwingHelper.initializeComboBoxItems(cityIconsSetComboBox, ImageCache.getInstance().getIconSets(IconType.cities), 
				settings.cityIconSetName);
		
		booksPanel.removeAll();
		MainWindow.createBooksCheckboxes(booksPanel, settings);
	}
	
	public void getSettingsFromGUI(MapSettings settings)
	{
		settings.hueRange = hueSlider.getValue();
		settings.saturationRange = saturationSlider.getValue();
		settings.brightnessRange = brightnessSlider.getValue();
		
		settings.books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
					settings.books.add(checkBox.getText());
			}
		}

		settings.cityIconSetName = (String) cityIconsSetComboBox.getSelectedItem();
	}
	
	public void handleToolSelected(EditorTool selectedTool)
	{
		enableOrDisableToolToggleButtonsAndZoom(false);

		mapEditingPanel.clearHighlightedCenters();
		mapEditingPanel.clearAreasToDraw();
		mapEditingPanel.clearSelectedCenters();
		mapEditingPanel.clearHighlightedEdges();
		mapEditingPanel.hideBrush();
		currentTool.onSwitchingAway();
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		toolsOptionsPanelContainer.remove(currentToolOptionsPanel);
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.add(currentToolOptionsPanel);
		toolsOptionsPanelContainer.revalidate();
		toolsOptionsPanelContainer.repaint();
		if (mapEditingPanel.mapFromMapCreator != null)
		{
			mainWindow.updateDisplayedMapFromGeneratedMap(false);
		}
		currentTool.onActivate();
		mapEditingPanel.repaint();
		enableOrDisableToolToggleButtonsAndZoom(true);
	}
	
	public void handleColorRegionsChanged(boolean colorRegions)
	{
		// TODO


	}
	
	public String getZoomString()
	{
		return (String) zoomComboBox.getSelectedItem();
	}

	public void handleDrawTextChanged(boolean drawText)
	{
		// TODO Hide everything in the text tool options and show a message explaining that text drawing is disabled.
		
		// TODO Somehow access the booksAndLablePanel from TextTool.
		
		booksPanel.setEnabled(drawText);
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				checkBox.setEnabled(drawText);
			}
		}
	}
	
	public void enableOrDisableToolToggleButtonsAndZoom(boolean enable)
	{
		for (EditorTool tool : tools)
		{
			tool.setToggleButtonEnabled(enable);
		}
		
		zoomComboBox.setEnabled(enable);

		if (enable)
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}
		else
		{
			progressBarTimer.start();
		}

	}
}
