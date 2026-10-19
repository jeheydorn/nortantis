package nortantis.swing;

import nortantis.MapSettings;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("serial")
public class CustomArtPackDialog extends JDialog
{
	private JTextField customArtPackFolderField;

	public CustomArtPackDialog(MainWindow mainWindow, String currentCustomArtPackPath, Consumer<String> storeResult)
	{
		super(mainWindow, Translation.get("customArtPack.title"), Dialog.ModalityType.APPLICATION_MODAL);
		setSize(OSHelper.isWindows() ? new Dimension(860, 770) : new Dimension(1110, 840));
		JPanel content = new JPanel();
		add(content);
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		content.setLayout(new BorderLayout());

		int space = 6;

		GridBagOrganizer organizer = new GridBagOrganizer();
		JScrollPane helpScrollPane = new JScrollPane(organizer.panel);
		helpScrollPane.setBorder(BorderFactory.createEmptyBorder());
		helpScrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		content.add(helpScrollPane, BorderLayout.CENTER);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.description") + "</html>"), space, space, false);

		int spaceBetweenPaths = 2;
		String cif = Translation.get("customArtPack.placeholder.customArtPackFolder");
		organizer.addLeftAlignedComponent(new JLabel(cif + File.separator + "background textures" + File.separator + Translation.get("customArtPack.placeholder.backgroundTextureImages")), space,
				spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(
				cif + File.separator + "borders" + File.separator + Translation.get("customArtPack.placeholder.borderType") + File.separator + Translation.get("customArtPack.placeholder.borderImages")),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(
				cif + File.separator + "cities" + File.separator + Translation.get("customArtPack.placeholder.cityType") + File.separator + Translation.get("customArtPack.placeholder.cityImages")),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(cif + File.separator + "decorations" + File.separator + Translation.get("customArtPack.placeholder.decorationType") + File.separator
				+ Translation.get("customArtPack.placeholder.decorationImages")), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(cif + File.separator + "fonts" + File.separator + Translation.get("customArtPack.placeholder.fontFamily") + File.separator
				+ Translation.get("customArtPack.placeholder.fontFiles")), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(
				cif + File.separator + "hills" + File.separator + Translation.get("customArtPack.placeholder.hillType") + File.separator + Translation.get("customArtPack.placeholder.hillImages")),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(cif + File.separator + "mountains" + File.separator + Translation.get("customArtPack.placeholder.mountainType") + File.separator
				+ Translation.get("customArtPack.placeholder.mountainImages")), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(
				cif + File.separator + "sand" + File.separator + Translation.get("customArtPack.placeholder.duneType") + File.separator + Translation.get("customArtPack.placeholder.sandDuneImages")),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(cif + File.separator + Assets.themesFolderName + File.separator + Translation.get("customArtPack.placeholder.themeFiles")),
				spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel(
				cif + File.separator + "trees" + File.separator + Translation.get("customArtPack.placeholder.treeType") + File.separator + Translation.get("customArtPack.placeholder.treeImages")),
				spaceBetweenPaths, spaceBetweenPaths, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.folderStructureNote") + "</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.iconSizing") + "</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.borderImageNames") + "</html>"), space, space, false);

		// The tree-type placeholder is <tree type>, which Swing's HTML parser would drop as an unknown tag. The placeholder strings are
		// plain text because the folder-structure listing above renders them in plain labels, so escape here rather than in the bundles.
		organizer.addLeftAlignedComponent(
				new JLabel("<html>" + Translation.get("customArtPack.treeTypes", SwingHelper.escapeHtml(Translation.get("customArtPack.placeholder.treeType"))) + "</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.hillMountainPairing") + "</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.fontsAndThemes") + "</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.refreshInstructions", mainWindow.getFileMenuName(), mainWindow.getRefreshImagesMenuName()) + "</html>"),
				space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>" + Translation.get("customArtPack.revertInstructions") + "</html>"), space, 10, false);

		JButton openButton = new JButton(Translation.get("customArtPack.open"));

		customArtPackFolderField = new JTextField();
		customArtPackFolderField.getDocument().addDocumentListener(new DocumentListener()
		{

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customArtPackFolderField.getText().isEmpty());
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customArtPackFolderField.getText().isEmpty());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customArtPackFolderField.getText().isEmpty());
			}
		});
		customArtPackFolderField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				String trimmed = customArtPackFolderField.getText().trim();
				if (!trimmed.equals(customArtPackFolderField.getText()))
				{
					customArtPackFolderField.setText(trimmed);
				}
			}
		});
		customArtPackFolderField.setText(FileHelper.replaceHomeFolderPlaceholder(currentCustomArtPackPath));
		JButton browseButton = new JButton(Translation.get("customArtPack.browse"));
		browseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customArtPackFolderField.getText());
				if (!folder.exists())
				{
					folder = FileSystemView.getFileSystemView().getDefaultDirectory();
				}
				JFileChooser folderChooser = new JFileChooser(folder);
				folderChooser.setDialogTitle(Translation.get("customArtPack.selectFolder"));
				folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

				int returnValue = folderChooser.showOpenDialog(null);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					customArtPackFolderField.setText(folderChooser.getSelectedFile().toString());
				}
			}
		});

		openButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customArtPackFolderField.getText());
				if (!folder.exists())
				{
					SwingHelper.showMessageDialog(null, Translation.get("customArtPack.folderDoesNotExist", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (!folder.isDirectory())
				{
					SwingHelper.showMessageDialog(null, Translation.get("customArtPack.pathIsFile", folder.getAbsolutePath()), Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}

				SwingHelper.openFileExplorerTo(folder);
			}
		});
		openButton.setEnabled(!customArtPackFolderField.getText().isEmpty());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(new JLabel(Translation.get("customArtPack.folderLabel")));
		panel.add(Box.createHorizontalStrut(10));
		panel.add(customArtPackFolderField);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(browseButton);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(openButton);
		panel.setBorder(BorderFactory.createEmptyBorder(space, space, 0, space));

		organizer.addVerticalFillerRow();

		// The folder field and buttons stay below the scrolling help text, so they are visible however long the text is.
		JPanel southPanel = new JPanel(new BorderLayout());
		content.add(southPanel, BorderLayout.SOUTH);
		southPanel.add(panel, BorderLayout.NORTH);
		JPanel bottomPanel = new JPanel();
		southPanel.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton okayButton = new JButton(Translation.get("customArtPack.ok"));
		SwingHelper.bindAltMnemonic(okayButton, KeyEvent.VK_O);
		okayButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				boolean pathChanged = !Objects.equals(customArtPackFolderField.getText(), FileHelper.replaceHomeFolderPlaceholder(currentCustomArtPackPath));
				boolean folderContentsChanged = false;
				if (MapSettings.isOldCustomArtPackFolderStructure(customArtPackFolderField.getText()))
				{
					try
					{
						MapSettings.convertOldCustomArtPackFolder(customArtPackFolderField.getText());
						folderContentsChanged = true;

						SwingHelper.showMessageDialog(null, Translation.get("customArtPack.folderConvertedMessage"), Translation.get("customArtPack.folderConverted"), JOptionPane.INFORMATION_MESSAGE);
					}
					catch (IOException ex)
					{
						String errorMessage = Translation.get("customArtPack.errorRestructuring", customArtPackFolderField.getText(), ex.getMessage());
						Logger.printError(errorMessage, ex);
						SwingHelper.showMessageDialog(null, errorMessage, Translation.get("common.error"), JOptionPane.ERROR_MESSAGE);
					}
				}

				// Store the value, refresh images, and redraw the map when either the folder selected changed or the images inside it moved.
				// Converting an old folder structure changes what is on disk without changing the path, and the map would otherwise keep
				// drawing from what was cached before the move.
				if (pathChanged || folderContentsChanged)
				{
					storeResult.accept(FileHelper.replaceHomeFolderWithPlaceholder(customArtPackFolderField.getText()));
				}

				dispose();
			}
		});
		bottomPanel.add(okayButton);

		JButton cancelButton = new JButton(Translation.get("customArtPack.cancel"));
		SwingHelper.bindAltMnemonic(cancelButton, KeyEvent.VK_C);
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
}
