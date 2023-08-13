package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import nortantis.SettingsGenerator;
import nortantis.util.JFontChooser;
import nortantis.util.Pair;
import nortantis.util.Tuple2;

public class SwingHelper
{
	public static final int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelPreferredWidth = 300;
	public static final int sidePanelMinimumWidth = 300;
	private static final int sliderWidth = 160;
	private static final int rowVerticalInset = 10;
	public static final int colorPickerLeftPadding = 2;
	public static final int sidePanelScrollSpeed = 11; 
	private static final double labelWeight = 0.4;
	private static final double componentWeight = 0.6;
	
	private static int curY = 0;
	
	public static RowHider addLabelAndComponentToPanel(JPanel panelToAddTo, String labelText, String tooltip, JComponent component)
	{
		
		GridBagConstraints lc = new GridBagConstraints();
		lc.fill = GridBagConstraints.HORIZONTAL;
		lc.gridx = 0;
		lc.gridy = curY;
		lc.weightx = labelWeight;
		lc.anchor = GridBagConstraints.NORTHEAST;
		lc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		JLabel label = createWrappingLabel(labelText, tooltip);
		panelToAddTo.add(label, lc);
		
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = componentWeight;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		panelToAddTo.add(component, cc);
		
		curY++;
		
		return new RowHider(label, component);
	}
	
	private static JLabel createWrappingLabel(String text, String tooltip)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setToolTipText(tooltip);
		return label;
	}
	
	public static <T extends Component> RowHider addLabelAndComponentsToPanelVertical(JPanel panelToAddTo, String labelText, String tooltip, 
			List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.Y_AXIS, 0, components);
	}
	
	public static <T extends Component> RowHider addLabelAndComponentsToPanelHorizontal(JPanel panelToAddTo, String labelText, String tooltip, 
			int componentLeftPadding, List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.X_AXIS, componentLeftPadding, components);
	}
	
	private static <T extends Component> RowHider addLabelAndComponentsToPanel(JPanel panelToAddTo, String labelText, String tooltip, int boxLayoutDirection,
			int componentLeftPadding, List<T> components)
	{		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, boxLayoutDirection));
		compPanel.add(Box.createHorizontalStrut(componentLeftPadding));
		for (Component comp : components)
		{
			compPanel.add(comp);
			if (boxLayoutDirection == BoxLayout.X_AXIS && comp != components.get(components.size() - 1))
			{
				compPanel.add(Box.createHorizontalStrut(10));
			}
		}
		compPanel.add(Box.createHorizontalGlue());
		
		return addLabelAndComponentToPanel(panelToAddTo, labelText, tooltip, compPanel);
	}
	
	public static Tuple2<JPanel, JScrollPane> createPanelAndScrollPaneForLabeledComponents()
	{
		JPanel panel = createPanelForLabeledComponents();
		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(sidePanelScrollSpeed);
		return new Tuple2<>(panel, scrollPane);
	}
	
	public static JPanel createPanelForLabeledComponents()
	{
		JPanel panel = new VerticallyScrollablePanel();
		panel.setLayout(new GridBagLayout());
		return panel;
	}
	
	public static void resetGridY()
	{
		curY = 0;
	}
	
	public static void addVerticalFillerRow(JPanel panel)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.VERTICAL;
		c.gridx = 0;
		c.gridy = curY;
		c.weightx = 1.0;
		c.weighty = 1.0;
		
		JPanel filler = new JPanel();
		panel.add(filler, c);
		
		curY++;
	}
	
	/***
	 * Creates an invisible row that expands horizontally to try to stop other rows from causing the columns to become
	 * narrower or wider when components in other rows become visible or invisible.
	 * @param panelToAddTo
	 * @param componentWidthRatio a number between 0 and 1 for what ratio of with the component should occupy (as 
	 *        opposed to the label).
	 */
	public static void addHorizontalSpacerRowToHelpComponentAlignment(JPanel panelToAddTo, double componentWidthRatio)
	{
	    JPanel filler1 = new JPanel();
	    filler1.setLayout(new BoxLayout(filler1, BoxLayout.X_AXIS));
	    filler1.add(Box.createHorizontalStrut((int) (SwingHelper.sidePanelPreferredWidth * (1.0 - componentWidthRatio))));
			    
		GridBagConstraints lc = new GridBagConstraints();
		lc.fill = GridBagConstraints.HORIZONTAL;
		lc.gridx = 0;
		lc.gridy = curY;
		lc.weightx = labelWeight;
		lc.anchor = GridBagConstraints.NORTHEAST;
		panelToAddTo.add(filler1, lc);
		
	    JPanel filler2 = new JPanel();
	    filler2.setLayout(new BoxLayout(filler2, BoxLayout.X_AXIS));
	    filler2.add(Box.createHorizontalStrut((int)(SwingHelper.sidePanelPreferredWidth * componentWidthRatio)));

	    GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = componentWeight;
		cc.anchor = GridBagConstraints.LINE_START;
		panelToAddTo.add(filler2, cc);

	}

	public static void initializeComboBoxItems(JComboBox<String> comboBox, Collection<String> items, String selectedItem)
	{
		comboBox.removeAllItems();
		for (String item : items)
		{
			comboBox.addItem(item);
		}
		if (selectedItem != null && !selectedItem.isEmpty())
		{
			if (!items.contains(selectedItem))
			{
				comboBox.addItem(selectedItem);
			}
			comboBox.setSelectedItem(selectedItem);
		}
	}
	
	public static void showColorPickerWithPreviewPanel(JComponent parent, final JPanel colorDisplay, String title)
	{
		Color c = JColorChooser.showDialog(parent, "", colorDisplay.getBackground());
		if (c != null)
			colorDisplay.setBackground(c);
	}
	
	public static RowHider addLeftAlignedComponent(JPanel parent, JComponent component)
	{
		return addLeftAlignedComponent(parent, component, rowVerticalInset, rowVerticalInset);
	}
	
	public static RowHider addLeftAlignedComponent(JPanel parent, JComponent component, int topInset, int bottomInset)
	{
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 0;
		cc.gridwidth = 2;
		cc.gridy = curY;
		cc.weightx = 1;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(topInset, 5, bottomInset, 5);
		parent.add(component, cc);
		
		curY++;
		
		return new RowHider(component);
	}
	
	public static RowHider addLeftAlignedComponentWithStackedLabel(JPanel parent, String labelText, String toolTip, JComponent component)
	{
		JLabel label = new JLabel(labelText);
		label.setToolTipText(toolTip);
		RowHider labelHider = SwingHelper.addLeftAlignedComponent(parent, label, rowVerticalInset, 2);
		RowHider compHider = SwingHelper.addLeftAlignedComponent(parent, component, 0, rowVerticalInset);
		
		return new RowHider(labelHider, compHider);
	}
	
	public static void addSeperator(JPanel panelToAddTo)
	{
		final int minHeight = 2;
		
		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 0;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 5, 0, 0);
			JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
			sep.setMinimumSize(new Dimension(0, minHeight));
			panelToAddTo.add(sep, c);
		}

		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 1;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 0, 0, 5);
			JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
			sep.setMinimumSize(new Dimension(0, minHeight));
			panelToAddTo.add(sep, c);
		}

		curY++;
	}
	
	public static void setSliderWidthForSidePanel(JSlider slider)
	{
		slider.setPreferredSize(new Dimension(sliderWidth, slider.getPreferredSize().height));
	}
	
	
	public static JPanel createColorPickerPreviewPanel()
	{
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(50, 25));
		panel.setBackground(Color.BLACK);
		return panel;
	}

	public static void showColorPicker(JComponent parent, final JPanel colorDisplay, String title)
	{
		final JColorChooser colorChooser = new JColorChooser(colorDisplay.getBackground());
		colorChooser.setPreviewPanel(new JPanel());

		ActionListener okHandler = new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				colorDisplay.setBackground(colorChooser.getColor());
			}

		};
		Dialog dialog = JColorChooser.createDialog(colorDisplay, title, false, colorChooser, okHandler, null);
		dialog.setVisible(true);

	}
	
	public static Tuple2<JLabel, JButton> createFontChooser(JPanel panelToAddTo, String labelText, int height)
	{
		// TODO Make the choose button stay to the left when the display label gets big.
		final int spaceUnderFontDisplays = 4;
		JLabel fontDisplay = new JLabel("");
		JPanel displayHolder = new JPanel();
		displayHolder.setLayout(new BorderLayout());
		displayHolder.add(fontDisplay);
		displayHolder.setPreferredSize(new Dimension(displayHolder.getPreferredSize().width, height));

		final JButton chooseButton = new JButton("Choose");
		chooseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				runFontChooser(panelToAddTo, fontDisplay);
			}
		});
		JPanel chooseButtonHolder = new JPanel();
		chooseButtonHolder.setLayout(new BoxLayout(chooseButtonHolder, BoxLayout.X_AXIS));
		chooseButtonHolder.add(chooseButton);
		chooseButtonHolder.add(Box.createHorizontalGlue());
		SwingHelper.addLabelAndComponentsToPanelVertical(panelToAddTo, labelText, "",
				Arrays.asList(displayHolder, Box.createVerticalStrut(spaceUnderFontDisplays), chooseButtonHolder));
		
		
		return new Tuple2<>(fontDisplay, chooseButton);
	}
	

	private static void runFontChooser(JComponent parent, JLabel fontDisplay)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setSelectedFont(fontDisplay.getFont());
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			Font font = fontChooser.getSelectedFont();
			fontDisplay.setText(font.getFontName());
			fontDisplay.setFont(font);
		}
	}
	
	public static JPanel createBooksPanel()
	{
		JPanel booksPanel = new JPanel();
		booksPanel.setLayout(new BoxLayout(booksPanel, BoxLayout.Y_AXIS));
		
		createBooksCheckboxes(booksPanel);
		
		return booksPanel;
	}
	
	public static void createBooksCheckboxes(JPanel booksPanel)
	{
		for (String book : SettingsGenerator.getAllBooks())
		{
			final JCheckBox checkBox = new JCheckBox(book);
			booksPanel.add(checkBox);
		}
	}
	
	// TODO remove
//	public static List<JCheckBox> createBooksCheckboxes()
//	{
//		return SettingsGenerator.getAllBooks().stream().map(bookName -> new JCheckBox(bookName)).collect(Collectors.toList());
//	}
	
	public static void checkSelectedBooks(JPanel booksPanel, Set<String> selectedBooks)
	{
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				checkBox.setSelected(selectedBooks.contains(checkBox.getText()));
			}
		}
	}
	
	
	public static Set<String> getSelectedBooksFromGUI(JPanel booksPanel)
	{
		Set<String> books = new TreeSet<>();
		for (Component component : booksPanel.getComponents())
		{
			if (component instanceof JCheckBox)
			{
				JCheckBox checkBox = (JCheckBox) component;
				if (checkBox.isSelected())
				{
					books.add(checkBox.getText());
				}
			}
		}
		
		return books;
	}
	
	public static void setEnabled(Component component, boolean enabled)
	{
		component.setEnabled(enabled);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				setEnabled(child, enabled);
			}
		}
	}
	
	public static Tuple2<JComboBox<ImageIcon>, RowHider> createBrushSizeComboBox(JPanel panelToAddTo, List<Integer> brushSizes)
	{
	    JComboBox<ImageIcon> brushSizeComboBox = new JComboBox<>();
	    int largest = Collections.max(brushSizes);
	    for (int brushSize : brushSizes)
	    {
	    	if (brushSize == 1)
	    	{
	    		brushSize = 4; // Needed to make it visible
	    	}
	    	BufferedImage image = new BufferedImage(largest, largest, BufferedImage.TYPE_INT_ARGB);
	    	Graphics2D g = image.createGraphics();
	    	g.setColor(Color.white);
	    	g.setColor(Color.black);
	    	g.fillOval(largest/2 - brushSize/2, largest/2 - brushSize/2, brushSize, brushSize);
	    	brushSizeComboBox.addItem(new ImageIcon(image));
	    }
	    brushSizeComboBox.setPreferredSize(new Dimension(largest + 40, brushSizeComboBox.getPreferredSize().height));
	    JPanel brushSizeContainer = new JPanel();
	    brushSizeContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
	    brushSizeContainer.add(brushSizeComboBox);
	    RowHider brushSizeHider = SwingHelper.addLabelAndComponentToPanel(panelToAddTo, "Brush size:", "", brushSizeContainer);
	    
	    return new Tuple2<>(brushSizeComboBox, brushSizeHider);
	}
}
