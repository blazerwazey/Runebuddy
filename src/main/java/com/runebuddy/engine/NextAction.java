package com.runebuddy.engine;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One entry in the "what next" feed.
 *
 * <p>Entries are grouped into buckets rather than ranked on one score: a number that
 * claims to weigh "equip this shield" against "train Agility" would be neither honest
 * nor explainable, while "your Slayer task, then your goals, then quick wins, then
 * training" is both.
 */
@Value
public class NextAction
{
	@AllArgsConstructor
	@Getter
	public enum Bucket
	{
		PINNED("Slayer task"),
		GOAL("Goal"),
		QUICK_WIN("Quick win"),
		TRAINING("Train");

		private final String label;
	}

	/**
	 * Which tab holds the detail and the alternatives for an entry.
	 */
	public enum Destination
	{
		PLAN,
		SKILLS,
		GEAR,
		DO
	}

	Bucket bucket;

	String title;

	/**
	 * Why it is here, in a few words.
	 */
	String detail;

	/**
	 * Estimated hours, or null when there is no honest figure.
	 */
	@Nullable
	Double hours;

	Destination destination;

	/**
	 * The skill page to open, when the destination is the Skills tab.
	 */
	@Nullable
	Skill skill;
}
