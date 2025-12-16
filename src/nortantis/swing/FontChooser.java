package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class FontChooser
{
	private final JLabel fontDisplay = new JLabel("");
	private final JPanel displayHolder = new JPanel();
	final JButton chooseButton;
	private Font font;
	private final int maxFontDisplaySize;
	private final int height;
	private final Runnable okAction;
	private final String labelText;

	public FontChooser(String labelText, int height, int maxFontSize, Runnable okAction)
	{
		this.height = height;
		this.maxFontDisplaySize = maxFontSize;
		this.okAction = okAction;
		this.labelText = labelText;
		chooseButton = new JButton("Choose");
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer)
	{
		final int spaceUnderFontDisplays = 4;
		displayHolder.setLayout(new BorderLayout());
		displayHolder.add(fontDisplay);
		displayHolder.setPreferredSize(new Dimension(displayHolder.getPreferredSize().width, height));

		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(organizer.panel, fontDisplay, okAction);
			}
		});
		JPanel chooseButtonHolder = new JPanel();
		chooseButtonHolder.setLayout(new BoxLayout(chooseButtonHolder, BoxLayout.X_AXIS));
		chooseButtonHolder.add(chooseButton);
		chooseButtonHolder.add(Box.createHorizontalGlue());
		RowHider hider = organizer.addLabelAndComponentsVertical(labelText, "",
				Arrays.asList(displayHolder, Box.createVerticalStrut(spaceUnderFontDisplays), chooseButtonHolder));
		return hider;
	}

	private void runFontChooser(JComponent parent, JLabel fontDisplay, Runnable okAction)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setSelectedFont(font);
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			font = fontChooser.getSelectedFont();
			Font displayFont = font.deriveFont(font.getStyle(), (float) (Math.min(font.getSize(), maxFontDisplaySize)));
			fontDisplay.setFont(displayFont);
			fontDisplay.setText(font.getFontName());
			okAction.run();
		}
	}

	public Font getFont()
	{
		return font;
	}

	public void setFont(Font font)
	{
		this.font = font;
		Font displayFont = font.deriveFont(font.getStyle(), (float) (Math.min(font.getSize(), maxFontDisplaySize)));
		fontDisplay.setFont(displayFont);
		fontDisplay.setText(font.getFontName());
	}

}
