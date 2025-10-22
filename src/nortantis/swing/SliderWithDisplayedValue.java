package nortantis.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.function.Function;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import nortantis.editor.UserPreferences;
import nortantis.util.OSHelper;

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
		this(slider, valueFormatter, changeListener, 24);
	}

	public SliderWithDisplayedValue(JSlider slider, Function<Integer, String> valueFormatter, Runnable changeListener, int preferredWidth)
	{
		this.slider = slider;

		valueDisplay = new JLabel(getDisplayValue(valueFormatter));
		valueDisplay.setPreferredSize(new Dimension(preferredWidth, valueDisplay.getPreferredSize().height));
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
		
		// I can't seem to shut off the default displayed value in Ubuntu with the System look and feel, so 
		// hide my displayed value to avoid redundancy. 
		if (OSHelper.isLinux() && UserPreferences.getInstance().lookAndFeel == LookAndFeel.System)
		{
			valueDisplay.setVisible(false);
		}
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

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String toolTip, Component additionalComponent,
			int componentLeftPadding, int horizontalSpaceBetweenComponents)
	{
		return organizer.addLabelAndComponentsHorizontal(label, toolTip, Arrays.asList(slider, valueDisplay, additionalComponent),
				componentLeftPadding, horizontalSpaceBetweenComponents);
	}
}
