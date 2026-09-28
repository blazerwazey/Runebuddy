package com.runebuddy.engine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.runebuddy.data.DataStore;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoalPlannerTest
{
	private static final int BANDOS_CHESTPLATE = 11832;

	private GoalPlanner planner;

	@Before
	public void setUp()
	{
		planner = new Advisors(DataStore.load(new Gson())).getGoals();
	}

	private GoalPlan plan(Goal goal, PlayerProfile profile)
	{
		return planner.plan(goal, profile, EngineSettings.defaults(), null);
	}

	@Test
	public void skillGoalIsOneTimedTrainingStep()
	{
		GoalPlan plan = plan(Goal.skill(Skill.WOODCUTTING, 60), PlayerProfile.flat(40));

		assertTrue(plan.isRecognised());
		assertFalse(plan.isComplete());
		assertEquals("60 Woodcutting", plan.getTitle());
		assertEquals(1, plan.getSteps().size());

		GoalStep step = plan.getSteps().get(0);
		assertEquals(GoalStep.Kind.SKILL, step.getKind());
		assertNotNull(step.getRoute());
		assertTrue(plan.getHours() > 0);
		assertEquals(0, plan.getUntimedSteps());
		assertTrue(plan.getProgress() > 0 && plan.getProgress() < 1);
		assertEquals(step, plan.nextStep());
	}

	@Test
	public void reachedSkillGoalIsComplete()
	{
		GoalPlan plan = plan(Goal.skill(Skill.WOODCUTTING, 60), PlayerProfile.flat(70));

		assertTrue(plan.isComplete());
		assertTrue(plan.getSteps().isEmpty());
		assertEquals(1d, plan.getProgress(), 0);
		assertNull(plan.nextStep());
	}

	@Test
	public void activityGoalTimesEachMissingLevelQuickestFirst()
	{
		GoalPlan plan = plan(Goal.content("unlock_fire_cape"), PlayerProfile.flat(40));

		assertFalse(plan.isComplete());
		List<GoalStep> skillSteps = plan.getSteps().subList(0, 4);
		double total = 0;
		double previous = 0;
		for (GoalStep step : skillSteps)
		{
			assertEquals(GoalStep.Kind.SKILL, step.getKind());
			assertTrue("steps should run quickest first", step.getHours() >= previous);
			previous = step.getHours();
			total += step.getHours();
		}

		assertEquals(total, plan.getHours(), 1e-6);
	}

	@Test
	public void questPointsAreAnUntimedStep()
	{
		PlayerProfile profile = PlayerProfile.flat(60).toBuilder().questPoints(100).build();

		GoalPlan plan = plan(Goal.content("unlock_barrows_gloves"), profile);

		assertEquals(1, plan.getUntimedSteps());
		assertEquals(GoalStep.Kind.QUEST, plan.nextStep().getKind());
		assertTrue(plan.nextStep().getLabel().contains("175"));
	}

	@Test
	public void ownedGearGoalIsComplete()
	{
		PlayerProfile profile = PlayerProfile.flat(80).toBuilder().owned(BANDOS_CHESTPLATE, 1).build();

		assertTrue(plan(Goal.gear(BANDOS_CHESTPLATE), profile).isComplete());
	}

	@Test
	public void gearGoalEndsWithWhereToGetIt()
	{
		GoalPlan plan = plan(Goal.gear(BANDOS_CHESTPLATE), PlayerProfile.flat(80));

		assertEquals("Bandos chestplate", plan.getTitle());
		assertFalse(plan.isComplete());
		GoalStep last = plan.getSteps().get(plan.getSteps().size() - 1);
		assertEquals(GoalStep.Kind.ITEM, last.getKind());
		assertTrue(last.getLabel().startsWith("Get it: "));
	}

	@Test
	public void ironmenCarryTheirExtraRequirements()
	{
		PlayerProfile main = PlayerProfile.flat(70);
		PlayerProfile ironman = main.toBuilder().ironman(true).build();

		GoalPlan forMain = plan(Goal.gear(BANDOS_CHESTPLATE), main);
		GoalPlan forIronman = plan(Goal.gear(BANDOS_CHESTPLATE), ironman);

		assertEquals("a main at 70 only has to go and get it", 0d, forMain.getHours(), 0);
		assertTrue("an ironman has to be able to kill for it", forIronman.getHours() > 0);
		assertTrue(forIronman.getSteps().stream()
			.anyMatch(s -> s.getKind() == GoalStep.Kind.NOTE && s.getLabel().contains("killcount")));
	}

	@Test
	public void combatStepsCountTowardHitpoints()
	{
		PlayerProfile ironman = PlayerProfile.flat(45).toBuilder().ironman(true).build();
		GoalPlan plan = plan(Goal.gear(BANDOS_CHESTPLATE), ironman);

		double hitpointsAlone = plan(Goal.skill(Skill.HITPOINTS, 80), ironman).getHours();
		double hitpointsInGoal = plan.getSteps().stream()
			.filter(s -> s.getSkill() == Skill.HITPOINTS)
			.mapToDouble(s -> s.getHours() == null ? 0 : s.getHours())
			.sum();

		assertTrue("training Attack, Strength and Defence to 70+ raises Hitpoints too, so its step ("
				+ hitpointsInGoal + "h) must be well under training it alone (" + hitpointsAlone + "h)",
			hitpointsInGoal < hitpointsAlone / 2);
	}

	@Test
	public void goalForMissingDataIsKeptButFlagged()
	{
		GoalPlan plan = plan(Goal.content("no_such_activity"), PlayerProfile.flat(40));

		assertFalse(plan.isRecognised());
		assertFalse(plan.isComplete());
	}

	@Test
	public void goalsSurviveAJsonRoundTrip()
	{
		Gson gson = new Gson();
		List<Goal> goals = Arrays.asList(
			Goal.skill(Skill.SLAYER, 85), Goal.gear(BANDOS_CHESTPLATE), Goal.content("unlock_fire_cape"));

		String json = gson.toJson(goals);
		List<Goal> back = gson.fromJson(json, new TypeToken<List<Goal>>()
		{
		}.getType());

		assertEquals(goals, back);
	}

	@Test
	public void storedGoalWithAnUnknownSkillIsNotWellFormed()
	{
		Goal goal = new Gson().fromJson("{\"type\":\"SKILL\",\"skill\":\"DUNGEONEERING\",\"level\":50}", Goal.class);

		assertFalse(goal.isWellFormed());
	}

	@Test
	public void aNewSkillTargetReplacesTheOldOne()
	{
		assertEquals(Goal.skill(Skill.SLAYER, 85).key(), Goal.skill(Skill.SLAYER, 99).key());
	}
}
