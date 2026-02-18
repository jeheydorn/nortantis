package nortantis.swing.translation;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

@SuppressWarnings("serial")
public class TranslatedEnumRenderer extends DefaultListCellRenderer
{
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
	{
		super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		if (value instanceof Enum)
		{
			setText(Translation.enumDisplayName((Enum) value));
		}
		return this;
	}
}
