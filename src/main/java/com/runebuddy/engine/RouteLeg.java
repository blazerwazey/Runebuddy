package com.runebuddy.engine;

import com.runebuddy.data.TrainingMethod;
import lombok.Value;

/**
 * One stretch of a route trained with a single method.
 */
@Value
public class RouteLeg
{
	TrainingMethod method;

	int fromLevel;

	int toLevel;

	double hours;

	/**
	 * Gold over the whole leg: negative is a cost, positive is a profit.
	 */
	long gold;
}
