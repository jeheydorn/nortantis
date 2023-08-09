package nortantis.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.UIManager;

public class SwingHelper
{
	public static int spaceBetweenRowsOfComponents = 8;
	private static final int labelWidth = 80;
	private static final int labelColumns = 8;
	public static final int borderWidthBetweenComponents = 4;
	public static final int sidePanelWidth = 280;
	private static final int sliderWidth = 170;
	
	public static JPanel addLabelAndComponentToPanel(JPanel panelToAddTo, String labelText, String tooltip, JComponent component)
	{
		JPanel panel = new JPanel();
		panelToAddTo.add(panel);
		//panelToAddTo.add(Box.createVerticalStrut(10));

		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		int borderWidth = borderWidthBetweenComponents;
		panel.setBorder(BorderFactory.createEmptyBorder(borderWidth, borderWidth, borderWidth, borderWidth));
		
		JPanel labelPanel = new JPanel();
		labelPanel.setMaximumSize(new Dimension(labelWidth, labelPanel.getMaximumSize().height));
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));
		labelPanel.add(createWrappingLabel(labelText, tooltip));
		labelPanel.add(Box.createVerticalGlue());
		panel.add(labelPanel);
				
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, BoxLayout.X_AXIS));
		compPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, spaceBetweenRowsOfComponents, 0));
		compPanel.add(component);

		JPanel compPanelWrapper = new JPanel();
		compPanelWrapper.setLayout(new BoxLayout(compPanelWrapper, BoxLayout.Y_AXIS));
		compPanelWrapper.add(compPanel);
		compPanelWrapper.add(Box.createVerticalGlue());
		
		panel.add(compPanelWrapper);
		panel.add(Box.createHorizontalGlue());
		
		
		return panel;
	}
	
	private static Component createWrappingLabel(String text, String tooltip)
	{
		JTextArea textArea = new JTextArea(2, labelColumns);
		textArea.setText(text);
		textArea.setToolTipText(tooltip);
		textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);
		textArea.setOpaque(false);
		textArea.setEditable(false);
		textArea.setFocusable(false);
		textArea.setBackground(UIManager.getColor("Label.background"));
		textArea.setFont(UIManager.getFont("Label.font"));
		textArea.setBorder(UIManager.getBorder("Label.border"));
		textArea.setPreferredSize(new Dimension(labelWidth, textArea.getPreferredSize().height));	
		return textArea;
	}
	
	public static <T extends JComponent> JPanel addLabelAndComponentsToPanelVertical(JPanel panelToAddTo, String labelText, String tooltip, 
			List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.Y_AXIS, components);
	}
	
	public static <T extends JComponent> JPanel addLabelAndComponentsToPanelHorizontal(JPanel panelToAddTo, String labelText, String tooltip, 
			List<T> components)
	{
		return addLabelAndComponentsToPanel(panelToAddTo, labelText, tooltip, BoxLayout.X_AXIS, components);
	}
	
	private static <T extends JComponent> JPanel addLabelAndComponentsToPanel(JPanel panelToAddTo, String labelText, String tooltip, int boxLayoutDirection, 
			List<T> components)
	{		
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new BoxLayout(compPanel, boxLayoutDirection));
		for (JComponent comp : components)
		{
			compPanel.add(comp);
		}
		
		return addLabelAndComponentToPanel(panelToAddTo, labelText, tooltip, compPanel);
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
}
