package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
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

@SuppressWarnings("serial")
public class ToolsPanel extends JPanel
{
	EditorTool currentTool;
	List<EditorTool> tools;
	private JScrollPane toolsOptionsPanelContainer;
	private JPanel currentToolOptionsPanel;
	JComboBox<String> zoomComboBox;
	private List<String> zoomLevels;
	private TitledBorder toolOptionsPanelBorder;
	private JProgressBar progressBar;
	private JPanel bottomPanel;
	static final String fitToWindowZoomLevel = "Fit to Window";
	private Timer progressBarTimer;
	MainWindow mainWindow;

	
	public ToolsPanel(MainWindow mainWindow, MapEditingPanel mapEditingPanel)
	{
		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, getPreferredSize().height));
		setMinimumSize(new Dimension(SwingHelper.sidePanelMinimumWidth, getMinimumSize().height));
		
		this.mainWindow = mainWindow;
		
		// Setup tools
		tools = Arrays.asList(new LandWaterTool(mainWindow, this), new IconTool(mainWindow, this), new TextTool(mainWindow, this));
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

		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, mainWindow.getContentPane().getHeight()));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JPanel toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolSelectPanel.getMaximumSize().width, 20));
		toolSelectPanel.setBorder(BorderFactory.createTitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Editing Tools"));
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

		zoomLevels = Arrays.asList(new String[] { fitToWindowZoomLevel, "50%", "75%", "100%", "125%", "150%" });
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
		
		// Add a little space between the label and combo box. I'm using this because for some reason Box.createHorizontalStrut
		// causes bottomPanel to expand vertically.
		bottomPanel.add(Box.createRigidArea(new Dimension(5, 4)));


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
		for (EditorTool tool : tools)
		{
			tool.loadSettingsIntoGUI(settings);
		}
	}
	
	public void getSettingsFromGUI(MapSettings settings)
	{
		for (EditorTool tool : tools)
		{
			tool.getSettingsFromGUI(settings);
		}
	}
	
	public void handleToolSelected(EditorTool selectedTool)
	{
		enableOrDisableToolToggleButtonsAndZoom(false);

		mainWindow.mapEditingPanel.clearHighlightedCenters();
		mainWindow.mapEditingPanel.clearAreasToDraw();
		mainWindow.mapEditingPanel.clearSelectedCenters();
		mainWindow.mapEditingPanel.clearHighlightedEdges();
		mainWindow.mapEditingPanel.hideBrush();
		currentTool.onSwitchingAway();
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		currentToolOptionsPanel = currentTool.getToolOptionsPanel();
		toolsOptionsPanelContainer.setViewportView(currentToolOptionsPanel);
		toolsOptionsPanelContainer.revalidate();
		toolsOptionsPanelContainer.repaint();
		if (mainWindow.mapEditingPanel.mapFromMapCreator != null)
		{
			mainWindow.updateDisplayedMapFromGeneratedMap(false);
		}
		currentTool.onActivate();
		mainWindow.mapEditingPanel.repaint();
		enableOrDisableToolToggleButtonsAndZoom(true);
	}

	public String getZoomString()
	{
		return (String) zoomComboBox.getSelectedItem();
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
