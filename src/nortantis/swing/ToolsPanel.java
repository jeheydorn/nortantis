package nortantis.swing;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
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
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import nortantis.MapSettings;
import nortantis.editor.DisplayQuality;
import nortantis.editor.MapUpdater;
import nortantis.editor.UserPreferences;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class ToolsPanel extends JPanel
{
	EditorTool currentTool;
	List<EditorTool> tools;
	private JPanel toolOptionsPanelContainer;
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
	private JPanel toolSelectPanel;
	private CardLayout toolOptionsCardLayout;

	public ToolsPanel(MainWindow mainWindow, MapEditingPanel mapEditingPanel, MapUpdater updater)
	{
		this.mainWindow = mainWindow;
		this.updater = updater;

		// Setup tools
		tools = Arrays.asList(new LandWaterTool(mainWindow, this, updater), new IconsTool(mainWindow, this, updater),
				new TextTool(mainWindow, this, updater), new OverlayTool(mainWindow, null, updater));
		currentTool = tools.get(0);

		setPreferredSize(new Dimension(SwingHelper.sidePanelPreferredWidth, mainWindow.getContentPane().getHeight()));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		toolSelectPanel = new JPanel(new FlowLayout());
		toolSelectPanel.setMaximumSize(new Dimension(toolSelectPanel.getMaximumSize().width, 20));
		add(toolSelectPanel);
		for (EditorTool tool : tools)
		{
			JToggleButton toolButton = new JToggleButton()
			{
				@Override
				protected void paintComponent(Graphics g)
				{
					Graphics2D g2d = (Graphics2D) g;

					// Set rendering hints
					g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					super.paintComponent(g);
				}
			};
			try
			{
				BufferedImage icon = AwtFactory.unwrap(Assets.readImage(tool.getImageIconFilePath()));
				toolButton.setIcon(new ImageIcon(icon));
			}
			catch (Exception e)
			{
				e.printStackTrace();
				Logger.printError("Error while setting an image for a tool: ", e);
			}
			toolButton.setToolTipText(tool.getToolbarName() + " " + tool.getKeyboardShortcutText());
			toolButton.setMnemonic(tool.getMnemonic());
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

		toolOptionsPanelContainer = new JPanel();
		toolOptionsCardLayout = new CardLayout();
		toolOptionsPanelContainer.setLayout(toolOptionsCardLayout);
		for (EditorTool tool : tools)
		{
			toolOptionsPanelContainer.add(tool.getToolOptionsPane(), tool.getToolbarName());
		}

		add(toolOptionsPanelContainer);
		updateBordersThatHaveColors();

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
		lblZoom.setToolTipText(
				"Zoom the map in or out (mouse wheel). To view more details at higher zoom levels," + " adjust the 'Display quality'.");

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
				mainWindow.updateDisplayedMapFromGeneratedMap(true, null, true);
			}
		});

		bottomPanel.add(SwingHelper.stackLabelAndComponent(lblZoom, zoomComboBox));

		// bottomPanel.add(Box.createHorizontalGlue());
		bottomPanel.add(Box.createRigidArea(new Dimension(12, 4)));

		JLabel lblDisplayQuality = new JLabel("Display Quality");
		lblDisplayQuality.setToolTipText(
				"Change the quality of the map displayed in the editor. Does not apply when exporting the map to an image. Higher values make the editor slower.");

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
	
	private void updateBordersThatHaveColors()
	{
		toolSelectPanel
		.setBorder(BorderFactory.createTitledBorder(new LineBorder(UIManager.getColor("controlShadow"), 1), "Editing Tools"));
		
		toolOptionsPanelBorder = BorderFactory.createTitledBorder(new LineBorder(UIManager.getColor("controlShadow"), 1),
				currentTool.getToolbarName() + " Options");
		toolOptionsPanelContainer.setBorder(toolOptionsPanelBorder);
	}

	public void loadSettingsIntoGUI(MapSettings settings, boolean isUndoRedoOrAutomaticChange, boolean changeEffectsBackgroundImages,
			boolean willDoImageRefresh)
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

		mainWindow.mapEditingPanel.clearAllToolSpecificSelectionsAndHighlights();
		EditorTool prevTool = currentTool;
		currentTool.setToggled(false);
		currentTool = selectedTool;
		currentTool.setToggled(true);
		// I'm calling onSwitchingAway after setting currentTool because the place EditorTool.shouldShowTextWhenTextIsEnabled
		// in MainWindow.createMapUpdater depends on it.
		prevTool.onSwitchingAway();
		toolOptionsPanelBorder.setTitle(currentTool.getToolbarName() + " Options");
		toolOptionsCardLayout.show(toolOptionsPanelContainer, currentTool.getToolbarName());
		toolOptionsPanelContainer.revalidate();
		toolOptionsPanelContainer.repaint();
		currentTool.onSwitchingTo();

		if (!updater.isMapBeingDrawn())
		{
			showAsDrawing(false);
		}
	}

	public void handleImagesRefresh(MapSettings settings)
	{
		// Cause the Icons tool to update its image previews
		for (EditorTool tool : tools)
		{
			tool.handleImagesRefresh(settings);
		}
	}

	public void handleCustomImagesPathChanged(String customImagesPath)
	{
		for (EditorTool tool : tools)
		{
			tool.handleCustomImagesPathChanged(customImagesPath);
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
			zoomComboBox.setEnabled(enable);

			if (settings != null)
			{
				for (EditorTool tool : tools)
				{
					tool.handleEnablingAndDisabling(settings);
				}
			}
		}
		else
		{
			// Always enabled
			displayQualityComboBox.setEnabled(true);
		}
	}

	public void handleLookAndFeelChange(LookAndFeel lookAndFeel)
	{
		updateBordersThatHaveColors();
		MapSettings settings = mainWindow.getSettingsFromGUI(false);
		for (EditorTool tool : tools)
		{
			SwingUtilities.updateComponentTreeUI(tool.getToolOptionsPanel());
			// Call this to make the icons tool update the borders on named-icon selectors.
			tool.handleImagesRefresh(settings);
			tool.updateBorder();
		}
	}
	
	public static Color getColorForToggledButtons()
	{
		int shade;
		if (UserPreferences.getInstance().lookAndFeel == LookAndFeel.Dark)
		{
			shade = 170;
		}
		else if (UserPreferences.getInstance().lookAndFeel == LookAndFeel.Light)
		{
			shade = 135;
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized look and feel for getting color for toggle buttons: " + UserPreferences.getInstance().lookAndFeel);
		}
		return new Color(shade, shade, shade);
	}
}
