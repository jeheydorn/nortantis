package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nortantis.editor.UserPreferences;
import nortantis.platform.awt.AwtFactory;

@SuppressWarnings("serial")
public class CollapsiblePanel extends JPanel
{
	private JButton toggleButton;
	private String namespace;
	private String name;
	private JPanel contentPanel;
	private boolean isCollapsed;

	/**
	 * Create a CollapsiblePanel with the given name and content.
	 * 
	 * @param namespace
	 *            A unique identifier to use when storing the name in user preferences to store the collapsed state. Name + namespace must
	 *            be globally unique.
	 * @param name
	 *            Name to display
	 * @param contentPanel
	 *            Content to display when expanded
	 */
	public CollapsiblePanel(String namespace, String name, JPanel contentPanel)
	{
		this.namespace = namespace;
		this.name = name;
		this.contentPanel = contentPanel;
		setLayout(new BorderLayout());

		// Create the button with an arrow icon (you can customize this)
		toggleButton = new JButton(); // Downward arrow initially
		isCollapsed = UserPreferences.getInstance().collapsedPanels.contains(getNameKey());
		toggleButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				toggleCollapsed();
			}
		});
		toggleButton.setBorder(BorderFactory.createEmptyBorder());
		if (AwtFactory.getInstance().isFontInstalled("Arial"))
		{
			toggleButton.setFont(new Font("Arial", Font.PLAIN, 14));
		}
		else if (AwtFactory.getInstance().isFontInstalled("Helvetica"))
		{
			toggleButton.setFont(new Font("Helvetica", Font.PLAIN, 14));
		}
		updateCollapsed();

		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.X_AXIS));
		add(northPanel, BorderLayout.NORTH);
		northPanel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		// Add the button to the North (top-left corner)
		northPanel.add(toggleButton, BorderLayout.NORTH);
		northPanel.add(Box.createHorizontalStrut(3));
		northPanel.add(new JLabel(name), BorderLayout.NORTH);

		add(contentPanel, BorderLayout.CENTER);
	}
	
	public void toggleCollapsed()
	{
		isCollapsed = !isCollapsed;
		updateCollapsed();
		storeCollapsedState();
	}

	private void updateCollapsed()
	{
		if (isCollapsed)
		{
			contentPanel.setVisible(false);
			toggleButton.setText("►");
		}
		else
		{
			contentPanel.setVisible(true);
			toggleButton.setText("▼");
		}
	}

	private void storeCollapsedState()
	{
		if (isCollapsed)
		{
			UserPreferences.getInstance().collapsedPanels.add(getNameKey());
		}
		else
		{
			UserPreferences.getInstance().collapsedPanels.remove(getNameKey());
		}
	}

	private String getNameKey()
	{
		return namespace + "~" + name;
	}
}
