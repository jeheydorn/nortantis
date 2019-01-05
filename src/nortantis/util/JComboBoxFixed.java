package nortantis.util;

import java.awt.Dimension;

import javax.swing.JComboBox;

/**
 * A JComboBox that respects when you set the maximum dimensions. See https://stackoverflow.com/questions/7581846/swing-boxlayout-problem-with-jcombobox-without-using-setxxxsize
 * @author joseph
 *
 * @param <T>
 */
@SuppressWarnings("serial")
public class JComboBoxFixed<T> extends JComboBox<T>
{
	 /** 
     * @inherited <p>
     */
    @Override
    public Dimension getMaximumSize() {
        Dimension max = super.getMaximumSize();
        max.height = getPreferredSize().height;
        return max;
    }
}
