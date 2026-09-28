package com.runebuddy.engine;

import com.runebuddy.data.DataStore;
import lombok.Getter;

/**
 * Every engine built over one data set. Swapped as a whole when the data changes, so
 * nothing ever ranks with one version of the data and plans with another.
 */
@Getter
public class Advisors
{
	private final DataStore data;
	private final RecommendationEngine engine;
	private final GearAdvisor gear;
	private final ContentAdvisor content;
	private final TimeEstimator estimator;
	private final GoalPlanner goals;
	private final SlayerAdvisor slayer;
	private final NextActions next;

	public Advisors(DataStore data)
	{
		this.data = data;
		this.engine = new RecommendationEngine(data);
		this.gear = new GearAdvisor(data);
		this.content = new ContentAdvisor(data);
		this.estimator = new TimeEstimator(engine);
		this.goals = new GoalPlanner(data, estimator, content);
		this.slayer = new SlayerAdvisor(data);
		this.next = new NextActions(this);
	}
}
