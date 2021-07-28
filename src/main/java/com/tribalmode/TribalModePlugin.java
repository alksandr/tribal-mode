package com.tribalmode;

import com.google.common.base.Splitter;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Tribal Mode"
)
public class TribalModePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private TribalModeConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	private final int MASK_ONE = 6335, MASK_TWO = 6337;

	private static final java.util.List<String> tribeMembers = new ArrayList<>();

	private static final java.util.List<String> otherTribeMembers = new ArrayList<>();

	private int chatImageId;

	private int getIconId() {
		return chatImageId;
	}

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::loadImages);
		hideEntities();
		log.info("Tribal mode plugin started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		resetEntityHider();
		clientThread.invoke(() -> client.runScript(ScriptID.CHAT_PROMPT_INIT));
		log.info("Tribal mode plugin stopped!");
	}

	@Provides
	TribalModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TribalModeConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			loadImages();
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (config.displayMasks()) {
			Player player = event.getPlayer();
			player.getPlayerComposition().getEquipmentIds()[KitType.JAW.getIndex()] = getMask(player) + 512;
			player.getPlayerComposition().setHash();
		}
	}

	@Subscribe
	private void onPlayerChanged(PlayerChanged ev)
	{
		if (config.displayMasks()) {
			Player player = ev.getPlayer();
			player.getPlayerComposition().getEquipmentIds()[KitType.JAW.getIndex()] = getMask(player) + 512;
			player.getPlayerComposition().setHash();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String target = Text.removeTags(event.getMenuTarget());

		if ((entryMatches(event, "Trade with") || entryMatches(event, "Accept trade")) && !isTribeMember(event.getMenuTarget()))
		{
			// Scold the player for attempting to trade with a non-group member
			event.consume();
			sendChatMessage("You can only trade with your tribe.");
			return;
		}

	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("tribalmode"))
		{
			switch(event.getKey()) {
				case "tribeMembers":
					populateTribeMembers();
					break;
				case "hidePlayers":
					if (config.hidePlayers())
						hideEntities();
					else
						resetEntityHider();
					break;
			}
		}
	}

	@Subscribe
	private void onScriptCallbackEvent(ScriptCallbackEvent event)
	{

		switch (event.getEventName()) {
			case "setChatboxInput":
				updateChatbox();
				break;

		}
	}

	@Subscribe
	private void onChatMessage(ChatMessage event)
	{
		if (event.getName() == null || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			return;
		}

		boolean isLocalPlayer = Text.standardize(event.getName()).equalsIgnoreCase(Text.standardize(client.getLocalPlayer().getName()));

		if (isLocalPlayer || isTribeMember(event.getName().toLowerCase()) || isOtherTribeMember(event.getName().toLowerCase()))
		{
			event.getMessageNode().setName(getImgTag() + Text.removeTags(event.getName()));
		}
	}

	@Subscribe
	private void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] menuEntries = event.getMenuEntries();
		java.util.List<MenuEntry> newEntries = new ArrayList<>(menuEntries.length);

		for (MenuEntry entry : event.getMenuEntries())
		{

			if (isTribeMember(entry.getTarget()) || isOtherTribeMember(entry.getTarget()))
			{
				entry.setTarget(getImgTag() + entry.getTarget());
			}

			newEntries.add(entry);
		}

		client.setMenuEntries(newEntries.toArray(new MenuEntry[0]));
	}

	private void loadImages() {
		final BufferedImage icon = ImageUtil.loadImageResource(TribalModePlugin.class, "/tribemask.png");

		IndexedSprite ICON = ImageUtil.getImageIndexedSprite(icon, client);

		IndexedSprite[] modIcons = client.getModIcons();
		if (modIcons != null) {
			IndexedSprite[] newArray = Arrays.copyOf(modIcons, modIcons.length + 2);
			int modIconsStart = modIcons.length - 1;

			newArray[++modIconsStart] = ICON;
			chatImageId = modIconsStart;

			client.setModIcons(newArray);
		}
	}

	private int getMask(Player player) {
		if (isOtherTribeMember(player.getName()))
			return MASK_TWO;
		return MASK_ONE;
	}

	private boolean isTribeMember(String name)
	{
		return tribeMembers.contains(cleanName(name));
	}

	private boolean isOtherTribeMember(String name)
	{
		return otherTribeMembers.contains(cleanName(name));
	}

	private void populateTribeMembers()
	{
		Splitter NEWLINE_SPLITTER = Splitter.on("\n").omitEmptyStrings().trimResults();

		tribeMembers.clear();
		tribeMembers.addAll(
				NEWLINE_SPLITTER.splitToList(config.tribeMembers())
						.stream().map(this::standardizeToJagexName).collect(Collectors.toList()));

		otherTribeMembers.clear();
		otherTribeMembers.addAll(
				NEWLINE_SPLITTER.splitToList(config.enemyTribeMembers())
						.stream().map(this::standardizeToJagexName).collect(Collectors.toList()));
	}

	private boolean entryMatches(MenuEntry entry, String option)
	{
		return entry.getOption().equals(option);
	}

	private boolean entryMatches(MenuOptionClicked event, String option)
	{
		return event.getMenuOption().equals(option);
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(message)
				.build());
	}

	private String cleanName(String playerName)
	{
		int index = playerName.indexOf('(');
		if (index == -1)
		{
			return standardizeToJagexName(playerName);
		}
		return standardizeToJagexName(playerName.substring(0, index));
	}

	private String standardizeToJagexName(String name)
	{
		return Text.standardize(Text.toJagexName(name));
	}

	private void hideEntities()
	{
		client.setIsHidingEntities(true);

		client.setOthersHidden(true);
		client.setOthersHidden2D(true);

		client.setFriendsHidden(false);
		client.setFriendsChatMembersHidden(false);

	}

	private void resetEntityHider() {
		client.setIsHidingEntities(false);

		client.setOthersHidden(false);
		client.setOthersHidden2D(false);
	}

	private String getImgTag()
	{
		return "<img=" + getIconId() + "> ";
	}

	private void updateChatbox()
	{
		Widget chatboxTypedText = client.getWidget(WidgetInfo.CHATBOX_INPUT);

		if (getIconId() == -1)
		{
			return;
		}

		if (chatboxTypedText == null || chatboxTypedText.isHidden())
		{
			return;
		}

		String[] chatbox = chatboxTypedText.getText().split(":", 2);
		String rsn = Objects.requireNonNull(client.getLocalPlayer()).getName();

		if (chatbox.length > 1)
			chatboxTypedText.setText(getImgTag() + Text.removeTags(rsn) + ":" + chatbox[1]);
	}

}
