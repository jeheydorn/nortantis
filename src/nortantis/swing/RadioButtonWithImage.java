package nortantis.swing;

import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

@SuppressWarnings("serial")
public class RadioButtonWithImage extends JPanel
{

	private JRadioButton radioButton;
	private UnscaledImagePanel imageDisplay;

	public RadioButtonWithImage(String text, BufferedImage image)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		radioButton = new JRadioButton(text);
		JPanel radioButtonPanel = new JPanel();
		radioButtonPanel.setLayout(new BoxLayout(radioButtonPanel, BoxLayout.X_AXIS));
		radioButtonPanel.add(radioButton);
		radioButtonPanel.add(Box.createHorizontalGlue());
		add(radioButtonPanel);

		imageDisplay = new UnscaledImagePanel();
		JPanel imageDisplayHolder = new JPanel();
		imageDisplayHolder.setLayout(new BoxLayout(imageDisplayHolder, BoxLayout.X_AXIS));
		imageDisplayHolder.add(Box.createHorizontalStrut(17));
		imageDisplayHolder.add(imageDisplay);
		imageDisplayHolder.add(Box.createHorizontalGlue());
		add(imageDisplayHolder);

		imageDisplayHolder.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				radioButton.doClick();
			}
		});

		setImage(image);
	}

	public JRadioButton getRadioButton()
	{
		return radioButton;
	}

	public void addActionListener(ActionListener listener)
	{
		getRadioButton().addActionListener(listener);
	}

	public void removeActionListener(ActionListener listener)
	{
		getRadioButton().removeActionListener(listener);
	}

	public String getText()
	{
		return radioButton.getText();
	}

	public void setImage(BufferedImage image)
	{
		imageDisplay.setImage(image);
		if (image != null)
		{
			imageDisplay.setMinimumSize(new Dimension(image.getWidth(), image.getHeight()));
		}
	}
}