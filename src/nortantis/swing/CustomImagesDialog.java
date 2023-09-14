package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultFocusManager;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;
import org.junit.runner.Computer;

import nortantis.editor.UserPreferences;
import nortantis.util.AssetsPath;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class CustomImagesDialog extends JDialog
{
	private JTextField customImagesFolderField;

	public CustomImagesDialog(MainWindow mainWindow, String fileMenuName, String nameOfMenuOptionToRefreshImages)
	{
		super(mainWindow, "Custom Images Folder", Dialog.ModalityType.APPLICATION_MODAL);
		setSize(new Dimension(800, 610));
		JPanel content = new JPanel();
		add(content);
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		content.setLayout(new BorderLayout());

		String originalCustomImagesPath = UserPreferences.getInstance().customImagesPath;

		int space = 6;

		GridBagOrganizer organizer = new GridBagOrganizer();
		content.add(organizer.panel, BorderLayout.CENTER);
		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>Custom images are an advanced feature. To use them, enter a path to a "
								+ "folder with your images. If the folder is empty, Nortantis will copy its installed images into it as a "
								+ "starting point. After that, it "
								+ "will be up to you to ensure that your custom images folder has the structure Nortantis expects. "
								+ "That structure is: </html>"
				), space, space, false
		);

		int spaceBetweenPaths = 2;
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "borders" + File.separator + "<border type>" + File.separator + "<border images>"), space, spaceBetweenPaths, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "icons" + File.separator + "cities" + File.separator + "<city type>" + File.separator + "<city images>"), spaceBetweenPaths, spaceBetweenPaths, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "icons" + File.separator + "hills" + File.separator + "<hill type>" + File.separator + "<hill images>"), spaceBetweenPaths, spaceBetweenPaths, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "icons" + File.separator + "mountains" + File.separator + "<mountain type>" + File.separator + "<mountain images>"), spaceBetweenPaths,
				spaceBetweenPaths, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "icons" + File.separator + "sand" + File.separator + "<sand type>" + File.separator + "<sand images>"), spaceBetweenPaths, spaceBetweenPaths, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<custom images folder>" + File.separator + "icons" + File.separator + "trees" + File.separator + "<tree type>" + File.separator + "<tree images>"), spaceBetweenPaths, spaceBetweenPaths, false
		);

		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>The names above in angle brackets are folder and file names"
								+ " that you can configure. Folder names without angle brackets, however, must be exactly as described above or else Nortantis won't be able to find the image is in those folders. Images must be either PNG or"
								+ " JPG format. PNG is recommended because it" + " supports transparency and isn't lossy.</html>"
				), space, space, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>Valid border image names are 'upper_left_corner', 'upper_right_corner', "
								+ "'lower_left_corner', 'lower_right_corner', 'top_edge', 'bottom_edge', 'left_edge', 'right_edge'. At least one corner and"
								+ " one edge must be given.</html>"
				), space, space, false
		);

		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>Regarding tree images, although the &lt;tree type&gt; folder can have any name,"
								+ " if you want new maps to use your tree type, then you must use 'cacti', 'deciduous', and 'pine'.</html>"
				), space, space, false
		);

		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>After making changes to custom images, to get Nortantis to see those changes you"
								+ " can either close and re-open the program or use " + fileMenuName + " -> "
								+ nameOfMenuOptionToRefreshImages + ".</html>"
				), space, space, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel(
						"<html>Using a custom images folder causes Nortantis to use your images rather than its own,"
								+ " even if you install a new version of Nortantis that might include new images or fixes to existing images."
								+ " To update your custom"
								+ " images folder, you can change the path below to a new folder to cause Nortantis to add it's new images to"
								+ " that folder, then hand-merge the two folders." + "</html>"
				), space, space, false
		);
		organizer.addLeftAlignedComponent(
				new JLabel("<html>To revert back to using Nortantis's installed images, clear out the" + " field below.</html>"), space, 10,
				false
		);

		JButton openButton = new JButton("Open");

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
		customImagesFolderField.setText(UserPreferences.getInstance().customImagesPath);
		JButton browseButton = new JButton("Browse");
		browseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customImagesFolderField.getText());
				if (!folder.exists())
				{
					folder = FileSystemView.getFileSystemView().getHomeDirectory();
				}
				JFileChooser folderChooser = new JFileChooser(folder);
				folderChooser.setDialogTitle("Select a Folder");
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
					JOptionPane.showMessageDialog(
							null, "Unable to open " + folder.getAbsolutePath() + ". The folder does not exist.", "Error",
							JOptionPane.ERROR_MESSAGE
					);
					return;
				}
				if (!folder.isDirectory())
				{
					JOptionPane.showMessageDialog(
							null, "Unable to open " + folder.getAbsolutePath() + ". That path is a file, not a folder.", "Error",
							JOptionPane.ERROR_MESSAGE
					);
					return;
				}

				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().open(folder);
					}
					catch (IOException ex)
					{
						ex.printStackTrace();
						Logger.printError("Error while trying to open custom images folder: ", ex);
					}
				}
			}
		});
		openButton.setEnabled(!customImagesFolderField.getText().isEmpty());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(new JLabel("Custom images folder:"));
		panel.add(Box.createHorizontalStrut(10));
		panel.add(customImagesFolderField);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(browseButton);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(openButton);

		organizer.addLeftAlignedComponent(panel, false);

		organizer.addVerticalFillerRow();

		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton okayButton = new JButton("<html><u>O</u>K</html>");
		okayButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				boolean isChanged = !Objects.equals(customImagesFolderField.getText(), originalCustomImagesPath);
				UserPreferences.getInstance().customImagesPath = customImagesFolderField.getText();
				if (mergeInstalledImagesIntoCustomFolderIfEmpty(customImagesFolderField.getText()))
				{
					JOptionPane.showMessageDialog(null, "Installed images successfully copied into " 
							+ Paths.get(customImagesFolderField.getText()).toAbsolutePath(),
							"Success", JOptionPane.INFORMATION_MESSAGE);
				}
				
				// If the custom images folder changed, then refresh images and redraw the map.
				if (isChanged)
				{
					mainWindow.handleImagesRefresh();
				}

				dispose();
			}
		});
		bottomPanel.add(okayButton);

		JButton cancelButton = new JButton("<html><u>C</u>ancel</html>");
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		bottomPanel.add(cancelButton);

		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager()
		{
			public boolean dispatchKeyEvent(KeyEvent e)
			{
				if ((e.getKeyCode() == KeyEvent.VK_C) && e.isAltDown())
				{
					cancelButton.doClick();
				}
				else if ((e.getKeyCode() == KeyEvent.VK_O) && e.isAltDown())
				{
					okayButton.doClick();
				}
				return false;
			}
		};
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
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
			JOptionPane.showMessageDialog(null, "Unable to copy installed images into " + folder.getAbsolutePath() + ". The folder does not exist.",
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}				
		else if (!folder.isDirectory())
		{
			JOptionPane.showMessageDialog(null, "Unable to copy installed images into " + folder.getAbsolutePath() + ". That path is a file, not a folder.",
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		boolean isFolderEmpty;
		try
		{
			isFolderEmpty = !Files.newDirectoryStream(folder.toPath()).iterator().hasNext();
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(null, "Error while checking if " + folder.getAbsolutePath() + " is empty: " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		try
		{
			if (isFolderEmpty)
			{
				FileUtils.copyDirectoryToDirectory(Paths.get(AssetsPath.getInstallPath(), "borders").toFile(), folder);
				FileUtils.copyDirectoryToDirectory(Paths.get(AssetsPath.getInstallPath(), "icons").toFile(), folder);
				return true;
			}
		}
		catch(IOException ex)
		{
			JOptionPane.showMessageDialog(null, "Error while copying installed images into " + folder.getAbsolutePath() + ": " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}

		return false;
	}
}
