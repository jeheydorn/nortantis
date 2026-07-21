package nortantis.swing;

import javax.swing.*;
import java.util.Arrays;

public class SliderWithSpinner
{
	/**
	 * Idle window after the last spinner change before we treat a burst (rapid arrow clicks, held button, typing digits) as finished and
	 * fire the after-hook. JSpinner has no {@code getValueIsAdjusting()} equivalent, so we infer the end of an edit by waiting for
	 * inactivity.
	 */
	private static final int SPINNER_BURST_IDLE_MS = 400;

	private final JSlider slider;
	private final JSpinner spinner;
	private boolean updatingFromSync;
	private Runnable beforeSpinnerEdit;
	private Runnable afterSpinnerEdit;
	private boolean spinnerBurstInProgress;
	private Timer spinnerBurstEndTimer;

	public SliderWithSpinner(JSlider slider, Runnable changeListener)
	{
		this.slider = slider;
		this.spinner = new JSpinner(new SpinnerNumberModel(slider.getValue(), slider.getMinimum(), slider.getMaximum(), 1));

		int columns = Math.max(Integer.toString(slider.getMinimum()).length(), Integer.toString(slider.getMaximum()).length());
		((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(columns);
		if (slider.getMinimum() <= 0 && slider.getMaximum() >= 0)
		{
			SwingHelper.resetSpinnerToValueWhenBlank(spinner, 0);
		}

		SwingHelper.addListener(slider, () ->
		{
			if (updatingFromSync)
				return;
			updatingFromSync = true;
			try
			{
				spinner.setValue(slider.getValue());
				changeListener.run();
			}
			finally
			{
				updatingFromSync = false;
			}
		}, true);
		SwingHelper.addListener(spinner, () ->
		{
			if (updatingFromSync)
				return;
			// Spinner has no getValueIsAdjusting() — treat consecutive changes as one burst. Snapshot once at the start of the burst,
			// restart an idle timer on every change, and fire the commit hook after the timer expires (or immediately on flush).
			if (!spinnerBurstInProgress && beforeSpinnerEdit != null)
			{
				beforeSpinnerEdit.run();
			}
			spinnerBurstInProgress = true;
			updatingFromSync = true;
			try
			{
				slider.setValue(((Number) spinner.getValue()).intValue());
				changeListener.run();
			}
			finally
			{
				updatingFromSync = false;
			}
			if (afterSpinnerEdit != null)
			{
				if (spinnerBurstEndTimer == null)
				{
					spinnerBurstEndTimer = new Timer(SPINNER_BURST_IDLE_MS, e -> flushPendingSpinnerEdit());
					spinnerBurstEndTimer.setRepeats(false);
				}
				spinnerBurstEndTimer.restart();
			}
		});
	}

	public void commitEdit()
	{
		try
		{
			spinner.commitEdit();
		}
		catch (java.text.ParseException ignored)
		{
		}
		// commitEdit is called at the start of mouse presses so the user's pending spinner edit is sealed before the press mutates
		// state. Flush any in-flight burst so its undo point lands here, BEFORE the press's own undo point.
		flushPendingSpinnerEdit();
	}

	/**
	 * Immediately ends any in-progress spinner burst, firing the after-hook (typically an undo-point commit). Safe to call when no burst is
	 * in progress — it's a no-op then.
	 */
	public void flushPendingSpinnerEdit()
	{
		if (spinnerBurstEndTimer != null)
		{
			spinnerBurstEndTimer.stop();
		}
		if (spinnerBurstInProgress)
		{
			spinnerBurstInProgress = false;
			if (afterSpinnerEdit != null)
			{
				afterSpinnerEdit.run();
			}
		}
	}

	/**
	 * Registers hooks that fire for user-initiated spinner edits (not slider-driven syncs), debounced to one (before, after) pair per burst
	 * of consecutive ticks. {@code beforeSpinnerEdit} runs once when a burst starts — use it to snapshot pre-edit state.
	 * {@code afterSpinnerEdit} runs once when the burst ends ({@value #SPINNER_BURST_IDLE_MS} ms of inactivity OR an explicit
	 * {@link #flushPendingSpinnerEdit()} / {@link #commitEdit()}) — use it to commit an undo point if anything changed.
	 */
	public void setSpinnerEditHooks(Runnable beforeSpinnerEdit, Runnable afterSpinnerEdit)
	{
		this.beforeSpinnerEdit = beforeSpinnerEdit;
		this.afterSpinnerEdit = afterSpinnerEdit;
	}

	public RowHider addToOrganizer(GridBagOrganizer organizer, String label, String toolTip)
	{
		return organizer.addLabelAndComponentsHorizontal(label, toolTip, Arrays.asList(slider, spinner));
	}
}
