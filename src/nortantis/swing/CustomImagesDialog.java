package nortantis.swing;

import nortantis.IconType;
import nortantis.MapSettings;
import nortantis.editor.UserPreferences;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.swing.translation.Translation;
import nortantis.util.Logger;
import nortantis.util.OSHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("serial")
public class CustomImagesDialog extends JDialog
{
	private JTextField customImagesFolderField;

	public CustomImagesDialog(MainWindow mainWindow, String currentCustomImagesPath, Consumer<String> storeResult)
	{
		super(mainWindow, Translation.get("customImages.title"), Dialog.ModalityType.APPLICATION_MODAL);
		setSize(OSHelper.isWindows() ? new Dimension(860, 750) : new Dimension(1020, 840));
		JPanel content = new JPanel();
		add(content);
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		content.setLayout(new BorderLayout());

		int space = 6;

		GridBagOrganizer organizer = new GridBagOrganizer();
		content.add(organizer.panel, BorderLayout.CENTER);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.description") + "</html>"), space, space, false);

		int spaceBetweenPaths = 2;
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "background textures" + File.separator + "<background texture images>"), space, spaceBetweenPaths,
				false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "borders" + File.separator + "<border type>" + File.separator + "<border images>"), space,
				spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "cities" + File.separator + "<city type>" + File.separator + "<city images>"), spaceBetweenPaths,
				spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "decorations" + File.separator + "<decoration type>" + File.separator + "<decoration images>"),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "hills" + File.separator + "<hill type>" + File.separator + "<hill images>"), spaceBetweenPaths,
				spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "mountains" + File.separator + "<mountain type>" + File.separator + "<mountain images>"),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "sand" + File.separator + "<dune type>" + File.separator + "<sand dune images>"), spaceBetweenPaths,
				spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "trees" + File.separator + "<tree type>" + File.separator + "<tree images>"), spaceBetweenPaths,
				spaceBetweenPaths, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.folderStructureNote") + "</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.iconSizing") + "</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.borderImageNames") + "</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.treeTypes") + "</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.hillMountainPairing") + "</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.refreshInstructions", mainWindow.getFileMenuName(), mainWindow.getRefreshImagesMenuName()) + "</html>"),
				space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customImages.revertInstructions") + "</html>"), space, 10, false);

		JButton openButton = new JButton(Translation.get("customImages.open"));

		customImagesFolderField = new JTextField();
		customImagesFolderField.getDocument().addDocumentListener(new DocumentListener()
		{

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}
		});
		customImagesFolderField.setText(FileHelper.replaceHomeFolderPlaceholder(currentCustomImagesPath));
		JButton browseButton = new JButton(Translation.get("customImages.browse"));
		browseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customImagesFolderField.getText());
				if (!folder.exists())
				{
					folder = FileSystemView.getFileSystemView().getDefaultDirectory();
				}
				JFileChooser folderChooser = new JFileChooser(folder);
				folderChooser.setDialogTitle(Translation.get("customImages.selectFolder"));
				folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

				int returnValue = folderChooser.showOpenDialog(null);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					customImagesFolderField.setText(folderChooser.getSelectedFile().toString());
				}
			}
		});

		openButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customImagesFolderField.getText());
				if (!folder.exists())
				{
					JOptionPane.showMessageDialog(null, Translation.get("customImages.folderDoesNotExist", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (!folder.isDirectory())
				{
					JOptionPane.showMessageDialog(null, Translation.get("customImages.pathIsFile", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}

				OSHelper.openFileExplorerTo(folder);
			}
		});
		openButton.setEnabled(!customImagesFolderField.getText().isEmpty());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(new JLabel(Translation.get("customImages.folderLabel")));
		panel.add(Box.createHorizontalStrut(10));
		panel.add(customImagesFolderField);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(browseButton);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(openButton);

		organizer.addLeftAlignedComponent(panel, false);

		JCheckBox makeDefaultCheckbox = new JCheckBox(Translation.get("customImages.makeDefault"));
		organizer.addLeftAlignedComponent(makeDefaultCheckbox);

		organizer.addVerticalFillerRow();

		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton okayButton = new JButton(Translation.get("customImages.ok"));
		okayButton.setMnemonic(KeyEvent.VK_O);
		okayButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				boolean isChanged = !Objects.equals(customImagesFolderField.getText(), FileHelper.replaceHomeFolderPlaceholder(currentCustomImagesPath));
				if (mergeInstalledImagesIntoCustomFolderIfEmpty(customImagesFolderField.getText()))
				{
					JOptionPane.showMessageDialog(null, Translation.get("customImages.imagesCopied", Paths.get(customImagesFolderField.getText()).toAbsolutePath()), Translation.get("common.success"),
							JOptionPane.INFORMATION_MESSAGE);
				}
				else if (MapSettings.isOldCustomImagesFolderStructure(customImagesFolderField.getText()))
				{
					try
					{
						MapSettings.convertOldCustomImagesFolder(customImagesFolderField.getText());

						JOptionPane.showMessageDialog(null, Translation.get("customImages.folderConvertedMessage"), Translation.get("customImages.folderConverted"), JOptionPane.INFORMATION_MESSAGE);
					}
					catch (IOException ex)
					{
						String errorMessage = Translation.get("customImages.errorRestructuring", customImagesFolderField.getText(), ex.getMessage());
						Logger.printError(errorMessage, ex);
						JOptionPane.showMessageDialog(null, errorMessage, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					}
				}

				// If the custom images folder changed, then store the value, refresh images, and redraw the map.
				if (isChanged)
				{
					storeResult.accept(FileHelper.replaceHomeFolderWithPlaceholder(customImagesFolderField.getText()));
				}

				if (makeDefaultCheckbox.isSelected())
				{
					UserPreferences.getInstance().defaultCustomImagesPath = FileHelper.replaceHomeFolderWithPlaceholder(customImagesFolderField.getText());
				}

				dispose();
			}
		});
		bottomPanel.add(okayButton);

		JButton cancelButton = new JButton(Translation.get("customImages.cancel"));
		cancelButton.setMnemonic(KeyEvent.VK_C);
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		bottomPanel.add(cancelButton);
	}

	private boolean mergeInstalledImagesIntoCustomFolderIfEmpty(String customImagesFolder)
	{
		if (customImagesFolder == null || customImagesFolder.isEmpty())
		{
			return false;
		}

		File folder = new File(customImagesFolder);
		if (!folder.exists())
		{
			JOptionPane.showMessageDialog(null, Translation.get("customImages.copyFolderDoesNotExist", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		else if (!folder.isDirectory())
		{
			JOptionPane.showMessageDialog(null, Translation.get("customImages.copyPathIsFile", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		boolean isFolderEmpty;
		try
		{
			isFolderEmpty = !Files.newDirectoryStream(folder.toPath()).iterator().hasNext();
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(null, Translation.get("customImages.errorCheckingEmpty", folder.getAbsolutePath(), ex.getMessage()), Translation.get("common.error"),
					JOptionPane.ERROR_MESSAGE);
			return false;
		}

		try
		{
			if (isFolderEmpty)
			{
				Assets.copyDirectoryToDirectory(Paths.get(Assets.getInstalledArtPackPath(), "background textures"), folder.toPath());
				Assets.copyDirectoryToDirectory(Paths.get(Assets.getInstalledArtPackPath(), "borders"), folder.toPath());
				for (IconType type : IconType.values())
				{
					Assets.copyDirectoryToDirectory(Paths.get(Assets.getInstalledArtPackPath(), type.toString()), folder.toPath());
				}
				return true;
			}
		}
		catch (IOException ex)
		{
			String message = Translation.get("customImages.errorCopyingImages", folder.getAbsolutePath(), ex.getMessage());
			JOptionPane.showMessageDialog(this, message, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
			Logger.printError(message, ex);
		}

		return false;
	}
}
