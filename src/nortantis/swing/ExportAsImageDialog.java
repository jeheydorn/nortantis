package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.io.FilenameUtils;

import nortantis.CancelledException;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.editor.ExportAction;
import nortantis.editor.UserPreferences;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class ExportAsImageDialog extends JDialog
{
	private JProgressBar progressBar;
	boolean isCanceled;
	private JButton cancelButton;
	private JButton exportButton;
	private JSlider resolutionSlider;
	private MapCreator mapCreator;
	private RowHider pathChooserHider;
	private JRadioButton fileRadioButton;
	private JRadioButton openInViewerRadioButton;

	public ExportAsImageDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Export as Image", Dialog.ModalityType.APPLICATION_MODAL);
		
		setSize(new Dimension(450, 290));
		getContentPane().setLayout(new BorderLayout());
		
		GridBagOrganizer organizer = new GridBagOrganizer();
		getContentPane().add(organizer.panel, BorderLayout.CENTER);
		
		resolutionSlider = new JSlider();
		resolutionSlider.setPaintLabels(true);
		resolutionSlider.setValue(100);
		resolutionSlider.setSnapToTicks(true);
		resolutionSlider.setPaintTicks(true);
		resolutionSlider.setMinorTickSpacing(25);
		resolutionSlider.setMajorTickSpacing(25);
		resolutionSlider.setMinimum(25);
		resolutionSlider.setMaximum(calcMaximumResolution());
		int labelFrequency = resolutionSlider.getMaximum() < 300 ? 50 : 100;
		{
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			for (int i = resolutionSlider.getMinimum(); i < resolutionSlider.getMaximum() + 1; i += resolutionSlider.getMinorTickSpacing())
			{
				if (i % labelFrequency == 0)
				{
					labelTable.put(i, new JLabel(i + "%"));
				}
			}
			resolutionSlider.setLabelTable(labelTable);
		}
		resolutionSlider.setValue((int)(mainWindow.getResolutionToSave() * 100));
		organizer.addLabelAndComponentToPanel("Resolution:", "This percentage is multiplied by the size of the map to determine "
				+ "the resolution to draw at. The maximum allowed resolution is determined by the amount of memory on this device.", 
				resolutionSlider);
		
		
		ActionListener radioButtonListener = new ActionListener()
		{
			
			@Override
			public void actionPerformed(ActionEvent e)
			{
				pathChooserHider.setVisible(fileRadioButton.isSelected());
			}
		};
		
		// TODO Store the folder path without the file name in user settings. Default the file name to the nort file name.
		
		fileRadioButton = new JRadioButton("Save to file");
		fileRadioButton.addActionListener(radioButtonListener);

		openInViewerRadioButton = new JRadioButton("Open with this device's default image viewer");
		openInViewerRadioButton.addActionListener(radioButtonListener);

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(fileRadioButton);
		buttonGroup.add(openInViewerRadioButton);
		
		organizer.addLabelAndComponentsToPanelVertical("Export action:", "Select what to do with the generated image.", 
				Arrays.asList(fileRadioButton, openInViewerRadioButton));

		
		JTextField pathField = new JTextField();
		{
			// Determine the default path to save to.
			try
			{
				String curPath = mainWindow.imageExportPath;
				if (curPath == null || curPath.isEmpty())
				{
					curPath = mainWindow.getOpenSettingsFilePath() == null ? Paths.get("~").toAbsolutePath().toString() 
							: mainWindow.getOpenSettingsFilePath().toString();
				}
						
				String folder = new File(curPath).getParent();
				Path fileSavePath = Paths.get(folder, FilenameUtils.getBaseName(curPath) + ".png").toAbsolutePath();
				pathField.setText(fileSavePath.toString());
			}
			catch (Exception ex)
			{
				
			}
		}
		

		JButton browseSavePathButton = new JButton("Browse");
		browseSavePathButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String filename = chooseImageFileDestination(getContentPane(), Paths.get(pathField.getText()).toAbsolutePath().toString());
				if (filename != null)
				{
					pathField.setText(filename);
				}
			}
		});
		
		
		JPanel pathPanel = new JPanel();
		pathPanel.setLayout(new BoxLayout(pathPanel, BoxLayout.X_AXIS));
		pathPanel.add(browseSavePathButton);
		pathPanel.add(Box.createHorizontalGlue());

		pathChooserHider = organizer.addLabelAndComponentsToPanelVertical("Export file path:", "", 
				Arrays.asList(pathField, Box.createVerticalStrut(5), pathPanel));

		
		
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Exporting...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		organizer.addVerticalFillerRow();
		organizer.addLeftAlignedComponent(progressBar, 0, 0, false);
		
		
		if (UserPreferences.getInstance().defaultExportAction == ExportAction.OpenInDefaultImageViewer)
		{
			openInViewerRadioButton.setSelected(true);
		}
		else
		{
			fileRadioButton.setSelected(true);
		}
		radioButtonListener.actionPerformed(null);

		
		JPanel bottomButtonsPanel = new JPanel();														
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		exportButton = new JButton("<html><u>E</u>xport</html>");
		exportButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				ExportAction exportAction = fileRadioButton.isSelected() ? ExportAction.SaveToFile : ExportAction.OpenInDefaultImageViewer;
				String exportPath = null;

				if (exportAction == ExportAction.SaveToFile)
				{
					// Validate pathField's text
					try
					{
						Path path = Paths.get(pathField.getText());
						exportPath = path.toString();
						
						List<String> allowedExtension = Arrays.asList("png", "jpg", "jpeg");
						String extension = FilenameUtils.getExtension(path.getFileName().toString());
						if (extension.isEmpty())
						{
							exportPath += ".png";
						}
						else if (!allowedExtension.contains(extension.toLowerCase()))
						{
							JOptionPane.showMessageDialog(getContentPane(), "The export file must be a png or jpeg image.", 
									"Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
					}
					catch (InvalidPathException ex) 
					{
						JOptionPane.showMessageDialog(getContentPane(), "The export file path is invalid.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
			        }
					
					mainWindow.imageExportPath = exportPath;
				}
				
				final String exportPathFinal = exportPath;

				UserPreferences.getInstance().defaultExportAction = exportAction;
				exportButton.setEnabled(false);
				resolutionSlider.setEnabled(false);
				pathField.setEnabled(false);
				browseSavePathButton.setEnabled(false);
				fileRadioButton.setEnabled(false);
				openInViewerRadioButton.setEnabled(false);
				mapCreator = new MapCreator();

				// Run the export through doWhenMapIsReadyForInteractions so that we don't risk running out of memory
				// or end up clearing the image cache while a draw is still going.
				mainWindow.updater.doWhenMapIsReadyForInteractions(() -> exportMapAndCloseDialog(mainWindow, resolutionSlider.getValue() / 100.0,
						exportAction, exportPathFinal));
			}
		});
		bottomButtonsPanel.add(exportButton);

		cancelButton = new JButton("<html><u>C</u>ancel</html>");
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (mapCreator != null)
				{
					mapCreator.cancel();
				}
				dispose();
			}
		});
		bottomButtonsPanel.add(cancelButton);
		getContentPane().add(bottomButtonsPanel, BorderLayout.SOUTH);
		
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_E) && e.isAltDown())
				{
					exportButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_C) && e.isAltDown())
				{
					cancelButton.doClick();
				}
			return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
		
		addWindowListener(new WindowListener()
		{
			
			@Override
			public void windowOpened(WindowEvent e)
			{
			}
			
			@Override
			public void windowIconified(WindowEvent e)
			{
			}
			
			@Override
			public void windowDeiconified(WindowEvent e)
			{
			}
			
			@Override
			public void windowDeactivated(WindowEvent e)
			{
			}
			
			@Override
			public void windowClosing(WindowEvent e)
			{
				if (mapCreator != null)
				{
					mapCreator.cancel();
				}
			}
			
			@Override
			public void windowClosed(WindowEvent e)
			{
			}
			
			@Override
			public void windowActivated(WindowEvent e)
			{
			}
		});
	}
	
	private void exportMapAndCloseDialog(MainWindow mainWindow, double resolution, ExportAction exportAction, String pathToSaveTo)
	{		
		progressBar.setVisible(true);
		mainWindow.setResolutionToSave(resolution);
		final MapSettings settings = mainWindow.getSettingsFromGUI(false).deepCopy();
				

		SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>()
		{
			@Override
			public BufferedImage doInBackground() throws Exception
			{
				Logger.clear();
				ImageCache.getInstance().clear();

				BufferedImage map;
				try
				{
					map = mapCreator.createMap(settings, null, null);
				}
				catch (CancelledException e)
				{
					Logger.println("Map creation cancelled.");
					return null;
				}
				
				System.gc();

				String fileName;
				if (exportAction == ExportAction.OpenInDefaultImageViewer)
				{
					Logger.println("Opening the map in your system's default image editor.");
					fileName = ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);
				}
				else
				{
					fileName = pathToSaveTo;
					ImageHelper.write(map, fileName);
				}
				Logger.println("Map written to " + fileName);
				ImageCache.getInstance().clear();
				return map;
			}

			@Override
			public void done()
			{
				try
				{
					get();
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, true);
				}
				
				dispose();
			}
		};
		worker.execute();
	}
	
	private int calcMaximumResolution()
	{
		long maxBytes = Runtime.getRuntime().maxMemory();
		// The required memory is quadratic in the resolution used.
		// To generate a map at resolution 225 takes 7GB, so 7×1024^3÷(225^2)
		// = 148468.
		int maxResolution = (int) Math.sqrt(maxBytes / 148468L);

		// The FFT-based code will create arrays in powers of 2.
		int nextPowerOf2 = ImageHelper.getPowerOf2EqualOrLargerThan(maxResolution / 100.0);
		int resolutionAtNextPowerOf2 = nextPowerOf2 * 100;
		// Average with the original prediction because not all code is
		// FFT-based.
		maxResolution = (maxResolution + resolutionAtNextPowerOf2) / 2;

		if (maxResolution > 500)
		{
			// This is in case Runtime.maxMemory returns Long's max value, which
			// it says it will if it fails.
			return 1000;
		}
		if (maxResolution < 100)
		{
			return 100;
		}
		// The resolution slider uses multiples of 25.
		maxResolution -= maxResolution % 25;
		return maxResolution;
	}
	
	static String chooseImageFileDestination(Component parent, String filePath)
	{
		String folder = Paths.get(FilenameUtils.getPath(filePath)).toAbsolutePath().toString();
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory( new File(folder));
		fileChooser.setFileFilter(new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return null;
			}

			@Override
			public boolean accept(File f)
			{
				return f.isDirectory() || f.getName().endsWith(".png");
			}
		});
		
		if (filePath != null && !FilenameUtils.getName(filePath).equals(""))
		{
			fileChooser.setSelectedFile(new File(filePath));
		}
		
		int status = fileChooser.showDialog(parent, "Select");
		if (status == JFileChooser.APPROVE_OPTION)
		{
			return fileChooser.getSelectedFile().toString();
		}
		return null;
	}
}
