package nortantis.util;

import java.awt.Dimension;

import javax.swing.JTextField;

@SuppressWarnings("serial")
public class JTextFieldFixed extends JTextField
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
