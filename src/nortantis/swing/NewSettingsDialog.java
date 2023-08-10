package nortantis.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Hashtable;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import nortantis.ImagePanel;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.util.Logger;
import nortantis.util.Range;

@SuppressWarnings("serial")
public class NewSettingsDialog extends JDialog
{
	private JTextField randomSeedTextField;
	JSlider worldSizeSlider;
	JSlider edgeLandToWaterProbSlider;
	JSlider centerLandToWaterProbSlider;
	private JComboBox<String> dimensionsComboBox;
	private ImagePanel previewPanel;
	private JLabel lblEdgeLandToWaterRatio;
	private JLabel lblDimensions;
	JPanel booksPanel;
	MapSettings settings;
	private JProgressBar progressBar;



	public NewSettingsDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Create New Map", Dialog.ModalityType.APPLICATION_MODAL);
		setSize(900, 700);
		
		settings = SettingsGenerator.generate();
		loadSettingsIntoGUI(settings);
		
		ActionListener previewUpdateListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				// TODO handle another update being queued up, like the editor does. Maybe I can extract that code and re-use it here.
				
				final MapSettings settings = getSettingsFromGUI();

				Logger.clear();
				SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
				{
					@Override
					public BufferedImage doInBackground() throws IOException
					{
						Dimension bounds = new Dimension(previewPanel.getWidth(), previewPanel.getHeight());

						// Copy the settings because generating at differing
						// resolutions can make slight changes in text
						// positions,
						// which will trigger the prompt to save when closing.
						BufferedImage map = new MapCreator().createMap(settings.deepCopy(), bounds, null);
						System.gc();
						return map;
					}

					@Override
					public void done()
					{
						BufferedImage map = null;
						try
						{
							map = get();
						}
						catch (Exception ex)
						{
							mainWindow.handleBackgroundThreadException(ex);
						}

						if (map != null)
						{
							previewPanel.image = map;
							previewPanel.repaint();
						}
					}

				};
				worker.execute();

			}
		};
		
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		previewPanel = new ImagePanel();
		previewPanel.setLayout(null);
		previewPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlShadow")));
		add(previewPanel);
		
		JPanel progressBarPanel = new JPanel();
		progressBarPanel.setLayout(new BoxLayout(progressBarPanel, BoxLayout.X_AXIS));
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Drawing...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		progressBarPanel.add(progressBar);
		add(progressBarPanel);
		
		
		add(new JLabel("<html> The World Size and Dimensions of the map cannot be changed once the map is created. Other "
				+ "settings here are only to help guide the random generator to what you want. Terrain, theme, colors, "
				+ "icons, background, border, and text can all be edited after creating the map. </html>"));

			
		final JPanel generatorSettingsPanel = new JPanel();
		generatorSettingsPanel.setLayout(new BoxLayout(generatorSettingsPanel, BoxLayout.X_AXIS));
		final JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		generatorSettingsPanel.add(leftPanel);
		final JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		generatorSettingsPanel.add(rightPanel);
		
		// TODO Add buttons for regenerating theme or land.
		
		
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
		SwingHelper.addLabelAndComponentToPanel(rightPanel, "Edge land probability:", 
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
		SwingHelper.addLabelAndComponentToPanel(rightPanel, "Center land probability:",
				"The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.",
				centerLandToWaterProbSlider);

		
		JLabel lblBooks = new JLabel("Books:");
		lblBooks.setToolTipText("Selected books will be used to generate new names.");
		leftPanel.add(lblBooks);

		booksPanel = new JPanel();
		JScrollPane booksScrollPane = MainWindow.createBooksScrollPane(booksPanel, settings.books);
		leftPanel.add(booksScrollPane);
		
		
		// TODO decide if I want these sliders.
//		lblEdgeLandtowaterRatio = new JLabel("Edge land probability:");
//		lblEdgeLandtowaterRatio
//				.setToolTipText("The probability that a tectonic plate touching the edge of the map will be land rather than ocean.");
//		terrainPanel.add(lblEdgeLandtowaterRatio);
//
//		edgeLandToWaterProbSlider = new JSlider();
//		edgeLandToWaterProbSlider.setValue(70);
//		edgeLandToWaterProbSlider.setPaintTicks(true);
//		edgeLandToWaterProbSlider.setPaintLabels(true);
//		edgeLandToWaterProbSlider.setMinorTickSpacing(25);
//		edgeLandToWaterProbSlider.setMajorTickSpacing(25);
//		{
//			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
//			for (int i = edgeLandToWaterProbSlider.getMinimum(); i < edgeLandToWaterProbSlider.getMaximum()
//					+ 1; i += edgeLandToWaterProbSlider.getMajorTickSpacing())
//			{
//				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
//			}
//			edgeLandToWaterProbSlider.setLabelTable(labelTable);
//		}
//		terrainPanel.add(edgeLandToWaterProbSlider);
//
//		lblCenterLandtowaterRatio = new JLabel("Center land probability:");
//		lblCenterLandtowaterRatio
//				.setToolTipText("The probability that a tectonic plate not touching the edge of the map will be land rather than ocean.");
//		terrainPanel.add(lblCenterLandtowaterRatio);
//
//		centerLandToWaterProbSlider = new JSlider();
//		centerLandToWaterProbSlider.setValue(70);
//		centerLandToWaterProbSlider.setPaintTicks(true);
//		centerLandToWaterProbSlider.setPaintLabels(true);
//		centerLandToWaterProbSlider.setMinorTickSpacing(25);
//		centerLandToWaterProbSlider.setMajorTickSpacing(25);
//		{
//			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
//			for (int i = centerLandToWaterProbSlider.getMinimum(); i < centerLandToWaterProbSlider.getMaximum()
//					+ 1; i += centerLandToWaterProbSlider.getMajorTickSpacing())
//			{
//				labelTable.put(i, new JLabel(Double.toString(i / 100.0)));
//			}
//			centerLandToWaterProbSlider.setLabelTable(labelTable);
//		}
//		terrainPanel.add(centerLandToWaterProbSlider);

		
		// TODO Add Accept button. And when pressed, Call MainWindow.createPlaceholderImage with the message: "Drawing the map..."
	}
	
	private void loadSettingsIntoGUI(MapSettings settings)
	{
		// TODO
	}
	
	private MapSettings getSettingsFromGUI()
	{
		// TODO
		MapSettings resultSettings = settings.deepCopy();
		resultSettings.worldSize = worldSizeSlider.getValue();
		resultSettings.randomSeed = Long.parseLong(randomSeedTextField.getText());
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
}
