package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import nortantis.util.Pair;
import nortantis.util.Tuple2;

public class SwingHelper
{
	public static int spaceBetweenRowsOfComponents = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelWidth = 310;
	private static final int sliderWidth = 170;
	private static final int rowVerticalInset = 10;
	public static int colorPickerLeftPadding = 2;
	
	private static int curY = 0;
	
	public static RowHider addLabelAndComponentToPanel(JPanel panelToAddTo, String labelText, String tooltip, JComponent component)
	{
		
		GridBagConstraints lc = new GridBagConstraints();
		lc.fill = GridBagConstraints.HORIZONTAL;
		lc.gridx = 0;
		lc.gridy = curY;
		lc.weightx = 0.4;
		lc.anchor = GridBagConstraints.NORTHEAST;
		lc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		panelToAddTo.add(createWrappingLabel(labelText, tooltip), lc);
		
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = 0.6;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(rowVerticalInset, 5, rowVerticalInset, 5);
		panelToAddTo.add(component, cc);
		
		curY++;
		
		return new RowHider(lc, cc);
	}
	
	private static Component createWrappingLabel(String text, String tooltip)
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
	
	public static Tuple2<JPanel, JScrollPane> createPanelForLabeledComponents()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(10);
		return new Tuple2<>(panel, scrollPane);
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
	
	public static void addLeftAlignedComponent(JComponent parent, JComponent comp)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(comp);
		panel.add(Box.createHorizontalGlue());
		parent.add(panel);
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
}
