package nortantis.swing;

import java.awt.Dimension;
import java.util.Arrays;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SliderWithDisplayedValue
{
	JSlider slider;
	JLabel valueDisplay;
	Runnable changeListener;
	
	public SliderWithDisplayedValue(JSlider slider)
	{
		this(slider, () -> {});
	}

	public SliderWithDisplayedValue(JSlider slider, Runnable changeListener)
	{
		this.slider = slider;
		this.changeListener = changeListener;
		
		valueDisplay = new JLabel(slider.getValue() + "");
		valueDisplay.setPreferredSize(new Dimension(24, valueDisplay.getPreferredSize().height));
		slider.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				valueDisplay.setText(slider.getValue() + "");
				if (!slider.getValueIsAdjusting())
				{
					changeListener.run();
				}	
			}
		});
	}
	
	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String toolTip)
	{
		return organizer.addLabelAndComponentsHorizontal(label, toolTip, Arrays.asList(slider, valueDisplay));
	}
}
