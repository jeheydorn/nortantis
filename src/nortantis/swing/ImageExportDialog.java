package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
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
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FilenameUtils;

import nortantis.CancelledException;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.editor.ExportAction;
import nortantis.platform.Image;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class ImageExportDialog extends JDialog
{
	private JProgressBar progressBar;
	private boolean isCanceled;
	private JButton cancelButton;
	private JButton exportButton;
	private JSlider resolutionSlider;
	private MapCreator mapCreator;
	private RowHider pathChooserHider;
	private JRadioButton fileRadioButton;
	private JRadioButton openInViewerRadioButton;
	List<String> allowedExtension = Arrays.asList("png", "jpg", "jpeg");
	private ImageExportType type;

	public ImageExportDialog(MainWindow mainWindow, ImageExportType type)
	{
		super(mainWindow, type == ImageExportType.Map ? "Export as Image" : "Export Heightmap", Dialog.ModalityType.APPLICATION_MODAL);
		this.type = type;

		setSize(new Dimension(460, type == ImageExportType.Map ? 293 : 380));
		JPanel contents = new JPanel();
		contents.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		contents.setLayout(new BorderLayout());
		getContentPane().add(contents);

		GridBagOrganizer organizer = new GridBagOrganizer();
		contents.add(organizer.panel, BorderLayout.CENTER);

		if (type == ImageExportType.Heightmap)
		{
			organizer.addLeftAlignedComponent(
					new JLabel("<html>This option exports a map's height data as a 16-bit grayscale image for use in"
							+ " other applications such as creating a videogame world. Note that the heightmap will not"
							+ " reflect changes made by editing brushes, such as adding or removing land or changing mountain placement.</html>"),
					false);
		}

		resolutionSlider = new JSlider();
		resolutionSlider.setPaintLabels(true);
		resolutionSlider.setValue(100);
		resolutionSlider.setSnapToTicks(true);
		resolutionSlider.setPaintTicks(true);
		resolutionSlider.setMinorTickSpacing(25);
		resolutionSlider.setMajorTickSpacing(25);
		resolutionSlider.setMinimum(25);
		resolutionSlider.setMaximum(MapCreator.calcMaximumResolution());
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
		String tooltip = type == ImageExportType.Map
				? "This percentage is multiplied by the size of the map to determine "
						+ "the resolution to draw at. The maximum allowed resolution is determined by the amount of memory on this device."
				: "This percentage is multiplied by the dimensions of the map to determine the resolution to draw at. Higher resolutions"
						+ " will give more detailed terrain.";
		resolutionSlider
				.setValue((int) ((type == ImageExportType.Map ? mainWindow.exportResolution : mainWindow.heightmapExportResolution) * 100));
		organizer.addLabelAndComponent("Resolution:", tooltip, resolutionSlider);

		ActionListener radioButtonListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				pathChooserHider.setVisible(fileRadioButton.isSelected());
			}
		};

		fileRadioButton = new JRadioButton("Save to file");
		fileRadioButton.addActionListener(radioButtonListener);

		openInViewerRadioButton = new JRadioButton("Open with this device's default image viewer");
		openInViewerRadioButton.addActionListener(radioButtonListener);

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(fileRadioButton);
		buttonGroup.add(openInViewerRadioButton);

		organizer.addLabelAndComponentsVertical("Export action:", "Select what to do with the generated image.",
				Arrays.asList(fileRadioButton, openInViewerRadioButton));

		JTextField pathField = new JTextField();
		{
			// Determine the default path to save to.
			try
			{
				String curPath = FileHelper.replaceHomeFolderPlaceholder(
						type == ImageExportType.Map ? mainWindow.imageExportPath : mainWindow.heightmapExportPath);
				if (curPath == null || curPath.isEmpty())
				{
					curPath = mainWindow.getOpenSettingsFilePath() == null
							? Paths.get(FileSystemView.getFileSystemView().getDefaultDirectory().toPath().toString(), "unnamed").toString()
							: mainWindow.getOpenSettingsFilePath().toString();
					String folder = new File(curPath).getParent();
					String fileBaseName = FilenameUtils.getBaseName(curPath);
					if (fileBaseName == null || fileBaseName.isEmpty())
					{
						fileBaseName = type == ImageExportType.Map ? "map" : "heightmap";
					}
					Path fileSavePath = Paths.get(folder, fileBaseName + (type == ImageExportType.Map ? "" : " heightmap") + ".png")
							.toAbsolutePath();
					pathField.setText(fileSavePath.toString());
				}
				else
				{
					pathField.setText(curPath);
				}
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

		pathChooserHider = organizer.addLabelAndComponentsVertical("Export file path:", "",
				Arrays.asList(pathField, Box.createVerticalStrut(5), pathPanel));

		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Exporting...");
		progressBar.setIndeterminate(true);
		progressBar.setVisible(false);
		organizer.addVerticalFillerRow();
		organizer.addLeftAlignedComponent(progressBar, 0, 0, false);

		if (type == ImageExportType.Map)
		{
			if (mainWindow.defaultMapExportAction == ExportAction.OpenInDefaultImageViewer)
			{
				openInViewerRadioButton.setSelected(true);
			}
			else
			{
				fileRadioButton.setSelected(true);
			}
		}
		else
		{
			if (mainWindow.defaultHeightmapExportAction == ExportAction.OpenInDefaultImageViewer)
			{
				openInViewerRadioButton.setSelected(true);
			}
			else
			{
				fileRadioButton.setSelected(true);
			}
		}

		radioButtonListener.actionPerformed(null);

		JPanel bottomButtonsPanel = new JPanel();
		bottomButtonsPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		exportButton = new JButton("<html><u>E</u>xport</html>");
		exportButton.setMnemonic(KeyEvent.VK_E);
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
						if (pathField.getText() == null || pathField.getText().isEmpty())
						{
							JOptionPane.showMessageDialog(getContentPane(), "Export file path is required.", "Error",
									JOptionPane.ERROR_MESSAGE);
							return;
						}

						Path path = Paths.get(pathField.getText());
						exportPath = path.toString();

						String extension = FilenameUtils.getExtension(path.getFileName().toString());
						if (extension.isEmpty())
						{
							exportPath += ".png";
						}
						else if (!allowedExtension.contains(extension.toLowerCase()))
						{
							JOptionPane.showMessageDialog(getContentPane(), "The export file must be a png or jpeg image.", "Error",
									JOptionPane.ERROR_MESSAGE);
							return;
						}

						if (new File(exportPath).isDirectory())
						{
							JOptionPane.showMessageDialog(getContentPane(),
									"There is a directory with the same name as the export file, in the same folder.", "Error",
									JOptionPane.ERROR_MESSAGE);
							return;
						}

						if (!new File(new File(exportPath).getParent()).exists())
						{
							JOptionPane.showMessageDialog(getContentPane(), "The export file folder does not exist.", "Error",
									JOptionPane.ERROR_MESSAGE);
							return;
						}
					}
					catch (InvalidPathException ex)
					{
						JOptionPane.showMessageDialog(getContentPane(), "The export file path is invalid.", "Error",
								JOptionPane.ERROR_MESSAGE);
						return;
					}

					if (type == ImageExportType.Map)
					{
						mainWindow.imageExportPath = FileHelper.replaceHomeFolderWithPlaceholder(exportPath);
					}
					else
					{
						mainWindow.heightmapExportPath = FileHelper.replaceHomeFolderWithPlaceholder(exportPath);
					}
				}

				final String exportPathFinal = exportPath;

				if (type == ImageExportType.Map)
				{
					mainWindow.defaultMapExportAction = exportAction;
				}
				else
				{
					mainWindow.defaultHeightmapExportAction = exportAction;
				}
				exportButton.setEnabled(false);
				resolutionSlider.setEnabled(false);
				pathField.setEnabled(false);
				browseSavePathButton.setEnabled(false);
				fileRadioButton.setEnabled(false);
				openInViewerRadioButton.setEnabled(false);
				mapCreator = new MapCreator();

				// Run the export through doWhenMapIsReadyForInteractions so
				// that we don't risk running out of memory
				// or end up clearing the image cache while a draw is still
				// going.
				mainWindow.updater.dowWhenMapIsNotDrawing(
						() -> exportMapAndCloseDialog(mainWindow, resolutionSlider.getValue() / 100.0, exportAction, exportPathFinal));
			}
		});
		bottomButtonsPanel.add(exportButton);

		cancelButton = new JButton("<html><u>C</u>ancel</html>");
		cancelButton.setMnemonic(KeyEvent.VK_C);
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				isCanceled = true;
				if (mapCreator != null)
				{
					mapCreator.cancel();
				}
				dispose();
			}
		});
		bottomButtonsPanel.add(cancelButton);
		contents.add(bottomButtonsPanel, BorderLayout.SOUTH);

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
		if (type == ImageExportType.Map)
		{
			mainWindow.exportResolution = resolution;
		}
		else
		{
			mainWindow.heightmapExportResolution = resolution;
		}
		final MapSettings settings = mainWindow.getSettingsFromGUI(false).deepCopy();

		SwingWorker<Image, Void> worker = new SwingWorker<Image, Void>()
		{
			@Override
			public Image doInBackground() throws Exception
			{
				Logger.clear();
				ImageCache.clear();

				Image result;
				try
				{
					if (type == ImageExportType.Map)
					{
						result = mapCreator.createMap(settings, null, null);
					}
					else
					{
						Logger.println("Creating heightmap...");
						result = new MapCreator().createHeightMap(settings);
					}
				}
				catch (CancelledException e)
				{
					Logger.println((type == ImageExportType.Map ? "Map" : "Heightmap") + " creation cancelled.");
					return null;
				}

				System.gc();

				if (isCanceled)
				{
					Logger.println((type == ImageExportType.Map ? "Map" : "Heightmap") + " creation cancelled.");
					return null;
				}

				String fileName;
				if (exportAction == ExportAction.OpenInDefaultImageViewer)
				{
					Logger.println("Opening the " + (type == ImageExportType.Map ? "map" : "heightmap")
							+ " in your system's default image editor.");
					fileName = ImageHelper.openImageInSystemDefaultEditor(result, "map_" + settings.randomSeed);
				}
				else
				{
					fileName = pathToSaveTo;
					ImageHelper.write(result, fileName);
				}
				Logger.println("Map written to " + fileName);
				ImageCache.clear();
				return result;
			}

			@Override
			public void done()
			{
				boolean isError = false;
				try
				{
					get();
				}
				catch (Exception ex)
				{
					SwingHelper.handleBackgroundThreadException(ex, getContentPane(), true);
					isError = true;
				}

				if (exportAction == ExportAction.SaveToFile && !isError && !isCanceled)
				{
					progressBar.setVisible(false);
					JOptionPane.showMessageDialog(getContentPane(),
							(type == ImageExportType.Map ? "Map" : "Heightmap") + " successfully exported.", "Success",
							JOptionPane.INFORMATION_MESSAGE);
				}

				dispose();
			}
		};
		worker.execute();
	}

	private String chooseImageFileDestination(Component parent, String filePath)
	{
		String folder = Paths.get(FilenameUtils.getPath(filePath)).toAbsolutePath().toString();
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new File(folder));
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
				return f.isDirectory() || allowedExtension.contains(FilenameUtils.getExtension(f.getName()));
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
