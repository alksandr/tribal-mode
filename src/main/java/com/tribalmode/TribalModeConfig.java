package com.tribalmode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("tribalmode")
public interface TribalModeConfig extends Config
{
	@ConfigItem(
			position = 1,
			keyName = "hidePlayers",
			name = "Hide Players",
			description = "Hides the players who are not in the clan chat."
	)

	default boolean hidePlayers() {
		return true;
	}

	@ConfigItem(
			position = 2,
			keyName = "displayMasks",
			name = "Display Masks",
			description = "Displays the tribal masks."
	)

	default boolean displayMasks() {
		return true;
	}

	@ConfigItem(
			position = 3,
			keyName = "tribeMembers",
			name = "My Tribe Members",
			description = "Names of your tribe members, one per line"
	)
	default String tribeMembers()
	{
		return "";
	}

	@ConfigItem(
			position = 4,
			keyName = "enemyTribeMembers",
			name = "Enemy Tribe Members",
			description = "Names of enemy tribe members, one per line"
	)
	default String enemyTribeMembers()
	{
		return "";
	}
}
