package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import nortantis.geom.Point;
import nortantis.util.Tuple2;

public class GridBagOrganizer
{
	public final JPanel panel;
	private final double labelWeight = 0.4;
	private final double componentWeight = 0.6;
	private int curY = 0;
	public static final int rowVerticalInset = 10;
	private final int defaultHorizontalSpaceBetweenComponentsAddAsList = 10;

	public GridBagOrganizer()
	{
		panel = new VerticallyScrollablePanel();
		panel.setLayout(new GridBagLayout());
	}

	public RowHider addLabelAndComponent(String labelText, String tooltip, JComponent component)
	{
		return addLabelAndComponent(labelText, tooltip, component, rowVerticalInset);
	}

	public RowHider addLabelAndComponent(String labelText, String tooltip, JComponent component, int topInset)
	{
		GridBagConstraints lc = new GridBagConstraints();
		lc.fill = GridBagConstraints.HORIZONTAL;
		lc.gridx = 0;
		lc.gridy = curY;
		lc.weightx = labelWeight;
		lc.weighty = 0;
		lc.anchor = GridBagConstraints.NORTHEAST;
		lc.insets = new Insets(topInset, 5, rowVerticalInset, 5);
		JLabel label = createWrappingLabel(labelText, tooltip);
		panel.add(label, lc);

		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = componentWeight;
		cc.weighty = 0;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(topInset, 5, rowVerticalInset, 5);
		panel.add(component, cc);

		curY++;

		return new RowHider(label, component);
	}

	private JLabel createWrappingLabel(String text, String tooltip)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setToolTipText(tooltip);
		return label;
	}

	public <T extends Component> RowHider addLabelAndComponentsVertical(String labelText, String tooltip, List<T> components)
	{
		return addLabelAndComponents(labelText, tooltip, BoxLayout.Y_AXIS, components, 0, 0, null);
	}

	public <T extends Component> RowHider addLabelAndComponentsVerticalWithComponentPanel(String labelText, String tooltip,
			List<T> components, JPanel compPanel)
	{
		return addLabelAndComponents(labelText, tooltip, BoxLayout.Y_AXIS, components, 0, 0, compPanel);
	}

	public <T extends Component> RowHider addLabelAndComponentsHorizontal(String labelText, String tooltip, List<T> components)
	{
		return addLabelAndComponentsHorizontal(labelText, tooltip, components, 0);
	}

	public <T extends Component> RowHider addLabelAndComponentsHorizontal(String labelText, String tooltip, List<T> components,
			int componentLeftPadding)
	{
		return addLabelAndComponents(labelText, tooltip, BoxLayout.X_AXIS, components, componentLeftPadding,
				defaultHorizontalSpaceBetweenComponentsAddAsList, null);
	}

	public <T extends Component> RowHider addLabelAndComponentsHorizontal(String labelText, String tooltip, List<T> components,
			int componentLeftPadding, int horizontalSpaceBetweenComponents)
	{
		return addLabelAndComponents(labelText, tooltip, BoxLayout.X_AXIS, components, componentLeftPadding,
				horizontalSpaceBetweenComponents, null);
	}

	public static <T extends Component> void updateComponentsPanelVertical(List<T> components, JPanel compPanel)
	{
		updateComponentsPanel(BoxLayout.Y_AXIS, components, 0, 0, compPanel);
	}

	private static <T extends Component> void updateComponentsPanel(int boxLayoutDirection, List<T> components, int componentLeftPadding,
			int horizontalSpaceBetweenComponents, JPanel compPanel)
	{
		compPanel.removeAll();
		compPanel.setLayout(new BoxLayout(compPanel, boxLayoutDirection));
		compPanel.add(Box.createHorizontalStrut(componentLeftPadding));
		for (Component comp : components)
		{
			compPanel.add(comp);
			if (boxLayoutDirection == BoxLayout.X_AXIS && comp != components.get(components.size() - 1))
			{
				compPanel.add(Box.createHorizontalStrut(horizontalSpaceBetweenComponents));
			}
		}
		compPanel.add(Box.createHorizontalGlue());
	}

	private <T extends Component> RowHider addLabelAndComponents(String labelText, String tooltip, int boxLayoutDirection,
			List<T> components, int componentLeftPadding, int horizontalSpaceBetweenComponents, JPanel compPanel)
	{
		if (compPanel == null)
		{
			compPanel = new JPanel();
		}

		updateComponentsPanel(boxLayoutDirection, components, componentLeftPadding, horizontalSpaceBetweenComponents, compPanel);

		return addLabelAndComponent(labelText, tooltip, compPanel);
	}

	public void resetGridY()
	{
		curY = 0;
	}

	public void addVerticalFillerRow()
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
	 * Creates an invisible row that expands horizontally to try to stop other rows from causing the columns to become narrower or wider
	 * when components in other rows become visible or invisible.
	 * 
	 * @param componentWidthRatio
	 *            a number between 0 and 1 for what ratio of with the component should occupy (as opposed to the label).
	 */
	public void addHorizontalSpacerRowToHelpComponentAlignment(double componentWidthRatio)
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
		panel.add(filler1, lc);

		JPanel filler2 = new JPanel();
		filler2.setLayout(new BoxLayout(filler2, BoxLayout.X_AXIS));
		filler2.add(Box.createHorizontalStrut((int) (SwingHelper.sidePanelPreferredWidth * componentWidthRatio)));

		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.HORIZONTAL;
		cc.gridx = 1;
		cc.gridy = curY;
		cc.weightx = componentWeight;
		cc.anchor = GridBagConstraints.LINE_START;
		panel.add(filler2, cc);

	}

	public JScrollPane createScrollPane()
	{
		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(SwingHelper.sidePanelScrollSpeed);
		return scrollPane;
	}

	public RowHider addLeftAlignedComponent(Component component)
	{
		return addLeftAlignedComponent(component, rowVerticalInset, rowVerticalInset);
	}

	public RowHider addLeftAlignedComponents(List<Component> components)
	{
		JPanel compPanel = new JPanel();
		compPanel.setLayout(new WrapLayout(WrapLayout.LEFT));
		// Remove the horizontal and vertical gaps from the border around the elements.
		compPanel.setBorder(BorderFactory.createEmptyBorder(-5, -5, -5, -5));
		for (Component comp : components)
		{
			compPanel.add(comp);
			if (comp != components.get(components.size() - 1))
			{
				compPanel.add(Box.createHorizontalStrut(0));
			}
		}
		return addLeftAlignedComponent(compPanel);

	}

	public RowHider addLeftAlignedComponent(Component component, boolean allowToExpandVertically)
	{
		return addLeftAlignedComponent(component, rowVerticalInset, rowVerticalInset, allowToExpandVertically);
	}

	public RowHider addLeftAlignedComponent(Component component, int topInset, int bottomInset)
	{
		return addLeftAlignedComponent(component, topInset, bottomInset, true);
	}

	public RowHider addLeftAlignedComponent(Component component, int topInset, int bottomInset, boolean allowToExpandVertically)
	{
		return addLeftAlignedComponent(component, topInset, bottomInset, allowToExpandVertically, 1.0);
	}

	public RowHider addLeftAlignedComponent(Component component, int topInset, int bottomInset, boolean allowToExpandVertically,
			double verticalWeight)
	{
		GridBagConstraints cc = new GridBagConstraints();
		if (allowToExpandVertically)
		{
			cc.fill = GridBagConstraints.BOTH;
		}
		else
		{
			cc.fill = GridBagConstraints.HORIZONTAL;
		}
		cc.gridx = 0;
		cc.gridwidth = 2;
		cc.gridy = curY;
		cc.weightx = 1;
		if (allowToExpandVertically)
		{
			cc.weighty = verticalWeight;
		}
		else
		{
			cc.weighty = 0;
		}
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(topInset, 5, bottomInset, 5);
		panel.add(component, cc);

		curY++;

		return new RowHider(component);
	}

	public RowHider addExpandableRowComponent(Component component, int topInset, int bottomInset, double verticalWeight)
	{
		GridBagConstraints cc = new GridBagConstraints();
		cc.fill = GridBagConstraints.BOTH;

		cc.gridx = 0;
		cc.gridwidth = 2;
		cc.gridy = curY;
		cc.weightx = 1;
		cc.weighty = verticalWeight;
		cc.anchor = GridBagConstraints.LINE_START;
		cc.insets = new Insets(topInset, 0, bottomInset, 0);
		panel.add(component, cc);

		curY++;

		return new RowHider(component);
	}

	public RowHider addLeftAlignedComponentWithStackedLabel(String labelText, String toolTip, JComponent component)
	{
		return addLeftAlignedComponentWithStackedLabel(labelText, toolTip, component, true, 1.0);
	}

	public RowHider addLeftAlignedComponentWithStackedLabel(String labelText, String toolTip, JComponent component,
			boolean allowToExpandVertically)
	{
		return addLeftAlignedComponentWithStackedLabel(labelText, toolTip, component, allowToExpandVertically, 1.0);
	}

	public RowHider addLeftAlignedComponentWithStackedLabel(String labelText, String toolTip, JComponent component,
			boolean allowToExpandVertically, double verticalWeight)
	{
		return addLeftAlignedComponentWithStackedLabel(labelText, toolTip, component, rowVerticalInset, rowVerticalInset,
				allowToExpandVertically, verticalWeight);
	}

	public RowHider addLeftAlignedComponentWithStackedLabel(String labelText, String toolTip, JComponent component, int topInset,
			int bottomInset, boolean allowToExpandVertically, double verticalWeight)
	{
		JLabel label = new JLabel(labelText);
		label.setToolTipText(toolTip);
		RowHider labelHider = addLeftAlignedComponent(label, topInset, 2, false);
		RowHider compHider = addLeftAlignedComponent(component, 0, bottomInset, allowToExpandVertically, verticalWeight);

		return new RowHider(labelHider, compHider);
	}

	public RowHider addSeperator()
	{
		final int minHeight = 2;

		JSeparator sep1;
		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 0;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 5, 0, 0);
			sep1 = new JSeparator(JSeparator.HORIZONTAL);
			sep1.setMinimumSize(new Dimension(0, minHeight));
			panel.add(sep1, c);
		}

		JSeparator sep2;
		{
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 1;
			c.gridy = curY;
			c.weightx = 0.5;
			c.anchor = GridBagConstraints.LINE_START;
			c.insets = new Insets(0, 0, 0, 5);
			sep2 = new JSeparator(JSeparator.HORIZONTAL);
			sep2.setMinimumSize(new Dimension(0, minHeight));
			panel.add(sep2, c);
		}

		curY++;
		return new RowHider(sep1, sep2);
	}

	public Tuple2<JLabel, JButton> addFontChooser(String labelText, int height, Runnable okAction)
	{
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
				runFontChooser(panel, fontDisplay, okAction);
			}
		});
		JPanel chooseButtonHolder = new JPanel();
		chooseButtonHolder.setLayout(new BoxLayout(chooseButtonHolder, BoxLayout.X_AXIS));
		chooseButtonHolder.add(chooseButton);
		chooseButtonHolder.add(Box.createHorizontalGlue());
		addLabelAndComponentsVertical(labelText, "",
				Arrays.asList(displayHolder, Box.createVerticalStrut(spaceUnderFontDisplays), chooseButtonHolder));

		return new Tuple2<>(fontDisplay, chooseButton);
	}

	private void runFontChooser(JComponent parent, JLabel fontDisplay, Runnable okAction)
	{
		JFontChooser fontChooser = new JFontChooser();
		fontChooser.setSelectedFont(fontDisplay.getFont());
		int status = fontChooser.showDialog(parent);
		if (status == JFontChooser.OK_OPTION)
		{
			Font font = fontChooser.getSelectedFont();
			fontDisplay.setText(font.getFontName());
			fontDisplay.setFont(font);
			okAction.run();
		}
	}

	public Tuple2<JComboBox<ImageIcon>, RowHider> addBrushSizeComboBox(List<Integer> brushSizes)
	{
		final int maxBrushSizeDisplay = 70;
		JComboBox<ImageIcon> brushSizeComboBox = new JComboBox<>();
		int displaySize = Math.min(maxBrushSizeDisplay, Collections.max(brushSizes));
		for (int brushSize : brushSizes)
		{
			if (brushSize == 1)
			{
				brushSize = 4; // Needed to make it visible
			}
			BufferedImage image = new BufferedImage(displaySize, displaySize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();

			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			g.setColor(Color.black);
			int sizeToDraw = Math.min(maxBrushSizeDisplay, brushSize);
			g.fillOval(displaySize / 2 - sizeToDraw / 2, displaySize / 2 - sizeToDraw / 2, sizeToDraw, sizeToDraw);
			brushSizeComboBox.addItem(new ImageIcon(image));
			if (brushSize > maxBrushSizeDisplay)
			{
				g.setColor(new Color(187, 187, 187));
				g.setFont(new Font("default", Font.BOLD, 24));
				String text = (brushSize / maxBrushSizeDisplay) + "x";
				drawCenteredString(g, text, new Point(sizeToDraw / 2.0, sizeToDraw / 2.0));
			}
		}
		brushSizeComboBox.setPreferredSize(new Dimension(displaySize + 40, brushSizeComboBox.getPreferredSize().height));
		JPanel brushSizeContainer = new JPanel();
		brushSizeContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
		brushSizeContainer.add(brushSizeComboBox);
		RowHider brushSizeHider = addLabelAndComponent("Brush size:", "", brushSizeContainer);

		return new Tuple2<>(brushSizeComboBox, brushSizeHider);
	}

	private void drawCenteredString(Graphics2D g, String text, Point point)
	{
		FontMetrics metrics = g.getFontMetrics(g.getFont());
		int x = (int) (point.x - metrics.stringWidth(text) / 2.0);
		int y = (int) (point.y - metrics.getHeight() / 2.0 + metrics.getAscent());
		g.drawString(text, x, y);
	}

}
