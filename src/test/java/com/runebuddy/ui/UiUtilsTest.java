package com.runebuddy.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UiUtilsTest
{
	@Test
	public void hoursAreRoundedToUsefulPrecision()
	{
		assertEquals("45 min", UiUtils.hours(0.75));
		assertEquals("1 min", UiUtils.hours(0.001));
		assertEquals("4.5h", UiUtils.hours(4.5));
		assertEquals("123h", UiUtils.hours(122.6));
		assertEquals("?", UiUtils.hours(Double.POSITIVE_INFINITY));
	}

	@Test
	public void goldTotalsAreSignedAndShort()
	{
		assertEquals("free", UiUtils.goldTotal(0));
		assertEquals("+950", UiUtils.goldTotal(950));
		assertEquals("+12.5k", UiUtils.goldTotal(12_500));
		assertEquals("-6.4m", UiUtils.goldTotal(-6_397_826));
		assertEquals("+178m", UiUtils.goldTotal(177_528_615));
		assertEquals("+2m", UiUtils.goldTotal(2_000_000));
		assertEquals("-3b", UiUtils.goldTotal(-3_000_000_000L));
	}
}
