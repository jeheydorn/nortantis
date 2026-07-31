package nortantis.swing;

import nortantis.editor.UserPreferences;
import nortantis.util.OSHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Function;

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

	public SliderWithDisplayedValue(JSlider slider, Function<Integer, String> valueFormatter, Runnable changeListener, Integer preferredWidth)
	{
		this.slider = slider;

		valueDisplay = new JLabel(getDisplayValue(valueFormatter));
		if (preferredWidth != null)
		{
			valueDisplay.setPreferredSize(new Dimension(preferredWidth, valueDisplay.getPreferredSize().height));
		}
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
		//
		// Only when it really is redundant, though. Without a formatter this label shows exactly the number the
		// look and feel already paints above the knob. With one it shows something else entirely - the exported
		// image size, a slider value scaled into the units the user thinks in (27 shown as 2.7), the polygon
		// count of a sub-map - which no look and feel paints, so hiding it loses the value rather than repeating it.
		if (valueFormatter == null && OSHelper.isLinux() && UserPreferences.getInstance().lookAndFeel == LookAndFeel.System)
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

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String toolTip, Component additionalComponent, int componentLeftPadding, int horizontalSpaceBetweenComponents)
	{
		return organizer.addLabelAndComponentsHorizontal(label, toolTip, Arrays.asList(slider, valueDisplay, additionalComponent), componentLeftPadding, horizontalSpaceBetweenComponents);
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, JLabel label)
	{
		return organizer.addLabelAndComponentsHorizontal(label, Arrays.asList(slider, valueDisplay));
	}
}
