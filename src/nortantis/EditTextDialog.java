package nortantis;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;

import javax.swing.JTextField;
import javax.swing.JSeparator;

import java.awt.Component;
import java.io.File;

import javax.swing.Box;

import util.ImageHelper;

public class EditTextDialog extends JDialog
{

	private final JPanel contentPanel;
	private JTextField textField;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		try
		{
			EditTextDialog dialog = new EditTextDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public EditTextDialog()
	{
		setBounds(100, 100, 655, 478);
		BufferedImage image = ImageHelper.read("/home/joseph/Dropbox/Fantasy Map Creator/favorites/map_piece_leaving_middle.png");
		contentPanel = new ImagePanel(image);
		
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
		JScrollPane scrollPane = new JScrollPane(contentPanel);
		getContentPane().add(scrollPane);
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton doneButton = new JButton("Done");
				doneButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
					}
				});
				buttonPane.setLayout(new BorderLayout(0, 0));

				textField = new JTextField();
				buttonPane.add(textField, BorderLayout.WEST);
				textField.setColumns(20);
				doneButton.setActionCommand("OK");
				buttonPane.add(doneButton, BorderLayout.EAST);
				
				getRootPane().setDefaultButton(doneButton);
			}
		}
	}

}
