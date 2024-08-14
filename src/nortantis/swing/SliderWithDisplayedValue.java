package nortantis.swing;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.function.Function;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SliderWithDisplayedValue
{
	JSlider slider;
	JLabel valueDisplay;
	
	public SliderWithDisplayedValue(JSlider slider)
	{
		this(slider, null, null);
	}

	public SliderWithDisplayedValue(JSlider slider, Function<Integer, String> valueFormatter, Runnable changeListener)
	{
		this.slider = slider;
		
		valueDisplay = new JLabel(getDisplayValue(valueFormatter));
		valueDisplay.setPreferredSize(new Dimension(24, valueDisplay.getPreferredSize().height));
		slider.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e)
			{
				valueDisplay.setText(getDisplayValue(valueFormatter));
				
				if (changeListener != null && !slider.getValueIsAdjusting())
				{
					changeListener.run();
				}	
			}
		});
	}
	
	private String getDisplayValue(Function<Integer, String> valueFormatter)
	{
		if (valueFormatter == null)
		{
			return slider.getValue() + "";
		}
		else
		{
			return valueFormatter.apply(slider.getValue());
		}
	}
	
	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String toolTip)
	{
		return organizer.addLabelAndComponentsHorizontal(label, toolTip, Arrays.asList(slider, valueDisplay));
	}
}
