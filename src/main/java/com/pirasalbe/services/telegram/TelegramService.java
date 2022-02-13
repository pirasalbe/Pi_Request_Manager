package com.pirasalbe.services.telegram;

import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.model.Chat.Type;
import com.pirasalbe.models.telegram.handlers.TelegramCondition;
import com.pirasalbe.services.telegram.conditions.TelegramCallbackQueryConditionFactory;
import com.pirasalbe.services.telegram.conditions.TelegramCallbackQueryConditionFactory.Condition;
import com.pirasalbe.services.telegram.conditions.TelegramChatConditionFactory;
import com.pirasalbe.services.telegram.conditions.TelegramCommandConditionFactory;
import com.pirasalbe.services.telegram.conditions.TelegramReplyToCommandConditionFactory;
import com.pirasalbe.services.telegram.conditions.TelegramRoleConditionFactory;
import com.pirasalbe.services.telegram.handlers.command.TelegramAliveCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.command.TelegramContributorsCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.command.TelegramGroupsCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.command.TelegramHelpCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.command.TelegramMeCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.command.TelegramSuperAdminCommandHandlerService;
import com.pirasalbe.services.telegram.handlers.request.TelegramNewRequestHandlerService;
import com.pirasalbe.services.telegram.handlers.request.TelegramUpdateRequestHandlerService;

/**
 * Service to manage the telegram logic
 *
 * @author pirasalbe
 *
 */
@Component
public class TelegramService {

	@Autowired
	private TelegramBotService bot;

	/*
	 * CONDITIONS
	 */

	@Autowired
	private TelegramChatConditionFactory chatConditionFactory;

	@Autowired
	@Qualifier("telegramCommandConditionFactory")
	private TelegramCommandConditionFactory commandConditionFactory;

	@Autowired
	private TelegramReplyToCommandConditionFactory replyToCommandConditionFactory;

	@Autowired
	private TelegramCallbackQueryConditionFactory callbackQueryConditionFactory;

	@Autowired
	private TelegramRoleConditionFactory roleConditionFactory;

	/*
	 * HANDLERS
	 */

	@Autowired
	private TelegramHelpCommandHandlerService helpCommandHandlerService;

	@Autowired
	private TelegramAliveCommandHandlerService aliveCommandHandlerService;

	@Autowired
	private TelegramMeCommandHandlerService meCommandHandlerService;

	@Autowired
	private TelegramSuperAdminCommandHandlerService superAdminCommandHandlerService;

	@Autowired
	private TelegramGroupsCommandHandlerService groupsCommandHandlerService;

	@Autowired
	private TelegramNewRequestHandlerService newRequestHandlerService;

	@Autowired
	private TelegramUpdateRequestHandlerService updateRequestHandlerService;

	@Autowired
	private TelegramContributorsCommandHandlerService contributorsCommandHandlerService;

	@PostConstruct
	public void initialize() {

		// help
		bot.register(commandConditionFactory.onCommand(TelegramHelpCommandHandlerService.COMMAND),
				helpCommandHandlerService);

		// alive
		bot.register(commandConditionFactory.onCommands(TelegramAliveCommandHandlerService.COMMANDS),
				aliveCommandHandlerService);

		// me
		bot.register(commandConditionFactory.onCommand(TelegramMeCommandHandlerService.COMMAND),
				meCommandHandlerService);

		// super admin
		registerSuperAdminHandlers();

		// groups
		registerGroupsHandlers();

		// requests
		registerRequestsHandlers();

		// contributors
		registerContributorsHandlers();

		bot.launch();

	}

	private void registerSuperAdminHandlers() {
		TelegramCondition superAdminChatCondition = chatConditionFactory.onChatType(Type.Private);
		TelegramCondition superAdminRoleCondition = roleConditionFactory
				.onRole(TelegramSuperAdminCommandHandlerService.ROLE);

		// main command
		bot.register(Arrays.asList(superAdminChatCondition,
				commandConditionFactory.onCommand(TelegramSuperAdminCommandHandlerService.COMMAND),
				superAdminRoleCondition), superAdminCommandHandlerService.showActions());

		// list
		bot.register(
				Arrays.asList(
						callbackQueryConditionFactory.onCallbackQuery(
								TelegramSuperAdminCommandHandlerService.COMMAND_LIST, Condition.STARTS_WITH),
						superAdminRoleCondition),
				superAdminCommandHandlerService.listUsers());
		bot.register(
				Arrays.asList(
						callbackQueryConditionFactory.onCallbackQuery(
								TelegramSuperAdminCommandHandlerService.COMMAND_COPY, Condition.STARTS_WITH),
						superAdminRoleCondition),
				superAdminCommandHandlerService.copyUser());

		// add
		bot.register(
				Arrays.asList(
						callbackQueryConditionFactory
								.onCallbackQuery(TelegramSuperAdminCommandHandlerService.COMMAND_ADD, Condition.EQUALS),
						superAdminRoleCondition),
				superAdminCommandHandlerService.addUserHelp());
		bot.register(Arrays.asList(
				replyToCommandConditionFactory.onCommand(TelegramSuperAdminCommandHandlerService.COMMAND_ADD),
				superAdminRoleCondition), superAdminCommandHandlerService.addUser());

		// remove
		bot.register(
				Arrays.asList(
						callbackQueryConditionFactory.onCallbackQuery(
								TelegramSuperAdminCommandHandlerService.COMMAND_REMOVE, Condition.EQUALS),
						superAdminRoleCondition),
				superAdminCommandHandlerService.removeUserHelp());
		bot.register(
				Arrays.asList(replyToCommandConditionFactory
						.onCommand(TelegramSuperAdminCommandHandlerService.COMMAND_REMOVE), superAdminRoleCondition),
				superAdminCommandHandlerService.removeUser());

	}

	private void registerGroupsHandlers() {
		TelegramCondition groupChatCondition = chatConditionFactory
				.onChatTypes(Arrays.asList(Type.group, Type.supergroup));
		TelegramCondition groupRoleCondition = roleConditionFactory.onRole(TelegramGroupsCommandHandlerService.ROLE);

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_INFO),
				groupRoleCondition), groupsCommandHandlerService.showInfo());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_ENABLE),
				groupRoleCondition), groupsCommandHandlerService.enableGroup());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_DISABLE),
				groupRoleCondition), groupsCommandHandlerService.disableGroup());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_REQUEST_LIMIT),
				groupRoleCondition), groupsCommandHandlerService.updateRequestLimit());

		bot.register(
				Arrays.asList(groupChatCondition,
						commandConditionFactory.onCommand(
								TelegramGroupsCommandHandlerService.COMMAND_AUDIOBOOK_DAYS_WAIT),
						groupRoleCondition),
				groupsCommandHandlerService.updateAudiobooksDaysWait());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(
						TelegramGroupsCommandHandlerService.COMMAND_ENGLISH_AUDIOBOOK_DAYS_WAIT),
				groupRoleCondition), groupsCommandHandlerService.updateEnglishAudiobooksDaysWait());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_ALLOW),
				groupRoleCondition), groupsCommandHandlerService.updateAllow());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramGroupsCommandHandlerService.COMMAND_NO_REPEAT),
				groupRoleCondition), groupsCommandHandlerService.updateNoRepeat());
	}

	private void registerRequestsHandlers() {
		bot.register(newRequestHandlerService.geCondition(), newRequestHandlerService);
		bot.register(updateRequestHandlerService.geCondition(), updateRequestHandlerService);
	}

	private void registerContributorsHandlers() {
		TelegramCondition groupChatCondition = chatConditionFactory
				.onChatTypes(Arrays.asList(Type.group, Type.supergroup));
		TelegramCondition contributorRoleCondition = roleConditionFactory
				.onRole(TelegramContributorsCommandHandlerService.ROLE);

		bot.register(Arrays.asList(
				chatConditionFactory.onChatTypes(Arrays.asList(Type.group, Type.supergroup, Type.Private)),
				commandConditionFactory.onCommand(TelegramContributorsCommandHandlerService.COMMAND_GET),
				contributorRoleCondition), contributorsCommandHandlerService.getGroupRequests());

		bot.register(
				Arrays.asList(groupChatCondition,
						commandConditionFactory.onCommand(TelegramContributorsCommandHandlerService.COMMAND_PENDING),
						contributorsCommandHandlerService.replyToMessageCondition(), contributorRoleCondition),
				contributorsCommandHandlerService.markPending());

		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramContributorsCommandHandlerService.COMMAND_CANCEL),
				contributorRoleCondition), contributorsCommandHandlerService.markCancelled());
		bot.register(Arrays.asList(groupChatCondition,
				commandConditionFactory.onCommand(TelegramContributorsCommandHandlerService.COMMAND_REMOVE),
				contributorRoleCondition), contributorsCommandHandlerService.removeRequest());

		bot.register(
				Arrays.asList(groupChatCondition,
						commandConditionFactory.onCommand(TelegramContributorsCommandHandlerService.COMMAND_DONE),
						contributorsCommandHandlerService.replyToMessageCondition(), contributorRoleCondition),
				contributorsCommandHandlerService.markDone());
		bot.register(
				Arrays.asList(groupChatCondition,
						commandConditionFactory
								.onCommand(TelegramContributorsCommandHandlerService.COMMAND_SILENT_DONE),
						contributorsCommandHandlerService.replyToMessageCondition(), contributorRoleCondition),
				contributorsCommandHandlerService.markDoneSilently());

		bot.register(Arrays.asList(groupChatCondition,
				contributorsCommandHandlerService.replyToMessageWithFileCondition(), contributorRoleCondition),
				contributorsCommandHandlerService.markDoneWithFile());
	}

}
