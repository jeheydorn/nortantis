package nortantis.swing;

import nortantis.editor.UserPreferences;
import nortantis.util.OSHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SliderWithDisplayedValue
{
	JSlider slider;
	JLabel valueDisplay;
	private final Function<Integer, String> valueFormatter;
	/**
	 * True while the slider stands for several values that differ, in which case the value display shows a dash rather than the slider's
	 * value. Moving the slider clears it.
	 */
	private boolean isMixed;

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
		this.valueFormatter = valueFormatter;

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
				isMixed = false;
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

	/**
	 * Sets whether the slider stands for several values that differ. Call this after setting the slider's value, since setting the value
	 * clears it.
	 */
	public void setMixed(boolean isMixed)
	{
		this.isMixed = isMixed;
		valueDisplay.setText(getDisplayValue(valueFormatter));
	}

	/**
	 * Shows values that the slider stands for together. When they differ, the slider sits on the most common one, or the first of the most
	 * common ones, and the value display shows a dash.
	 */
	public void showValues(List<Integer> values)
	{
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		for (Integer value : values)
		{
			counts.merge(value, 1, Integer::sum);
		}
		int mostCommon = values.get(0);
		int highestCount = 0;
		for (Map.Entry<Integer, Integer> entry : counts.entrySet())
		{
			if (entry.getValue() > highestCount)
			{
				mostCommon = entry.getKey();
				highestCount = entry.getValue();
			}
		}
		slider.setValue(mostCommon);
		setMixed(counts.size() > 1);
	}

	private String getDisplayValue(Function<Integer, String> valueFormatter)
	{
		if (isMixed)
		{
			return "–";
		}
		if (valueFormatter == null)
		{
			return slider.getValue() + "";
		}
		else
		{
			return valueFormatter.apply(slider.getValue());
		}
	}

	/**
	 * @param separateFromAbove
	 *            Whether to add extra space between the label and what is above it. See SwingHelper.createPanelWithLabelAbove.
	 */
	public JPanel createPanelWithLabelAbove(String label, String toolTip, boolean separateFromAbove)
	{
		return SwingHelper.createPanelWithLabelAbove(label, toolTip, Arrays.asList(slider, valueDisplay), separateFromAbove);
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
