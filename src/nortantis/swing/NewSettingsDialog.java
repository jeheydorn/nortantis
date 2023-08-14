package nortantis.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;

import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.util.ImageHelper;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	JSlider worldSizeSlider;
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	private JComboBox<String> dimensionsComboBox;
	JPanel booksPanel;
	MapSettings settings;
	private JProgressBar progressBar;
	private MapUpdater mapUpdater;
	private MapEditingPanel mapEditingPanel;
	private JScrollPane mapEditingScrollPane;
	private Dimension defaultSize = new Dimension(900, 700);
	private Timer progressBarTimer;


	public NewSettingsDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Create New Map", Dialog.ModalityType.APPLICATION_MODAL);

		createGUI();
		
		settings = SettingsGenerator.generate();
		loadSettingsIntoGUI(settings);


	}
	
	private void createGUI()
	{
		setSize(defaultSize);
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		
		// TODO Move this into a popup with which by default will not show up again.
		add(new JLabel("<html> The World Size and Dimensions of the map cannot be changed once the map is created. Other "
				+ "settings here are only to help guide the random generator to what you want. Terrain, theme, colors, "
				+ "icons, background, border, and text can all be changed after creating the map. </html>"));
		
		JPanel generatorSettingsPanel = new JPanel();
		generatorSettingsPanel.setLayout(new BoxLayout(generatorSettingsPanel, BoxLayout.X_AXIS));
		add(generatorSettingsPanel);

		{
			SwingHelper.resetGridY();
			
			JPanel leftPanel = SwingHelper.createPanelForLabeledComponents();
			generatorSettingsPanel.add(leftPanel);

			SwingHelper.resetGridY();

			dimensionsComboBox = new JComboBox<>();
			for (String dimension : SettingsGenerator.getAllowedDimmensions())
			{
				dimensionsComboBox.addItem(dimension);
			}
			SwingHelper.addLabelAndComponentToPanel(leftPanel, "Dimensions:",
					"Dimensions of the map when exported at 100% resolution, although the resolution can be scaled up or down while"
							+ " exporting. This doesn't include the border, if you add one.",
					dimensionsComboBox);

			worldSizeSlider = new JSlider();
			worldSizeSlider.setSnapToTicks(true);
			worldSizeSlider.setMajorTickSpacing(8000);
			worldSizeSlider.setMinorTickSpacing(SettingsGenerator.worldSizePrecision);
			worldSizeSlider.setPaintLabels(true);
			worldSizeSlider.setPaintTicks(true);
			worldSizeSlider.setMinimum(SettingsGenerator.minWorldSize);
			worldSizeSlider.setMaximum(SettingsGenerator.maxWorldSize);
			SwingHelper.addLabelAndComponentToPanel(leftPanel, "World size:", "The number of polygons in the randomly generated world.",
					worldSizeSlider);

			
			edgeLandToWaterProbSlider = new JSlider();
			edgeLandToWaterProbSlider.setValue(70);
			edgeLandToWaterProbSlider.setPaintTicks(true);
			edgeLandToWaterProbSlider.setPaintLabels(true);
			edgeLandToWaterProbSlider.setMinorTickSpacing(25);
			edgeLandToWaterProbSlider.setMajorTickSpacing(25);
			{
				Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
				for (int i = edgeLandToWaterProbSlider.getMinimum(); i < edgeLandToWaterProbSlider.getMaximum()
						+ 1; i += edgeLandToWaterProbSlider.getMajorTickSpacing())
				{
					labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
				}
				edgeLandToWaterProbSlider.setLabelTable(labelTable);
			}
			SwingHelper.addLabelAndComponentToPanel(leftPanel, "Edge land probability:",
					"The probability that a tectonic plate touching the edge of the map will be land rather than ocean.",
					edgeLandToWaterProbSlider);

			centerLandToWaterProbSlider = new JSlider();
			centerLandToWaterProbSlider.setValue(70);
			centerLandToWaterProbSlider.setPaintTicks(true);
			centerLandToWaterProbSlider.setPaintLabels(true);
			centerLandToWaterProbSlider.setMinorTickSpacing(25);
			centerLandToWaterProbSlider.setMajorTickSpacing(25);
			{
				Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
				for (int i = centerLandToWaterProbSlider.getMinimum(); i < centerLandToWaterProbSlider.getMaximum()
						+ 1; i += centerLandToWaterProbSlider.getMajorTickSpacing())
				{
					labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
				}
				centerLandToWaterProbSlider.setLabelTable(labelTable);
			}
			SwingHelper.addLabelAndComponentToPanel(leftPanel, "Center land probability:",
					"The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.",
					centerLandToWaterProbSlider);
		}

		{
			SwingHelper.resetGridY();

			JPanel rightPanel = SwingHelper.createPanelForLabeledComponents();
			generatorSettingsPanel.add(rightPanel);
			
			// TODO Add city type drop-down and city probability.


			// TODO Put books checkboxes in a scroll pane to make books panel a fixed size.
			booksPanel = SwingHelper.createBooksPanel();
			SwingHelper.addLeftAlignedComponentWithStackedLabel(rightPanel, "Books for generating text:",
					"Selected books will be used to generate new names.", booksPanel);

		}
		

		JPanel randomizePanel = new JPanel();
		randomizePanel.setLayout(new BoxLayout(randomizePanel, BoxLayout.X_AXIS));
		JButton randomizeThemeButton = new JButton("Randomize Theme");
		randomizeThemeButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				randomizeTheme();
			}
		});
		randomizePanel.add(randomizeThemeButton);
		
		JButton randomizeLandButton = new JButton("Randomize Land");
		randomizeLandButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				randomizeLand();
			}
		});
		randomizePanel.add(randomizeLandButton);
		add(randomizePanel);


		createMapEditingPanel();
		createMapUpdater();
		add(mapEditingScrollPane);
		
		
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Drawing...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		add(progressBar);

		ActionListener listener = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				progressBar.setVisible(mapUpdater.isMapBeingDrawn);
			}
		};
		progressBarTimer = new Timer(50, listener);
		progressBarTimer.setInitialDelay(500);

		
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton cancelButton = new JButton("Cancel"); // TODO Add keyboard shortcut
		bottomPanel.add(cancelButton);
		JButton acceptButton = new JButton("Create Map"); // TODO Add keyboard shortcut
		bottomPanel.add(acceptButton);
		add(bottomPanel);
		
		
		
		// TODO When accept is pressed, Call
		// MainWindow.createPlaceholderImage with the message: "Drawing the
		// map..."
	}
	
	private void randomizeTheme()
	{
		// TODO
	}
	
	private void randomizeLand()
	{
		// TODO
	}
	
	private void createMapEditingPanel()
	{
		BufferedImage placeHolder = ImageHelper
				.createPlaceholderImage(new String[] { "Drawing..." });
		mapEditingPanel = new MapEditingPanel(placeHolder);
		
		
		mapEditingScrollPane = new JScrollPane(mapEditingPanel);

		// TODO Make sure the below works. It use to be on the frame.
		mapEditingScrollPane.addComponentListener(new ComponentAdapter()
		{
			public void componentResized(ComponentEvent componentEvent)
			{
				enableOrDisableProgressBar(true); // TODO Also call this in other places where the map is redrawn.
				mapUpdater.setMaxMapSize(getMapDrawingAreaSize());
				mapUpdater.createAndShowMapFull();
			}
		});

		// Speed up the scroll speed.
		mapEditingScrollPane.getVerticalScrollBar().setUnitIncrement(16);
	}
	
	private void createMapUpdater()
	{
		final NewSettingsDialog thisDialog = this;
		mapUpdater = new MapUpdater(false)
				{

					@Override
					protected void onBeginDraw()
					{
					}

					@Override
					protected MapSettings getSettingsFromGUI()
					{
						MapSettings settings = thisDialog.getSettingsFromGUI();
						
						// This is only the maximum size because I'm passing in maxDimensions to MapCreator.create.
						settings.resolution = 1.0; 
						
						return settings;
					}

					@Override
					protected void onFinishedDrawing(BufferedImage map)
					{
						mapEditingPanel.image = map;

						enableOrDisableProgressBar(false);

						// Tell the scroll pane to update itself.
						mapEditingPanel.revalidate();
						mapEditingPanel.repaint();
					}
					
					@Override
					protected void onFailedToDraw()
					{
						enableOrDisableProgressBar(false);
						mapEditingPanel.clearSelectedCenters();
						isMapBeingDrawn = false;						
					}

					@Override
					protected MapEdits getEdits()
					{
						assert settings.edits.isEmpty();
						return settings.edits;
					}

					@Override
					protected BufferedImage getCurrentMapForIncrementalUpdate()
					{
						return mapEditingPanel.mapFromMapCreator;
					}

					
			
				};
				mapUpdater.setMaxMapSize(getMapDrawingAreaSize());
	}
	
	private Dimension getMapDrawingAreaSize()
	{
		final int additionalWidthToRemoveIDontKnowWhereItsCommingFrom = 2;
		return new Dimension(mapEditingScrollPane.getSize().width - additionalWidthToRemoveIDontKnowWhereItsCommingFrom,
				mapEditingScrollPane.getSize().height - additionalWidthToRemoveIDontKnowWhereItsCommingFrom);

	}

	private void loadSettingsIntoGUI(MapSettings settings)
	{
		// TODO
		SwingHelper.checkSelectedBooks(booksPanel, settings.books);
	}

	private MapSettings getSettingsFromGUI()
	{
		// TODO
		MapSettings resultSettings = settings.deepCopy();
		resultSettings.worldSize = worldSizeSlider.getValue();
		resultSettings.edgeLandToWaterProbability = edgeLandToWaterProbSlider.getValue() / 100.0;
		resultSettings.centerLandToWaterProbability = centerLandToWaterProbSlider.getValue() / 100.0;

		Dimension generatedDimensions = getGeneratedBackgroundDimensionsFromGUI();
		resultSettings.generatedWidth = (int) generatedDimensions.getWidth();
		resultSettings.generatedHeight = (int) generatedDimensions.getHeight();

		resultSettings.books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
					resultSettings.books.add(checkBox.getText());
			}
		}

		return resultSettings;
	}

	private Dimension getGeneratedBackgroundDimensionsFromGUI()
	{
		String selected = (String) dimensionsComboBox.getSelectedItem();
		return parseGenerateBackgroundDimensionsFromDropdown(selected);
	}

	public static Dimension parseGenerateBackgroundDimensionsFromDropdown(String selected)
	{
		selected = selected.substring(0, selected.indexOf("("));
		String[] parts = selected.split("x");
		return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
	}

	private int getDimensionIndexFromDimensions(int generatedWidth, int generatedHeight)
	{
		for (int i : new Range(dimensionsComboBox.getItemCount()))
		{
			Dimension dim = parseGenerateBackgroundDimensionsFromDropdown(dimensionsComboBox.getItemAt(i));
			if (dim.getWidth() == generatedWidth && dim.getHeight() == generatedHeight)
			{
				return i;
			}
		}
		throw new IllegalArgumentException("No dropdown menu option with dimentions " + generatedWidth + " x " + generatedHeight);
	}
	
	private void enableOrDisableProgressBar(boolean enable)
	{
		if (enable)
		{
			progressBarTimer.start();
		}
		else
		{
			progressBarTimer.stop();
			progressBar.setVisible(false);
		}

	}
}
