package com.pirasalbe.services.telegram.handlers.request;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pirasalbe.models.RequestAssociationInfo;
import com.pirasalbe.models.RequestAssociationInfo.Association;
import com.pirasalbe.models.database.Group;
import com.pirasalbe.models.request.Format;
import com.pirasalbe.models.telegram.handlers.TelegramCondition;
import com.pirasalbe.utils.DateUtils;
import com.pirasalbe.utils.RequestUtils;

/**
 * Service to manage requests from users
 *
 * @author pirasalbe
 *
 */
@Component
public class TelegramUpdateRequestHandlerService extends AbstractTelegramRequestHandlerService {

	public TelegramCondition geCondition() {
		// edit request
		return update -> update.editedMessage() != null && hasRequestTag(update.editedMessage().text());
	}

	@Override
	public void handle(TelegramBot bot, Update update) {
		Message message = update.editedMessage();

		Long chatId = message.chat().id();

		// manage only requests from active groups
		Optional<Group> optional = groupService.findById(chatId);
		if (optional.isPresent()) {

			Long userId = message.from().id();
			String content = message.text();
			String link = RequestUtils.getLink(content, message.entities());

			if (link != null) {
				Group group = optional.get();

				// check association
				RequestAssociationInfo requestAssociationInfo = requestManagementService
						.getRequestAssociationInfo(message.messageId().longValue(), group.getId(), userId, link);

				if (requestAssociationInfo.requestExists()
						&& requestAssociationInfo.getAssociation() == Association.CREATOR) {

					// request exists and user is creator
					updateRequest(message, group, content, link);

				} else if (requestAssociationInfo.getAssociation() == Association.NONE) {

					// request may or may not exists, but the association doesn't
					LocalDateTime requestTime = DateUtils.integerToLocalDateTime(message.editDate());

					newRequest(bot, message, chatId, requestTime, group, content, link);
				}
			} else {
				manageIncompleteRequest(bot, message, chatId);
			}
		}
	}

	private void updateRequest(Message message, Group group, String content, String link) {
		Format format = getFormat(content);

		requestService.update(message.messageId().longValue(), group.getId(), link, content, format,
				getSource(content, format), getOtherTags(content));
	}

}
