package com.pirasalbe.services.telegram.handlers.command;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Audio;
import com.pengrad.telegrambot.model.Chat.Type;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pirasalbe.configurations.TelegramConfiguration;
import com.pirasalbe.models.ContributorAction;
import com.pirasalbe.models.UserRole;
import com.pirasalbe.models.database.Group;
import com.pirasalbe.models.database.Request;
import com.pirasalbe.models.request.Format;
import com.pirasalbe.models.request.RequestStatus;
import com.pirasalbe.models.request.Source;
import com.pirasalbe.models.telegram.handlers.TelegramCondition;
import com.pirasalbe.models.telegram.handlers.TelegramHandler;
import com.pirasalbe.services.GroupService;
import com.pirasalbe.services.RequestManagementService;
import com.pirasalbe.services.RequestService;
import com.pirasalbe.services.telegram.handlers.AbstractTelegramHandlerService;
import com.pirasalbe.utils.DateUtils;
import com.pirasalbe.utils.RequestUtils;
import com.pirasalbe.utils.TelegramConditionUtils;
import com.pirasalbe.utils.TelegramUtils;

/**
 * Service to manage contributors related commands
 *
 * @author pirasalbe
 *
 */
@Component
public class TelegramContributorsCommandHandlerService extends AbstractTelegramHandlerService {

	private static final List<String> VALID_MIME_TYPES = Arrays.asList("application/zip", "application/vnd.rar",
			"document/x-m4b", "audio/x-m4b", "audio/mpeg", "application/epub+zip", "application/vnd.amazon.mobi8-ebook",
			"application/vnd.amazon.ebook", "application/x-mobipocket-ebook", "application/pdf", "image/vnd.djvu",
			"application/octet-stream");
	private static final List<String> VALID_EXTENSIONS = Arrays.asList(".zip", ".rar", ".mobi", ".pdf", ".epub",
			".azw3", ".azw", ".txt", ".doc", ".docx", ".rtf", ".cbz", ".cbr", ".djvu", ".chm", ".fb2", ".mp3", ".m4b",
			".opus");

	private static final String START_PAYLOAD_SHOW = "^\\/start show_message=[0-9]+_group=[+-]?[0-9]+$";
	private static final String MESSAGE_INFO_CALLBACK = TelegramConditionUtils.MESSAGE_CONDITION + "[0-9]+ "
			+ TelegramConditionUtils.GROUP_CONDITION + "[+-]?[0-9]+";
	public static final String CONFIRM_CALLBACK = "^" + ContributorAction.CONFIRM + " " + MESSAGE_INFO_CALLBACK
			+ " action=[a-zA-Z]+$";
	public static final String CHANGE_STATUS_CALLBACK = "^(" + ContributorAction.PENDING + "|" + ContributorAction.PAUSE
			+ "|" + ContributorAction.DONE + "|" + ContributorAction.CANCEL + "|" + ContributorAction.REMOVE + ") "
			+ MESSAGE_INFO_CALLBACK + " " + TelegramConditionUtils.REFRESH_SHOW_CONDITION + "[0-9]+" + "$";

	public static final String COMMAND_SHOW = "/show";
	public static final String COMMAND_PENDING = "/pending";
	public static final String COMMAND_PAUSE = "/pause";
	public static final String COMMAND_CANCEL = "/cancel";
	public static final String COMMAND_REMOVE = "/remove";
	public static final String COMMAND_DONE = "/done";
	public static final String COMMAND_SILENT_DONE = "/sdone";

	public static final String COMMAND_REQUESTS = "/requests";

	private static final String REQUEST_NOT_FOUND = "Request not found";

	public static final UserRole ROLE = UserRole.CONTRIBUTOR;

	@Autowired
	private TelegramConfiguration configuration;

	@Autowired
	private GroupService groupService;

	@Autowired
	private RequestService requestService;

	@Autowired
	private RequestManagementService requestManagementService;

	public TelegramCondition replyToMessageCondition() {
		return this::replyToMessage;
	}

	private boolean replyToMessage(Update update) {
		return update.message() != null && update.message().replyToMessage() != null;
	}

	private String getErrorMessage(String command) {
		StringBuilder builder = new StringBuilder();

		builder.append("The right format is: <code>");
		builder.append(command);
		builder.append("</code> [message id]");

		return builder.toString();
	}

	public TelegramHandler markPending() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);

			Optional<Group> optional = groupService.findById(chatId);

			if (optional.isPresent()) {
				Message message = update.message().replyToMessage();

				boolean success = requestManagementService.markPending(message, optional.get());

				// send a message to notify operation
				String link = TelegramUtils.getLink(message);
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append(requestStatusMessage(link, success, "marked as pending"));
				SendMessage sendMessage = new SendMessage(chatId, stringBuilder.toString());
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.SECONDS);
				deleteMessage(bot, update.message());
			}
		};
	}

	public TelegramHandler markPaused() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);

			Optional<Group> optional = groupService.findById(chatId);

			if (optional.isPresent()) {
				Message message = update.message().replyToMessage();

				boolean success = requestManagementService.markPaused(message, optional.get());

				// send a message to notify operation
				String link = TelegramUtils.getLink(message);
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append(requestStatusMessage(link, success, "marked as paused"));
				SendMessage sendMessage = new SendMessage(chatId, stringBuilder.toString());
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.SECONDS);
				deleteMessage(bot, update.message());
			}
		};
	}

	private String requestStatusMessage(String link, boolean success, String successMessage) {
		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("<a href='");
		stringBuilder.append(link);
		stringBuilder.append("'>Request</a>");
		if (success) {
			stringBuilder.append(" ").append(successMessage);
		} else {
			stringBuilder.append(" not found");
		}

		return stringBuilder.toString();
	}

	public TelegramHandler markDone() {
		return markDone(true);
	}

	public TelegramHandler markDoneSilently() {
		return markDone(false);
	}

	public TelegramHandler changeStatusWithCallback() {
		return (bot, update) -> {
			String text = update.callbackQuery().data();

			Optional<Long> optionalGroupId = TelegramConditionUtils.getGroupId(text);
			Optional<Long> optionalMessageId = TelegramConditionUtils.getMessageId(text);
			Optional<Integer> optionalShowMessageId = TelegramConditionUtils.getRefreshShow(text);

			String actionString = text.substring(0, text.indexOf(' '));
			ContributorAction action = ContributorAction.valueOf(actionString);

			String result = null;
			if (optionalGroupId.isPresent() && optionalMessageId.isPresent()) {
				Long messageId = optionalMessageId.get();
				Long groupId = optionalGroupId.get();

				result = performAction(action, messageId, groupId);

				if (optionalShowMessageId.isPresent()) {
					// delete original messsage and send it again
					DeleteMessage deleteMessage = new DeleteMessage(update.callbackQuery().from().id(),
							optionalShowMessageId.get());
					bot.execute(deleteMessage);

					sendRequestWithAction(bot, update.callbackQuery().from().id(), groupId, messageId);
				}

			} else {
				result = "Request id not found";
			}

			// callback response
			AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery(update.callbackQuery().id());
			answerCallbackQuery.text(result);

			bot.execute(answerCallbackQuery);

			// delete confirmation button
			if (update.callbackQuery().message() != null) {
				DeleteMessage deleteMessage = new DeleteMessage(update.callbackQuery().from().id(),
						update.callbackQuery().message().messageId());
				bot.execute(deleteMessage);
			}

		};
	}

	private String performAction(ContributorAction action, Long messageId, Long groupId) {
		String result = null;

		if (action == ContributorAction.REMOVE) {

			boolean deleteRequest = requestManagementService.deleteRequest(messageId, groupId);
			result = deleteRequest ? "Request removed" : REQUEST_NOT_FOUND;

		} else if (action == ContributorAction.CANCEL || action == ContributorAction.DONE
				|| action == ContributorAction.PAUSE || action == ContributorAction.PENDING) {

			result = changeRequestStatus(action, messageId, groupId);

		} else {
			result = "Nothing to do";
		}

		return result;
	}

	private String changeRequestStatus(ContributorAction action, Long messageId, Long groupId) {
		String result;
		RequestStatus newStatus = null;

		switch (action) {
		case CANCEL:
			newStatus = RequestStatus.CANCELLED;
			break;
		case DONE:
			newStatus = RequestStatus.RESOLVED;
			break;
		case PAUSE:
			newStatus = RequestStatus.PAUSED;
			break;
		case PENDING:
		default:
			newStatus = RequestStatus.PENDING;
			break;
		}

		Optional<Group> group = groupService.findById(groupId);
		Optional<Request> optional = requestService.findById(messageId, groupId);
		if (group.isPresent() && optional.isPresent()) {
			requestManagementService.updateStatus(optional.get(), group.get(), newStatus);
			result = "Request marked as " + newStatus.getDescription();
		} else {
			result = REQUEST_NOT_FOUND;
		}
		return result;
	}

	private TelegramHandler markDone(boolean reply) {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);

			Optional<Group> optional = groupService.findById(chatId);

			if (optional.isPresent()) {
				Message message = update.message().replyToMessage();

				boolean success = requestManagementService.markDone(message, optional.get());

				// reply
				String link = TelegramUtils.getLink(message);
				if (reply) {
					markDoneWithMessage(bot, update, chatId, message);
				}

				// send a message to notify operation
				StringBuilder notificationBuilder = new StringBuilder();
				notificationBuilder.append(requestStatusMessage(link, success, "marked as done"));
				SendMessage sendMessageNotification = new SendMessage(chatId, notificationBuilder.toString());
				sendMessageNotification.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessageNotification, 5, TimeUnit.SECONDS, true);
				deleteMessage(bot, update.message());
			}

		};
	}

	private void markDoneWithMessage(TelegramBot bot, Update update, Long chatId, Message message) {
		StringBuilder replyBuilder = new StringBuilder();

		String text = TelegramUtils.removeCommand(update.message().text(), update.message().entities()).trim();

		replyBuilder.append(TelegramUtils.tagUser(message));
		replyBuilder.append(text).append("\n");

		replyBuilder.append("Request fulfilled by <code>").append(TelegramUtils.getUserName(update.message().from()))
				.append("</code>");

		SendMessage sendMessageReply = new SendMessage(chatId, replyBuilder.toString());
		sendMessageReply.parseMode(ParseMode.HTML);

		// reply to the request
		sendMessageReply.replyToMessageId(message.messageId());

		bot.execute(sendMessageReply);
	}

	public TelegramCondition replyToMessageWithFileCondition() {
		return update -> replyToMessage(update)
				&& (isValidDocument(update.message().document()) || isValidAudio(update.message().audio()));
	}

	private boolean isValidDocument(Document document) {
		return document != null && (document.mimeType().isEmpty() || VALID_MIME_TYPES.contains(document.mimeType())
				|| isValidExtension(document.fileName()));
	}

	private boolean isValidAudio(Audio audio) {
		return audio != null && (audio.mimeType().isEmpty() || VALID_MIME_TYPES.contains(audio.mimeType())
				|| isValidExtension(audio.fileName()));
	}

	private boolean isValidExtension(String filename) {
		boolean valid = false;

		String lowerFileName = filename.toLowerCase();
		for (int i = 0; i < VALID_EXTENSIONS.size() && !valid; i++) {
			String extension = VALID_EXTENSIONS.get(i);

			valid = lowerFileName.endsWith(extension);
		}

		return valid;
	}

	public TelegramHandler markDoneWithFile() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);

			Optional<Group> optional = groupService.findById(chatId);

			if (optional.isPresent()) {
				Message message = update.message().replyToMessage();

				boolean success = requestManagementService.markDone(message, optional.get());

				String link = TelegramUtils.getLink(message);
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append(requestStatusMessage(link, success, "marked as done"));
				SendMessage sendMessage = new SendMessage(chatId, stringBuilder.toString());
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.SECONDS);
			}
		};
	}

	public TelegramHandler markCancelled() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);
			String text = update.message().text();

			Optional<Group> optional = groupService.findById(chatId);
			if (optional.isPresent()) {
				String message = null;

				// get message id
				Long messageId = null;
				if (update.message().replyToMessage() != null) {
					messageId = update.message().replyToMessage().messageId().longValue();
				} else {
					String messageText = TelegramUtils.removeCommand(text, update.message().entities()).trim();
					messageId = messageText.isEmpty() ? null : Long.parseLong(messageText);
				}

				// delete message
				if (messageId != null) {
					boolean success = requestManagementService.markCancelled(messageId, optional.get());

					String link = TelegramUtils.getLink(chatId.toString(), messageId.toString());
					StringBuilder builder = new StringBuilder();
					builder.append(requestStatusMessage(link, success, "marked as cancelled"));
					message = builder.toString();
				} else {
					message = getErrorMessage(COMMAND_CANCEL);
				}

				SendMessage sendMessage = new SendMessage(chatId, message);
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.SECONDS);
				deleteMessage(bot, update.message());
			}
		};
	}

	public TelegramHandler removeRequest() {
		return (bot, update) -> {
			deleteMessage(bot, update.message());

			Long chatId = TelegramUtils.getChatId(update);
			String text = update.message().text();

			Optional<Group> optional = groupService.findById(chatId);
			if (optional.isPresent()) {
				String message = null;

				// get message id
				Long messageId = null;
				if (update.message().replyToMessage() != null) {
					messageId = update.message().replyToMessage().messageId().longValue();
				} else {
					String messageText = TelegramUtils.removeCommand(text, update.message().entities()).trim();
					messageId = messageText.isEmpty() ? null : Long.parseLong(messageText);
				}

				// delete message
				if (messageId != null) {
					boolean success = requestManagementService.deleteRequest(messageId, chatId);

					String link = TelegramUtils.getLink(chatId.toString(), messageId.toString());
					StringBuilder builder = new StringBuilder();
					builder.append(requestStatusMessage(link, success, "removed"));
					message = builder.toString();
				} else {
					message = getErrorMessage(COMMAND_REMOVE);
				}

				SendMessage sendMessage = new SendMessage(chatId, message);
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.SECONDS);
			}
		};
	}

	public TelegramHandler getRequests() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);

			String text = update.message().text();
			boolean isPrivate = update.message().chat().type() == Type.Private;
			Optional<Long> group = getGroup(chatId, text, isPrivate);

			// check if the context is valid, either enabled group or PM
			if (groupService.existsById(chatId) || isPrivate) {
				deleteMessage(bot, update.message(), !isPrivate);

				Optional<RequestStatus> status = TelegramConditionUtils.getStatus(text);
				Optional<Format> format = TelegramConditionUtils.getFormat(text);
				Optional<Source> source = TelegramConditionUtils.getSource(text);
				Optional<Boolean> optionalDescendent = TelegramConditionUtils.getDescendent(text);

				boolean descendent = optionalDescendent.isPresent() && optionalDescendent.get();
				RequestStatus requestStatus = status.orElse(RequestStatus.PENDING);
				List<Request> requests = requestService.findRequests(group, requestStatus, source, format, descendent);

				String title = getTitle(requestStatus, group, format, source, descendent);

				if (requests.isEmpty()) {
					SendMessage sendMessage = new SendMessage(chatId, title + "No requests found");
					sendMessage.parseMode(ParseMode.HTML);
					sendMessageAndDelete(bot, sendMessage, 30, TimeUnit.SECONDS, group.isPresent());
				} else {
					sendRequestList(bot, chatId, group, title, requests);
				}
			}
		};
	}

	private Optional<Long> getGroup(Long chatId, String text, boolean isPrivate) {
		Optional<Long> group;

		if (isPrivate) {
			// get requests in PM
			group = TelegramConditionUtils.getGroupId(text);
		} else {
			// get requests of the group
			group = Optional.of(chatId);
		}

		return group;
	}

	private void sendRequestList(TelegramBot bot, Long chatId, Optional<Long> group, String title,
			List<Request> requests) {
		StringBuilder builder = new StringBuilder(title);

		// chat name, when in PM
		Map<Long, String> chatNames = new HashMap<>();
		LocalDateTime now = DateUtils.getNow();
		boolean deleteMessages = group.isPresent();

		// create text foreach request
		for (int i = 0; i < requests.size(); i++) {
			Request request = requests.get(i);

			// build request text
			StringBuilder requestBuilder = new StringBuilder();
			Long messageId = request.getId().getMessageId();
			Long groupId = request.getId().getGroupId();
			requestBuilder.append("<a href='").append(TelegramUtils.getLink(groupId.toString(), messageId.toString()))
					.append("'>");

			requestBuilder.append(getChatName(chatNames, groupId)).append(" ");
			requestBuilder.append(i + 1).append("</a> ");

			requestBuilder.append(RequestUtils.getTimeBetweenDates(request.getRequestDate(), now)).append(" ago ");

			requestBuilder.append("[<a href='")
					.append(RequestUtils.getActionsLink(configuration.getUsername(), messageId, groupId))
					.append("'>Actions</a> for <code>").append(messageId).append("</code>]\n");

			String requestText = requestBuilder.toString();

			// if length is > message limit, send current text
			if (builder.length() + requestText.length() > 4096) {
				sendRequestListMessage(bot, chatId, builder.toString(), deleteMessages);
				builder = new StringBuilder(title);
			}
			builder.append(requestText);
			// send last message
			if (i == requests.size() - 1) {
				sendRequestListMessage(bot, chatId, builder.toString(), deleteMessages);
			}
		}
	}

	private void sendRequestListMessage(TelegramBot bot, Long chatId, String message, boolean deleteMessages) {
		SendMessage sendMessage = new SendMessage(chatId, message);
		sendMessage.parseMode(ParseMode.HTML);
		sendMessage.disableWebPagePreview(true);
		sendMessageAndDelete(bot, sendMessage, 5, TimeUnit.MINUTES, deleteMessages);
	}

	private String getChatName(Map<Long, String> chatNames, Long groupId) {
		String chatName = null;
		if (chatNames.containsKey(groupId)) {
			chatName = chatNames.get(groupId);
		} else {
			Optional<Group> optional = groupService.findById(groupId);
			if (optional.isPresent()) {
				chatName = optional.get().getName();
				chatNames.put(groupId, chatName);
			} else {
				chatName = "Unknown";
			}
		}

		return chatName;
	}

	public TelegramHandler showRequest() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);
			String text = update.message().text();

			Optional<Group> optional = groupService.findById(chatId);
			if (optional.isPresent()) {
				String message = null;

				// get message id
				Long messageId = null;
				if (update.message().replyToMessage() != null) {
					messageId = update.message().replyToMessage().messageId().longValue();
				} else {
					String messageText = TelegramUtils.removeCommand(text, update.message().entities()).trim();
					messageId = messageText.isEmpty() ? null : Long.parseLong(messageText);
				}

				// get message info
				if (messageId != null) {
					Optional<Request> requestOptional = requestService.findById(messageId, optional.get().getId());
					message = getRequestInfo(bot, optional.get(), requestOptional);
				} else {
					message = getErrorMessage(COMMAND_SHOW);
				}

				SendMessage sendMessage = new SendMessage(chatId, message);
				sendMessage.parseMode(ParseMode.HTML);

				sendMessageAndDelete(bot, sendMessage, 60, TimeUnit.SECONDS);
				deleteMessage(bot, update.message());
			}
		};
	}

	public TelegramCondition showRequestWithActionCondition() {
		return update -> update.message() != null && update.message().text() != null
				&& update.message().text().matches(START_PAYLOAD_SHOW);
	}

	public TelegramHandler showRequestWithAction() {
		return (bot, update) -> {
			Long chatId = TelegramUtils.getChatId(update);
			String text = update.message().text().replace('_', ' ');

			Optional<Long> optionalGroupId = TelegramConditionUtils.getGroupId(text);
			Optional<Long> optionalMessageId = TelegramConditionUtils.getMessageId(text);

			if (optionalGroupId.isPresent() && optionalMessageId.isPresent()) {
				Long groupId = optionalGroupId.get();
				Long messageId = optionalMessageId.get();

				sendRequestWithAction(bot, chatId, groupId, messageId);
				deleteMessage(bot, update.message());
			}
		};
	}

	private void sendRequestWithAction(TelegramBot bot, Long chatId, Long groupId, Long messageId) {
		Optional<Group> optional = groupService.findById(groupId);
		if (optional.isPresent()) {

			Optional<Request> requestOptional = requestService.findById(messageId, optional.get().getId());
			if (requestOptional.isPresent()) {
				RequestStatus status = requestOptional.get().getStatus();

				String message = getRequestInfo(bot, optional.get(), requestOptional);
				SendMessage sendMessage = new SendMessage(chatId, message);
				sendMessage.parseMode(ParseMode.HTML);
				sendMessage.disableWebPagePreview(true);
				InlineKeyboardMarkup inlineKeyboard = getRequestKeyboard(groupId, messageId, status);

				sendMessage.replyMarkup(inlineKeyboard);

				bot.execute(sendMessage);
			}
		}
	}

	private InlineKeyboardMarkup getRequestKeyboard(Long groupId, Long messageId, RequestStatus status) {
		String callbackMessage = TelegramConditionUtils.MESSAGE_CONDITION + messageId + " "
				+ TelegramConditionUtils.GROUP_CONDITION + groupId;
		String callbackBegin = ContributorAction.CONFIRM + " " + callbackMessage + " action=";

		InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
		InlineKeyboardButton requestButton = new InlineKeyboardButton("📚 Request")
				.url(TelegramUtils.getLink(groupId, messageId));
		InlineKeyboardButton refreshButton = new InlineKeyboardButton("📝 Refresh")
				.url(RequestUtils.getActionsLink(configuration.getUsername(), messageId, groupId));
		InlineKeyboardButton doneButton = new InlineKeyboardButton("✅ Done")
				.callbackData(callbackBegin + ContributorAction.DONE);
		InlineKeyboardButton pendingButton = new InlineKeyboardButton("⏳ Pending")
				.callbackData(callbackBegin + ContributorAction.PENDING);
		InlineKeyboardButton pauseButton = new InlineKeyboardButton("⏸ Pause")
				.callbackData(callbackBegin + ContributorAction.PAUSE);
		InlineKeyboardButton cancelButton = new InlineKeyboardButton("✖️ Cancel")
				.callbackData(callbackBegin + ContributorAction.CANCEL);
		InlineKeyboardButton removeButton = new InlineKeyboardButton("🗑 Remove")
				.callbackData(callbackBegin + ContributorAction.REMOVE);

		inlineKeyboard.addRow(requestButton, refreshButton);
		inlineKeyboard.addRow(getButton(status, RequestStatus.PAUSED, pauseButton, pendingButton),
				getButton(status, RequestStatus.RESOLVED, doneButton, pendingButton));
		inlineKeyboard.addRow(getButton(status, RequestStatus.CANCELLED, cancelButton, pendingButton), removeButton);

		return inlineKeyboard;
	}

	/**
	 * Get the right button. Pending if status == otherButtonStatus, otherwise
	 * otherButton
	 *
	 * @param status            Status of the request
	 * @param otherButtonStatus Status of the button
	 * @param otherButton       Button
	 * @param pendingButton     Pending button
	 * @return Button
	 */
	private InlineKeyboardButton getButton(RequestStatus status, RequestStatus otherButtonStatus,
			InlineKeyboardButton otherButton, InlineKeyboardButton pendingButton) {
		return status == otherButtonStatus ? pendingButton : otherButton;
	}

	private String getRequestInfo(TelegramBot bot, Group group, Optional<Request> requestOptional) {
		StringBuilder messageBuilder = new StringBuilder();

		if (requestOptional.isPresent()) {
			Request request = requestOptional.get();

			String requestInfo = RequestUtils.getRequestInfo(bot, group.getName(), request);
			messageBuilder.append(requestInfo);
		} else {
			messageBuilder.append(REQUEST_NOT_FOUND);
		}

		return messageBuilder.toString();
	}

	public TelegramHandler confirmAction() {
		return (bot, update) -> {
			String text = update.callbackQuery().data();

			Optional<ContributorAction> actionOptional = TelegramConditionUtils.getAction(text);
			if (actionOptional.isPresent()) {
				ContributorAction action = actionOptional.get();

				// reply to callback
				AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery(update.callbackQuery().id());
				bot.execute(answerCallbackQuery);

				// prepare confirmation button
				StringBuilder stringBuilder = new StringBuilder();
				stringBuilder.append("You chose to <code>");
				stringBuilder.append(action.getDescription());
				stringBuilder.append("</code>\n");
				stringBuilder.append("Are you sure you want to continue?\n");
				stringBuilder.append("<i>This message will disappear in 1 minute.</i>");
				SendMessage sendMessage = new SendMessage(update.callbackQuery().from().id(), stringBuilder.toString());
				sendMessage.parseMode(ParseMode.HTML);

				String callbackData = text.substring("confirm ".length(),
						text.indexOf(TelegramConditionUtils.ACTION_CONDITION) - 1);

				InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
				InlineKeyboardButton yesButton = new InlineKeyboardButton("✔️ Yes")
						.callbackData(action + " " + callbackData + " " + TelegramConditionUtils.REFRESH_SHOW_CONDITION
								+ update.callbackQuery().message().messageId());

				inlineKeyboard.addRow(yesButton);

				sendMessage.replyMarkup(inlineKeyboard);

				sendMessageAndDelete(bot, sendMessage, 1, TimeUnit.MINUTES);
			}
		};
	}

	private String getTitle(RequestStatus requestStatus, Optional<Long> group, Optional<Format> format,
			Optional<Source> source, boolean descendent) {
		StringBuilder title = new StringBuilder();
		title.append("<b>Requests ").append(requestStatus.getDescription()).append("</b>");
		if (group.isPresent()) {
			Long groupId = group.get();
			Optional<Group> groupOptional = groupService.findById(groupId);
			title.append("\nGroup [").append(groupOptional.orElseThrow().getName()).append(" (<code>").append(groupId)
					.append("</code>)]");
		}
		if (format.isPresent()) {
			title.append("\nFormat [").append(format.get()).append("]");
		}
		if (format.isPresent()) {
			title.append("\nFormat [").append(format.get()).append("]");
		}
		if (source.isPresent()) {
			title.append("\nSource [").append(source.get()).append("]");
		}
		title.append("\nShow ").append(
				descendent ? TelegramConditionUtils.ORDER_CONDITION_NEW : TelegramConditionUtils.ORDER_CONDITION_OLD)
				.append(" first.").append("\n\n");

		return title.toString();
	}

}
