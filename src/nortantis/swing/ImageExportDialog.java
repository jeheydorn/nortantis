package nortantis.swing;

import nortantis.BorderPosition;
import nortantis.CancelledException;
import nortantis.ImageCache;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.editor.ExportAction;
import nortantis.platform.Image;
import nortantis.util.FileHelper;
import nortantis.platform.ImageHelper;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

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
		super(mainWindow, type == ImageExportType.Map ? Translation.get("imageExport.title.map") : Translation.get("imageExport.title.heightmap"), Dialog.ModalityType.APPLICATION_MODAL);
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
			organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("imageExport.heightmapDescription") + "</html>"), false);
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
		String tooltip = Translation.get("imageExport.resolution.help");
		resolutionSlider.setValue((int) ((type == ImageExportType.Map ? mainWindow.exportResolution : mainWindow.heightmapExportResolution) * 100));

		// Compute the base map pixel dimensions (at 100% resolution) to display in the slider label.
		MapSettings exportSettings = mainWindow.getSettingsFromGUI(false);
		final int exportBaseWidth = (exportSettings.rightRotationCount == 1 || exportSettings.rightRotationCount == 3)
				? exportSettings.generatedHeight : exportSettings.generatedWidth;
		final int exportBaseHeight = (exportSettings.rightRotationCount == 1 || exportSettings.rightRotationCount == 3)
				? exportSettings.generatedWidth : exportSettings.generatedHeight;
		final int borderPaddingPerSide = exportSettings.drawBorder && exportSettings.borderPosition == BorderPosition.Outside_map
				? exportSettings.borderWidth : 0;
		SliderWithDisplayedValue resolutionSliderWithDisplay = new SliderWithDisplayedValue(resolutionSlider, value ->
		{
			double resolution = value / 100.0;
			int w = (int) (exportBaseWidth * resolution) + 2 * (int) (borderPaddingPerSide * resolution);
			int h = (int) (exportBaseHeight * resolution) + 2 * (int) (borderPaddingPerSide * resolution);
			return w + " \u00d7 " + h;
		}, null, null);
		resolutionSliderWithDisplay.addToOrganizer(organizer, Translation.get("imageExport.resolution.label"), tooltip);

		ActionListener radioButtonListener = new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				pathChooserHider.setVisible(fileRadioButton.isSelected());
			}
		};

		fileRadioButton = new JRadioButton(Translation.get("imageExport.saveToFile"));
		fileRadioButton.addActionListener(radioButtonListener);

		openInViewerRadioButton = new JRadioButton(Translation.get("imageExport.openInViewer"));
		openInViewerRadioButton.addActionListener(radioButtonListener);

		ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(fileRadioButton);
		buttonGroup.add(openInViewerRadioButton);

		organizer.addLabelAndComponentsVertical(Translation.get("imageExport.exportAction.label"), Translation.get("imageExport.exportAction.help"),
				Arrays.asList(fileRadioButton, openInViewerRadioButton));

		JTextField pathField = new JTextField();
		{
			// Determine the default path to save to.
			try
			{
				String curPath = FileHelper.replaceHomeFolderPlaceholder(type == ImageExportType.Map ? mainWindow.imageExportPath : mainWindow.heightmapExportPath);
				if (curPath == null || curPath.isEmpty())
				{
					curPath = mainWindow.getOpenSettingsFilePath() == null ? Paths.get(FileSystemView.getFileSystemView().getDefaultDirectory().toPath().toString(), "unnamed").toString()
							: mainWindow.getOpenSettingsFilePath().toString();
					String folder = new File(curPath).getParent();
					String fileBaseName = FilenameUtils.getBaseName(curPath);
					if (fileBaseName == null || fileBaseName.isEmpty())
					{
						fileBaseName = type == ImageExportType.Map ? "map" : "heightmap";
					}
					Path fileSavePath = Paths.get(folder, fileBaseName + (type == ImageExportType.Map ? "" : " heightmap") + ".png").toAbsolutePath();
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

		JButton browseSavePathButton = new JButton(Translation.get("theme.browse"));
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

		pathChooserHider = organizer.addLabelAndComponentsVertical(Translation.get("imageExport.exportFilePath.label"), "", Arrays.asList(pathField, Box.createVerticalStrut(5), pathPanel));

		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString(Translation.get("imageExport.exporting"));
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
		exportButton = new JButton(Translation.get("imageExport.export"));
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
							JOptionPane.showMessageDialog(getContentPane(), Translation.get("imageExport.pathRequired"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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
							JOptionPane.showMessageDialog(getContentPane(), Translation.get("imageExport.mustBePngOrJpeg"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
							return;
						}

						if (new File(exportPath).isDirectory())
						{
							JOptionPane.showMessageDialog(getContentPane(), Translation.get("imageExport.directoryConflict"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
							return;
						}

						if (!new File(new File(exportPath).getParent()).exists())
						{
							JOptionPane.showMessageDialog(getContentPane(), Translation.get("imageExport.folderDoesNotExist"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
							return;
						}
					}
					catch (InvalidPathException ex)
					{
						JOptionPane.showMessageDialog(getContentPane(), Translation.get("imageExport.pathInvalid"), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
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
				mainWindow.updater.doWhenMapIsNotDrawing(() -> exportMapAndCloseDialog(mainWindow, resolutionSlider.getValue() / 100.0, exportAction, exportPathFinal));
			}
		});
		bottomButtonsPanel.add(exportButton);

		cancelButton = new JButton(Translation.get("imageExport.cancel"));
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
					Logger.println("Opening the " + (type == ImageExportType.Map ? "map" : "heightmap") + " in your system's default image editor.");
					fileName = ImageHelper.getInstance().openImageInSystemDefaultEditor(result, "map_" + settings.randomSeed);
				}
				else
				{
					fileName = pathToSaveTo;
					ImageHelper.getInstance().write(result, fileName);
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
					SwingHelper.handleException(ex, getContentPane(), true);
					isError = true;
				}

				if (exportAction == ExportAction.SaveToFile && !isError && !isCanceled)
				{
					progressBar.setVisible(false);
					JOptionPane.showMessageDialog(getContentPane(), type == ImageExportType.Map ? Translation.get("imageExport.mapExported") : Translation.get("imageExport.heightmapExported"),
							"Success", JOptionPane.INFORMATION_MESSAGE);
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

		int status = fileChooser.showDialog(parent, Translation.get("imageExport.select"));
		if (status == JFileChooser.APPROVE_OPTION)
		{
			return fileChooser.getSelectedFile().toString();
		}
		return null;
	}
}
