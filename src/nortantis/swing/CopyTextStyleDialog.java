package nortantis.swing;

import nortantis.TextStylePart;
import nortantis.TextType;
import nortantis.swing.translation.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks which parts of a text style to copy to the styles of which types of text.
 */
class CopyTextStyleDialog extends JDialog
{
	/**
	 * What the user chose.
	 *
	 * @param resetsOwnStyles
	 *            Whether text of the chosen types that has its own values for the chosen parts goes back to following its type's style for
	 *            them.
	 */
	record Choice(Set<TextStylePart> parts, Set<TextType> types, boolean resetsOwnStyles)
	{
	}

	/**
	 * The parts checked when the dialog opens, unless it's given others.
	 */
	static final Set<TextStylePart> defaultParts = EnumSet.of(TextStylePart.Font);

	private final Map<TextStylePart, JCheckBox> partCheckboxes = new EnumMap<>(TextStylePart.class);
	private final Map<TextType, JCheckBox> typeCheckboxes = new EnumMap<>(TextType.class);

	/**
	 * @param initialType
	 *            The type of text checked when the dialog opens.
	 * @param initialParts
	 *            The parts checked when the dialog opens. If empty, {@link #defaultParts} are checked.
	 */
	CopyTextStyleDialog(Window owner, TextType initialType, Set<TextStylePart> initialParts, Consumer<Choice> onCopy)
	{
		super(owner, Translation.get("copyTextStyle.title"), ModalityType.APPLICATION_MODAL);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		content.add(createLeftAlignedLabel(Translation.get("copyTextStyle.whatToCopy")));
		for (TextStylePart part : TextStylePart.values())
		{
			JCheckBox checkbox = new JCheckBox(Translation.get("textStylePart." + part.name()));
			checkbox.setSelected((initialParts.isEmpty() ? defaultParts : initialParts).contains(part));
			partCheckboxes.put(part, checkbox);
		}
		JPanel partsPanel = createRowMajorGridTabbingDownColumns(new ArrayList<>(partCheckboxes.values()), 2);
		partsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(partsPanel);
		content.add(Box.createVerticalStrut(10));

		content.add(createLeftAlignedLabel(Translation.get("copyTextStyle.copyTo")));
		for (TextType type : TextType.values())
		{
			JCheckBox checkbox = new JCheckBox(type.toString());
			checkbox.setSelected(type == initialType);
			typeCheckboxes.put(type, checkbox);
		}
		JPanel typesPanel = createColumnMajorGrid(new ArrayList<>(typeCheckboxes.values()), 2);
		typesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(typesPanel);

		content.add(Box.createVerticalStrut(10));
		JCheckBox resetCheckbox = new JCheckBox(Translation.get("copyTextStyle.alsoReset"));
		resetCheckbox.setToolTipText(Translation.get("copyTextStyle.alsoReset.tooltip"));
		resetCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(resetCheckbox);

		content.add(Box.createVerticalStrut(10));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton copyButton = new JButton(Translation.get("copyTextStyle.copy"));
		copyButton.addActionListener(e ->
		{
			Set<TextStylePart> parts = EnumSet.noneOf(TextStylePart.class);
			partCheckboxes.forEach((part, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					parts.add(part);
				}
			});
			Set<TextType> types = EnumSet.noneOf(TextType.class);
			typeCheckboxes.forEach((type, checkbox) ->
			{
				if (checkbox.isSelected())
				{
					types.add(type);
				}
			});
			dispose();
			if (!parts.isEmpty() && !types.isEmpty())
			{
				onCopy.accept(new Choice(parts, types, resetCheckbox.isSelected()));
			}
		});
		JButton cancelButton = new JButton(Translation.get("common.cancel"));
		cancelButton.addActionListener(e -> dispose());
		buttons.add(copyButton);
		buttons.add(cancelButton);
		content.add(buttons);

		setContentPane(content);
		getRootPane().setDefaultButton(copyButton);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * Lays out components in columns of equal width, filling each column from top to bottom before starting the next, so that they read
	 * and tab down each column in order.
	 */
	static JPanel createColumnMajorGrid(List<? extends Component> components, int columnCount)
	{
		int rowCount = (components.size() + columnCount - 1) / columnCount;
		List<List<Component>> columns = new ArrayList<>();
		for (int col = 0; col < columnCount; col++)
		{
			List<Component> column = new ArrayList<>();
			for (int row = 0; row < rowCount && col * rowCount + row < components.size(); row++)
			{
				column.add(components.get(col * rowCount + row));
			}
			columns.add(column);
		}
		return createGridFromColumns(columns, rowCount);
	}

	/**
	 * Lays out components in columns of equal width, filling each row from left to right before starting the next, but tabbing down each
	 * column in order.
	 */
	static JPanel createRowMajorGridTabbingDownColumns(List<? extends Component> components, int columnCount)
	{
		int rowCount = (components.size() + columnCount - 1) / columnCount;
		List<List<Component>> columns = new ArrayList<>();
		for (int col = 0; col < columnCount; col++)
		{
			List<Component> column = new ArrayList<>();
			for (int i = col; i < components.size(); i += columnCount)
			{
				column.add(components.get(i));
			}
			columns.add(column);
		}
		return createGridFromColumns(columns, rowCount);
	}

	/**
	 * Tab order follows the columns because each column is its own panel.
	 */
	private static JPanel createGridFromColumns(List<List<Component>> columns, int rowCount)
	{
		JPanel grid = new JPanel(new GridLayout(1, columns.size()));
		for (List<Component> components : columns)
		{
			// A fixed row count keeps a shorter column's rows the same height as the others'.
			JPanel column = new JPanel(new GridLayout(rowCount, 1));
			for (Component component : components)
			{
				column.add(component);
			}
			grid.add(column);
		}
		return grid;
	}

	static JLabel createLeftAlignedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
